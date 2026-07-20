package com.orangeisland.app.util

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ShellCrypto {
    private const val GCM_NONCE_SIZE = 12
    private const val GCM_TAG_SIZE = 128
    private const val HKDF_INFO = "conch-orangeisland-v1"

    private val secureRandom = SecureRandom()
    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDecoder = Base64.getUrlDecoder()

    // --- Key Generation (X25519) ---

    fun generateEphemeralKeyPair(): java.security.KeyPair {
        val generator = KeyPairGenerator.getInstance("X25519")
        return generator.generateKeyPair()
    }

    fun encodePublicKey(publicKey: java.security.PublicKey): String {
        // X25519 SPKI encoding deterministically ends with the 32-byte raw key
        val encoded = publicKey.encoded
        val rawKey = encoded.copyOfRange(encoded.size - 32, encoded.size)
        return b64url.encodeToString(rawKey)
    }

    private val X25519_SPKI_PREFIX: ByteArray by lazy {
        val kp = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        val encoded = kp.public.encoded
        encoded.copyOfRange(0, encoded.size - 32)
    }

    fun decodePublicKey(base64urlKey: String): java.security.PublicKey {
        val rawKey = b64urlDecoder.decode(base64urlKey)
        if (rawKey.size != 32) {
            throw IllegalArgumentException("invalid X25519 public key length: ${rawKey.size}")
        }
        val spki = X25519_SPKI_PREFIX + rawKey
        val keySpec = X509EncodedKeySpec(spki)
        return KeyFactory.getInstance("XDH").generatePublic(keySpec)
    }

    // --- ECDH + HKDF ---

    fun deriveAesKey(
        ourPrivateKey: java.security.PrivateKey,
        theirPublicKey: java.security.PublicKey
    ): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(ourPrivateKey)
        keyAgreement.doPhase(theirPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        return hkdfExpand(sharedSecret, HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var i: Byte = 1
        while (offset < length) {
            mac.update(t)
            mac.update(info)
            mac.update(i)
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            i++
        }
        return result
    }

    // --- AES-256-GCM ---

    fun encrypt(key: ByteArray, plaintext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(GCM_NONCE_SIZE)
        secureRandom.nextBytes(nonce)
        val spec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        val ciphertext = cipher.doFinal(plaintext)
        val combined = ByteArray(nonce.size + ciphertext.size)
        System.arraycopy(nonce, 0, combined, 0, nonce.size)
        System.arraycopy(ciphertext, 0, combined, nonce.size, ciphertext.size)
        return b64url.encodeToString(combined)
    }

    fun decrypt(key: ByteArray, encoded: String): ByteArray {
        val raw = b64urlDecoder.decode(encoded)
        if (raw.size < GCM_NONCE_SIZE + 1) {
            throw IllegalArgumentException("ciphertext too short: ${raw.size} bytes")
        }
        val nonce = raw.copyOfRange(0, GCM_NONCE_SIZE)
        val ciphertext = raw.copyOfRange(GCM_NONCE_SIZE, raw.size)
        val spec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        return cipher.doFinal(ciphertext)
    }

    // --- HMAC-SHA256 ---

    fun sign(apiKey: String, timestamp: Long, method: String, path: String, bodySha256: String, nonce: String, clientPubKey: String): String {
        val message = "$timestamp|$method|$path|$bodySha256|$nonce|$clientPubKey"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    fun generateNonce(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return b64url.encodeToString(bytes)
    }
}
