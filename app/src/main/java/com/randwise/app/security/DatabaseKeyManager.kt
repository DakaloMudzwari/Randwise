package com.randwise.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates a random 256-bit SQLCipher passphrase on first run and stores it wrapped by an
 * AES-GCM key that never leaves the Android Keystore. The raw passphrase is never persisted.
 */
class DatabaseKeyManager(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("rw_keys", Context.MODE_PRIVATE)

    /**
     * Returns the bytes to hand SQLCipher. New installs use SQLCipher's raw-key syntax (x'<hex>'):
     * the key is already 256 random bits, so PBKDF2 stretching adds nothing but ~4 s of startup.
     * Databases created before this change keep the original passphrase form so they still open.
     */
    fun getOrCreateSqlCipherKey(): ByteArray {
        val secret = getOrCreateSecret()
        return if (prefs.getBoolean(KEY_RAW, false)) {
            ("x'" + secret.joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)
        } else {
            secret
        }
    }

    private fun getOrCreateSecret(): ByteArray {
        val stored = prefs.getString(KEY_WRAPPED, null)
        val iv = prefs.getString(KEY_IV, null)
        if (stored != null && iv != null) {
            return runCatching { unwrap(Base64.decode(stored, Base64.NO_WRAP), Base64.decode(iv, Base64.NO_WRAP)) }
                .getOrElse { throw IllegalStateException("Database key unavailable", it) }
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val wrapped = cipher.doFinal(passphrase)
        prefs.edit()
            .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putBoolean(KEY_RAW, true)
            .commit()
        return passphrase
    }

    /** Right to erasure: forget the wrapped key and delete the Keystore entry so old files are unreadable. */
    fun destroy() {
        prefs.edit().clear().commit()
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun unwrap(wrapped: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(wrapped)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "randwise_db_kek"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val KEY_WRAPPED = "db_pass_wrapped"
        private const val KEY_IV = "db_pass_iv"
        private const val KEY_RAW = "db_key_raw"
    }
}
