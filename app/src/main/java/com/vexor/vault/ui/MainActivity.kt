package com.vexor.vault.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
    
    companion object {
        private const val LOCK_TIMEOUT = 30000L // 30 seconds
    }
    
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
                Toast.makeText(this, "Settings not available in decoy mode", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Tabs
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
        val options = arrayOf("Photos & Videos", "Documents", "All Files")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Files")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkPermissionAndPick("image/*")
                    1 -> pickFiles.launch(arrayOf("application/pdf", "application/*", "text/*"))
                    2 -> pickFiles.launch(arrayOf("*/*"))
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
            // Try multiple methods to delete
            try {
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                try {
                    android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
                } catch (e2: Exception) { }
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
    
    private fun showSelectionOptions() {
        val options = arrayOf("📂 Move to Device", "🗑️ Delete Permanently")
        
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
        binding.progressBar.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.VISIBLE
        binding.tvProgress.text = "Exporting..."
        
        lifecycleScope.launch {
            var successCount = 0
            val totalCount = selectedFiles.size
            val filesToExport = selectedFiles.toList()
            
            for ((index, file) in filesToExport.withIndex()) {
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = "Exporting ${index + 1}/$totalCount..."
                }
                
                val success = exportFileToDevice(file)
                if (success) {
                    encryptionManager.deleteFile(file)
                    repository.removeFile(file)
                    successCount++
                }
            }
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                if (successCount > 0) {
                    Toast.makeText(this@MainActivity, "$successCount files moved to gallery", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Export failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
                exitSelectionMode()
                loadFiles()
            }
        }
    }
    
    private suspend fun exportFileToDevice(file: VaultFile): Boolean = withContext(Dispatchers.IO) {
        try {
            // Decrypt file
            val decryptedBytes = encryptionManager.decryptFile(file)
            if (decryptedBytes == null) {
                return@withContext false
            }
            
            // Determine destination based on file type
            val relativePath = when (file.fileType) {
                FileType.PHOTO -> Environment.DIRECTORY_PICTURES + "/Vexor"
                FileType.VIDEO -> Environment.DIRECTORY_MOVIES + "/Vexor"
                FileType.AUDIO -> Environment.DIRECTORY_MUSIC + "/Vexor"
                else -> Environment.DIRECTORY_DOCUMENTS + "/Vexor"
            }
            
            val mimeType = file.mimeType
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                val collection = when (file.fileType) {
                    FileType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    FileType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    FileType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.originalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                
                val uri = contentResolver.insert(collection, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(decryptedBytes)
                    }
                    
                    // Mark as complete
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    
                    return@withContext true
                }
            } else {
                // Legacy storage
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    when (file.fileType) {
                        FileType.PHOTO -> Environment.DIRECTORY_PICTURES
                        FileType.VIDEO -> Environment.DIRECTORY_MOVIES
                        FileType.AUDIO -> Environment.DIRECTORY_MUSIC
                        else -> Environment.DIRECTORY_DOCUMENTS
                    }
                ), "Vexor")
                dir.mkdirs()
                
                val outputFile = File(dir, file.originalName)
                FileOutputStream(outputFile).use { it.write(decryptedBytes) }
                
                // Notify media scanner
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(outputFile)
                sendBroadcast(intent)
                
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
    
    override fun onPause() {
        super.onPause()
        lastPauseTime = System.currentTimeMillis()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if we need to re-authenticate
        val timeSincePause = System.currentTimeMillis() - lastPauseTime
        if (lastPauseTime > 0 && timeSincePause > LOCK_TIMEOUT) {
            // Go back to auth screen
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        
        loadFiles()
    }
    
    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            // Go to home instead of auth
            moveTaskToBack(true)
        }
    }
}
