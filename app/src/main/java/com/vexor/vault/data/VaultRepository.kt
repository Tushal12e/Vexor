package com.vexor.vault.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vexor.vault.security.CryptoManager
import java.io.File

class VaultRepository(private val context: Context) {
    
    private val gson = Gson()
    
    private val filesDbFile: File by lazy {
        File(context.filesDir, "vault_files.enc")
    }
    
    private val intruderDbFile: File by lazy {
        File(context.filesDir, "intruder_logs.enc")
    }
    
    private val foldersDbFile: File by lazy {
        File(context.filesDir, "vault_folders.enc")
    }
    
    private var cachedFiles: MutableList<VaultFile>? = null
    private var cachedIntruderLogs: MutableList<IntruderLog>? = null
    private var cachedFolders: MutableList<VaultFolder>? = null
    
    // ===== Vault Files =====
    
    fun getAllFiles(vaultId: String): List<VaultFile> {
        if (cachedFiles == null) {
            cachedFiles = loadFiles().toMutableList()
        }
        return cachedFiles!!.filter { it.vaultId == vaultId }
            .sortedByDescending { it.dateAdded }
    }
    
    fun getFilesByType(type: FileType, vaultId: String): List<VaultFile> {
        if (cachedFiles == null) {
            cachedFiles = loadFiles().toMutableList()
        }
        return cachedFiles!!.filter { it.fileType == type && it.vaultId == vaultId }
            .sortedByDescending { it.dateAdded }
    }
    
    fun getFilesByFolder(vaultId: String, folderId: String?): List<VaultFile> {
        if (cachedFiles == null) {
            cachedFiles = loadFiles().toMutableList()
        }
        return cachedFiles!!.filter { it.vaultId == vaultId && it.folderId == folderId }
            .sortedByDescending { it.dateAdded }
    }
    
    // Legacy support
    fun getAllFiles(isFakeVault: Boolean): List<VaultFile> {
        val targetId = if (isFakeVault) "fake" else "main"
        return getAllFiles(targetId)
    }
    
    fun getFilesByType(type: FileType, isFakeVault: Boolean): List<VaultFile> {
        val targetId = if (isFakeVault) "fake" else "main"
        return getFilesByType(type, targetId)
    }
    
    fun addFile(file: VaultFile) {
        val files = getAllFiles(file.vaultId).toMutableList() // Use the new getAllFiles with vaultId
        files.add(file)
        cachedFiles = (cachedFiles?.filter { it.vaultId != file.vaultId } ?: emptyList()).toMutableList().apply { addAll(files) } // Update cachedFiles carefully
        saveFiles(cachedFiles!!)
    }
    
    fun removeFile(file: VaultFile) {
        if (cachedFiles == null) cachedFiles = loadFiles().toMutableList()
        cachedFiles!!.removeAll { it.id == file.id }
        saveFiles(cachedFiles!!)
    }
    
    fun getFileCount(isFakeVault: Boolean = false): Int {
        return getAllFiles(isFakeVault).size
    }
    
    fun getTotalSize(isFakeVault: Boolean = false): Long {
        return getAllFiles(isFakeVault).sumOf { it.size }
    }
    
    private fun loadFiles(): List<VaultFile> {
        return try {
            if (!filesDbFile.exists()) return emptyList()
            
            val encrypted = filesDbFile.readText()
            val json = CryptoManager.decryptString(encrypted)
            
            val type = object : TypeToken<List<VaultFile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun saveFiles(files: List<VaultFile>) {
        try {
            val json = gson.toJson(files)
            val encrypted = CryptoManager.encryptString(json)
            filesDbFile.writeText(encrypted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun clearAllFiles(isFakeVault: Boolean = false) {
        if (cachedFiles == null) cachedFiles = loadFiles().toMutableList()
        val targetId = if (isFakeVault) "fake" else "main"
        cachedFiles!!.removeAll { it.vaultId == targetId }
        saveFiles(cachedFiles!!)
    }
    
    // ===== Folders =====
    
    fun getFolders(vaultId: String, parentId: String? = null): List<VaultFolder> {
        if (cachedFolders == null) {
            cachedFolders = loadFolders().toMutableList()
        }
        return cachedFolders!!.filter { it.vaultId == vaultId && it.parentFolderId == parentId }
            .sortedByDescending { it.dateCreated }
    }
    
    fun addFolder(folder: VaultFolder) {
        val folders = getFolders(folder.vaultId).toMutableList()
        // Wait, getFolders filters. We need to load all first.
        if (cachedFolders == null) cachedFolders = loadFolders().toMutableList()
        
        cachedFolders!!.add(folder)
        saveFolders(cachedFolders!!)
    }
    
    fun removeFolder(folder: VaultFolder) {
        if (cachedFolders == null) cachedFolders = loadFolders().toMutableList()
        cachedFolders!!.removeAll { it.id == folder.id }
        saveFolders(cachedFolders!!)
        
        // Also delete contents? Or move to root? 
        // For security, usually better to warn or move to root.
        // Let's implement "Delete Folder & Contents" logic in UI, but repository just deletes the folder entry.
        // Ideally, we should cascade delete or move files. 
        // Simple approach: files become orphaned (visible in "All Files" but not in folder view).
        // Better: Update their folderId to null.
        
        val allFiles = getAllFiles(folder.vaultId).map { 
            if (it.folderId == folder.id) it.copy(folderId = null) else it 
        }
        cachedFiles = allFiles.toMutableList()
        saveFiles(allFiles)
    }
    
    private fun loadFolders(): List<VaultFolder> {
        return try {
            if (!foldersDbFile.exists()) return emptyList()
            val encrypted = foldersDbFile.readText()
            val json = CryptoManager.decryptString(encrypted)
            val type = object : TypeToken<List<VaultFolder>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun saveFolders(folders: List<VaultFolder>) {
        try {
            val json = gson.toJson(folders)
            val encrypted = CryptoManager.encryptString(json)
            foldersDbFile.writeText(encrypted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ===== Intruder Logs =====
    
    fun getAllIntruderLogs(): List<IntruderLog> {
        if (cachedIntruderLogs == null) {
            cachedIntruderLogs = loadIntruderLogs().toMutableList()
        }
        return cachedIntruderLogs!!.sortedByDescending { it.timestamp }
    }
    
    fun addIntruderLog(log: IntruderLog) {
        val logs = getAllIntruderLogs().toMutableList()
        logs.add(log)
        cachedIntruderLogs = logs.toMutableList()
        saveIntruderLogs(logs)
    }
    
    fun removeIntruderLog(log: IntruderLog) {
        val logs = getAllIntruderLogs().toMutableList()
        logs.removeAll { it.id == log.id }
        cachedIntruderLogs = logs.toMutableList()
        saveIntruderLogs(logs)
    }
    
    fun clearIntruderLogs() {
        cachedIntruderLogs = mutableListOf()
        intruderDbFile.delete()
    }
    
    private fun loadIntruderLogs(): List<IntruderLog> {
        return try {
            if (!intruderDbFile.exists()) return emptyList()
            
            val encrypted = intruderDbFile.readText()
            val json = CryptoManager.decryptString(encrypted)
            
            val type = object : TypeToken<List<IntruderLog>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun saveIntruderLogs(logs: List<IntruderLog>) {
        try {
            val json = gson.toJson(logs)
            val encrypted = CryptoManager.encryptString(json)
            intruderDbFile.writeText(encrypted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
