package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.db.PdfHistoryRepository
import com.example.domain.pdf.DocumentFilter
import com.example.domain.pdf.DocumentScannerHelper
import com.example.domain.pdf.FileExportHelper
import com.example.domain.pdf.OcrEngine
import com.example.domain.pdf.OfficeConverter
import com.example.domain.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ScannerScreen(
    historyRepository: PdfHistoryRepository,
    onOpenPdfInReader: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scannedBitmaps = remember { mutableStateListOf<Bitmap>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var extractedOcrText by remember { mutableStateOf<String?>(null) }
    var isRunningOcr by remember { mutableStateOf(false) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            // Apply readability enhancement by default
            val enhanced = DocumentScannerHelper.applyFilter(bitmap, DocumentFilter.ENHANCE_READABILITY)
            scannedBitmaps.add(enhanced)
            selectedPageIndex = scannedBitmaps.size - 1
            generatedPdfFile = null
        }
    }

    // Fallback photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bmp = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bmp != null) {
                    scannedBitmaps.add(bmp)
                    selectedPageIndex = scannedBitmaps.size - 1
                    generatedPdfFile = null
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission denied. Using photo picker fallback.", Toast.LENGTH_SHORT).show()
            photoPickerLauncher.launch("image/*")
        }
    }

    fun openCameraOrPicker() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun createPdfFromScans() {
        if (scannedBitmaps.isEmpty()) return
        isGeneratingPdf = true
        coroutineScope.launch {
            val pdfFile = withContext(Dispatchers.IO) {
                val tempFiles = scannedBitmaps.mapIndexed { idx, bmp ->
                    val f = File(context.cacheDir, "scan_page_$idx.jpg")
                    FileOutputStream(f).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    f
                }
                OfficeConverter.convertImagesToPdf(context, tempFiles, "A4", "Portrait")
            }

            // Save to room
            historyRepository.addRecord(
                toolName = "PDF Scanner",
                inputFileName = "${scannedBitmaps.size} Scanned Pages",
                outputFileName = pdfFile.name,
                inputSize = scannedBitmaps.size * 200_000L,
                outputSize = pdfFile.length(),
                filePath = pdfFile.absolutePath
            )

            generatedPdfFile = pdfFile
            isGeneratingPdf = false
            Toast.makeText(context, "Scanned PDF created successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    fun runOcrOnCurrentPage() {
        if (scannedBitmaps.isEmpty()) return
        val currentBmp = scannedBitmaps.getOrNull(selectedPageIndex) ?: return
        isRunningOcr = true
        coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                OcrEngine.recognizeTextFromBitmap(currentBmp)
            }
            extractedOcrText = text
            isRunningOcr = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("scanner_screen")
    ) {
        // Scanner Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PDF Document Scanner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${scannedBitmaps.size} pages scanned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row {
                IconButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.testTag("scanner_gallery_fallback_button")
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Pick Image")
                }

                Button(
                    onClick = { openCameraOrPicker() },
                    modifier = Modifier.testTag("scanner_capture_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Page")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (scannedBitmaps.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { openCameraOrPicker() }
                    .testTag("scanner_empty_prompt"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Scan Physical Documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap to open camera with automated perspective adjustment and readability filters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { openCameraOrPicker() },
                        modifier = Modifier.testTag("start_scan_button")
                    ) {
                        Text("Start Scanning")
                    }
                }
            }
        } else {
            // Active Page Preview with Filters & Controls
            val currentBmp = scannedBitmaps.getOrNull(selectedPageIndex)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                if (currentBmp != null) {
                    Image(
                        bitmap = currentBmp.asImageBitmap(),
                        contentDescription = "Scanned Page ${selectedPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Page count overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Page ${selectedPageIndex + 1} of ${scannedBitmaps.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Delete Page Button
                IconButton(
                    onClick = {
                        if (selectedPageIndex in scannedBitmaps.indices) {
                            scannedBitmaps.removeAt(selectedPageIndex)
                            selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .testTag("delete_scanned_page_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (Color, B&W, Grayscale, Readability)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        if (currentBmp != null) {
                            scannedBitmaps[selectedPageIndex] = DocumentScannerHelper.applyFilter(currentBmp, DocumentFilter.ENHANCE_READABILITY)
                        }
                    },
                    label = { Text("Enhanced") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = false,
                    onClick = {
                        if (currentBmp != null) {
                            scannedBitmaps[selectedPageIndex] = DocumentScannerHelper.applyFilter(currentBmp, DocumentFilter.BLACK_AND_WHITE)
                        }
                    },
                    label = { Text("B&W") },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = false,
                    onClick = {
                        if (currentBmp != null) {
                            scannedBitmaps[selectedPageIndex] = DocumentScannerHelper.applyFilter(currentBmp, DocumentFilter.GRAYSCALE)
                        }
                    },
                    label = { Text("Grayscale") },
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (currentBmp != null) {
                            scannedBitmaps[selectedPageIndex] = DocumentScannerHelper.rotateBitmap(currentBmp, 90f)
                        }
                    },
                    modifier = Modifier.testTag("rotate_scanned_page_button")
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-Page Thumbnails Slider
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(scannedBitmaps) { idx, bmp ->
                    val isSelected = (idx == selectedPageIndex)
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedPageIndex = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Thumb ${idx + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Add Page Button at the end of thumbnail list
                item {
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .clickable { openCameraOrPicker() }
                            .testTag("scanner_add_page_thumbnail_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Add Page", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions: OCR & Create PDF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { runOcrOnCurrentPage() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("scanner_ocr_button"),
                    enabled = !isRunningOcr
                ) {
                    if (isRunningOcr) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OCR Text")
                    }
                }

                Button(
                    onClick = { createPdfFromScans() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_scanned_pdf_button"),
                    enabled = !isGeneratingPdf && scannedBitmaps.isNotEmpty()
                ) {
                    if (isGeneratingPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create PDF")
                    }
                }
            }

            // PDF Created Success Bar
            if (generatedPdfFile != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PDF Created!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = FileExportHelper.formatFileSize(generatedPdfFile!!.length()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Row {
                            IconButton(onClick = { FileExportHelper.saveToDownloads(context, generatedPdfFile!!, "application/pdf") }) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                            IconButton(onClick = { onOpenPdfInReader(generatedPdfFile!!) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open")
                            }
                        }
                    }
                }
            }

            // OCR Result Card
            if (extractedOcrText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "OCR Recognized Text:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = extractedOcrText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
