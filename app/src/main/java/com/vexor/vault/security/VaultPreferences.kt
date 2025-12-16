package com.vexor.vault.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class VaultPreferences(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "vexor_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_FAKE_PIN_HASH = "fake_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_INTRUDER_DETECTION = "intruder_detection"
        private const val KEY_FAKE_VAULT_ENABLED = "fake_vault_enabled"
        private const val KEY_FIRST_SETUP = "first_setup_complete"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LAST_FAILED_TIME = "last_failed_time"
    }
    
    // Hash PIN for secure storage
    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    // PIN Management
    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()
    
    fun setPin(pin: String) {
        pinHash = hashPin(pin)
    }
    
    fun verifyPin(pin: String): Boolean {
        val storedHash = pinHash ?: return false
        return hashPin(pin) == storedHash
    }
    
    // Fake Vault PIN
    var fakePinHash: String?
        get() = prefs.getString(KEY_FAKE_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_FAKE_PIN_HASH, value).apply()
    
    fun setFakePin(pin: String) {
        fakePinHash = hashPin(pin)
    }
    
    fun isFakePin(pin: String): Boolean {
        val fakeHash = fakePinHash ?: return false
        return hashPin(pin) == fakeHash
    }
    
    // Settings
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()
    
    var intruderDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTRUDER_DETECTION, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRUDER_DETECTION, value).apply()
    
    var fakeVaultEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAKE_VAULT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FAKE_VAULT_ENABLED, value).apply()
    
    var isFirstSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_FIRST_SETUP, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_SETUP, value).apply()
    
    // Failed attempts tracking
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, value).apply()
    
    var lastFailedTime: Long
        get() = prefs.getLong(KEY_LAST_FAILED_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_FAILED_TIME, value).apply()
    
    fun recordFailedAttempt() {
        failedAttempts++
        lastFailedTime = System.currentTimeMillis()
    }
    
    fun resetFailedAttempts() {
        failedAttempts = 0
    }
    
    fun isLocked(): Boolean {
        // Lock for 30 seconds after 5 failed attempts
        if (failedAttempts >= 5) {
            val timeSinceLast = System.currentTimeMillis() - lastFailedTime
            return timeSinceLast < 30_000
        }
        return false
    }
    
    fun getLockRemainingSeconds(): Int {
        val elapsed = System.currentTimeMillis() - lastFailedTime
        return ((30_000 - elapsed) / 1000).toInt().coerceAtLeast(0)
    }
}
