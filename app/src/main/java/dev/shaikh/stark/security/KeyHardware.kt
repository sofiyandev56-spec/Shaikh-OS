package dev.shaikh.stark.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory

/**
 * Measures where Android Keystore actually stores a key on this device by generating a
 * throwaway AES key and reading its reported security level. Nothing is assumed.
 */
object KeyHardware {
    private const val ALIAS = "stark.hardware.probe"

    /** Blocking; call off the main thread. */
    fun measure(): String = try {
        level(strongBox = true)
    } catch (_: StrongBoxUnavailableException) {
        level(strongBox = false)
    }

    private fun level(strongBox: Boolean): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(strongBox)
                .build()
            val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }
                .generateKey()
            val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            return when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityState.KEY_STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityState.KEY_TEE
                else -> SecurityState.KEY_SOFTWARE
            }
        } finally {
            keyStore.deleteEntry(ALIAS)
        }
    }
}
