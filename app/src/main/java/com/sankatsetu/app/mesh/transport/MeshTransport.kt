package com.sankatsetu.app.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.sankatsetu.app.mesh.router.MeshLink
import com.sankatsetu.app.mesh.router.MessageRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the actual radio: BLE advertising + scanning (peer discovery) and a
 * dual GATT server/client role (every device is simultaneously both, per
 * docs/concepts/ble-mesh-protocol.md#dual-role). Feeds decoded bytes to
 * [MessageRouter] and never makes routing decisions itself.
 *
 * Permission preconditions (all checked by the caller before construction —
 * see `ui/setup/PermissionsScreen.kt`, Day 1 UI scaffold):
 * `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+), or
 * `BLUETOOTH`/`BLUETOOTH_ADMIN` + `ACCESS_FINE_LOCATION` below API 31.
 *
 * Day 1 scope: one GATT connection per discovered peer, full-duplex over a
 * single characteristic (write from central, notify from peripheral — see
 * [GattProfile]). No fragmentation, no source routing, no courier envelopes
 * yet — those land Day 2 per docs/PLAN.md.
 */
@SuppressLint("MissingPermission") // permission preconditions documented above; runtime checks happen in the UI layer
class MeshTransport(
    private val context: Context,
    private val router: MessageRouter,
    private val scope: CoroutineScope
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null
    private var meshCharacteristic: BluetoothGattCharacteristic? = null

    // Centrals currently subscribed to our peripheral (i.e. they connected to us).
    private val subscribedCentrals = ConcurrentHashMap<String, BluetoothDevice>()
    // Our outgoing connections where we act as central (i.e. we connected to them).
    private val clientConnections = ConcurrentHashMap<String, BluetoothGatt>()

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    fun start() {
        val bt = adapter ?: return
        if (!bt.isEnabled) return
        startGattServer(bt)
        startAdvertising(bt)
        startScanning(bt)
    }

    fun stop() {
        adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        gattServer?.close()
        gattServer = null
        clientConnections.values.forEach { it.close() }
        clientConnections.clear()
        subscribedCentrals.clear()
    }

    // ---------------------------------------------------------------------
    // Peripheral role: advertise + GATT server
    // ---------------------------------------------------------------------

    private fun startGattServer(bt: BluetoothAdapter) {
        val server = bluetoothManager.openGattServer(context, gattServerCallback) ?: return
        val service = BluetoothGattService(GattProfile.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristic = BluetoothGattCharacteristic(
            GattProfile.MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            GattProfile.CLIENT_CONFIG_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccd)
        service.addCharacteristic(characteristic)
        server.addService(service)

        gattServer = server
        meshCharacteristic = characteristic
    }

    private fun startAdvertising(bt: BluetoothAdapter) {
        val advertiser = bt.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(android.os.ParcelUuid(GattProfile.SERVICE_UUID))
            .setIncludeDeviceName(false) // no device-name leak beyond the service UUID itself
            .build()
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Common cause on some OEMs: too many concurrent advertisers. Non-fatal —
            // we still function as central-only until the next start() retry.
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                registerLink(PeripheralLink(device))
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribedCentrals.remove(device.address)
                router.onLinkDisconnected(linkId(device))
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == GattProfile.MESH_CHARACTERISTIC_UUID) {
                scope.launch { router.handleInboundBytes(linkId(device), value) }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == GattProfile.CLIENT_CONFIG_DESCRIPTOR_UUID) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    subscribedCentrals[device.address] = device
                } else {
                    subscribedCentrals.remove(device.address)
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Central role: scan + connect
    // ---------------------------------------------------------------------

    private fun startScanning(bt: BluetoothAdapter) {
        val scanner = bt.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(GattProfile.SERVICE_UUID))
            .build()
        // Balanced duty cycle per docs/concepts/ble-mesh-protocol.md: not the
        // most battery-efficient possible setting, but SCAN_MODE_LOW_LATENCY
        // burns too much battery for a multi-hour demo and LOW_POWER can miss
        // fast-moving handshakes. Adaptive duty cycling (idle vs "active
        // conversation" mode) is Day 2 scope.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (clientConnections.containsKey(device.address) || subscribedCentrals.containsKey(device.address)) return
            // Tie-break so two mutually-scanning devices don't both open a
            // redundant second connection to each other: only the
            // lexicographically-lesser MAC address initiates as central.
            // (Simplified stand-in for Bitchat's link-collapsing logic — see
            // FanoutSelector.kt's doc comment on what we didn't port.)
            if (adapter?.address != null && device.address < (adapter?.address ?: "")) {
                connectAsCentral(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // Non-fatal: we still function as peripheral-only until the platform recovers.
        }
    }

    private fun connectAsCentral(device: BluetoothDevice) {
        val gatt = device.connectGatt(context, false, clientGattCallback, BluetoothDevice.TRANSPORT_LE)
        clientConnections[device.address] = gatt
    }

    private val clientGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(GattProfile.REQUESTED_MTU)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clientConnections.remove(gatt.device.address)
                router.onLinkDisconnected(linkId(gatt.device))
                gatt.close()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(GattProfile.SERVICE_UUID)
                ?.getCharacteristic(GattProfile.MESH_CHARACTERISTIC_UUID) ?: return

            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(GattProfile.CLIENT_CONFIG_DESCRIPTOR_UUID)?.let { cccd ->
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }

            registerLink(CentralLink(gatt, characteristic))
        }

        @Suppress("DEPRECATION") // pre-API-33 callback signature; both are handled where this project's minSdk requires it
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != GattProfile.MESH_CHARACTERISTIC_UUID) return
            val value = characteristic.value ?: return
            scope.launch { router.handleInboundBytes(linkId(gatt.device), value) }
        }
    }

    // ---------------------------------------------------------------------
    // MeshLink implementations
    // ---------------------------------------------------------------------

    private fun registerLink(link: MeshLink) = router.onLinkConnected(link)

    private fun linkId(device: BluetoothDevice): String = device.address

    /** Represents a central that connected to *our* peripheral; we send by notifying it. */
    private inner class PeripheralLink(private val device: BluetoothDevice) : MeshLink {
        override val linkId: String = device.address

        @Suppress("DEPRECATION") // characteristic.value setter form; API-33's overload with an explicit value param is the alternative once minSdk allows dropping this
        override suspend fun send(bytes: ByteArray): Boolean {
            val server = gattServer ?: return false
            val characteristic = meshCharacteristic ?: return false
            if (!subscribedCentrals.containsKey(device.address)) return false
            characteristic.value = bytes
            return server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    /** Represents a peripheral we connected to as central; we send by writing to it. */
    private inner class CentralLink(
        private val gatt: BluetoothGatt,
        private val characteristic: BluetoothGattCharacteristic
    ) : MeshLink {
        override val linkId: String = gatt.device.address

        @Suppress("DEPRECATION")
        override suspend fun send(bytes: ByteArray): Boolean {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = bytes
            return gatt.writeCharacteristic(characteristic)
        }
    }
}
