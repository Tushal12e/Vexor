package com.vexor.vault.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import java.util.UUID

class VaultPreferences(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            e.printStackTrace()
            // If failed, clear and retry once
            context.getSharedPreferences("vexor_secure_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            try {
                createEncryptedPrefs()
            } catch (e2: Exception) {
                // Fallback to standard prefs if crypto is permanently broken on this device
                // This is not ideal for security but prevents crash.
                context.getSharedPreferences("vexor_secure_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            "vexor_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
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
    
    // Fake Vault PIN
    var fakePinHash: String?
        get() = prefs.getString(KEY_FAKE_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_FAKE_PIN_HASH, value).apply()
    
    fun setFakePin(pin: String) {
        fakePinHash = hashPin(pin)
    }
    
    // Multiple Vaults - Map PIN Hash to Vault ID
    // Stored as "pin_hash_MAP_vault_id" implies we check all keys? No, too slow.
    // Store a Set<String> of "vault_id:pin_hash" ?
    // Or just check specific keys for Main and Fake (legacy) + others?
    
    // For now, let's keep Main and Fake as special, and allow adding custom ones.
    // Custom vaults stored in Json String "custom_vaults" -> List<VaultConfig>
    
    data class VaultConfig(val id: String, val pinHash: String, val name: String)
    
    fun getCustomVaults(): List<VaultConfig> {
        val json = prefs.getString("custom_vaults", "[]") ?: "[]"
        val type = object : TypeToken<List<VaultConfig>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }
    
    fun addCustomVault(name: String, pin: String) {
        val vaults = getCustomVaults().toMutableList()
        val id = UUID.randomUUID().toString()
        vaults.add(VaultConfig(id, hashPin(pin), name))
        
        val json = Gson().toJson(vaults)
        prefs.edit().putString("custom_vaults", json).apply()
    }
    
    fun deleteCustomVault(id: String) {
        val vaults = getCustomVaults().toMutableList()
        vaults.removeAll { it.id == id }
        val json = Gson().toJson(vaults)
        prefs.edit().putString("custom_vaults", json).apply()
    }
    
    fun verifyPin(pin: String): String? {
        val hash = hashPin(pin)
        // Check Main
        if (hash == prefs.getString(KEY_PIN_HASH, "")) return "main"
        // Check Fake
        if (fakeVaultEnabled && hash == prefs.getString("fake_pin_hash", "")) return "fake"
        
        // Check custom vaults
        val customVaults = getCustomVaults()
        val match = customVaults.find { it.pinHash == hash }
        if (match != null) return match.id
        
        return null
    }
    
    // Legacy boolean check
    fun verifyPinBoolean(pin: String): Boolean {
        return verifyPin(pin) == "main"
    }

    fun isFakePin(pin: String): Boolean {
        return verifyPin(pin) == "fake"
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
    
    fun getLockDuration(): Long {
        return when {
            failedAttempts >= 5 -> 30_000 // 30 seconds after 5+ attempts
            failedAttempts == 4 -> 15_000 // 15 seconds after 4 attempts
            failedAttempts == 3 -> 5_000  // 5 seconds after 3 attempts
            else -> 0
        }
    }

    fun isLocked(): Boolean {
        val duration = getLockDuration()
        if (duration > 0) {
            val timeSinceLast = System.currentTimeMillis() - lastFailedTime
            return timeSinceLast < duration
        }
        return false
    }
    
    fun getLockRemainingSeconds(): Int {
        val duration = getLockDuration()
        val elapsed = System.currentTimeMillis() - lastFailedTime
        return ((duration - elapsed) / 1000).toInt().coerceAtLeast(0)
    }
}
