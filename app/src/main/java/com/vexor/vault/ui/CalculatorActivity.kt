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
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import com.vexor.vault.R
import com.vexor.vault.data.IntruderLog
import com.vexor.vault.data.VaultRepository
import com.vexor.vault.databinding.ActivityCalculatorBinding
import com.vexor.vault.security.BiometricHelper
import com.vexor.vault.security.BreakInNotificationHelper
import com.vexor.vault.security.VaultPreferences
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CalculatorActivity : BaseActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private lateinit var prefs: VaultPreferences
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var repository: VaultRepository
    private lateinit var breakInNotificationHelper: BreakInNotificationHelper

    private var currentExpression = ""
    
    // Camera for intruder detection
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            initializeActivity()
        } catch (e: Exception) {
            e.printStackTrace()
            // Show error and don't crash
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initializeActivity() {
        // 1. Check Permissions
        if (!hasPermissions()) {
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
            return
        }

        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        biometricHelper = BiometricHelper(this)
        repository = VaultRepository(this)
        breakInNotificationHelper = BreakInNotificationHelper(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // 2. Check Setup
        if (!prefs.isFirstSetupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        
        // 3. Initialize Camera (if permissions granted, which they should be)
        if (prefs.intruderDetectionEnabled) {
            startCamera()
        }
        
        setupCalculatorUI()
        
        // Auto-show biometric
        if (prefs.biometricEnabled) {
              checkBiometric()
        }
    }
    
    private fun hasPermissions(): Boolean {
        // 1. Camera is always required
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        
        // 2. Storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Use MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                return cameraGranted
            }
            // If not managed, check if we mistakenly have READ/WRITE? (Unlikely for this app type)
            return false
        } else {
            // Android 10-: Use READ/WRITE
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            return cameraGranted && read && write
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                // Preview not required for ImageCapture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
            } catch (e: Exception) {
                // Ignore camera init errors (don't crash app)
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
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
        
        // Operations
        val opButtons = listOf(
            binding.btnPlus, binding.btnMinus, binding.btnMultiply, binding.btnDivide
        )
        opButtons.forEach { btn ->
            btn.setOnClickListener { appendToDisplay(btn.text.toString()) }
        }
        
        binding.btnPercent.setOnClickListener { appendToDisplay("%") }
        
        // Clear: AC
        binding.btnAC.setOnClickListener { 
            currentExpression = ""
            updateDisplay("0")
        }
        
        // Delete
        binding.btnDelete.setOnClickListener {
            if (currentExpression.isNotEmpty()) {
                currentExpression = currentExpression.dropLast(1)
                updateDisplay(if (currentExpression.isEmpty()) "0" else currentExpression)
            }
        }
        
        // Equals
        binding.btnEquals.setOnClickListener { onEqualsClick() }
        
        // Long press on dot -> Biometric
        binding.btnDot.setOnLongClickListener {
             if (prefs.biometricEnabled) showBiometricPrompt()
             true
        }
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
        
        // 1. PIN Check
        val vaultId = prefs.verifyPin(currentExpression)
        
        if (vaultId != null) {
            prefs.resetFailedAttempts()
            openVault(vaultId)
            return
        }
        
        // 2. Math
        try {
            val expressionStr = currentExpression.replace("x", "*")
            val expression = ExpressionBuilder(expressionStr).build()
            val result = expression.evaluate()
            
            val longResult = result.toLong()
            if (result == longResult.toDouble()) {
                currentExpression = longResult.toString()
            } else {
                currentExpression = result.toString()
            }
            updateDisplay(currentExpression)
            
            // Check for potential failed PIN attempt (4-8 digits)
            if (currentExpression.all { it.isDigit() } && currentExpression.length in 4..8) {
                handleFailedAttempt()
            }
            
        } catch (e: Exception) {
            binding.displayScreen.text = "Error"
            currentExpression = ""
        }
    }
    
    private fun handleFailedAttempt() {
        prefs.recordFailedAttempt()
        
        if (prefs.intruderDetectionEnabled) {
            captureIntruder()
        }
        
        if (prefs.failedAttempts >= 3) {
             breakInNotificationHelper.showBreakInNotification(prefs.failedAttempts)
        }
    }
    
    private fun checkBiometric() {
        if (prefs.biometricEnabled && biometricHelper.isBiometricAvailable()) {
            Handler(Looper.getMainLooper()).postDelayed({
                showBiometricPrompt()
            }, 300)
        }
    }
    
    private fun showBiometricPrompt() {
        biometricHelper.authenticate(
            title = "Unlock Vault",
            subtitle = "Verify identity",
            negativeButtonText = "Cancel",
            callback = object : BiometricHelper.BiometricCallback {
                override fun onSuccess() {
                    prefs.resetFailedAttempts()
                    openVault("main")
                }
                
                override fun onError(errorCode: Int, errorMessage: String) {
                     // Optionally show error
                }
                
                override fun onFailed() {
                    vibrate()
                }
            }
        )
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun captureIntruder() {
        val imageCapture = imageCapture ?: return
        
        val intruderDir = File(filesDir, "intruders").apply { mkdirs() }
        val photoFile = File(intruderDir, "intruder_${System.currentTimeMillis()}.jpg")
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val log = IntruderLog(
                        id = System.currentTimeMillis(),
                        timestamp = System.currentTimeMillis(),
                        photoPath = photoFile.absolutePath,
                        attemptCount = prefs.failedAttempts
                    )
                    repository.addIntruderLog(log)
                }
                
                override fun onError(exception: ImageCaptureException) {
                     val log = IntruderLog(
                        id = System.currentTimeMillis(),
                        timestamp = System.currentTimeMillis(),
                        photoPath = null,
                        attemptCount = prefs.failedAttempts
                    )
                    repository.addIntruderLog(log)
                }
            }
        )
    }
    
    private fun openVault(vaultId: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("vault_id", vaultId)
        intent.putExtra("fake_vault", vaultId == "fake")
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
