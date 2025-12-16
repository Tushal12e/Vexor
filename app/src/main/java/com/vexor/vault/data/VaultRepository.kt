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
    
    private var cachedFiles: MutableList<VaultFile>? = null
    private var cachedIntruderLogs: MutableList<IntruderLog>? = null
    
    // ===== Vault Files =====
    
    fun getAllFiles(isFakeVault: Boolean = false): List<VaultFile> {
        if (cachedFiles == null) {
            cachedFiles = loadFiles().toMutableList()
        }
        return cachedFiles!!.filter { it.isFakeVault == isFakeVault }
    }
    
    fun getFilesByType(type: FileType, isFakeVault: Boolean = false): List<VaultFile> {
        return getAllFiles(isFakeVault).filter { it.fileType == type }
    }
    
    fun addFile(file: VaultFile) {
        val files = getAllFiles().toMutableList()
        files.add(file)
        cachedFiles = files.toMutableList()
        saveFiles(files)
    }
    
    fun removeFile(file: VaultFile) {
        val files = getAllFiles().toMutableList()
        files.removeAll { it.id == file.id }
        cachedFiles = files.toMutableList()
        saveFiles(files)
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
        val files = getAllFiles().toMutableList()
        files.removeAll { it.isFakeVault == isFakeVault }
        cachedFiles = files.toMutableList()
        saveFiles(files)
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
