package com.vexor.vault.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivitySettingsBinding
import com.vexor.vault.security.BiometricHelper
import com.vexor.vault.security.FileEncryptionManager
import com.vexor.vault.security.VaultPreferences

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: VaultPreferences
    private lateinit var biometricHelper: BiometricHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        biometricHelper = BiometricHelper(this)
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Biometric toggle
        binding.switchBiometric.isChecked = prefs.biometricEnabled
        binding.switchBiometric.isEnabled = biometricHelper.isBiometricAvailable()
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            prefs.biometricEnabled = isChecked
        }
        
        // Intruder detection
        binding.switchIntruder.isChecked = prefs.intruderDetectionEnabled
        binding.switchIntruder.setOnCheckedChangeListener { _, isChecked ->
            prefs.intruderDetectionEnabled = isChecked
        }
        
        // Fake vault
        binding.switchFakeVault.isChecked = prefs.fakeVaultEnabled
        binding.switchFakeVault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showFakePinSetup()
            } else {
                prefs.fakeVaultEnabled = false
            }
        }
        
        // Change PIN
        binding.btnChangePin.setOnClickListener {
            Toast.makeText(this, "Change PIN feature", Toast.LENGTH_SHORT).show()
        }
        
        // Intruder logs
        binding.btnIntruderLogs.setOnClickListener {
            startActivity(android.content.Intent(this, IntruderLogActivity::class.java))
        }
        
        // Clear vault
        binding.btnClearVault.setOnClickListener {
            showClearVaultConfirmation()
        }
        
        // Version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvVersion.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            binding.tvVersion.text = "Version 1.0.0"
        }
    }
    
    private fun showFakePinSetup() {
        Toast.makeText(this, "Set a different PIN for fake vault", Toast.LENGTH_LONG).show()
        prefs.fakeVaultEnabled = true
        prefs.setFakePin("0000") // Default fake PIN
    }
    
    private fun showClearVaultConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_vault_confirm))
            .setMessage(getString(R.string.clear_vault_msg))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val encryptionManager = FileEncryptionManager(this)
                encryptionManager.clearVault()
                Toast.makeText(this, "Vault cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
