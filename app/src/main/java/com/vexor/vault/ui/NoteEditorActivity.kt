package com.vexor.vault.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.vexor.vault.R
import com.vexor.vault.data.Note
import com.vexor.vault.data.NoteRepository
import com.vexor.vault.databinding.ActivityNoteEditorBinding

class NoteEditorActivity : BaseActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private lateinit var repository: NoteRepository
    private var currentNote: Note? = null
    private var vaultId: String = "main"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = NoteRepository(this)
        vaultId = intent.getStringExtra("vault_id") ?: "main"
        
        val noteId = intent.getStringExtra("note_id")
        if (noteId != null) {
            val notes = repository.getAllNotes(vaultId)
            currentNote = notes.find { it.id == noteId }
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        currentNote?.let {
            binding.etTitle.setText(it.title)
            binding.etContent.setText(it.content)
            supportActionBar?.title = "Edit Note"
        } ?: run {
            supportActionBar?.title = "New Note"
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_note_editor, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveNote()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun saveNote() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString()
        
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }
        
        val note = currentNote?.copy(
            title = title,
            content = content,
            dateModified = System.currentTimeMillis()
        ) ?: Note(
            title = title,
            content = content,
            vaultId = vaultId
        )
        
        repository.saveNote(note)
        Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
