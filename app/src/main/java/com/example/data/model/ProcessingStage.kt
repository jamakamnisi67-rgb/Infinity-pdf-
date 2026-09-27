package com.example.data.model

enum class ProcessingStage(val displayText: String) {
    IDLE("Ready"),
    PREPARING("Preparing file..."),
    READING("Reading document..."),
    PROCESSING("Processing pages..."),
    CREATING("Creating output..."),
    FINALIZING("Finalizing..."),
    COMPLETED("Your file is ready!"),
    ERROR("Processing error")
}

data class ProcessingResult(
    val success: Boolean,
    val originalFileName: String,
    val outputFileName: String,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val outputFilePath: String? = null,
    val mimeType: String = "application/pdf",
    val message: String? = null,
    val extractedText: String? = null
)
