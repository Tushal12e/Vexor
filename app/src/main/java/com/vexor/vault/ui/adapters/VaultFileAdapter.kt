package com.vexor.vault.ui.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vexor.vault.R
import com.vexor.vault.data.FileType
import com.vexor.vault.data.VaultFile
import com.vexor.vault.databinding.ItemVaultFileBinding
import com.vexor.vault.security.FileEncryptionManager
import kotlinx.coroutines.*

class VaultFileAdapter(
    private val encryptionManager: FileEncryptionManager,
    private val onItemClick: (VaultFile) -> Unit,
    private val onItemLongClick: (VaultFile) -> Unit
) : ListAdapter<VaultFile, VaultFileAdapter.ViewHolder>(DiffCallback()) {
    
    private var selectedFiles = emptySet<VaultFile>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    fun setSelectedFiles(files: Set<VaultFile>) {
        selectedFiles = files
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVaultFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemVaultFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var thumbnailJob: Job? = null
        
        fun bind(file: VaultFile) {
            val context = binding.root.context
            val isSelected = selectedFiles.contains(file)
            
            binding.tvFileName.text = file.originalName
            binding.tvFileSize.text = file.getFormattedSize()
            
            // Selection state
            binding.checkSelected.visibility = if (selectedFiles.isNotEmpty()) View.VISIBLE else View.GONE
            binding.checkSelected.isChecked = isSelected
            binding.cardView.strokeWidth = if (isSelected) 2 else 0
            binding.cardView.strokeColor = ContextCompat.getColor(context, R.color.primary)
            
            // File type icon/color
            val typeColor = when (file.fileType) {
                FileType.PHOTO -> R.color.file_photo
                FileType.VIDEO -> R.color.file_video
                FileType.DOCUMENT -> R.color.file_document
                FileType.AUDIO -> R.color.file_audio
                FileType.OTHER -> R.color.text_secondary
            }
            
            // Load thumbnail
            thumbnailJob?.cancel()
            binding.ivThumbnail.setImageDrawable(null)
            
            if (file.thumbnailPath != null && (file.fileType == FileType.PHOTO || file.fileType == FileType.VIDEO)) {
                thumbnailJob = scope.launch {
                    val bitmap = encryptionManager.decryptThumbnail(file.thumbnailPath)
                    if (bitmap != null) {
                        binding.ivThumbnail.setImageBitmap(bitmap)
                    } else {
                        showFileTypeIcon(file.fileType)
                    }
                }
            } else {
                showFileTypeIcon(file.fileType)
            }
            
            // Video indicator
            binding.ivPlayIcon.visibility = if (file.fileType == FileType.VIDEO) View.VISIBLE else View.GONE
            
            // Click listeners
            binding.root.setOnClickListener { onItemClick(file) }
            binding.root.setOnLongClickListener { 
                onItemLongClick(file)
                true
            }
        }
        
        private fun showFileTypeIcon(type: FileType) {
            val iconRes = when (type) {
                FileType.PHOTO -> R.drawable.ic_photo
                FileType.VIDEO -> R.drawable.ic_video
                FileType.DOCUMENT -> R.drawable.ic_document
                FileType.AUDIO -> R.drawable.ic_audio
                FileType.OTHER -> R.drawable.ic_file
            }
            binding.ivThumbnail.setImageResource(iconRes)
            binding.ivThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<VaultFile>() {
        override fun areItemsTheSame(oldItem: VaultFile, newItem: VaultFile) = 
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: VaultFile, newItem: VaultFile) = 
            oldItem == newItem
    }
}
