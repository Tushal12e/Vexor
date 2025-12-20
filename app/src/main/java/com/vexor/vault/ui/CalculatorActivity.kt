package com.vexor.vault.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.vexor.vault.databinding.ActivityCalculatorBinding
import com.vexor.vault.security.VaultPreferences
import net.objecthunter.exp4j.ExpressionBuilder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

class CalculatorActivity : BaseActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private lateinit var prefs: VaultPreferences
    private var currentExpression = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check Permissions FIRST
        if (!hasPermissions()) {
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
            return
        }

        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        
        // Check first time setup
        if (!prefs.isFirstSetupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        
        setupButtons()
    }

    private fun hasPermissions(): Boolean {
        val required = mutableListOf<String>().apply {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val standardGranted = required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        val storageManagerGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        return standardGranted && storageManagerGranted
    }
    
    private fun setupButtons() {
        val numberButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnDot
        )
        
        numberButtons.forEach { btn ->
            btn.setOnClickListener { appendToExpression(btn.text.toString()) }
        }
        
        val opButtons = listOf(
            binding.btnAdd, binding.btnSub, binding.btnMul, binding.btnDiv, binding.btnPercent
        )
        
        opButtons.forEach { btn ->
            btn.setOnClickListener { appendToExpression(btn.text.toString()) }
        }
        
        binding.btnC.setOnClickListener { 
            currentExpression = ""
            updateDisplay()
        }
        
        binding.btnDel.setOnClickListener {
            if (currentExpression.isNotEmpty()) {
                currentExpression = currentExpression.dropLast(1)
                updateDisplay()
            }
        }
        
        binding.btnEq.setOnClickListener {
            checkPinOrCalculate()
        }
    }
    
    private fun appendToExpression(str: String) {
        currentExpression += str
        updateDisplay()
    }
    
    private fun updateDisplay() {
        binding.tvDisplay.text = if (currentExpression.isEmpty()) "0" else currentExpression
    }
    
    private fun checkPinOrCalculate() {
        // SECRET: Check if expression matches PIN
        val vaultId = prefs.verifyPin(currentExpression)
        
        if (vaultId != null) {
            // Correct PIN - Open Vault
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("vault_id", vaultId)
            intent.putExtra("fake_vault", vaultId == "fake")
            startActivity(intent)
            finish()
            return
        }

        // Calculate
        try {
            val expression = ExpressionBuilder(currentExpression
                .replace("×", "*")
                .replace("÷", "/")
            ).build()
            val result = expression.evaluate()
            
            // Format result (remove .0 if integer)
            currentExpression = if (result % 1 == 0.0) {
                result.toLong().toString()
            } else {
                String.format("%.2f", result)
            }
            updateDisplay()
            
        } catch (e: Exception) {
            binding.tvDisplay.text = "Error"
            currentExpression = ""
        }
    }
}
