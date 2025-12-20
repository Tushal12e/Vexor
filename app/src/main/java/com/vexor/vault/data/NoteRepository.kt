package com.vexor.vault.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vexor.vault.security.CryptoManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class NoteRepository(private val context: Context) {
    
    private val cryptoManager = CryptoManager()
    private val gson = Gson()
    private val notesFile = File(context.filesDir, "notes.enc")
    
    
    fun getAllNotes(vaultId: String): List<Note> {
        val allNotes = loadAllNotes()
        return allNotes.filter { it.vaultId == vaultId }
            .sortedByDescending { it.dateModified }
    }
    
    // ... saveNote, deleteNote ...
    
    private fun loadAllNotes(): List<Note> {
        if (!notesFile.exists()) return emptyList()
        
        return try {
            val encryptedContent = notesFile.readText()
            val json = cryptoManager.decryptString(encryptedContent)
            val type = object : TypeToken<List<Note>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun saveAllNotes(notes: List<Note>) {
        try {
            val json = gson.toJson(notes)
            val encryptedContent = cryptoManager.encryptString(json)
            notesFile.writeText(encryptedContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
