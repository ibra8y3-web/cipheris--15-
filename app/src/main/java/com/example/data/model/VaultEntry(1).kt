package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // "Note", "Password", "Wallet"
    val content: String, // Encrypted content
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: String = "#303030"
)
