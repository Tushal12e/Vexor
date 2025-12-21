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
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.vexor.vault.databinding.ActivityCalculatorBinding
import net.objecthunter.exp4j.ExpressionBuilder

/**
 * Main Calculator Activity with all features
 */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private var currentExpression = ""
    
    private val prefs by lazy {
        getSharedPreferences("vexor_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Screenshot blocking
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        try {
            if (!hasPermissions()) {
                startActivity(Intent(this, PermissionsActivity::class.java))
                finish()
                return
            }
            
            binding = ActivityCalculatorBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            if (!prefs.getBoolean("setup_complete", false)) {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
                return
            }
            
            setupCalculatorUI()
            
            // Auto-show biometric if enabled
            if (prefs.getBoolean("biometric_enabled", false)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    showBiometricPrompt()
                }, 300)
            }
            
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
        
        // Long press on dot -> biometric
        binding.btnDot.setOnLongClickListener {
            if (prefs.getBoolean("biometric_enabled", false)) {
                showBiometricPrompt()
            }
            true
        }
        
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
        
        // Check main PIN
        val mainPin = prefs.getString("pin", null)
        if (mainPin != null && currentExpression == mainPin) {
            openVault(isFake = false)
            return
        }
        
        // Check fake PIN
        val fakePin = prefs.getString("fake_pin", null)
        if (fakePin != null && currentExpression == fakePin) {
            openVault(isFake = true)
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
            
            // Record failed attempt if looks like PIN (4-8 digits)
            if (currentExpression.all { it.isDigit() } && currentExpression.length in 4..8) {
                recordFailedAttempt()
            }
            
        } catch (e: Exception) {
            binding.displayScreen.text = "Error"
            currentExpression = ""
        }
    }
    
    private fun recordFailedAttempt() {
        val attempts = prefs.getInt("failed_attempts", 0) + 1
        prefs.edit().putInt("failed_attempts", attempts).apply()
        
        if (attempts >= 3) {
            vibrate()
            // Could add intruder photo capture here
        }
    }
    
    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            return
        }
        
        val executor = ContextCompat.getMainExecutor(this)
        
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    prefs.edit().putInt("failed_attempts", 0).apply()
                    openVault(isFake = false)
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    vibrate()
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vexor")
            .setSubtitle("Use your fingerprint")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun openVault(isFake: Boolean) {
        prefs.edit().putInt("failed_attempts", 0).apply()
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("fake_vault", isFake)
        startActivity(intent)
        finish()
    }
}
