package com.sankatsetu.app.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

sealed class LocationResult {
    data class Fix(val latitude: Double, val longitude: Double) : LocationResult()
    data object PermissionDenied : LocationResult()
    data object NoProviderAvailable : LocationResult()
    data object TimedOut : LocationResult()
}

/**
 * A single GPS fix, not continuous tracking — this app has no ongoing
 * location tracking anywhere else, and the offline-map download only ever
 * needs "where am I right now" once, to center the download. Plain
 * `LocationManager`, not Play Services' `FusedLocationProviderClient`: this
 * project already avoids frameworks it doesn't need (hand-rolled DI, no
 * Hilt; see `AppContainer`'s own doc), and Fused Location pulls in the
 * whole Google Play Services dependency graph for one GPS read.
 *
 * `ACCESS_FINE_LOCATION` is already declared in the manifest (originally
 * only for pre-Android-12 BLE scan results — see the manifest's own
 * comment) but this is the app's first *actual read* of a real location
 * fix. See `docs/adr/0020-offline-maps.md` (once written) for why that's
 * a genuine new privacy surface, not just a permission line.
 */
class LocationProvider(private val context: Context) {

    /**
     * Returns a fix within [timeoutMs], preferring a cached fix under 5
     * minutes old over waiting for a fresh one — a real GPS cold start can
     * take 30+ seconds, worse with no internet for A-GPS assistance data,
     * exactly the scenario "before a trip to a remote area" implies.
     */
    suspend fun getCurrentFix(timeoutMs: Long = 30_000): LocationResult {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return LocationResult.PermissionDenied

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationResult.NoProviderAvailable

        cachedFix(locationManager)?.let { return it }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return LocationResult.NoProviderAvailable
        }

        val fix = withTimeoutOrNull(timeoutMs) { awaitFreshFix(locationManager, provider) }
        return fix ?: LocationResult.TimedOut
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun cachedFix(locationManager: LocationManager): LocationResult.Fix? {
        val fresh = 5 * 60_000L
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < fresh }
            .maxByOrNull { it.time }
            ?.let { LocationResult.Fix(it.latitude, it.longitude) }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private suspend fun awaitFreshFix(locationManager: LocationManager, provider: String): LocationResult.Fix =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (cont.isActive) cont.resume(LocationResult.Fix(location.latitude, location.longitude)) {}
                }
            }
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
}
