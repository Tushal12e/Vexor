package com.vexor.vault.ui

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.vexor.vault.R
import com.vexor.vault.data.FileType
import com.vexor.vault.data.VaultFile
import com.vexor.vault.data.VaultRepository
import com.vexor.vault.databinding.ActivityMainBinding
import com.vexor.vault.security.FileEncryptionManager
import com.vexor.vault.ui.adapters.VaultFileAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: VaultRepository
    private lateinit var encryptionManager: FileEncryptionManager
    private lateinit var adapter: VaultFileAdapter
    
    private var isFakeVault = false
    private var currentFilter: FileType? = null
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<VaultFile>()
    
    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            importFiles(uris)
        }
    }
    
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            importFiles(uris)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        isFakeVault = intent.getBooleanExtra("fake_vault", false)
        
        repository = VaultRepository(this)
        encryptionManager = FileEncryptionManager(this)
        
        setupUI()
        loadFiles()
    }
    
    private fun setupUI() {
        // Toolbar - show indicator for fake vault mode
        binding.toolbar.title = if (isFakeVault) "Vexor (Decoy)" else "Vexor"
        binding.btnSettings.setOnClickListener {
            if (!isFakeVault) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                Toast.makeText(this, "Settings not available in decoy mode", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Tabs
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = when (tab?.position) {
                    0 -> null // All
                    1 -> FileType.PHOTO
                    2 -> FileType.VIDEO
                    3 -> FileType.DOCUMENT
                    else -> null
                }
                loadFiles()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // RecyclerView
        adapter = VaultFileAdapter(
            encryptionManager = encryptionManager,
            onItemClick = { file -> openFile(file) },
            onItemLongClick = { file -> toggleSelection(file) }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerView.adapter = adapter
        
        // FAB
        binding.fabAdd.setOnClickListener { showAddOptions() }
        
        // Selection toolbar
        binding.btnCancelSelection.setOnClickListener { exitSelectionMode() }
        binding.btnDeleteSelected.setOnClickListener { deleteSelectedFiles() }
    }
    
    private fun loadFiles() {
        val files = if (currentFilter != null) {
            repository.getFilesByType(currentFilter!!, isFakeVault)
        } else {
            repository.getAllFiles(isFakeVault)
        }
        
        adapter.submitList(files)
        
        binding.tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.tvFileCount.text = "${files.size} files"
    }
    
    private fun showAddOptions() {
        val options = arrayOf("Photos & Videos", "Documents", "All Files")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Files")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        checkPermissionAndPick("image/*")
                    }
                    1 -> {
                        pickFiles.launch(arrayOf("application/pdf", "application/*", "text/*"))
                    }
                    2 -> {
                        pickFiles.launch(arrayOf("*/*"))
                    }
                }
            }
            .show()
    }
    
    private fun checkPermissionAndPick(mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                ), 100)
            } else {
                pickMedia.launch(mimeType)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            } else {
                pickMedia.launch(mimeType)
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickMedia.launch("image/*")
        }
    }
    
    private fun importFiles(uris: List<Uri>) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.VISIBLE
        binding.tvProgress.text = "Encrypting files..."
        
        lifecycleScope.launch {
            var count = 0
            for (uri in uris) {
                count++
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = "Encrypting $count/${uris.size}..."
                }
                
                val vaultFile = encryptionManager.encryptFile(uri, isFakeVault)
                if (vaultFile != null) {
                    repository.addFile(vaultFile)
                    
                    // Delete original file after successful encryption
                    deleteOriginalFile(uri)
                }
            }
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                Toast.makeText(this@MainActivity, "$count files hidden in vault", Toast.LENGTH_SHORT).show()
                loadFiles()
            }
        }
    }
    
    private suspend fun deleteOriginalFile(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            // Try to delete the original file
            when {
                uri.scheme == "content" -> {
                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (e: SecurityException) {
                        // If we can't delete, try using DocumentsContract
                        try {
                            android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
                        } catch (e2: Exception) {
                            // Can't delete - user might need to delete manually
                        }
                    }
                }
                uri.scheme == "file" -> {
                    uri.path?.let { path ->
                        java.io.File(path).delete()
                    }
                }
            }
            
            // Also try to remove from MediaStore
            try {
                val id = android.content.ContentUris.parseId(uri)
                val deleteUri = MediaStore.Files.getContentUri("external")
                contentResolver.delete(deleteUri, "${MediaStore.MediaColumns._ID}=?", arrayOf(id.toString()))
            } catch (e: Exception) {
                // Ignore
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun openFile(file: VaultFile) {
        if (isSelectionMode) {
            toggleSelection(file)
            return
        }
        
        when (file.fileType) {
            FileType.PHOTO -> {
                val intent = Intent(this, PhotoViewerActivity::class.java)
                intent.putExtra("file", file)
                startActivity(intent)
            }
            FileType.VIDEO -> {
                val intent = Intent(this, VideoPlayerActivity::class.java)
                intent.putExtra("file", file)
                startActivity(intent)
            }
            else -> {
                Toast.makeText(this, "Opening ${file.originalName}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun toggleSelection(file: VaultFile) {
        if (!isSelectionMode) {
            isSelectionMode = true
            binding.selectionToolbar.visibility = View.VISIBLE
        }
        
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        
        adapter.setSelectedFiles(selectedFiles)
        binding.tvSelectedCount.text = "${selectedFiles.size} selected"
        
        if (selectedFiles.isEmpty()) {
            exitSelectionMode()
        }
    }
    
    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedFiles.clear()
        adapter.setSelectedFiles(emptySet())
        binding.selectionToolbar.visibility = View.GONE
    }
    
    private fun deleteSelectedFiles() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirm))
            .setMessage(getString(R.string.delete_confirm_msg))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                selectedFiles.forEach { file ->
                    encryptionManager.deleteFile(file)
                    repository.removeFile(file)
                }
                Toast.makeText(this, "${selectedFiles.size} files deleted permanently", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadFiles()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        loadFiles()
    }
    
    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
}
