package com.vexor.vault.ui.adapters

import com.vexor.vault.data.VaultFile
import com.vexor.vault.data.VaultFolder

sealed class VaultItem {
    data class FileItem(val file: VaultFile) : VaultItem()
    data class FolderItem(val folder: VaultFolder) : VaultItem()
}
