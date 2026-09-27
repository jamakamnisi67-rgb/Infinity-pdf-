package com.example.domain.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object PdfEngine {

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            isInitialized = true
        }
    }

    /**
     * Helper to create a new unique output file in app cache
     */
    fun createOutputFile(context: Context, prefix: String, extension: String = "pdf"): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "processed_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${prefix}_$timeStamp.$extension")
    }

    /**
     * Get total page count of a PDF file using PdfRenderer
     */
    fun getPageCount(file: File): Int {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            try {
                val doc = PDDocument.load(file)
                val count = doc.numberOfPages
                doc.close()
                count
            } catch (ex: Exception) {
                1
            }
        }
    }

    /**
     * Render page as a Bitmap for thumbnails and viewer
     */
    fun renderPageToBitmap(file: File, pageIndex: Int, maxDimension: Int = 1024): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return null
            }
            val page = renderer.openPage(pageIndex)
            val origWidth = page.width
            val origHeight = page.height

            val scale = min(maxDimension.toFloat() / origWidth, maxDimension.toFloat() / origHeight).coerceAtMost(2.0f)
            val renderWidth = max(1, (origWidth * scale).toInt())
            val renderHeight = max(1, (origHeight * scale).toInt())

            val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 1. COMPRESS PDF
     * Compression level 1 (Low: 80% quality), 2 (Medium: 60% quality), 3 (High: 40% quality)
     * Re-encodes pages to optimized streams for guaranteed real reduction.
     */
    fun compressPdf(context: Context, inputFile: File, compressionLevel: Int): File {
        val outputFile = createOutputFile(context, "compressed")
        val pageCount = getPageCount(inputFile)
        val outDoc = PdfDocument()

        val (quality, scaleFactor) = when (compressionLevel) {
            1 -> Pair(80, 0.90f) // Low compression (highest visual quality)
            3 -> Pair(40, 0.65f) // High compression (smallest size)
            else -> Pair(60, 0.75f) // Medium compression (balanced)
        }

        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        for (i in 0 until min(pageCount, renderer.pageCount)) {
            val page = renderer.openPage(i)
            val w = (page.width * scaleFactor).toInt().coerceAtLeast(100)
            val h = (page.height * scaleFactor).toInt().coerceAtLeast(100)

            val pageBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val c = Canvas(pageBitmap)
            c.drawColor(Color.WHITE)
            page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            // Compress to JPEG byte array
            val byteStream = ByteArrayOutputStream()
            pageBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteStream)
            val jpegBytes = byteStream.toByteArray()
            val compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

            val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
            val docPage = outDoc.startPage(pageInfo)
            val pageCanvas = docPage.canvas
            if (compressedBitmap != null) {
                pageCanvas.drawBitmap(compressedBitmap, 0f, 0f, null)
                compressedBitmap.recycle()
            }
            pageBitmap.recycle()
            outDoc.finishPage(docPage)
        }
        renderer.close()
        pfd.close()

        FileOutputStream(outputFile).use { out ->
            outDoc.writeTo(out)
        }
        outDoc.close()
        return outputFile
    }

    /**
     * 2. MERGE PDF
     */
    fun mergePdfs(context: Context, inputFiles: List<File>): File {
        val outputFile = createOutputFile(context, "merged")
        val resultDoc = PDDocument()
        for (file in inputFiles) {
            val doc = PDDocument.load(file)
            for (i in 0 until doc.numberOfPages) {
                resultDoc.addPage(doc.getPage(i))
            }
            doc.close()
        }
        resultDoc.save(outputFile)
        resultDoc.close()
        return outputFile
    }

    /**
     * 3. SPLIT PDF
     * Extracts selected page numbers (0-based) into a new PDF
     */
    fun splitPdf(context: Context, inputFile: File, selectedPages: List<Int>): File {
        val outputFile = createOutputFile(context, "split")
        val inputDoc = PDDocument.load(inputFile)
        val outputDoc = PDDocument()

        for (idx in selectedPages) {
            if (idx in 0 until inputDoc.numberOfPages) {
                outputDoc.addPage(inputDoc.getPage(idx))
            }
        }
        outputDoc.save(outputFile)
        outputDoc.close()
        inputDoc.close()
        return outputFile
    }

    /**
     * 4. ROTATE PDF
     * Rotations map: page index -> rotation angle (90, 180, 270)
     */
    fun rotatePdf(context: Context, inputFile: File, pageRotations: Map<Int, Int>, allPagesRotation: Int = 0): File {
        val outputFile = createOutputFile(context, "rotated")
        val doc = PDDocument.load(inputFile)
        for (i in 0 until doc.numberOfPages) {
            val page = doc.getPage(i)
            val currentRot = page.rotation
            val additionalRot = pageRotations[i] ?: allPagesRotation
            page.rotation = (currentRot + additionalRot) % 360
        }
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * 5. DELETE PDF PAGES
     */
    fun deletePages(context: Context, inputFile: File, pagesToDelete: Set<Int>): File {
        val outputFile = createOutputFile(context, "pages_deleted")
        val doc = PDDocument.load(inputFile)
        val sortedIndices = pagesToDelete.sortedDescending()
        for (idx in sortedIndices) {
            if (idx in 0 until doc.numberOfPages) {
                doc.removePage(idx)
            }
        }
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * 6. EXTRACT PDF PAGES
     */
    fun extractPages(context: Context, inputFile: File, pagesToExtract: List<Int>): File {
        return splitPdf(context, inputFile, pagesToExtract)
    }

    /**
     * 7. ORGANIZE PDF (Reorder, Rotate, Delete)
     */
    fun organizePdf(
        context: Context,
        inputFile: File,
        newOrder: List<Int>,
        pageRotations: Map<Int, Int> = emptyMap()
    ): File {
        val outputFile = createOutputFile(context, "organized")
        val inputDoc = PDDocument.load(inputFile)
        val outputDoc = PDDocument()

        for (idx in newOrder) {
            if (idx in 0 until inputDoc.numberOfPages) {
                val page = inputDoc.getPage(idx)
                val extraRot = pageRotations[idx] ?: 0
                if (extraRot != 0) {
                    page.rotation = (page.rotation + extraRot) % 360
                }
                outputDoc.addPage(page)
            }
        }
        outputDoc.save(outputFile)
        outputDoc.close()
        inputDoc.close()
        return outputFile
    }

    /**
     * 25. NUMBER PAGES
     */
    fun numberPages(
        context: Context,
        inputFile: File,
        position: String = "Bottom Center",
        format: String = "Page %d of %d",
        startNumber: Int = 1
    ): File {
        val outputFile = createOutputFile(context, "numbered")
        val doc = PDDocument.load(inputFile)
        val totalPages = doc.numberOfPages
        val font = PDType1Font.HELVETICA_BOLD
        val fontSize = 10f

        for (i in 0 until totalPages) {
            val page = doc.getPage(i)
            val mediaBox = page.mediaBox
            val currentNumber = i + startNumber
            val text = String.format(format, currentNumber, totalPages)

            val textWidth = font.getStringWidth(text) / 1000 * fontSize
            val (x, y) = when (position) {
                "Top Left" -> Pair(40f, mediaBox.height - 30f)
                "Top Center" -> Pair((mediaBox.width - textWidth) / 2f, mediaBox.height - 30f)
                "Top Right" -> Pair(mediaBox.width - textWidth - 40f, mediaBox.height - 30f)
                "Bottom Left" -> Pair(40f, 30f)
                "Bottom Right" -> Pair(mediaBox.width - textWidth - 40f, 30f)
                else -> Pair((mediaBox.width - textWidth) / 2f, 30f) // Bottom Center
            }

            val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
            cs.beginText()
            cs.setFont(font, fontSize)
            cs.setNonStrokingColor(80, 80, 80)
            cs.newLineAtOffset(x, y)
            cs.showText(text)
            cs.endText()
            cs.close()
        }
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * 28. WATERMARK PDF
     */
    fun watermarkPdf(
        context: Context,
        inputFile: File,
        watermarkText: String,
        opacity: Float = 0.3f,
        fontSize: Float = 42f,
        rotationDegrees: Float = 45f
    ): File {
        val outputFile = createOutputFile(context, "watermarked")
        val doc = PDDocument.load(inputFile)
        val font = PDType1Font.HELVETICA_BOLD

        for (i in 0 until doc.numberOfPages) {
            val page = doc.getPage(i)
            val mediaBox = page.mediaBox

            val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
            val gs = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = opacity
            }
            cs.setGraphicsStateParameters(gs)
            cs.setNonStrokingColor(180, 180, 180)

            val textWidth = font.getStringWidth(watermarkText) / 1000 * fontSize
            val centerX = mediaBox.width / 2f
            val centerY = mediaBox.height / 2f

            cs.saveGraphicsState()
            cs.transform(
                com.tom_roush.pdfbox.util.Matrix.getRotateInstance(
                    Math.toRadians(rotationDegrees.toDouble()),
                    centerX,
                    centerY
                )
            )
            cs.beginText()
            cs.setFont(font, fontSize)
            cs.newLineAtOffset(centerX - (textWidth / 2f), centerY)
            cs.showText(watermarkText)
            cs.endText()
            cs.restoreGraphicsState()

            cs.close()
        }
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * 31. UNLOCK PDF (Decrypt with password)
     */
    fun unlockPdf(context: Context, inputFile: File, password: String): File {
        val outputFile = createOutputFile(context, "unlocked")
        val doc = PDDocument.load(inputFile, password)
        doc.isAllSecurityToBeRemoved = true
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * 32. PROTECT PDF (Encrypt with password)
     */
    fun protectPdf(context: Context, inputFile: File, password: String): File {
        val outputFile = createOutputFile(context, "protected")
        val doc = PDDocument.load(inputFile)
        val ap = AccessPermission()
        val spp = StandardProtectionPolicy(password, password, ap).apply {
            encryptionKeyLength = 128
            permissions = ap
        }
        doc.protect(spp)
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * 33. FLATTEN PDF (Flatten form fields & annotations into static pages)
     */
    fun flattenPdf(context: Context, inputFile: File): File {
        val outputFile = createOutputFile(context, "flattened")
        val pageCount = getPageCount(inputFile)
        val outDoc = PdfDocument()

        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        for (i in 0 until min(pageCount, renderer.pageCount)) {
            val page = renderer.openPage(i)
            val w = page.width
            val h = page.height

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
            val docPage = outDoc.startPage(pageInfo)
            docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()
            outDoc.finishPage(docPage)
        }
        renderer.close()
        pfd.close()

        FileOutputStream(outputFile).use { out ->
            outDoc.writeTo(out)
        }
        outDoc.close()
        return outputFile
    }

    /**
     * 26. CROP PDF
     */
    fun cropPdf(
        context: Context,
        inputFile: File,
        leftMarginPct: Float = 0.05f,
        topMarginPct: Float = 0.05f,
        rightMarginPct: Float = 0.05f,
        bottomMarginPct: Float = 0.05f
    ): File {
        val outputFile = createOutputFile(context, "cropped")
        val doc = PDDocument.load(inputFile)
        for (i in 0 until doc.numberOfPages) {
            val page = doc.getPage(i)
            val mb = page.mediaBox
            val cropX = mb.lowerLeftX + (mb.width * leftMarginPct)
            val cropY = mb.lowerLeftY + (mb.height * bottomMarginPct)
            val cropW = mb.width * (1f - leftMarginPct - rightMarginPct)
            val cropH = mb.height * (1f - topMarginPct - bottomMarginPct)
            page.cropBox = PDRectangle(cropX, cropY, cropW, cropH)
        }
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * 27. REDACT PDF (Permanently burn redaction into page raster)
     */
    fun redactPdf(context: Context, inputFile: File, pageIndex: Int, redactRects: List<RectF>): File {
        val outputFile = createOutputFile(context, "redacted")
        val pageCount = getPageCount(inputFile)
        val outDoc = PdfDocument()

        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        val redactPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        for (i in 0 until min(pageCount, renderer.pageCount)) {
            val page = renderer.openPage(i)
            val w = page.width
            val h = page.height

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            if (i == pageIndex) {
                for (r in redactRects) {
                    val pixelRect = RectF(
                        r.left * w,
                        r.top * h,
                        r.right * w,
                        r.bottom * h
                    )
                    c.drawRect(pixelRect, redactPaint)
                }
            }

            val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
            val docPage = outDoc.startPage(pageInfo)
            docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()
            outDoc.finishPage(docPage)
        }
        renderer.close()
        pfd.close()

        FileOutputStream(outputFile).use { out ->
            outDoc.writeTo(out)
        }
        outDoc.close()
        return outputFile
    }

    /**
     * 30. SIGN PDF
     */
    fun signPdf(
        context: Context,
        inputFile: File,
        targetPageIndex: Int,
        signatureBitmap: Bitmap,
        xPct: Float = 0.5f,
        yPct: Float = 0.8f,
        widthPct: Float = 0.35f
    ): File {
        val outputFile = createOutputFile(context, "signed")
        val pageCount = getPageCount(inputFile)
        val outDoc = PdfDocument()

        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        for (i in 0 until min(pageCount, renderer.pageCount)) {
            val page = renderer.openPage(i)
            val w = page.width
            val h = page.height

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            if (i == targetPageIndex) {
                val sigW = (w * widthPct).toInt().coerceAtLeast(50)
                val sigH = (sigW.toFloat() / signatureBitmap.width * signatureBitmap.height).toInt().coerceAtLeast(20)
                val sigX = (w * xPct) - (sigW / 2f)
                val sigY = (h * yPct) - (sigH / 2f)

                val scaledSig = Bitmap.createScaledBitmap(signatureBitmap, sigW, sigH, true)
                c.drawBitmap(scaledSig, sigX, sigY, null)
                scaledSig.recycle()
            }

            val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
            val docPage = outDoc.startPage(pageInfo)
            docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()
            outDoc.finishPage(docPage)
        }
        renderer.close()
        pfd.close()

        FileOutputStream(outputFile).use { out ->
            outDoc.writeTo(out)
        }
        outDoc.close()
        return outputFile
    }

    /**
     * 24. ANNOTATE PDF (Highlight, Underline, Draw, Text notes)
     */
    fun annotatePdf(
        context: Context,
        inputFile: File,
        targetPageIndex: Int,
        annotationLayer: Bitmap
    ): File {
        val outputFile = createOutputFile(context, "annotated")
        val pageCount = getPageCount(inputFile)
        val outDoc = PdfDocument()

        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        for (i in 0 until min(pageCount, renderer.pageCount)) {
            val page = renderer.openPage(i)
            val w = page.width
            val h = page.height

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            if (i == targetPageIndex) {
                val scaledLayer = Bitmap.createScaledBitmap(annotationLayer, w, h, true)
                c.drawBitmap(scaledLayer, 0f, 0f, null)
                scaledLayer.recycle()
            }

            val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
            val docPage = outDoc.startPage(pageInfo)
            docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()
            outDoc.finishPage(docPage)
        }
        renderer.close()
        pfd.close()

        FileOutputStream(outputFile).use { out ->
            outDoc.writeTo(out)
        }
        outDoc.close()
        return outputFile
    }

    /**
     * 29. FORM FILLER
     */
    fun fillFormFields(
        context: Context,
        inputFile: File,
        fieldValues: Map<String, String>
    ): File {
        val outputFile = createOutputFile(context, "filled_form")
        val doc = PDDocument.load(inputFile)
        val acroForm = doc.documentCatalog.acroForm
        if (acroForm != null) {
            for ((key, value) in fieldValues) {
                val field = acroForm.getField(key)
                field?.setValue(value)
            }
        }
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    /**
     * Extract all text from PDF using PDFTextStripper
     */
    fun extractText(inputFile: File): String {
        return try {
            val doc = PDDocument.load(inputFile)
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)
            doc.close()
            text.trim()
        } catch (e: Exception) {
            "Unable to extract text: ${e.message}"
        }
    }
}
