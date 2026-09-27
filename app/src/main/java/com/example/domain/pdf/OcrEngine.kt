package com.example.domain.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val result = visionText.text
                continuation.resume(if (result.isNotBlank()) result else "No text could be detected in this image.")
            }
            .addOnFailureListener { e ->
                continuation.resume("OCR failed: ${e.localizedMessage ?: "Unknown error"}")
            }
    }

    suspend fun recognizePdfPages(pdfFile: File, maxPages: Int = 5): String {
        val totalPages = PdfEngine.getPageCount(pdfFile)
        val sb = StringBuilder()

        for (i in 0 until minOf(totalPages, maxPages)) {
            val bitmap = PdfEngine.renderPageToBitmap(pdfFile, i, 1600)
            if (bitmap != null) {
                sb.append("--- PAGE ${i + 1} ---\n")
                val text = recognizeTextFromBitmap(bitmap)
                sb.append(text).append("\n\n")
                bitmap.recycle()
            }
        }
        return sb.toString().trim()
    }
}
