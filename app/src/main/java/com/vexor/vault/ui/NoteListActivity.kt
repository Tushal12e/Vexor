package com.vexor.vault.ui

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.vexor.vault.data.Note
import com.vexor.vault.data.NoteRepository
import com.vexor.vault.databinding.ActivityNoteListBinding
import com.vexor.vault.ui.adapters.NoteAdapter

class NoteListActivity : BaseActivity() {

    private lateinit var binding: ActivityNoteListBinding
    private lateinit var repository: NoteRepository
    private lateinit var adapter: NoteAdapter
    private var vaultId: String = "main"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        vaultId = intent.getStringExtra("vault_id") ?: "main"
        repository = NoteRepository(this)
        
        setupUI()
    }
    
    override fun onResume() {
        super.onResume()
        loadNotes()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        adapter = NoteAdapter { note ->
            val intent = Intent(this, NoteEditorActivity::class.java)
            intent.putExtra("note_id", note.id)
            intent.putExtra("vault_id", vaultId)
            startActivity(intent)
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, NoteEditorActivity::class.java)
            intent.putExtra("vault_id", vaultId)
            startActivity(intent)
        }
    }
    
    private fun loadNotes() {
        val notes = repository.getAllNotes(vaultId)
        adapter.submitList(notes)
    }
}
