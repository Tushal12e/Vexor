package com.vexor.vault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.vexor.vault.R
import com.vexor.vault.data.IntruderLog
import com.vexor.vault.data.VaultRepository
import com.vexor.vault.databinding.ActivityAuthBinding
import com.vexor.vault.security.BiometricHelper
import com.vexor.vault.security.BreakInNotificationHelper
import com.vexor.vault.security.VaultPreferences
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AuthActivity : BaseActivity() {
    
    private lateinit var binding: ActivityAuthBinding
    private lateinit var prefs: VaultPreferences
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var repository: VaultRepository
    private lateinit var breakInNotificationHelper: BreakInNotificationHelper
    
    private var currentExpression = ""
    private var isFakeVaultMode = false
    
    // Camera for intruder detection
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        biometricHelper = BiometricHelper(this)
        repository = VaultRepository(this)
        breakInNotificationHelper = BreakInNotificationHelper(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Check if first time setup
        if (!prefs.isFirstSetupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        
        // Request camera permission for intruder detection
        if (prefs.intruderDetectionEnabled) {
            checkCameraPermission()
        }
        
        setupCalculatorUI()
        
        // Auto-show biometric if enabled
        if (prefs.biometricEnabled) {
              checkBiometric()
        }
    }
    
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
        } else {
            startCamera()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
            } catch (e: Exception) {
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
        
        // Clear
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
        
        // Equals - This is the Trigger
        binding.btnEquals.setOnClickListener { onEqualsClick() }
        
        // Long press on title/logo to trigger Biometric? 
        // Or long press on "."
        binding.btnDot.setOnLongClickListener {
             if (prefs.biometricEnabled) showBiometricPrompt()
             true
        }
    }
    
    private fun appendToDisplay(str: String) {
        if (currentExpression.length > 20) return // Limit length
        currentExpression += str
        updateDisplay(currentExpression)
    }
    
    private fun updateDisplay(text: String) {
        binding.tvDisplay.text = text
    }
    
    private fun onEqualsClick() {
        if (currentExpression.isEmpty()) return
        
        // 1. Check if expression MATCHES A VAULT PIN
        val vaultId = prefs.verifyPin(currentExpression)
        
        if (vaultId != null) {
            // Unlocking Vault
            prefs.resetFailedAttempts()
            openVault(vaultId)
            return
        }
        
        // 2. If not a PIN, Evaluate Math
        try {
            // Replace x with * for expression builder
            val expressionStr = currentExpression.replace("x", "*")
            val expression = ExpressionBuilder(expressionStr).build()
            val result = expression.evaluate()
            
            // Format result (remove .0 if integer)
            val longResult = result.toLong()
            if (result == longResult.toDouble()) {
                currentExpression = longResult.toString()
            } else {
                currentExpression = result.toString()
            }
            updateDisplay(currentExpression)
            
            // NOTE: If math fails or user enters weird pattern, we could count it as failed attempt?
            // But standard calculator shouldn't penalize bad math.
            // We ONLY capture intruder if they enter a specific length (4-6) AND it's numeric AND it fails?
            // Let's rely on Explicit "Login Mode" for intruder? 
            // Or just if they try to enter a PIN that is close?
            // User requirement: "wrong pass capture pic".
            // Implementation: If input length is 4-8 digits and numeric, and NOT a pin, capture!
            
            if (currentExpression.all { it.isDigit() } && currentExpression.length in 4..8) {
                // Potential PIN attempt failed
                handleFailedAttempt()
            }
            
        } catch (e: Exception) {
            binding.tvDisplay.text = "Error"
            currentExpression = ""
        }
    }
    
    private fun handleFailedAttempt() {
        prefs.recordFailedAttempt()
        
        // Only capture selfie if multiple attempts or specific setting?
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
            subtitle = "Verify identity to access secured files",
            negativeButtonText = "Cancel",
            callback = object : BiometricHelper.BiometricCallback {
                override fun onSuccess() {
                    prefs.resetFailedAttempts()
                    openVault("main")
                }
                
                override fun onError(errorCode: Int, errorMessage: String) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(this@AuthActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
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
