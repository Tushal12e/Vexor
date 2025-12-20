package com.vexor.vault.data

import java.io.Serializable
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var content: String,
    val dateModified: Long = System.currentTimeMillis(),
    val vaultId: String = "main"
) : Serializable
