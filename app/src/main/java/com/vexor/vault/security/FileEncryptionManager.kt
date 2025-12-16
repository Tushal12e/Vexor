package com.vexor.vault.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vexor.vault.data.FileType
import com.vexor.vault.data.VaultFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.UUID

class FileEncryptionManager(private val context: Context) {
    
    private val vaultDir: File by lazy {
        File(context.filesDir, "vault").apply { mkdirs() }
    }
    
    private val thumbnailDir: File by lazy {
        File(context.filesDir, "thumbnails").apply { mkdirs() }
    }
    
    private val fakeVaultDir: File by lazy {
        File(context.filesDir, "fake_vault").apply { mkdirs() }
    }
    
    interface EncryptionCallback {
        fun onProgress(progress: Int)
        fun onComplete(vaultFile: VaultFile)
        fun onError(error: String)
    }
    
    suspend fun encryptFile(
        uri: Uri,
        isFakeVault: Boolean = false,
        callback: EncryptionCallback? = null
    ): VaultFile? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val fileType = FileType.fromMimeType(mimeType)
            
            // Get original filename
            val cursor = contentResolver.query(uri, null, null, null, null)
            val originalName = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else "file_${System.currentTimeMillis()}"
                } else "file_${System.currentTimeMillis()}"
            } ?: "file_${System.currentTimeMillis()}"
            
            // Generate encrypted filename
            val encryptedName = UUID.randomUUID().toString()
            val targetDir = if (isFakeVault) fakeVaultDir else vaultDir
            val encryptedFile = File(targetDir, encryptedName)
            
            // Read and encrypt file
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open input stream")
            
            val fileBytes = inputStream.use { it.readBytes() }
            val fileSize = fileBytes.size.toLong()
            
            callback?.onProgress(30)
            
            // Encrypt
            val encrypted = CryptoManager.encrypt(fileBytes)
            
            callback?.onProgress(70)
            
            // Write encrypted data
            FileOutputStream(encryptedFile).use { fos ->
                // Write IV length + IV + encrypted data
                val ivBytes = encrypted.iv
                fos.write(ivBytes.size)
                fos.write(ivBytes)
                fos.write(encrypted.cipherText)
            }
            
            callback?.onProgress(90)
            
            // Generate thumbnail for images/videos
            var thumbnailPath: String? = null
            if (fileType == FileType.PHOTO || fileType == FileType.VIDEO) {
                thumbnailPath = generateThumbnail(uri, fileType, encryptedName)
            }
            
            val vaultFile = VaultFile(
                id = System.currentTimeMillis(),
                originalName = originalName,
                encryptedName = encryptedName,
                mimeType = mimeType,
                fileType = fileType,
                size = fileSize,
                encryptedPath = encryptedFile.absolutePath,
                thumbnailPath = thumbnailPath,
                isFakeVault = isFakeVault
            )
            
            callback?.onProgress(100)
            callback?.onComplete(vaultFile)
            
            vaultFile
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onError(e.message ?: "Encryption failed")
            null
        }
    }
    
    suspend fun decryptFile(vaultFile: VaultFile): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(vaultFile.encryptedPath)
            if (!encryptedFile.exists()) return@withContext null
            
            FileInputStream(encryptedFile).use { fis ->
                // Read IV
                val ivLength = fis.read()
                val iv = ByteArray(ivLength)
                fis.read(iv)
                
                // Read encrypted data
                val cipherText = fis.readBytes()
                
                // Decrypt
                val encryptedData = CryptoManager.EncryptedData(cipherText, iv)
                CryptoManager.decrypt(encryptedData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun decryptToTempFile(vaultFile: VaultFile): File? = withContext(Dispatchers.IO) {
        try {
            val decrypted = decryptFile(vaultFile) ?: return@withContext null
            
            val ext = vaultFile.getExtension()
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.$ext")
            
            FileOutputStream(tempFile).use { it.write(decrypted) }
            
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun deleteFile(vaultFile: VaultFile): Boolean {
        try {
            // Delete encrypted file
            File(vaultFile.encryptedPath).delete()
            
            // Delete thumbnail if exists
            vaultFile.thumbnailPath?.let { File(it).delete() }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    private fun generateThumbnail(uri: Uri, fileType: FileType, name: String): String? {
        return try {
            val bitmap: Bitmap? = when (fileType) {
                FileType.PHOTO -> {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4 // Scale down
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                }
                FileType.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val frame = retriever.getFrameAtTime(1000000) // 1 second
                    retriever.release()
                    frame
                }
                else -> null
            }
            
            bitmap?.let {
                val thumbFile = File(thumbnailDir, "${name}_thumb.jpg")
                FileOutputStream(thumbFile).use { fos ->
                    it.compress(Bitmap.CompressFormat.JPEG, 70, fos)
                }
                // Encrypt thumbnail
                val thumbBytes = thumbFile.readBytes()
                val encrypted = CryptoManager.encrypt(thumbBytes)
                
                val encThumbFile = File(thumbnailDir, "${name}_thumb.enc")
                FileOutputStream(encThumbFile).use { fos ->
                    fos.write(encrypted.iv.size)
                    fos.write(encrypted.iv)
                    fos.write(encrypted.cipherText)
                }
                
                thumbFile.delete() // Remove unencrypted
                encThumbFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun decryptThumbnail(thumbnailPath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(thumbnailPath)
            if (!file.exists()) return@withContext null
            
            FileInputStream(file).use { fis ->
                val ivLength = fis.read()
                val iv = ByteArray(ivLength)
                fis.read(iv)
                val cipherText = fis.readBytes()
                
                val decrypted = CryptoManager.decrypt(CryptoManager.EncryptedData(cipherText, iv))
                BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun getVaultSize(): Long {
        return vaultDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
    
    fun clearVault() {
        vaultDir.deleteRecursively()
        thumbnailDir.deleteRecursively()
        vaultDir.mkdirs()
        thumbnailDir.mkdirs()
    }
    
    fun clearFakeVault() {
        fakeVaultDir.deleteRecursively()
        fakeVaultDir.mkdirs()
    }
}
