package com.vexor.vault.ui

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivitySetupBinding

/**
 * Setup Activity with biometric and fake vault options
 */
class SetupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySetupBinding
    
    private val prefs by lazy {
        getSharedPreferences("vexor_prefs", MODE_PRIVATE)
    }
    
    private var step = 1 // 1=create PIN, 2=confirm PIN, 3=fake PIN, 4=confirm fake PIN, 5=biometric
    private var firstPin = ""
    private var fakePin = ""
    private var enteredPin = ""
    private val pinDots = mutableListOf<View>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Screenshot blocking
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        try {
            binding = ActivitySetupBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupUI()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupUI() {
        pinDots.addAll(listOf(
            binding.dot1, binding.dot2, binding.dot3, binding.dot4
        ))
        
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
        
        binding.btnEnableBiometric.setOnClickListener {
            prefs.edit().putBoolean("biometric_enabled", true).apply()
            finishSetup()
        }
        
        binding.btnSkipBiometric.setOnClickListener {
            prefs.edit().putBoolean("biometric_enabled", false).apply()
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
                binding.tvTitle.text = "Create Fake PIN"
                binding.pinContainer.visibility = View.VISIBLE
                binding.biometricContainer.visibility = View.GONE
            }
            4 -> {
                binding.tvTitle.text = "Confirm Fake PIN"
                binding.pinContainer.visibility = View.VISIBLE
                binding.biometricContainer.visibility = View.GONE
            }
            5 -> {
                binding.tvTitle.text = "Enable Fingerprint?"
                binding.pinContainer.visibility = View.GONE
                
                val biometricManager = BiometricManager.from(this)
                if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                    binding.biometricContainer.visibility = View.VISIBLE
                } else {
                    finishSetup()
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
                firstPin = enteredPin
                enteredPin = ""
                step = 2
                updatePinDots()
                updateUI()
            }
            2 -> {
                if (enteredPin == firstPin) {
                    prefs.edit().putString("pin", enteredPin).apply()
                    enteredPin = ""
                    askFakeVaultSetup()
                } else {
                    vibrate()
                    Toast.makeText(this, "PINs don't match", Toast.LENGTH_SHORT).show()
                    enteredPin = ""
                    step = 1
                    firstPin = ""
                    updatePinDots()
                    updateUI()
                }
            }
            3 -> {
                if (enteredPin == firstPin) {
                    vibrate()
                    Toast.makeText(this, "Fake PIN must be different", Toast.LENGTH_SHORT).show()
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
                if (enteredPin == fakePin) {
                    prefs.edit().putString("fake_pin", enteredPin).apply()
                    enteredPin = ""
                    step = 5
                    updateUI()
                } else {
                    vibrate()
                    Toast.makeText(this, "Fake PINs don't match", Toast.LENGTH_SHORT).show()
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
            .setMessage("A fake vault shows decoy files when forced to unlock. Use a different PIN.")
            .setPositiveButton("Yes") { _, _ ->
                step = 3
                updateUI()
            }
            .setNegativeButton("Skip") { _, _ ->
                step = 5
                updateUI()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun finishSetup() {
        prefs.edit().putBoolean("setup_complete", true).apply()
        Toast.makeText(this, "Setup Complete!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
