package com.vexor.vault.ui

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivityChangePinBinding
import com.vexor.vault.security.VaultPreferences

class SetupFakePinActivity : BaseActivity() {
    
    private lateinit var binding: ActivityChangePinBinding
    private lateinit var prefs: VaultPreferences
    
    private var step = 1 // 1 = enter fake PIN, 2 = confirm
    private var fakePin = ""
    private var enteredPin = ""
    private val pinDots = mutableListOf<View>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        
        setupUI()
        updateUI()
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = "Setup Fake Vault PIN"
        
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
            button.setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin += number
                    updatePinDots()
                    if (enteredPin.length == 4) handlePinComplete()
                }
            }
        }
        
        binding.btnDelete.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
                updatePinDots()
            }
        }
    }
    
    private fun updateUI() {
        binding.tvTitle.text = when (step) {
            1 -> "Enter Fake Vault PIN"
            2 -> "Confirm Fake Vault PIN"
            else -> ""
        }
    }
    
    private fun updatePinDots() {
        pinDots.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index < enteredPin.length) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }
    
    private fun handlePinComplete() {
        when (step) {
            1 -> {
                // Check if same as main PIN
                if (prefs.verifyPin(enteredPin)) {
                    vibrate()
                    Toast.makeText(this, "Cannot use main PIN as fake PIN", Toast.LENGTH_SHORT).show()
                    enteredPin = ""
                    updatePinDots()
                    return
                }
                
                fakePin = enteredPin
                step = 2
                enteredPin = ""
                updatePinDots()
                updateUI()
            }
            2 -> {
                if (enteredPin == fakePin) {
                    prefs.setFakePin(enteredPin)
                    prefs.fakeVaultEnabled = true
                    Toast.makeText(this, "Fake vault PIN set!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    vibrate()
                    Toast.makeText(this, "PINs don't match. Try again.", Toast.LENGTH_SHORT).show()
                    step = 1
                    fakePin = ""
                    enteredPin = ""
                    updatePinDots()
                    updateUI()
                }
            }
        }
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
