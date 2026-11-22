package com.omniai.studio.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts `.omniai` backup archives using PBKDF2WithHmacSHA256
 * (120,000 iterations, 256-bit key) + AES-256-GCM (12-byte random IV, 128-bit auth tag).
 *
 * Binary format:
 *   [4 bytes magic "OMNI"] [1 byte version=1] [16 bytes salt] [12 bytes IV] [ciphertext + 16-byte GCM tag]
 */
object BackupCryptoManager {

    private val MAGIC = byteArrayOf('O'.code.toByte(), 'M'.code.toByte(), 'N'.code.toByte(), 'I'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    fun encrypt(plaintextUtf8: String, password: String): ByteArray {
        require(password.length >= 6) { "Backup password must be at least 6 characters." }

        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val secretKey = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintextUtf8.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(MAGIC.size + 1 + SALT_BYTES + IV_BYTES + ciphertext.size)
        buffer.put(MAGIC)
        buffer.put(FORMAT_VERSION)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    fun decrypt(encryptedPayload: ByteArray, password: String): String {
        val headerSize = MAGIC.size + 1 + SALT_BYTES + IV_BYTES
        require(encryptedPayload.size > headerSize + 16) { "Invalid or corrupted .omniai backup file." }

        val buffer = ByteBuffer.wrap(encryptedPayload)
        val magicRead = ByteArray(MAGIC.size)
        buffer.get(magicRead)
        require(magicRead.contentEquals(MAGIC)) { "File is not a valid OmniAI-Studio encrypted backup." }

        val version = buffer.get()
        require(version == FORMAT_VERSION) { "Unsupported backup format version: $version" }

        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
