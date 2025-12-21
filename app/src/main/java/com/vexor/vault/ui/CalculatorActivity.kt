package com.vexor.vault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivityCalculatorBinding
import net.objecthunter.exp4j.ExpressionBuilder

/**
 * Main Calculator Activity - Simplified for stability
 */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private var currentExpression = ""
    
    // Simple prefs for PIN (no encryption for stability)
    private val prefs by lazy {
        getSharedPreferences("vexor_simple_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Check Permissions first
            if (!hasPermissions()) {
                startActivity(Intent(this, PermissionsActivity::class.java))
                finish()
                return
            }
            
            binding = ActivityCalculatorBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Check if first setup needed
            if (!prefs.getBoolean("setup_complete", false)) {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
                return
            }
            
            setupCalculatorUI()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun hasPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cameraGranted && Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            cameraGranted && read && write
        }
    }
    
    private fun setupCalculatorUI() {
        val numberButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )
        
        numberButtons.forEach { btn ->
            btn.setOnClickListener { appendToDisplay(btn.text.toString()) }
        }
        
        binding.btnDot.setOnClickListener { appendToDisplay(".") }
        
        val opButtons = listOf(
            binding.btnPlus, binding.btnMinus, binding.btnMultiply, binding.btnDivide
        )
        opButtons.forEach { btn ->
            btn.setOnClickListener { appendToDisplay(btn.text.toString()) }
        }
        
        binding.btnPercent.setOnClickListener { appendToDisplay("%") }
        
        binding.btnAC.setOnClickListener { 
            currentExpression = ""
            updateDisplay("0")
        }
        
        binding.btnDelete.setOnClickListener {
            if (currentExpression.isNotEmpty()) {
                currentExpression = currentExpression.dropLast(1)
                updateDisplay(if (currentExpression.isEmpty()) "0" else currentExpression)
            }
        }
        
        binding.btnEquals.setOnClickListener { onEqualsClick() }
    }
    
    private fun appendToDisplay(str: String) {
        if (currentExpression.length > 20) return
        currentExpression += str
        updateDisplay(currentExpression)
    }
    
    private fun updateDisplay(text: String) {
        binding.displayScreen.text = text
    }
    
    private fun onEqualsClick() {
        if (currentExpression.isEmpty()) return
        
        // Check PIN
        val savedPin = prefs.getString("pin", null)
        if (savedPin != null && currentExpression == savedPin) {
            openVault()
            return
        }
        
        // Math calculation
        try {
            val expressionStr = currentExpression.replace("x", "*")
            val expression = ExpressionBuilder(expressionStr).build()
            val result = expression.evaluate()
            
            val longResult = result.toLong()
            currentExpression = if (result == longResult.toDouble()) {
                longResult.toString()
            } else {
                result.toString()
            }
            updateDisplay(currentExpression)
            
        } catch (e: Exception) {
            binding.displayScreen.text = "Error"
            currentExpression = ""
        }
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun openVault() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
