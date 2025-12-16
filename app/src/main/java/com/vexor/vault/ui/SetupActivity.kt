package com.vexor.vault.ui

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivitySetupBinding
import com.vexor.vault.security.BiometricHelper
import com.vexor.vault.security.VaultPreferences

class SetupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: VaultPreferences
    private lateinit var biometricHelper: BiometricHelper
    
    private var step = 1 // 1 = create PIN, 2 = confirm PIN, 3 = fake PIN, 4 = confirm fake PIN, 5 = biometric
    private var firstPin = ""
    private var fakePin = ""
    private var enteredPin = ""
    private val pinDots = mutableListOf<View>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        biometricHelper = BiometricHelper(this)
        
        setupUI()
    }
    
    private fun setupUI() {
        // PIN dots
        pinDots.addAll(listOf(
            binding.dot1, binding.dot2, binding.dot3, binding.dot4
        ))
        
        // Number buttons
        val buttons = listOf(
            binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6,
            binding.btn7, binding.btn8, binding.btn9,
            binding.btn0
        )
        
        buttons.forEachIndexed { index, button ->
            val number = if (index == 9) "0" else (index + 1).toString()
            button.text = number
            button.setOnClickListener { onNumberClick(number) }
        }
        
        binding.btnDelete.setOnClickListener { onDeleteClick() }
        
        // Biometric setup
        binding.btnEnableBiometric.setOnClickListener {
            prefs.biometricEnabled = true
            finishSetup()
        }
        
        binding.btnSkipBiometric.setOnClickListener {
            prefs.biometricEnabled = false
            finishSetup()
        }
        
        updateUI()
    }
    
    private fun updateUI() {
        when (step) {
            1 -> {
                binding.tvTitle.text = "Create Main PIN"
                binding.pinContainer.visibility = View.VISIBLE
                binding.biometricContainer.visibility = View.GONE
            }
            2 -> {
                binding.tvTitle.text = "Confirm Main PIN"
                binding.pinContainer.visibility = View.VISIBLE
                binding.biometricContainer.visibility = View.GONE
            }
            3 -> {
                binding.tvTitle.text = "Create Fake Vault PIN"
                binding.pinContainer.visibility = View.VISIBLE
                binding.biometricContainer.visibility = View.GONE
            }
            4 -> {
                binding.tvTitle.text = "Confirm Fake Vault PIN"
                binding.pinContainer.visibility = View.VISIBLE
                binding.biometricContainer.visibility = View.GONE
            }
            5 -> {
                binding.tvTitle.text = getString(R.string.enable_biometric)
                binding.pinContainer.visibility = View.GONE
                binding.biometricContainer.visibility = if (biometricHelper.isBiometricAvailable()) {
                    View.VISIBLE
                } else {
                    finishSetup()
                    View.GONE
                }
            }
        }
    }
    
    private fun onNumberClick(number: String) {
        if (enteredPin.length < 4) {
            enteredPin += number
            updatePinDots()
            
            if (enteredPin.length == 4) {
                handlePinComplete()
            }
        }
    }
    
    private fun onDeleteClick() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updatePinDots()
        }
    }
    
    private fun updatePinDots() {
        pinDots.forEachIndexed { index, view ->
            val filled = index < enteredPin.length
            view.setBackgroundResource(
                if (filled) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }
    
    private fun handlePinComplete() {
        when (step) {
            1 -> {
                // Save first PIN
                firstPin = enteredPin
                enteredPin = ""
                step = 2
                updatePinDots()
                updateUI()
            }
            2 -> {
                // Confirm main PIN
                if (enteredPin == firstPin) {
                    prefs.setPin(enteredPin)
                    enteredPin = ""
                    
                    // Ask about fake vault
                    askFakeVaultSetup()
                } else {
                    vibrate()
                    showError("PINs don't match. Try again.")
                    enteredPin = ""
                    step = 1
                    firstPin = ""
                    updatePinDots()
                    updateUI()
                }
            }
            3 -> {
                // Check fake PIN is different from main PIN
                if (enteredPin == firstPin) {
                    vibrate()
                    showError("Fake PIN must be different from main PIN")
                    enteredPin = ""
                    updatePinDots()
                } else {
                    fakePin = enteredPin
                    enteredPin = ""
                    step = 4
                    updatePinDots()
                    updateUI()
                }
            }
            4 -> {
                // Confirm fake PIN
                if (enteredPin == fakePin) {
                    prefs.setFakePin(enteredPin)
                    prefs.fakeVaultEnabled = true
                    enteredPin = ""
                    step = 5
                    updateUI()
                } else {
                    vibrate()
                    showError("Fake PINs don't match. Try again.")
                    enteredPin = ""
                    step = 3
                    fakePin = ""
                    updatePinDots()
                    updateUI()
                }
            }
        }
    }
    
    private fun askFakeVaultSetup() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Setup Fake Vault?")
            .setMessage("A fake vault shows decoy files when someone forces you to unlock. Enter a different PIN to access it.\n\nWould you like to set it up now?")
            .setPositiveButton("Yes") { _, _ ->
                step = 3
                updateUI()
            }
            .setNegativeButton("Skip") { _, _ ->
                prefs.fakeVaultEnabled = false
                step = 5
                updateUI()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun finishSetup() {
        prefs.isFirstSetupComplete = true
        prefs.intruderDetectionEnabled = true // Enable by default
        Toast.makeText(this, getString(R.string.setup_complete), Toast.LENGTH_SHORT).show()
        
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
