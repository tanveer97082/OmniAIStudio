package com.omniai.studio.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PinVerificationResult {
    MASTER,
    DECOY,
    INVALID,
    AUTO_WIPE_TRIGGERED,
    NOT_CONFIGURED
}

/**
 * Stores Master PIN and optional Duress/Decoy PIN as salted SHA-256 hashes inside
 * hardware-backed `EncryptedSharedPreferences`.
 *
 * Also maintains an encrypted Failed PIN Audit Log with consecutive failure tracking
 * and an optional Auto-Wipe threshold (`0` = Off, `5` = Wipe after 5 failures, `10` = Wipe after 10 failures).
 */
class DuressPinManager(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasMasterPin(): Boolean = !prefs.getString(KEY_MASTER_HASH, null).isNullOrBlank()

    fun hasDecoyPin(): Boolean = !prefs.getString(KEY_DECOY_HASH, null).isNullOrBlank()

    /** Allowed values: 0 (Off), 5, or 10 consecutive failed PIN attempts before triggering Panic Wipe. */
    var autoWipeThreshold: Int
        get() = prefs.getInt(KEY_AUTO_WIPE_THRESHOLD, 0)
        set(value) {
            val normalized = when (value) {
                5, 10 -> value
                else -> 0
            }
            prefs.edit().putInt(KEY_AUTO_WIPE_THRESHOLD, normalized).apply()
        }

    fun getConsecutiveFailures(): Int = prefs.getInt(KEY_CONSECUTIVE_FAILS, 0)

    fun getFailedPinAuditLog(): List<String> {
        val raw = prefs.getString(KEY_AUDIT_LOG, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lines().filter { it.isNotBlank() }
    }

    fun clearFailedPinAuditLog() {
        prefs.edit()
            .remove(KEY_AUDIT_LOG)
            .putInt(KEY_CONSECUTIVE_FAILS, 0)
            .apply()
    }

    fun setMasterPin(pin: String): Result<Unit> {
        val clean = pin.trim()
        if (clean.length < 4 || !clean.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Master PIN must be at least 4 digits."))
        }
        val existingDecoy = prefs.getString(KEY_DECOY_HASH, null)
        if (!existingDecoy.isNullOrBlank() && verifySaltedHash(clean, existingDecoy)) {
            return Result.failure(IllegalArgumentException("Master PIN cannot match the Decoy PIN."))
        }
        prefs.edit()
            .putString(KEY_MASTER_HASH, hashWithRandomSalt(clean))
            .putInt(KEY_CONSECUTIVE_FAILS, 0)
            .apply()
        return Result.success(Unit)
    }

    fun setDecoyPin(pin: String): Result<Unit> {
        val clean = pin.trim()
        if (clean.length < 4 || !clean.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Decoy PIN must be at least 4 digits."))
        }
        val existingMaster = prefs.getString(KEY_MASTER_HASH, null)
        if (!existingMaster.isNullOrBlank() && verifySaltedHash(clean, existingMaster)) {
            return Result.failure(IllegalArgumentException("Decoy PIN must differ from the Master PIN."))
        }
        prefs.edit().putString(KEY_DECOY_HASH, hashWithRandomSalt(clean)).apply()
        return Result.success(Unit)
    }

    fun clearDecoyPin() {
        prefs.edit().remove(KEY_DECOY_HASH).apply()
    }

    fun clearAllPins() {
        prefs.edit()
            .remove(KEY_MASTER_HASH)
            .remove(KEY_DECOY_HASH)
            .remove(KEY_AUDIT_LOG)
            .putInt(KEY_CONSECUTIVE_FAILS, 0)
            .putInt(KEY_AUTO_WIPE_THRESHOLD, 0)
            .apply()
    }

    fun verifyPin(candidatePin: String): PinVerificationResult {
        val clean = candidatePin.trim()
        val masterRecord = prefs.getString(KEY_MASTER_HASH, null)
        val decoyRecord = prefs.getString(KEY_DECOY_HASH, null)
        if (masterRecord.isNullOrBlank() && decoyRecord.isNullOrBlank()) {
            return PinVerificationResult.NOT_CONFIGURED
        }
        if (!masterRecord.isNullOrBlank() && verifySaltedHash(clean, masterRecord)) {
            prefs.edit().putInt(KEY_CONSECUTIVE_FAILS, 0).apply()
            return PinVerificationResult.MASTER
        }
        if (!decoyRecord.isNullOrBlank() && verifySaltedHash(clean, decoyRecord)) {
            prefs.edit().putInt(KEY_CONSECUTIVE_FAILS, 0).apply()
            return PinVerificationResult.DECOY
        }

        val nextFailures = getConsecutiveFailures() + 1
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "[$stamp] Failed PIN attempt #$nextFailures (length=${clean.length})"
        val updatedLog = (listOf(entry) + getFailedPinAuditLog()).take(50).joinToString("\n")
        prefs.edit()
            .putInt(KEY_CONSECUTIVE_FAILS, nextFailures)
            .putString(KEY_AUDIT_LOG, updatedLog)
            .apply()

        val threshold = autoWipeThreshold
        if (threshold > 0 && nextFailures >= threshold) {
            return PinVerificationResult.AUTO_WIPE_TRIGGERED
        }
        return PinVerificationResult.INVALID
    }

    private fun hashWithRandomSalt(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val digest = computeSha256(salt, pin)
        return "${salt.toHex()}:${digest.toHex()}"
    }

    private fun verifySaltedHash(pin: String, storedRecord: String): Boolean {
        val parts = storedRecord.split(':')
        if (parts.size != 2) return false
        val salt = parts[0].fromHex() ?: return false
        val expected = parts[1].fromHex() ?: return false
        val actual = computeSha256(salt, pin)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun computeSha256(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "omniai_duress_pin_vault"
        private const val KEY_MASTER_HASH = "master_pin_sha256"
        private const val KEY_DECOY_HASH = "decoy_pin_sha256"
        private const val KEY_AUDIT_LOG = "failed_pin_audit_log"
        private const val KEY_CONSECUTIVE_FAILS = "consecutive_failed_pins"
        private const val KEY_AUTO_WIPE_THRESHOLD = "auto_wipe_failure_threshold"
    }
}
