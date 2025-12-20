package com.vexor.vault.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vexor.vault.data.VaultRepository
import com.vexor.vault.databinding.ActivityShareReceiverBinding
import com.vexor.vault.security.FileEncryptionManager
import com.vexor.vault.security.VaultPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : BaseActivity() {
    
    private lateinit var binding: ActivityShareReceiverBinding
    private lateinit var prefs: VaultPreferences
    private lateinit var encryptionManager: FileEncryptionManager
    private lateinit var repository: VaultRepository
    
    private var pendingUris = mutableListOf<Uri>()
    private var enteredPin = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        encryptionManager = FileEncryptionManager(this)
        repository = VaultRepository(this)
        
        // Get shared files
        handleIntent(intent)
        
        if (pendingUris.isEmpty()) {
            Toast.makeText(this, "No files to add", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupPinPad()
        binding.tvTitle.text = "Enter PIN to add ${pendingUris.size} file(s)"
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                    pendingUris.add(it)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                    pendingUris.addAll(it)
                }
            }
        }
    }
    
    private fun setupPinPad() {
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
                    updateDots()
                    if (enteredPin.length == 4) verifyAndImport()
                }
            }
        }
        
        binding.btnDelete.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
                updateDots()
            }
        }
        
        binding.btnCancel.setOnClickListener { finish() }
    }
    
    private fun updateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        dots.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index < enteredPin.length) 
                    com.vexor.vault.R.drawable.pin_dot_filled 
                else 
                    com.vexor.vault.R.drawable.pin_dot_empty
            )
        }
    }
    
    private fun verifyAndImport() {
        val isFakeVault = prefs.fakeVaultEnabled && prefs.isFakePin(enteredPin)
        
        if (prefs.verifyPin(enteredPin) || isFakeVault) {
            importFiles(isFakeVault)
        } else {
            Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            enteredPin = ""
            updateDots()
        }
    }
    
    private fun importFiles(isFakeVault: Boolean) {
        binding.tvTitle.text = "Encrypting..."
        
        lifecycleScope.launch {
            var count = 0
            for (uri in pendingUris) {
                val vaultFile = encryptionManager.encryptFile(uri, isFakeVault)
                if (vaultFile != null) {
                    repository.addFile(vaultFile)
                    count++
                    
                    // Delete original
                    withContext(Dispatchers.IO) {
                        try {
                            contentResolver.delete(uri, null, null)
                        } catch (e: Exception) { }
                    }
                }
            }
            
            Toast.makeText(this@ShareReceiverActivity, 
                "$count file(s) added to vault", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
