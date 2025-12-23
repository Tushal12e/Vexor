package com.vexor.vault.ui

import android.content.Intent
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

class SettingsActivity : BaseActivity() {
    
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
    
    override fun onResume() {
        super.onResume()
        updateSwitches()
    }
    
    private fun updateSwitches() {
        binding.switchBiometric.isChecked = prefs.biometricEnabled
        binding.switchIntruder.isChecked = prefs.intruderDetectionEnabled
        binding.switchFakeVault.isChecked = prefs.fakeVaultEnabled
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Biometric toggle
        binding.switchBiometric.isEnabled = biometricHelper.isBiometricAvailable()
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            prefs.biometricEnabled = isChecked
        }
        
        // Intruder detection
        binding.switchIntruder.setOnCheckedChangeListener { _, isChecked ->
            prefs.intruderDetectionEnabled = isChecked
            if (isChecked) {
                Toast.makeText(this, "Front camera will capture on wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Fake vault
        binding.switchFakeVault.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !prefs.fakeVaultEnabled) {
                showFakePinSetup()
            } else if (!isChecked) {
                prefs.fakeVaultEnabled = false
            }
        }
        
        // Change PIN - Launch ChangePinActivity
        binding.btnChangePin.setOnClickListener {
            startActivity(Intent(this, ChangePinActivity::class.java))
        }
        
        binding.btnManageVaults.setOnClickListener {
            startActivity(Intent(this, ManageVaultsActivity::class.java))
        }
        
        // Intruder logs
        binding.btnIntruderLogs.setOnClickListener {
            startActivity(Intent(this, IntruderLogActivity::class.java))
        }
        
        // Clear vault
        binding.btnClearVault.setOnClickListener {
            showClearVaultConfirmation()
        }
        
        // Theme Selection
        binding.btnTheme.setOnClickListener {
            showThemeSelection()
        }
        
        // Version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvVersion.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            binding.tvVersion.text = "Version 1.3.0"
        }
    }
    
    private fun showFakePinSetup() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Setup Fake Vault PIN")
            .setMessage("When you enter this PIN, a decoy vault will be shown.\n\nUse a different 4-digit PIN.")
            .setPositiveButton("Setup") { _, _ ->
                startActivity(Intent(this, SetupFakePinActivity::class.java))
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchFakeVault.isChecked = false
            }
            .setOnCancelListener {
                binding.switchFakeVault.isChecked = false
            }
            .show()
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
    
    private fun showThemeSelection() {
        val themes = arrayOf("System Default", "Light", "Dark")
        val currentMode = ThemeManager.getThemeMode(this)
        val selectedIndex = when (currentMode) {
            ThemeManager.THEME_LIGHT -> 1
            ThemeManager.THEME_DARK -> 2
            else -> 0
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                val mode = when (which) {
                    1 -> ThemeManager.THEME_LIGHT
                    2 -> ThemeManager.THEME_DARK
                    else -> ThemeManager.THEME_SYSTEM
                }
                ThemeManager.setTheme(this, mode)
                dialog.dismiss()
                // Recreate activity to apply theme immediately
                recreate()
            }
            .show()
    }
}
