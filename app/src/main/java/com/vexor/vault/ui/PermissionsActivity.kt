package com.vexor.vault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vexor.vault.databinding.ActivityPermissionsBinding

class PermissionsActivity : BaseActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val requiredPermissions = mutableListOf<String>().apply {
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkAllPermissions()
    }

    // Special launcher for MANAGE_EXTERNAL_STORAGE (Android 11+)
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAllPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener {
            requestPermissions()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            proceed()
        }
    }

    private fun requestPermissions() {
        // 1. Check Standard Permissions
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // 2. Check Manage Storage (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    }
                    return
                }
            }
            // All good
            proceed()
        }
    }

    private fun checkAllPermissions() {
        if (hasAllPermissions()) {
            proceed()
        } else {
            Toast.makeText(this, "Permissions are required to use Vexor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasAllPermissions(): Boolean {
        // Standard checks
        val standardGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        // Storage Manager check (Android 11+)
        val storageManagerGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        return standardGranted && storageManagerGranted
    }

    private fun proceed() {
        startActivity(Intent(this, CalculatorActivity::class.java))
        finish()
    }
}
