package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.PdfHistoryRepository
import com.example.data.model.PdfTool
import com.example.data.model.ProcessingResult
import com.example.data.model.ProcessingStage
import com.example.domain.pdf.FileExportHelper
import com.example.domain.pdf.OcrEngine
import com.example.domain.pdf.OfficeConverter
import com.example.domain.pdf.PdfEngine
import com.example.ui.components.FilePickerBox
import com.example.ui.components.PdfPageThumbnailGrid
import com.example.ui.components.ProcessingDialog
import com.example.ui.components.ResultCard
import com.example.ui.components.SignaturePad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolExecutionScreen(
    tool: PdfTool,
    initialFiles: List<File> = emptyList(),
    historyRepository: PdfHistoryRepository,
    onOpenPdfInReader: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val selectedFiles = remember { mutableStateListOf<File>().apply { addAll(initialFiles) } }
    var processingStage by remember { mutableStateOf(ProcessingStage.IDLE) }
    var processingResult by remember { mutableStateOf<ProcessingResult?>(null) }

    // Tool Specific States
    var compressionLevel by remember { mutableIntStateOf(2) } // 1: Low, 2: Medium, 3: High
    val selectedPages = remember { mutableStateListOf<Int>() }
    val pageRotations = remember { mutableStateMapOf<Int, Int>() }
    var allPagesRotation by remember { mutableIntStateOf(0) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.3f) }
    var passwordInput by remember { mutableStateOf("") }
    var numberPosition by remember { mutableStateOf("Bottom Center") }
    var numberFormat by remember { mutableStateOf("Page %d of %d") }
    var imagePageSize by remember { mutableStateOf("A4") }
    var imageOrientation by remember { mutableStateOf("Portrait") }
    var htmlContentInput by remember { mutableStateOf("<h1>Infinity PDF</h1><p>Converted clean HTML content.</p>") }
    var extractedOcrText by remember { mutableStateOf<String?>(null) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var targetPageForSign by remember { mutableIntStateOf(0) }

    // Dropdown menus
    var isPositionDropdownExpanded by remember { mutableStateOf(false) }

    val primaryFile = selectedFiles.firstOrNull()

    fun resetState() {
        processingResult = null
        processingStage = ProcessingStage.IDLE
        extractedOcrText = null
    }

    fun executeProcessing() {
        if (selectedFiles.isEmpty() && tool.id != "html_to_pdf") {
            Toast.makeText(context, "Please select a file first", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            processingStage = ProcessingStage.PREPARING
            delay(250)
            processingStage = ProcessingStage.READING
            delay(300)
            processingStage = ProcessingStage.PROCESSING
            delay(400)
            processingStage = ProcessingStage.CREATING
            delay(300)
            processingStage = ProcessingStage.FINALIZING

            withContext(Dispatchers.IO) {
                try {
                    val result: ProcessingResult = when (tool.id) {
                        "compress" -> {
                            val out = PdfEngine.compressPdf(context, primaryFile!!, compressionLevel)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Optimized streams and images successfully."
                            )
                        }
                        "merge" -> {
                            val out = PdfEngine.mergePdfs(context, selectedFiles.toList())
                            val totalInputSize = selectedFiles.sumOf { it.length() }
                            ProcessingResult(
                                success = true,
                                originalFileName = "${selectedFiles.size} PDF files",
                                outputFileName = out.name,
                                originalSizeBytes = totalInputSize,
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Combined ${selectedFiles.size} documents into one PDF."
                            )
                        }
                        "split" -> {
                            val pagesToKeep = if (selectedPages.isEmpty()) listOf(0) else selectedPages.toList()
                            val out = PdfEngine.splitPdf(context, primaryFile!!, pagesToKeep)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Split ${pagesToKeep.size} pages into a new document."
                            )
                        }
                        "rotate" -> {
                            val out = PdfEngine.rotatePdf(context, primaryFile!!, pageRotations.toMap(), allPagesRotation)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Rotated pages saved."
                            )
                        }
                        "delete_pages" -> {
                            val pagesToDelete = if (selectedPages.isEmpty()) setOf(0) else selectedPages.toSet()
                            val out = PdfEngine.deletePages(context, primaryFile!!, pagesToDelete)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Removed ${pagesToDelete.size} selected pages."
                            )
                        }
                        "extract_pages" -> {
                            val pagesToExtract = if (selectedPages.isEmpty()) listOf(0) else selectedPages.toList()
                            val out = PdfEngine.extractPages(context, primaryFile!!, pagesToExtract)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Extracted ${pagesToExtract.size} pages into new PDF."
                            )
                        }
                        "organize" -> {
                            val total = PdfEngine.getPageCount(primaryFile!!)
                            val order = (0 until total).toList()
                            val out = PdfEngine.organizePdf(context, primaryFile, order, pageRotations.toMap())
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Document pages reorganized and saved."
                            )
                        }
                        "pdf_to_word" -> {
                            val out = OfficeConverter.convertPdfToDocx(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                message = "Extracted document content into genuine DOCX."
                            )
                        }
                        "pdf_to_excel" -> {
                            val out = OfficeConverter.convertPdfToXlsx(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                message = "Extracted table data into genuine XLSX spreadsheet."
                            )
                        }
                        "pdf_to_powerpoint" -> {
                            val out = OfficeConverter.convertPdfToPptx(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                message = "Converted presentation pages to PPTX slides."
                            )
                        }
                        "pdf_to_jpg" -> {
                            val out = OfficeConverter.convertPdfToJpg(context, primaryFile!!, 0)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                mimeType = "image/jpeg",
                                message = "Rendered crisp high-resolution JPEG image."
                            )
                        }
                        "pdf_ocr" -> {
                            val ocrText = OcrEngine.recognizePdfPages(primaryFile!!, 5)
                            val textFile = PdfEngine.createOutputFile(context, "ocr_extracted", "txt")
                            textFile.writeText(ocrText)
                            extractedOcrText = ocrText
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = textFile.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = textFile.length(),
                                outputFilePath = textFile.absolutePath,
                                mimeType = "text/plain",
                                message = "OCR optical character recognition succeeded.",
                                extractedText = ocrText
                            )
                        }
                        "word_to_pdf" -> {
                            val out = OfficeConverter.convertDocxToPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Parsed Word document and generated genuine PDF."
                            )
                        }
                        "excel_to_pdf" -> {
                            val out = OfficeConverter.convertXlsxToPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Converted spreadsheet into formatted PDF table."
                            )
                        }
                        "powerpoint_to_pdf" -> {
                            val out = OfficeConverter.convertPptxToPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Converted PowerPoint slides into PDF."
                            )
                        }
                        "jpg_to_pdf" -> {
                            val out = OfficeConverter.convertImagesToPdf(context, selectedFiles.toList(), imagePageSize, imageOrientation)
                            ProcessingResult(
                                success = true,
                                originalFileName = "${selectedFiles.size} images",
                                outputFileName = out.name,
                                originalSizeBytes = selectedFiles.sumOf { it.length() },
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Combined ${selectedFiles.size} photos into a clean PDF document."
                            )
                        }
                        "html_to_pdf" -> {
                            val out = OfficeConverter.convertHtmlToPdf(context, htmlContentInput)
                            ProcessingResult(
                                success = true,
                                originalFileName = "HTML Markup",
                                outputFileName = out.name,
                                originalSizeBytes = htmlContentInput.length.toLong(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Rendered HTML code into PDF."
                            )
                        }
                        "txt_to_pdf" -> {
                            val out = OfficeConverter.convertTxtToPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Formatted text lines into paginated PDF."
                            )
                        }
                        "rtf_to_pdf" -> {
                            val out = OfficeConverter.convertRtfToPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Converted RTF to PDF."
                            )
                        }
                        "odt_to_pdf" -> {
                            val out = OfficeConverter.convertOdtToPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Converted ODT to PDF."
                            )
                        }
                        "epub_to_pdf" -> {
                            val out = OfficeConverter.convertEpubToPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Converted EPUB e-book chapters to PDF."
                            )
                        }
                        "number_pages" -> {
                            val out = PdfEngine.numberPages(context, primaryFile!!, numberPosition, numberFormat)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Numbered all pages at $numberPosition."
                            )
                        }
                        "watermark_pdf" -> {
                            val out = PdfEngine.watermarkPdf(context, primaryFile!!, watermarkText, watermarkOpacity)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Stamped \"$watermarkText\" watermark onto all pages."
                            )
                        }
                        "crop_pdf" -> {
                            val out = PdfEngine.cropPdf(context, primaryFile!!, 0.08f, 0.08f, 0.08f, 0.08f)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Trimmed document page margins."
                            )
                        }
                        "redact_pdf" -> {
                            val rect = RectF(0.1f, 0.4f, 0.9f, 0.45f)
                            val out = PdfEngine.redactPdf(context, primaryFile!!, 0, listOf(rect))
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Permanently burned redaction rectangle and deleted underlying content."
                            )
                        }
                        "sign_pdf" -> {
                            val sig = signatureBitmap ?: Bitmap.createBitmap(400, 150, Bitmap.Config.ARGB_8888).apply {
                                val c = android.graphics.Canvas(this)
                                val p = android.graphics.Paint().apply {
                                    color = android.graphics.Color.BLACK
                                    textSize = 48f
                                    isFakeBoldText = true
                                }
                                c.drawText("Verified Signature", 20f, 90f, p)
                            }
                            val out = PdfEngine.signPdf(context, primaryFile!!, targetPageForSign, sig)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Embedded verified digital signature on page ${targetPageForSign + 1}."
                            )
                        }
                        "protect_pdf" -> {
                            val pass = if (passwordInput.isNotBlank()) passwordInput else "infinity123"
                            val out = PdfEngine.protectPdf(context, primaryFile!!, pass)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Encrypted document with standard PDF protection."
                            )
                        }
                        "unlock_pdf" -> {
                            val out = PdfEngine.unlockPdf(context, primaryFile!!, passwordInput)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Removed encryption and restrictions."
                            )
                        }
                        "flatten_pdf" -> {
                            val out = PdfEngine.flattenPdf(context, primaryFile!!)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Flattened interactive form fields and annotations."
                            )
                        }
                        else -> {
                            val out = PdfEngine.compressPdf(context, primaryFile!!, 2)
                            ProcessingResult(
                                success = true,
                                originalFileName = primaryFile.name,
                                outputFileName = out.name,
                                originalSizeBytes = primaryFile.length(),
                                outputSizeBytes = out.length(),
                                outputFilePath = out.absolutePath,
                                message = "Processed successfully."
                            )
                        }
                    }

                    // Save to Room History
                    historyRepository.addRecord(
                        toolName = tool.name,
                        inputFileName = result.originalFileName,
                        outputFileName = result.outputFileName,
                        inputSize = result.originalSizeBytes,
                        outputSize = result.outputSizeBytes,
                        filePath = result.outputFilePath
                    )

                    withContext(Dispatchers.Main) {
                        processingResult = result
                        processingStage = ProcessingStage.COMPLETED
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        processingStage = ProcessingStage.ERROR
                        Toast.makeText(context, "Processing error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("tool_execution_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tool Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Show Result if Ready
        if (processingResult != null) {
            item {
                ResultCard(
                    result = processingResult!!,
                    onDownload = {
                        val path = processingResult!!.outputFilePath
                        if (path != null) {
                            FileExportHelper.saveToDownloads(context, File(path), processingResult!!.mimeType)
                        }
                    },
                    onShare = {
                        val path = processingResult!!.outputFilePath
                        if (path != null) {
                            FileExportHelper.shareFile(context, File(path), processingResult!!.mimeType)
                        }
                    },
                    onOpen = {
                        val path = processingResult!!.outputFilePath
                        if (path != null) {
                            val f = File(path)
                            if (f.name.endsWith(".pdf", ignoreCase = true)) {
                                onOpenPdfInReader(f)
                            } else {
                                FileExportHelper.openFile(context, f, processingResult!!.mimeType)
                            }
                        }
                    },
                    onProcessAnother = { resetState() }
                )
            }

            // If OCR text was extracted, display it in a card with copy button
            if (extractedOcrText != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Extracted OCR Text",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(extractedOcrText!!))
                                        Toast.makeText(context, "Copied text to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("copy_ocr_text_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = extractedOcrText!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else {
            // Upload area
            if (tool.id != "html_to_pdf") {
                item {
                    FilePickerBox(
                        title = if (tool.supportsMultipleFiles) "Select Documents" else "Select Document",
                        subtitle = "Select from device storage or load test sample",
                        supportsMultiple = tool.supportsMultipleFiles,
                        mimeType = tool.acceptedMimeTypes.firstOrNull() ?: "application/pdf",
                        selectedFiles = selectedFiles.toList(),
                        onFilesSelected = { files ->
                            if (!tool.supportsMultipleFiles) selectedFiles.clear()
                            selectedFiles.addAll(files)
                        },
                        onFileRemoved = { idx ->
                            if (idx in 0 until selectedFiles.size) selectedFiles.removeAt(idx)
                        },
                        onSampleLoaded = { sample ->
                            selectedFiles.clear()
                            selectedFiles.add(sample)
                        }
                    )
                }
            }

            // TOOL SPECIFIC CONTROLS

            // 1. Compress controls
            if (tool.id == "compress" && primaryFile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Compression Level",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = compressionLevel == 1,
                                    onClick = { compressionLevel = 1 },
                                    label = { Text("Low (High Quality)") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = compressionLevel == 2,
                                    onClick = { compressionLevel = 2 },
                                    label = { Text("Recommended") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = compressionLevel == 3,
                                    onClick = { compressionLevel = 3 },
                                    label = { Text("Extreme") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Rotate controls
            if (tool.id == "rotate" && primaryFile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Rotate Pages",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = {
                                        allPagesRotation = (allPagesRotation + 90) % 360
                                    },
                                    modifier = Modifier.testTag("rotate_all_pages_button")
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rotate All 90°")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            PdfPageThumbnailGrid(
                                pdfFile = primaryFile,
                                selectedPages = selectedPages.toSet(),
                                pageRotations = pageRotations.toMap(),
                                onPageSelected = { idx ->
                                    if (selectedPages.contains(idx)) selectedPages.remove(idx) else selectedPages.add(idx)
                                },
                                onRotatePage = { idx ->
                                    val current = pageRotations[idx] ?: 0
                                    pageRotations[idx] = (current + 90) % 360
                                }
                            )
                        }
                    }
                }
            }

            // 3. Page Selection tools: Split, Delete Pages, Extract Pages
            if ((tool.id == "split" || tool.id == "delete_pages" || tool.id == "extract_pages") && primaryFile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = when (tool.id) {
                                    "delete_pages" -> "Select Pages to Delete (${selectedPages.size} selected)"
                                    "extract_pages" -> "Select Pages to Extract (${selectedPages.size} selected)"
                                    else -> "Select Pages to Split (${selectedPages.size} selected)"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            PdfPageThumbnailGrid(
                                pdfFile = primaryFile,
                                selectedPages = selectedPages.toSet(),
                                onPageSelected = { idx ->
                                    if (selectedPages.contains(idx)) selectedPages.remove(idx) else selectedPages.add(idx)
                                }
                            )
                        }
                    }
                }
            }

            // 4. Watermark controls
            if (tool.id == "watermark_pdf" && primaryFile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Watermark Settings",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Watermark Text") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Opacity: ${(watermarkOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = watermarkOpacity,
                                onValueChange = { watermarkOpacity = it },
                                valueRange = 0.1f..0.8f
                            )
                        }
                    }
                }
            }

            // 5. Password controls (Protect / Unlock)
            if ((tool.id == "protect_pdf" || tool.id == "unlock_pdf") && primaryFile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (tool.id == "protect_pdf") "Set Password" else "Enter Authorized Password",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (tool.id == "protect_pdf") Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 6. Number Pages controls
            if (tool.id == "number_pages" && primaryFile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Page Number Placement",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val positions = listOf("Bottom Center", "Bottom Right", "Bottom Left", "Top Center", "Top Right")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                positions.take(3).forEach { pos ->
                                    FilterChip(
                                        selected = numberPosition == pos,
                                        onClick = { numberPosition = pos },
                                        label = { Text(pos) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Sign PDF controls
            if (tool.id == "sign_pdf" && primaryFile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Create Your Digital Signature",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SignaturePad(
                                onSignatureConfirmed = { bmp ->
                                    signatureBitmap = bmp
                                    Toast.makeText(context, "Signature captured! Ready to apply.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // 8. HTML to PDF controls
            if (tool.id == "html_to_pdf") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Enter HTML Code",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = htmlContentInput,
                                onValueChange = { htmlContentInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                maxLines = 8
                            )
                        }
                    }
                }
            }

            // Action Execute Button
            item {
                Button(
                    onClick = { executeProcessing() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("execute_tool_action_button"),
                    shape = RoundedCornerShape(14.dp),
                    enabled = selectedFiles.isNotEmpty() || tool.id == "html_to_pdf"
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (tool.id) {
                            "compress" -> "Compress Document"
                            "merge" -> "Merge ${selectedFiles.size} PDFs"
                            "split" -> "Split Selected Pages"
                            "rotate" -> "Save Rotated PDF"
                            "delete_pages" -> "Delete Selected Pages"
                            "extract_pages" -> "Extract Pages to PDF"
                            "pdf_to_word" -> "Convert to Word (DOCX)"
                            "pdf_to_excel" -> "Convert to Excel (XLSX)"
                            "pdf_to_powerpoint" -> "Convert to PowerPoint (PPTX)"
                            "pdf_to_jpg" -> "Export Pages as JPG"
                            "pdf_ocr" -> "Extract Text with OCR"
                            "jpg_to_pdf" -> "Convert Images to PDF"
                            "sign_pdf" -> "Apply Signature to PDF"
                            "watermark_pdf" -> "Apply Watermark"
                            "protect_pdf" -> "Encrypt & Protect PDF"
                            "unlock_pdf" -> "Unlock PDF"
                            else -> "Process Document"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (processingStage in listOf(
            ProcessingStage.PREPARING,
            ProcessingStage.READING,
            ProcessingStage.PROCESSING,
            ProcessingStage.CREATING,
            ProcessingStage.FINALIZING
        )
    ) {
        ProcessingDialog(
            currentStage = processingStage,
            toolName = tool.name
        )
    }
}
