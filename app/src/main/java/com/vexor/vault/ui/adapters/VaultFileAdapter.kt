package com.vexor.vault.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.vexor.vault.R
import com.vexor.vault.data.FileType
import com.vexor.vault.data.VaultFile
import com.vexor.vault.data.VaultFolder
import com.vexor.vault.databinding.ItemVaultFileBinding
import com.vexor.vault.security.FileEncryptionManager
import java.io.File
import kotlinx.coroutines.*

class VaultFileAdapter(
    private val encryptionManager: com.vexor.vault.security.FileEncryptionManager,
    private val onItemClick: (VaultItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val selectedItems = mutableSetOf<VaultItem>()
    var isSelectionMode = false

    private val diffCallback = object : DiffUtil.ItemCallback<VaultItem>() {
        override fun areItemsTheSame(oldItem: VaultItem, newItem: VaultItem): Boolean {
            return when {
                oldItem is VaultItem.FileItem && newItem is VaultItem.FileItem -> oldItem.file.id == newItem.file.id
                oldItem is VaultItem.FolderItem && newItem is VaultItem.FolderItem -> oldItem.folder.id == newItem.folder.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: VaultItem, newItem: VaultItem): Boolean {
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<VaultItem>) {
        differ.submitList(list)
    }

    override fun getItemViewType(position: Int): Int {
        return when (differ.currentList[position]) {
            is VaultItem.FolderItem -> TYPE_FOLDER
            is VaultItem.FileItem -> TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderViewHolder(inflater.inflate(R.layout.item_folder, parent, false))
            else -> FileViewHolder(inflater.inflate(R.layout.item_vault_file, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = differ.currentList[position]
        when (holder) {
            is FolderViewHolder -> holder.bind(item as VaultItem.FolderItem)
            is FileViewHolder -> holder.bind(item as VaultItem.FileItem)
        }
    }

    override fun getItemCount(): Int = differ.currentList.size

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)

        fun bind(item: VaultItem.FolderItem) {
            tvName.text = item.folder.name
            
            // Selection logic
            if (selectedItems.contains(item)) {
                selectionOverlay.visibility = View.VISIBLE
            } else {
                selectionOverlay.visibility = View.GONE
            }

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item)
                } else {
                    onItemClick(item)
                }
            }

            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(item)
                    true
                } else {
                    false
                }
            }
        }
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)

        private var thumbnailJob: Job? = null

        private fun showFileTypeIcon(fileType: FileType) {
            ivThumbnail.setImageResource(when(fileType) {
                FileType.AUDIO -> R.drawable.ic_audio
                FileType.DOCUMENT -> R.drawable.ic_file
                FileType.PHOTO -> R.drawable.ic_image
                FileType.VIDEO -> R.drawable.ic_video
                else -> R.drawable.ic_file
            })
        }

        fun bind(item: VaultItem.FileItem) {
            val file = item.file
            tvName.text = file.originalName
            tvSize.text = file.getFormattedSize()

            // Load thumbnail
            thumbnailJob?.cancel()
            ivThumbnail.setImageDrawable(null)
            
            if (file.thumbnailPath != null && (file.fileType == FileType.PHOTO || file.fileType == FileType.VIDEO)) {
                // accessing outer class property
                thumbnailJob = (itemView.context as? LifecycleOwner)?.lifecycleScope?.launch {
                    val bitmap = encryptionManager.decryptThumbnail(file.thumbnailPath)
                     if (bitmap != null) {
                        ivThumbnail.setImageBitmap(bitmap)
                    } else {
                        showFileTypeIcon(file.fileType)
                    }
                }
            } else {
                showFileTypeIcon(file.fileType)
            }

            // Selection
            if (selectedItems.contains(item)) {
                selectionOverlay.visibility = View.VISIBLE
            } else {
                selectionOverlay.visibility = View.GONE
            }

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item)
                } else {
                    onItemClick(item)
                }
            }

            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(item)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun toggleSelection(item: VaultItem) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        notifyItemChanged(differ.currentList.indexOf(item))
        
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
        onSelectionChanged(selectedItems.size)
    }
    
    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }
    
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(differ.currentList)
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    companion object {
        private const val TYPE_FOLDER = 0
        private const val TYPE_FILE = 1
    }
}
