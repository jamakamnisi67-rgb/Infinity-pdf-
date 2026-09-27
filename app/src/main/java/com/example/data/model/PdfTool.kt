package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val title: String) {
    POPULAR("Popular Tools"),
    PDF_TOOLS("Organize & Manage"),
    CONVERT_FROM("Convert from PDF"),
    CONVERT_TO("Convert to PDF"),
    VIEW_EDIT("View & Edit"),
    SIGN_SECURITY("Sign & Security"),
    SCANNER("Scanner"),
    AI_TOOLS("AI Intelligence")
}

data class PdfTool(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val isPopular: Boolean = false,
    val acceptedMimeTypes: List<String> = listOf("application/pdf"),
    val supportsMultipleFiles: Boolean = false
)

object ToolRegistry {
    val ALL_TOOLS: List<PdfTool> = listOf(
        // Main PDF Tools
        PdfTool(
            id = "compress",
            name = "Compress PDF",
            description = "Reduce file size with smart stream & image optimization",
            category = ToolCategory.PDF_TOOLS,
            icon = Icons.Default.Compress,
            isPopular = true
        ),
        PdfTool(
            id = "merge",
            name = "Merge PDF",
            description = "Combine multiple PDFs in the exact order you want",
            category = ToolCategory.PDF_TOOLS,
            icon = Icons.Default.MergeType,
            isPopular = true,
            supportsMultipleFiles = true
        ),
        PdfTool(
            id = "split",
            name = "Split PDF",
            description = "Separate one page or a whole set for easy conversion",
            category = ToolCategory.PDF_TOOLS,
            icon = Icons.Default.VerticalSplit
        ),
        PdfTool(
            id = "rotate",
            name = "Rotate PDF",
            description = "Rotate specific or all pages 90, 180, or 270 degrees",
            category = ToolCategory.PDF_TOOLS,
            icon = Icons.Default.RotateRight
        ),
        PdfTool(
            id = "delete_pages",
            name = "Delete PDF Pages",
            description = "Select and remove unwanted pages from your document",
            category = ToolCategory.PDF_TOOLS,
            icon = Icons.Default.Delete
        ),
        PdfTool(
            id = "extract_pages",
            name = "Extract PDF Pages",
            description = "Extract individual pages into a separate clean PDF",
            category = ToolCategory.PDF_TOOLS,
            icon = Icons.Default.SelectAll
        ),
        PdfTool(
            id = "organize",
            name = "Organize PDF",
            description = "Sort, delete, rotate, and duplicate pages visually",
            category = ToolCategory.PDF_TOOLS,
            icon = Icons.Default.MenuBook
        ),

        // Convert From PDF
        PdfTool(
            id = "pdf_to_word",
            name = "PDF to Word",
            description = "Extract text & layout into an editable genuine DOCX",
            category = ToolCategory.CONVERT_FROM,
            icon = Icons.Default.Description,
            isPopular = true
        ),
        PdfTool(
            id = "pdf_to_excel",
            name = "PDF to Excel",
            description = "Extract detected tables and data directly into XLSX",
            category = ToolCategory.CONVERT_FROM,
            icon = Icons.Default.TableChart
        ),
        PdfTool(
            id = "pdf_to_powerpoint",
            name = "PDF to PowerPoint",
            description = "Turn your PDF slides and pages into a PPTX presentation",
            category = ToolCategory.CONVERT_FROM,
            icon = Icons.Default.PictureAsPdf
        ),
        PdfTool(
            id = "pdf_to_jpg",
            name = "PDF to JPG",
            description = "Render PDF pages into crisp high-resolution images",
            category = ToolCategory.CONVERT_FROM,
            icon = Icons.Default.Image
        ),
        PdfTool(
            id = "pdf_ocr",
            name = "PDF OCR",
            description = "On-device optical character recognition to extract searchable text",
            category = ToolCategory.CONVERT_FROM,
            icon = Icons.Default.FindInPage
        ),

        // Convert To PDF
        PdfTool(
            id = "word_to_pdf",
            name = "Word to PDF",
            description = "Convert DOC and DOCX files into high-fidelity PDF documents",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.Description,
            isPopular = true,
            acceptedMimeTypes = listOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "*/*"
            )
        ),
        PdfTool(
            id = "excel_to_pdf",
            name = "Excel to PDF",
            description = "Convert XLS and XLSX spreadsheets into formatted PDF tables",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.TableChart,
            acceptedMimeTypes = listOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "*/*"
            )
        ),
        PdfTool(
            id = "powerpoint_to_pdf",
            name = "PowerPoint to PDF",
            description = "Convert PPT and PPTX presentations into PDF slides",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.PictureAsPdf,
            acceptedMimeTypes = listOf(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint",
                "*/*"
            )
        ),
        PdfTool(
            id = "jpg_to_pdf",
            name = "JPG to PDF",
            description = "Transform single or multiple photos/images into a clean PDF",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.Image,
            isPopular = true,
            acceptedMimeTypes = listOf("image/jpeg", "image/png", "image/webp", "image/*"),
            supportsMultipleFiles = true
        ),
        PdfTool(
            id = "html_to_pdf",
            name = "HTML to PDF",
            description = "Convert HTML code or markup into a formatted PDF document",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.Html,
            acceptedMimeTypes = listOf("text/html", "text/plain", "*/*")
        ),
        PdfTool(
            id = "txt_to_pdf",
            name = "TXT to PDF",
            description = "Convert plain text files into paginated PDF documents",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.TextFields,
            acceptedMimeTypes = listOf("text/plain", "*/*")
        ),
        PdfTool(
            id = "rtf_to_pdf",
            name = "RTF to PDF",
            description = "Convert Rich Text Format files directly into PDF",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.Description,
            acceptedMimeTypes = listOf("application/rtf", "text/rtf", "*/*")
        ),
        PdfTool(
            id = "odt_to_pdf",
            name = "ODT to PDF",
            description = "Convert OpenDocument Text documents into PDF",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.Description,
            acceptedMimeTypes = listOf("application/vnd.oasis.opendocument.text", "*/*")
        ),
        PdfTool(
            id = "epub_to_pdf",
            name = "EPUB to PDF",
            description = "Convert EPUB e-books into printable PDF format",
            category = ToolCategory.CONVERT_TO,
            icon = Icons.Default.MenuBook,
            acceptedMimeTypes = listOf("application/epub+zip", "*/*")
        ),

        // View & Edit
        PdfTool(
            id = "pdf_reader",
            name = "PDF Reader",
            description = "High-performance viewer with zoom, thumbnail bar, text search & page navigation",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.Visibility
        ),
        PdfTool(
            id = "edit_pdf",
            name = "Edit PDF",
            description = "Add text, notes, shapes, and images to your PDF pages",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.Edit
        ),
        PdfTool(
            id = "pdf_annotator",
            name = "PDF Annotator",
            description = "Highlight, underline, strikethrough, draw, and add text comments",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.Draw
        ),
        PdfTool(
            id = "number_pages",
            name = "Number Pages",
            description = "Add customizable page numbers and stamps with exact positioning",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.FormatListNumbered
        ),
        PdfTool(
            id = "crop_pdf",
            name = "Crop PDF",
            description = "Trim margins or crop specific areas of your PDF pages",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.Crop
        ),
        PdfTool(
            id = "redact_pdf",
            name = "Redact PDF",
            description = "Permanently black out and remove sensitive information",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.ContentCut
        ),
        PdfTool(
            id = "watermark_pdf",
            name = "Watermark PDF",
            description = "Stamp custom text or image watermarks with angle and opacity control",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.FormatPaint
        ),
        PdfTool(
            id = "pdf_form_filler",
            name = "PDF Form Filler",
            description = "Detect and fill form text fields, checkboxes, and date pickers",
            category = ToolCategory.VIEW_EDIT,
            icon = Icons.Default.TextFields
        ),

        // Sign & Security
        PdfTool(
            id = "sign_pdf",
            name = "Sign PDF",
            description = "Draw, type, or place your digital signature securely onto any page",
            category = ToolCategory.SIGN_SECURITY,
            icon = Icons.Default.Draw
        ),
        PdfTool(
            id = "unlock_pdf",
            name = "Unlock PDF",
            description = "Remove password restrictions with the authorized password",
            category = ToolCategory.SIGN_SECURITY,
            icon = Icons.Default.LockOpen
        ),
        PdfTool(
            id = "protect_pdf",
            name = "Protect PDF",
            description = "Encrypt your document with genuine password security",
            category = ToolCategory.SIGN_SECURITY,
            icon = Icons.Default.Lock
        ),
        PdfTool(
            id = "flatten_pdf",
            name = "Flatten PDF",
            description = "Permanently merge interactive form fields and annotations into static pages",
            category = ToolCategory.SIGN_SECURITY,
            icon = Icons.Default.LayersClear
        ),

        // Scanner
        PdfTool(
            id = "scanner",
            name = "PDF Scanner",
            description = "Multi-page camera scanner with auto-cropping, color filters, and OCR",
            category = ToolCategory.SCANNER,
            icon = Icons.Default.CameraAlt,
            isPopular = true
        ),

        // AI Tools
        PdfTool(
            id = "chat_pdf",
            name = "Chat with PDF",
            description = "Ask questions and get intelligent answers directly from document text",
            category = ToolCategory.AI_TOOLS,
            icon = Icons.AutoMirrored.Filled.Chat
        ),
        PdfTool(
            id = "ai_summarizer",
            name = "AI Summarizer",
            description = "Generate concise executive summaries and bullet points from your PDF",
            category = ToolCategory.AI_TOOLS,
            icon = Icons.Default.Summarize
        ),
        PdfTool(
            id = "translate_pdf",
            name = "Translate PDF",
            description = "Translate document content into English, Spanish, French, German, and more",
            category = ToolCategory.AI_TOOLS,
            icon = Icons.Default.Translate
        ),
        PdfTool(
            id = "ai_questions",
            name = "AI Question Generator",
            description = "Generate study questions, quizzes, and comprehension checks from the PDF",
            category = ToolCategory.AI_TOOLS,
            icon = Icons.Default.QuestionAnswer
        )
    )

    fun getToolById(id: String): PdfTool? = ALL_TOOLS.find { it.id == id }
    fun getPopularTools(): List<PdfTool> = ALL_TOOLS.filter { it.isPopular }
}
