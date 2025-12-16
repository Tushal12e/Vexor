package com.vexor.vault.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.vexor.vault.data.VaultFile
import com.vexor.vault.databinding.ActivityVideoPlayerBinding
import com.vexor.vault.security.FileEncryptionManager
import kotlinx.coroutines.launch

class VideoPlayerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var encryptionManager: FileEncryptionManager
    private var player: ExoPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        encryptionManager = FileEncryptionManager(this)
        
        val file = intent.getSerializableExtra("file") as? VaultFile
        if (file == null) {
            finish()
            return
        }
        
        setupUI()
        loadVideo(file)
    }
    
    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
    }
    
    private fun loadVideo(file: VaultFile) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvFileName.text = file.originalName
        
        lifecycleScope.launch {
            val tempFile = encryptionManager.decryptToTempFile(file)
            if (tempFile != null) {
                initializePlayer(tempFile.absolutePath)
            }
            binding.progressBar.visibility = View.GONE
        }
    }
    
    private fun initializePlayer(path: String) {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer
            
            val mediaItem = MediaItem.fromUri(path)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }
    
    override fun onPause() {
        super.onPause()
        player?.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        
        // Clean temp files
        cacheDir.listFiles()?.filter { it.name.startsWith("temp_") }?.forEach { it.delete() }
    }
}
