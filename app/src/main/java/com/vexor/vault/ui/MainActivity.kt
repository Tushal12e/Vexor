package com.vexor.vault.ui

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
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
            Toast.makeText(this, "✅ Original files deleted from gallery!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Original files kept in gallery", Toast.LENGTH_SHORT).show()
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
            val mediaStoreUris = mutableListOf<Uri>()
            
            for (uri in uris) {
                count++
                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = "Encrypting $count/${uris.size}..."
                }
                
                val vaultFile = encryptionManager.encryptFile(uri, isFakeVault)
                if (vaultFile != null) {
                    repository.addFile(vaultFile)
                    
                    // Try to get MediaStore URI for this file
                    val mediaUri = getMediaStoreUri(uri, vaultFile.mimeType)
                    if (mediaUri != null) {
                        mediaStoreUris.add(mediaUri)
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                Toast.makeText(this@MainActivity, "✅ $count files added to vault!", Toast.LENGTH_SHORT).show()
                loadFiles()
                
                // Ask to delete originals if we have valid MediaStore URIs
                if (mediaStoreUris.isNotEmpty()) {
                    askToDeleteOriginals(mediaStoreUris)
                }
            }
        }
    }
    
    private suspend fun getMediaStoreUri(documentUri: Uri, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        try {
            // If it's already a MediaStore URI, return it
            if (documentUri.authority == "media") {
                return@withContext documentUri
            }
            
            // Get the file path from document URI
            val filePath = getPathFromUri(documentUri) ?: return@withContext null
            
            // Query MediaStore to find this file
            val collection = when {
                mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
            
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            
            contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return@withContext ContentUris.withAppendedId(collection, id)
                }
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun getPathFromUri(uri: Uri): String? {
        try {
            // Handle document URIs
            if (DocumentsContract.isDocumentUri(this, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                
                when (uri.authority) {
                    "com.android.externalstorage.documents" -> {
                        val split = docId.split(":")
                        if (split[0] == "primary") {
                            return Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                        }
                    }
                    "com.android.providers.downloads.documents" -> {
                        if (docId.startsWith("raw:")) {
                            return docId.substring(4)
                        }
                    }
                    "com.android.providers.media.documents" -> {
                        val split = docId.split(":")
                        val type = split[0]
                        val id = split[1]
                        
                        val contentUri = when (type) {
                            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> MediaStore.Files.getContentUri("external")
                        }
                        
                        contentResolver.query(
                            ContentUris.withAppendedId(contentUri, id.toLong()),
                            arrayOf(MediaStore.MediaColumns.DATA),
                            null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                return cursor.getString(0)
                            }
                        }
                    }
                }
            }
            
            // Handle content URIs
            if (uri.scheme == "content") {
                contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0)
                    }
                }
            }
            
            // Handle file URIs
            if (uri.scheme == "file") {
                return uri.path
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private fun askToDeleteOriginals(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            try {
                pendingDeleteUris.clear()
                pendingDeleteUris.addAll(uris)
                
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteRequest.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback: Ask user to delete manually
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Original Files?")
                    .setMessage("Files have been encrypted. Please delete the originals manually from your gallery to hide them.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } else if (uris.isNotEmpty()) {
            // Android 10 and below - try direct delete
            lifecycleScope.launch {
                var deleted = 0
                for (uri in uris) {
                    try {
                        val result = contentResolver.delete(uri, null, null)
                        if (result > 0) deleted++
                    } catch (e: Exception) { }
                }
                if (deleted > 0) {
                    Toast.makeText(this@MainActivity, "Deleted $deleted original files", Toast.LENGTH_SHORT).show()
                }
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
