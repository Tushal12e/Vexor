package com.vexor.vault.ui

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivitySetupBinding

/**
 * Setup Activity - Simplified for stability
 */
class SetupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySetupBinding
    
    private val prefs by lazy {
        getSharedPreferences("vexor_simple_prefs", MODE_PRIVATE)
    }
    
    private var step = 1 // 1 = create PIN, 2 = confirm PIN
    private var firstPin = ""
    private var enteredPin = ""
    private val pinDots = mutableListOf<View>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        
        // Hide biometric options for now
        binding.biometricContainer.visibility = View.GONE
        
        updateUI()
    }
    
    private fun updateUI() {
        when (step) {
            1 -> binding.tvTitle.text = "Create PIN"
            2 -> binding.tvTitle.text = "Confirm PIN"
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
                    // Save PIN
                    prefs.edit()
                        .putString("pin", enteredPin)
                        .putBoolean("setup_complete", true)
                        .apply()
                    
                    Toast.makeText(this, "Setup Complete!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
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
        }
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
