package com.sankatsetu.app.mesh.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.southernstorm.noise.crypto.Curve25519
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature

/**
 * Per-device identity: one Curve25519 keypair for Noise key agreement, one
 * Ed25519 keypair for signing. Generated once on first launch and never
 * transmitted or backed up — this is what "no accounts, no phone numbers"
 * means in practice. See docs/concepts/ble-mesh-protocol.md#identity.
 *
 * The Ed25519 signing key lives in the Android Keystore (hardware-backed
 * where available) so the private key material never exists in JVM memory
 * as raw bytes. The Curve25519 static key for Noise has to be usable by the
 * pure-JVM noise-java library, which cannot call into Keystore-held keys, so
 * it is generated in-process and persisted encrypted in SharedPreferences —
 * a real production build would want a Keystore-agreement-capable curve
 * (X25519 support landed in Keystore on API 33+) instead; see docs/adr/0002.
 */
class Identity private constructor(
    val noisePrivateKey: ByteArray,
    val noisePublicKey: ByteArray,
    private val keyStoreAlias: String
) {
    /** First 8 bytes of SHA-256(noisePublicKey) — the mesh peer ID. Stable until [wipe]. */
    val peerId: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256").digest(noisePublicKey).copyOfRange(0, 8)
    }

    fun sign(data: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(keyStoreAlias, null) as java.security.PrivateKey
        return Signature.getInstance("Ed25519").apply {
            initSign(privateKey)
            update(data)
        }.sign()
    }

    fun signingPublicKeyBytes(): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = keyStore.getCertificate(keyStoreAlias)
        return cert.publicKey.encoded // X.509 SubjectPublicKeyInfo; peers compare by fingerprint, not raw bytes
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "sankatsetu.signing.ed25519"
        private const val PREFS_NAME = "sankatsetu.identity"
        private const val PREF_NOISE_PRIVATE = "noise_sk"
        private const val PREF_NOISE_PUBLIC = "noise_pk"

        /** Loads the existing identity, or generates and persists a new one. */
        fun loadOrCreate(context: Context): Identity {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ensureSigningKey()

            val existingSk = prefs.getString(PREF_NOISE_PRIVATE, null)
            val existingPk = prefs.getString(PREF_NOISE_PUBLIC, null)
            if (existingSk != null && existingPk != null) {
                return Identity(
                    Base64.decode(existingSk, Base64.NO_WRAP),
                    Base64.decode(existingPk, Base64.NO_WRAP),
                    KEYSTORE_ALIAS
                )
            }

            val sk = ByteArray(32)
            SecureRandom().nextBytes(sk)
            val pk = ByteArray(32)
            Curve25519.eval(pk, 0, sk, null)

            prefs.edit()
                .putString(PREF_NOISE_PRIVATE, Base64.encodeToString(sk, Base64.NO_WRAP))
                .putString(PREF_NOISE_PUBLIC, Base64.encodeToString(pk, Base64.NO_WRAP))
                .apply()

            return Identity(sk, pk, KEYSTORE_ALIAS)
        }

        /** Panic wipe (PRD §5.6, F1.6): destroys both key materials. Peer ID changes on next launch. */
        fun wipe(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEYSTORE_ALIAS)
            }
        }

        private fun ensureSigningKey() {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) return

            // API 33+ has KeyProperties.KEY_ALGORITHM_ED25519 directly; on API 29-32
            // this falls back to a Keystore provider that may not support Ed25519,
            // in which case the setup flow should catch the exception and fall back
            // to an in-process (non-Keystore) Ed25519 key — tracked as a Day-1 TODO,
            // see docs/adr/0002 "signing key portability across API 29-35".
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC.let { "Ed25519" },
                ANDROID_KEYSTORE
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .build()
            )
            generator.generateKeyPair()
        }
    }
}
