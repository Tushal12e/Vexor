package com.vexor.vault.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vexor.vault.R
import com.vexor.vault.data.IntruderLog
import com.vexor.vault.data.VaultRepository
import com.vexor.vault.databinding.ActivityIntruderLogBinding
import com.vexor.vault.ui.adapters.IntruderLogAdapter
import java.io.File

class IntruderLogActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityIntruderLogBinding
    private lateinit var repository: VaultRepository
    private lateinit var adapter: IntruderLogAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntruderLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = VaultRepository(this)
        
        setupUI()
        loadLogs()
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        adapter = IntruderLogAdapter { log ->
            showIntruderPhoto(log)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        
        binding.btnClearLogs.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear Logs?")
                .setMessage("All intruder logs and photos will be deleted.")
                .setPositiveButton("Clear") { _, _ ->
                    val intruderDir = File(filesDir, "intruders")
                    intruderDir.deleteRecursively()
                    intruderDir.mkdirs()
                    
                    repository.clearIntruderLogs()
                    loadLogs()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun showIntruderPhoto(log: IntruderLog) {
        log.photoPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    // Create dialog with image
                    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_intruder_photo, null)
                    val imageView = dialogView.findViewById<ImageView>(R.id.ivIntruderPhoto)
                    imageView.setImageBitmap(bitmap)
                    
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Intruder Photo")
                        .setView(dialogView)
                        .setMessage("Captured: ${log.getFormattedTime()}\nAttempts: ${log.attemptCount}")
                        .setPositiveButton("Close", null)
                        .setNegativeButton("Delete") { _, _ ->
                            file.delete()
                            repository.removeIntruderLog(log)
                            loadLogs()
                        }
                        .show()
                    return
                }
            }
        }
        
        // No photo available
        MaterialAlertDialogBuilder(this)
            .setTitle("Intruder Attempt")
            .setMessage("Time: ${log.getFormattedTime()}\nFailed attempts: ${log.attemptCount}\n\nNo photo captured.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun loadLogs() {
        val logs = repository.getAllIntruderLogs()
        adapter.submitList(logs)
        
        binding.tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.btnClearLogs.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE
        binding.toolbar.subtitle = if (logs.isNotEmpty()) "${logs.size} attempts" else null
    }
}
