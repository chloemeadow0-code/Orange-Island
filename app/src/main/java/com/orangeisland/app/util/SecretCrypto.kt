package com.orangeisland.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encryption-at-rest for secret settings values (API keys, SSH passwords).
 *
 * Uses an AES-256-GCM key held in the Android Keystore (hardware-backed where
 * available), so the plaintext never lives in the DataStore prefs file. The
 * Keystore key is non-exportable; only this app's process can use it.
 *
 * Stored form: "enc:v1:" + base64(iv ‖ ciphertext+tag). Values without that
 * prefix are treated as legacy plaintext and passed through unchanged, so
 * existing installs keep working and are transparently re-encrypted on the next
 * save (lazy migration). Both encrypt and decrypt fail safe: encrypt falls back
 * to plaintext rather than losing data, decrypt returns "" on tamper/error.
 */
object SecretCrypto {
    private const val KEY_ALIAS = "orangeisland_secrets_v1"
    private const val PREFIX = "enc:v1:"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val TAG = "SecretCrypto"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Keystore generates a fresh random IV per encryption.
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    /** True if [stored] is already in our encrypted envelope. */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return plaintext
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ct.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ct, 0, combined, iv.size, ct.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fail open: never lose the user's data because the Keystore hiccuped.
            DebugLog.e(TAG, "encrypt failed; storing plaintext fallback", e)
            plaintext
        }
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored // legacy plaintext — pass through
        return try {
            val combined = Base64.decode(stored.substring(PREFIX.length), Base64.NO_WRAP)
            if (combined.size <= IV_LEN) return ""
            val iv = combined.copyOfRange(0, IV_LEN)
            val ct = combined.copyOfRange(IV_LEN, combined.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            DebugLog.e(TAG, "decrypt failed", e)
            ""
        }
    }
}
