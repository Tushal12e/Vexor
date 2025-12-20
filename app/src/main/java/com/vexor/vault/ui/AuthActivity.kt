package com.vexor.vault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.vexor.vault.R
import com.vexor.vault.data.IntruderLog
import com.vexor.vault.data.VaultRepository
import com.vexor.vault.databinding.ActivityAuthBinding
import com.vexor.vault.security.BiometricHelper
import com.vexor.vault.security.BreakInNotificationHelper
import com.vexor.vault.security.VaultPreferences
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AuthActivity : BaseActivity() {
    
    private lateinit var binding: ActivityAuthBinding
    private lateinit var prefs: VaultPreferences
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var repository: VaultRepository
    private lateinit var breakInNotificationHelper: BreakInNotificationHelper
    
    private var enteredPin = ""
    private var isFakeVaultMode = false
    private val pinDots = mutableListOf<View>()
    
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
        
        setupUI()
        checkBiometric()
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
                
                // Use front camera for intruder selfie
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
        
        // Delete button
        binding.btnDelete.setOnClickListener { onDeleteClick() }
        
        // Biometric button
        binding.btnBiometric.setOnClickListener { showBiometricPrompt() }
        binding.btnBiometric.visibility = if (prefs.biometricEnabled && biometricHelper.isBiometricAvailable()) {
            View.VISIBLE
        } else {
            View.GONE
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
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButtonText = getString(R.string.biometric_prompt_negative),
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
    
    private fun onNumberClick(number: String) {
        if (prefs.isLocked()) {
            val remaining = prefs.getLockRemainingSeconds()
            binding.tvStatus.text = "Locked. Try again in ${remaining}s"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            return
        }
        
        if (enteredPin.length < 4) {
            enteredPin += number
            updatePinDots()
            
            if (enteredPin.length == 4) {
                verifyPin()
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
    
    private fun verifyPin() {
        // Check if PIN matches any vault
        val vaultId = prefs.verifyPin(enteredPin)
        
        if (vaultId != null) {
             // Correct PIN
            prefs.resetFailedAttempts()
            openVault(vaultId)
        } else {
            // Wrong PIN
            prefs.recordFailedAttempt()
            vibrate()
            showError()
            
            // Break-in notification
            if (prefs.intruderDetectionEnabled && prefs.failedAttempts >= 3) {
                breakInNotificationHelper.showBreakInNotification(prefs.failedAttempts)
            }

            // Capture intruder photo
            if (prefs.intruderDetectionEnabled) {
                captureIntruder()
            }
        }
    }
    
    private fun showError() {
        binding.tvStatus.text = getString(R.string.wrong_pin)
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error))
        
        // Shake animation
        binding.pinContainer.animate()
            .translationX(-20f).setDuration(50)
            .withEndAction {
                binding.pinContainer.animate()
                    .translationX(20f).setDuration(50)
                    .withEndAction {
                        binding.pinContainer.animate()
                            .translationX(0f).setDuration(50)
                            .start()
                    }.start()
            }.start()
        
        Handler(Looper.getMainLooper()).postDelayed({
            enteredPin = ""
            updatePinDots()
            binding.tvStatus.text = getString(R.string.enter_pin)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }, 1000)
    }
    
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun captureIntruder() {
        val imageCapture = imageCapture ?: return
        
        // Create file for intruder photo
        val intruderDir = File(filesDir, "intruders").apply { mkdirs() }
        val photoFile = File(intruderDir, "intruder_${System.currentTimeMillis()}.jpg")
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Save intruder log with photo path
                    val log = IntruderLog(
                        id = System.currentTimeMillis(),
                        timestamp = System.currentTimeMillis(),
                        photoPath = photoFile.absolutePath,
                        attemptCount = prefs.failedAttempts
                    )
                    repository.addIntruderLog(log)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    // Still log the attempt even without photo
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
        intent.putExtra("vault_id", vaultId) // Pass ID directly
        intent.putExtra("fake_vault", vaultId == "fake") // Legacy support
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
