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

class ChangePinActivity : BaseActivity() {
    
    private lateinit var binding: ActivityChangePinBinding
    private lateinit var prefs: VaultPreferences
    
    private var step = 1 // 1 = verify old, 2 = enter new, 3 = confirm new
    private var newPin = ""
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
            1 -> "Enter Current PIN"
            2 -> "Enter New PIN"
            3 -> "Confirm New PIN"
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
                if (prefs.verifyPin(enteredPin) == "main") {
                    step = 2
                    enteredPin = ""
                    updatePinDots()
                    updateUI()
                } else {
                    vibrate()
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                    enteredPin = ""
                    updatePinDots()
                }
            }
            2 -> {
                newPin = enteredPin
                step = 3
                enteredPin = ""
                updatePinDots()
                updateUI()
            }
            3 -> {
                if (enteredPin == newPin) {
                    prefs.setPin(enteredPin)
                    Toast.makeText(this, "PIN changed successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    vibrate()
                    Toast.makeText(this, "PINs don't match. Try again.", Toast.LENGTH_SHORT).show()
                    step = 2
                    newPin = ""
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
