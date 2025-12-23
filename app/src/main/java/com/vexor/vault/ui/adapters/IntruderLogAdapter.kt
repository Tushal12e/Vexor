package com.vexor.vault.ui.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vexor.vault.data.IntruderLog
import com.vexor.vault.databinding.ItemIntruderLogBinding
import java.io.File

class IntruderLogAdapter(
    private val onItemClick: (IntruderLog) -> Unit = {}
) : ListAdapter<IntruderLog, IntruderLogAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIntruderLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemIntruderLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(log: IntruderLog) {
            binding.tvTime.text = log.getFormattedTime()
            binding.tvAttempts.text = "${log.attemptCount} failed attempt(s)"
            
            // Show photo indicator if available
            log.photoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    binding.tvAttempts.text = "${log.attemptCount} attempt(s) - Photo captured 📷"
                }
            }
            
            binding.root.setOnClickListener { onItemClick(log) }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<IntruderLog>() {
        override fun areItemsTheSame(oldItem: IntruderLog, newItem: IntruderLog) = 
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IntruderLog, newItem: IntruderLog) = 
            oldItem == newItem
    }
}
