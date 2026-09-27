package com.example.data.db

import kotlinx.coroutines.flow.Flow

class PdfHistoryRepository(private val dao: PdfHistoryDao) {
    val allHistory: Flow<List<PdfHistoryItem>> = dao.getAllHistory()

    suspend fun addRecord(
        toolName: String,
        inputFileName: String,
        outputFileName: String,
        inputSize: Long,
        outputSize: Long,
        filePath: String? = null
    ): Long {
        val item = PdfHistoryItem(
            toolName = toolName,
            inputFileName = inputFileName,
            outputFileName = outputFileName,
            inputSizeBytes = inputSize,
            outputSizeBytes = outputSize,
            filePath = filePath
        )
        return dao.insertItem(item)
    }

    suspend fun delete(item: PdfHistoryItem) = dao.deleteItem(item)

    suspend fun clearHistory() = dao.clearAll()
}
