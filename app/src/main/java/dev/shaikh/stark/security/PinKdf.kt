package dev.shaikh.stark.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * STARK PIN derivation, built only from established primitives:
 *
 *   verifier = HMAC-SHA256_K(label ‖ PBKDF2-HMAC-SHA256(pin, salt, 600 000))
 *
 * where K is an Android Keystore HMAC key (StrongBox when available) that cannot be exported
 * and only works while the phone is unlocked. Without this phone's secure hardware the
 * verifier can't be brute-forced offline; on the phone, [LockoutPolicy] throttles guesses.
 */
object PinKdf {
    /** OWASP's current recommendation for PBKDF2-HMAC-SHA256. */
    const val ITERATIONS = 600_000
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    private const val PEPPER_ALIAS = "stark.lock.pepper.v1"
    private const val VERIFIER_LABEL = "stark.lock.verifier.v1"

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun stretch(pin: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Blocking (~0.5 s by design); call off the main thread. */
    fun verifier(pin: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val stretched = stretch(pin, salt, iterations)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(pepperKey())
            mac.update(VERIFIER_LABEL.toByteArray())
            mac.update(0)
            return mac.doFinal(stretched)
        } finally {
            stretched.fill(0)
        }
    }

    fun matches(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    private fun pepperKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(PEPPER_ALIAS, null) as? SecretKey)?.let { return it }
        return try {
            generatePepper(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generatePepper(strongBox = false)
        } catch (_: ProviderException) {
            generatePepper(strongBox = false)
        }
    }

    private fun generatePepper(strongBox: Boolean): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(PEPPER_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setIsStrongBoxBacked(strongBox)
                    .setUnlockedDeviceRequired(true)
                    .build(),
            )
        }.generateKey()
}

/** Growing delays after repeated wrong PINs: four free tries, then 30 s doubling up to 1 hour. */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 4
    private const val BASE_MS = 30_000L
    private const val CAP_MS = 3_600_000L

    fun delayAfter(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0L
        val doublings = (failures - FREE_ATTEMPTS - 1).coerceAtMost(10)
        return (BASE_MS shl doublings).coerceAtMost(CAP_MS)
    }
}
