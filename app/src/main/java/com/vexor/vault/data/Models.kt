package com.vexor.vault.data

import java.io.Serializable

data class VaultFile(
    val id: Long = 0,
    val originalName: String,
    val encryptedName: String,
    val mimeType: String,
    val fileType: FileType,
    val size: Long,
    val encryptedPath: String,
    val thumbnailPath: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val isFakeVault: Boolean = false,
    val vaultId: String = if (isFakeVault) "fake" else "main",
    val folderId: String? = null
) : Serializable {
    
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    fun getExtension(): String {
        return originalName.substringAfterLast('.', "")
    }
}

data class VaultFolder(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val vaultId: String = "main",
    val parentFolderId: String? = null
) : Serializable

enum class FileType(val displayName: String, val icon: String) {
    PHOTO("Photo", "🖼️"),
    VIDEO("Video", "🎬"),
    DOCUMENT("Document", "📄"),
    AUDIO("Audio", "🎵"),
    OTHER("Other", "📁");
    
    companion object {
        fun fromMimeType(mimeType: String): FileType {
            return when {
                mimeType.startsWith("image/") -> PHOTO
                mimeType.startsWith("video/") -> VIDEO
                mimeType.startsWith("audio/") -> AUDIO
                mimeType.startsWith("application/pdf") ||
                mimeType.startsWith("application/msword") ||
                mimeType.startsWith("application/vnd") ||
                mimeType.startsWith("text/") -> DOCUMENT
                else -> OTHER
            }
        }
    }
}

data class IntruderLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val photoPath: String? = null,
    val attemptCount: Int = 1
) : Serializable {
    
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
