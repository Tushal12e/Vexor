package com.vexor.vault.ui

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
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
import com.vexor.vault.security.VaultPreferences
import com.vexor.vault.ui.adapters.VaultFileAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: VaultRepository
    private lateinit var encryptionManager: FileEncryptionManager
    private lateinit var prefs: VaultPreferences
    private lateinit var adapter: VaultFileAdapter
    
    private var isFakeVault = false
    private var currentFilter: FileType? = null
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<VaultFile>()
    private var lastPauseTime = 0L
    private var pendingDeleteUris = mutableListOf<Uri>()
    
    companion object {
        private const val LOCK_TIMEOUT = 30000L
        private const val DELETE_REQUEST_CODE = 1001
    }
    
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: Exception) { }
            }
            importFiles(uris)
        }
    }
    
    private val deleteRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Original files deleted from gallery", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteUris.clear()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        isFakeVault = intent.getBooleanExtra("fake_vault", false)
        
        repository = VaultRepository(this)
        encryptionManager = FileEncryptionManager(this)
        prefs = VaultPreferences(this)
        
        setupUI()
        loadFiles()
    }
    
    private fun setupUI() {
        binding.toolbar.title = if (isFakeVault) "Vexor (Decoy)" else "Vexor"
        binding.btnSettings.setOnClickListener {
            if (!isFakeVault) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                Toast.makeText(this, "Settings hidden in decoy mode", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = when (tab?.position) {
                    0 -> null
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
        
        adapter = VaultFileAdapter(
            encryptionManager = encryptionManager,
            onItemClick = { file -> openFile(file) },
            onItemLongClick = { file -> toggleSelection(file) }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerView.adapter = adapter
        
        binding.fabAdd.setOnClickListener { showAddOptions() }
        
        binding.btnCancelSelection.setOnClickListener { exitSelectionMode() }
        binding.btnDeleteSelected.setOnClickListener { showSelectionOptions() }
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
        val options = arrayOf(
            "📷 Photos", 
            "🎬 Videos", 
            "📄 Documents",
            "📁 All Files"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Files to Vault")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFiles.launch(arrayOf("image/*"))
                    1 -> pickFiles.launch(arrayOf("video/*"))
                    2 -> pickFiles.launch(arrayOf("application/pdf", "application/*", "text/*"))
                    3 -> pickFiles.launch(arrayOf("*/*"))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun importFiles(uris: List<Uri>) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.VISIBLE
        binding.tvProgress.text = "Encrypting files..."
        
        lifecycleScope.launch {
            var count = 0
            val urisToDelete = mutableListOf<Uri>()
            
            for (uri in uris) {
                count++
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = "Encrypting $count/${uris.size}..."
                }
                
                val vaultFile = encryptionManager.encryptFile(uri, isFakeVault)
                if (vaultFile != null) {
                    repository.addFile(vaultFile)
                    
                    // Try to delete original
                    val deleted = tryDeleteFile(uri)
                    if (!deleted) {
                        urisToDelete.add(uri)
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                Toast.makeText(this@MainActivity, "✅ $count files added to vault!", Toast.LENGTH_SHORT).show()
                loadFiles()
                
                // If some files couldn't be deleted, ask user
                if (urisToDelete.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestDeletePermission(urisToDelete)
                }
            }
        }
    }
    
    private suspend fun tryDeleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Method 1: Direct delete
            val deleted = contentResolver.delete(uri, null, null)
            if (deleted > 0) return@withContext true
            
            // Method 2: DocumentsContract
            try {
                if (DocumentsContract.isDocumentUri(this@MainActivity, uri)) {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                    return@withContext true
                }
            } catch (e: Exception) { }
            
            false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun requestDeletePermission(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pendingDeleteUris.clear()
                pendingDeleteUris.addAll(uris)
                
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteRequest.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Please delete original files manually from gallery", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun openFile(file: VaultFile) {
        if (isSelectionMode) {
            toggleSelection(file)
            return
        }
        
        when (file.fileType) {
            FileType.PHOTO -> {
                startActivity(Intent(this, PhotoViewerActivity::class.java).putExtra("file", file))
            }
            FileType.VIDEO -> {
                startActivity(Intent(this, VideoPlayerActivity::class.java).putExtra("file", file))
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
    
    private fun showSelectionOptions() {
        val options = arrayOf(
            "📂 Restore to Device (Decrypt & Save)",
            "🗑️ Delete from Vault"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("${selectedFiles.size} file(s) selected")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportSelectedFiles()
                    1 -> deleteSelectedFiles()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun exportSelectedFiles() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restore to Device?")
            .setMessage("Files will be decrypted and saved to your gallery/downloads folder.")
            .setPositiveButton("Restore") { _, _ -> doExport() }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun doExport() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            var successCount = 0
            val filesToExport = selectedFiles.toList()
            
            for ((index, file) in filesToExport.withIndex()) {
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = "Restoring ${index + 1}/${filesToExport.size}..."
                }
                
                val success = exportFileToDevice(file)
                if (success) successCount++
            }
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                if (successCount > 0) {
                    Toast.makeText(this@MainActivity, "✅ $successCount files restored!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ Export failed", Toast.LENGTH_SHORT).show()
                }
                exitSelectionMode()
            }
        }
    }
    
    private suspend fun exportFileToDevice(file: VaultFile): Boolean = withContext(Dispatchers.IO) {
        try {
            val decryptedBytes = encryptionManager.decryptFile(file) ?: return@withContext false
            
            val relativePath = when (file.fileType) {
                FileType.PHOTO -> Environment.DIRECTORY_PICTURES + "/Vexor"
                FileType.VIDEO -> Environment.DIRECTORY_MOVIES + "/Vexor"
                FileType.AUDIO -> Environment.DIRECTORY_MUSIC + "/Vexor"
                else -> Environment.DIRECTORY_DOWNLOADS + "/Vexor"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = when (file.fileType) {
                    FileType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    FileType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    FileType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
                
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.originalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                
                val uri = contentResolver.insert(collection, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(decryptedBytes) }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    return@withContext true
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    when (file.fileType) {
                        FileType.PHOTO -> Environment.DIRECTORY_PICTURES
                        FileType.VIDEO -> Environment.DIRECTORY_MOVIES
                        else -> Environment.DIRECTORY_DOWNLOADS
                    }
                ), "Vexor")
                dir.mkdirs()
                
                val outputFile = File(dir, file.originalName)
                FileOutputStream(outputFile).use { it.write(decryptedBytes) }
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outputFile)))
                return@withContext true
            }
            
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun deleteSelectedFiles() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ${selectedFiles.size} file(s)?")
            .setMessage("These files will be permanently deleted from the vault.")
            .setPositiveButton("Delete") { _, _ ->
                selectedFiles.forEach { file ->
                    encryptionManager.deleteFile(file)
                    repository.removeFile(file)
                }
                Toast.makeText(this, "🗑️ ${selectedFiles.size} files deleted!", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadFiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        lastPauseTime = System.currentTimeMillis()
    }
    
    override fun onResume() {
        super.onResume()
        
        val timeSincePause = System.currentTimeMillis() - lastPauseTime
        if (lastPauseTime > 0 && timeSincePause > LOCK_TIMEOUT) {
            startActivity(Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        
        loadFiles()
    }
    
    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            moveTaskToBack(true)
        }
    }
}
