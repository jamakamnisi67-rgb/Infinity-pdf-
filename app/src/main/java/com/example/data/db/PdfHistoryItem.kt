package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_history")
data class PdfHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val toolName: String,
    val inputFileName: String,
    val outputFileName: String,
    val inputSizeBytes: Long,
    val outputSizeBytes: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Success",
    val filePath: String? = null
)
