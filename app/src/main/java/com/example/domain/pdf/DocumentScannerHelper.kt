package com.example.domain.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

enum class DocumentFilter {
    COLOR,
    GRAYSCALE,
    BLACK_AND_WHITE,
    ENHANCE_READABILITY
}

object DocumentScannerHelper {

    fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun applyFilter(source: Bitmap, filter: DocumentFilter): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = true }

        when (filter) {
            DocumentFilter.COLOR -> {
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            DocumentFilter.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            DocumentFilter.BLACK_AND_WHITE -> {
                // High contrast binary threshold
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val m = cm.array
                // Increase contrast
                val scale = 2.0f
                val translate = (-128f * scale) + 128f
                m[0] = scale; m[1] = scale; m[2] = scale; m[4] = translate
                m[5] = scale; m[6] = scale; m[7] = scale; m[9] = translate
                m[10] = scale; m[11] = scale; m[12] = scale; m[14] = translate
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            DocumentFilter.ENHANCE_READABILITY -> {
                // Clean document filter: slightly boosted contrast, enhanced blacks, bright white background
                val cm = ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -25f,
                    0f, 1.4f, 0f, 0f, -25f,
                    0f, 0f, 1.4f, 0f, -25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
        }
        return output
    }
}
