package com.vexor.vault.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vexor.vault.data.VaultFile
import com.vexor.vault.databinding.ActivityPhotoViewerBinding
import com.vexor.vault.security.FileEncryptionManager
import kotlinx.coroutines.launch

class PhotoViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPhotoViewerBinding
    private lateinit var encryptionManager: FileEncryptionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        encryptionManager = FileEncryptionManager(this)
        
        val file = intent.getSerializableExtra("file") as? VaultFile
        if (file == null) {
            finish()
            return
        }
        
        setupUI()
        loadPhoto(file)
    }
    
    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.imageView.setOnClickListener {
            toggleUI()
        }
    }
    
    private fun toggleUI() {
        val visible = binding.toolbar.visibility == View.VISIBLE
        binding.toolbar.visibility = if (visible) View.GONE else View.VISIBLE
    }
    
    private fun loadPhoto(file: VaultFile) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvFileName.text = file.originalName
        
        lifecycleScope.launch {
            val bytes = encryptionManager.decryptFile(file)
            if (bytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                binding.imageView.setImageBitmap(bitmap)
            }
            binding.progressBar.visibility = View.GONE
        }
    }
}
