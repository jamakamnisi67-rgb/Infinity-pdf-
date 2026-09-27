package com.example.domain.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.min

object OfficeConverter {

    /**
     * 8. PDF TO WORD (Genuine .docx OpenXML document)
     */
    fun convertPdfToDocx(context: Context, inputFile: File): File {
        val outputFile = PdfEngine.createOutputFile(context, "converted", "docx")
        val extractedText = PdfEngine.extractText(inputFile)
        val paragraphs = extractedText.split("\n")

        val docXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("<w:body>")

            for (p in paragraphs) {
                val clean = escapeXml(p.trim())
                if (clean.isNotEmpty()) {
                    append("<w:p>")
                    append("<w:r>")
                    append("<w:t>$clean</w:t>")
                    append("</w:r>")
                    append("</w:p>")
                } else {
                    append("<w:p/>")
                }
            }
            append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr>""")
            append("</w:body></w:document>")
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(relsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return outputFile
    }

    /**
     * 9. PDF TO EXCEL (Genuine .xlsx OpenXML spreadsheet)
     */
    fun convertPdfToXlsx(context: Context, inputFile: File): File {
        val outputFile = PdfEngine.createOutputFile(context, "converted", "xlsx")
        val rawText = PdfEngine.extractText(inputFile)
        val lines = rawText.split("\n").filter { it.isNotBlank() }

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<sheetData>")

            for ((rowIdx, line) in lines.withIndex()) {
                val rowNum = rowIdx + 1
                append("""<row r="$rowNum">""")
                // Split line by tabs, multiple spaces, or commas to identify table columns
                val cells = line.split(Regex("\\t+| {2,}|,")).map { it.trim() }.filter { it.isNotEmpty() }
                for ((colIdx, cellVal) in cells.withIndex()) {
                    val colLetter = getColumnLetter(colIdx)
                    val ref = "$colLetter$rowNum"
                    val escaped = escapeXml(cellVal)
                    append("""<c r="$ref" t="inlineStr"><is><t>$escaped</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

        val wbRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(relsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(workbookXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(wbRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return outputFile
    }

    /**
     * 10. PDF TO POWERPOINT (Genuine .pptx OpenXML presentation)
     */
    fun convertPdfToPptx(context: Context, inputFile: File): File {
        val outputFile = PdfEngine.createOutputFile(context, "converted", "pptx")
        val rawText = PdfEngine.extractText(inputFile)
        val paragraphs = rawText.split("\n").filter { it.isNotBlank() }

        val slideXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""")
            append("<p:cSld><p:spTree>")
            append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>""")
            append("""<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>""")

            append("<p:sp>")
            append("""<p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="ctrTitle"/></p:nvPr></p:nvSpPr>""")
            append("<p:spPr/>")
            append("<p:txBody><a:bodyPr/><a:lstStyle/>")
            for (p in paragraphs.take(15)) {
                val clean = escapeXml(p)
                append("""<a:p><a:r><a:rPr lang="en-US" sz="1600"/><a:t>$clean</a:t></a:r></a:p>""")
            }
            append("</p:txBody></p:sp>")
            append("</p:spTree></p:cSld></p:sld>")
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

        val presentationXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <p:sldIdLst>
    <p:sldId id="256" r:id="rId1"/>
  </p:sldIdLst>
  <p:sldSz cx="9144000" cy="6858000"/>
</p:presentation>"""

        val presRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(relsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/presentation.xml"))
            zos.write(presentationXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/_rels/presentation.xml.rels"))
            zos.write(presRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
            zos.write(slideXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return outputFile
    }

    /**
     * 11. PDF TO JPG (Render all pages or page 0 to JPG)
     */
    fun convertPdfToJpg(context: Context, inputFile: File, pageIndex: Int = 0): File {
        val outputFile = PdfEngine.createOutputFile(context, "page_${pageIndex + 1}", "jpg")
        val bitmap = PdfEngine.renderPageToBitmap(inputFile, pageIndex, 1600)
            ?: Bitmap.createBitmap(800, 1100, Bitmap.Config.RGB_565).apply { eraseColor(Color.WHITE) }

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bitmap.recycle()
        return outputFile
    }

    /**
     * 13. WORD TO PDF (Parse DOCX OpenXML document.xml and render to PDF)
     */
    fun convertDocxToPdf(context: Context, inputFile: File): File {
        val textLines = mutableListOf<String>()
        try {
            val zip = ZipFile(inputFile)
            val entry = zip.getEntry("word/document.xml")
            if (entry != null) {
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
                val pRegex = Regex("<w:p[^>]*>(.*?)</w:p>")
                val pMatches = pRegex.findAll(xml)
                for (pm in pMatches) {
                    val pText = regex.findAll(pm.value).map { it.groupValues[1] }.joinToString("")
                    if (pText.isNotBlank()) textLines.add(unescapeXml(pText))
                }
            }
            zip.close()
        } catch (e: Exception) {
            textLines.addAll(inputFile.readLines())
        }
        if (textLines.isEmpty()) textLines.add("Document Content Converted from ${inputFile.name}")

        return renderTextLinesToPdf(context, textLines, "Word Converted Document")
    }

    /**
     * 14. EXCEL TO PDF (Parse XLSX OpenXML and render table to PDF)
     */
    fun convertXlsxToPdf(context: Context, inputFile: File): File {
        val rows = mutableListOf<List<String>>()
        try {
            val zip = ZipFile(inputFile)
            // Read shared strings if any
            val sharedStrings = mutableListOf<String>()
            val ssEntry = zip.getEntry("xl/sharedStrings.xml")
            if (ssEntry != null) {
                val ssXml = zip.getInputStream(ssEntry).bufferedReader().readText()
                val tRegex = Regex("<t[^>]*>(.*?)</t>")
                tRegex.findAll(ssXml).forEach { sharedStrings.add(unescapeXml(it.groupValues[1])) }
            }

            val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            if (sheetEntry != null) {
                val sheetXml = zip.getInputStream(sheetEntry).bufferedReader().readText()
                val rowRegex = Regex("<row[^>]*>(.*?)</row>")
                val cellRegex = Regex("<c[^>]*r=\"([A-Z]+[0-9]+)\"[^>]*(?:t=\"([^\"]+)\")?[^>]*>(.*?)</c>")
                val valRegex = Regex("<v>(.*?)</v>")
                val isRegex = Regex("<is><t>(.*?)</t></is>")

                for (rm in rowRegex.findAll(sheetXml)) {
                    val rowCells = mutableListOf<String>()
                    for (cm in cellRegex.findAll(rm.value)) {
                        val tAttr = cm.groupValues[2]
                        val inner = cm.groupValues[3]
                        val vVal = valRegex.find(inner)?.groupValues?.get(1)
                        val isVal = isRegex.find(inner)?.groupValues?.get(1)

                        val cellText = when {
                            isVal != null -> unescapeXml(isVal)
                            tAttr == "s" && vVal != null -> {
                                val sIdx = vVal.toIntOrNull() ?: 0
                                sharedStrings.getOrElse(sIdx) { vVal }
                            }
                            vVal != null -> vVal
                            else -> ""
                        }
                        rowCells.add(cellText)
                    }
                    if (rowCells.isNotEmpty()) rows.add(rowCells)
                }
            }
            zip.close()
        } catch (e: Exception) {
            rows.add(listOf("Spreadsheet Converted", inputFile.name))
        }

        return renderTableToPdf(context, rows, "Spreadsheet: ${inputFile.nameWithoutExtension}")
    }

    /**
     * 15. POWERPOINT TO PDF
     */
    fun convertPptxToPdf(context: Context, inputFile: File): File {
        val slideTexts = mutableListOf<List<String>>()
        try {
            val zip = ZipFile(inputFile)
            val slideEntries = zip.entries().asSequence()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { it.name }
                .toList()

            for (se in slideEntries) {
                val xml = zip.getInputStream(se).bufferedReader().readText()
                val tRegex = Regex("<a:t[^>]*>(.*?)</a:t>")
                val texts = tRegex.findAll(xml).map { unescapeXml(it.groupValues[1]) }.toList()
                slideTexts.add(texts)
            }
            zip.close()
        } catch (e: Exception) {
            slideTexts.add(listOf("Presentation: ${inputFile.name}"))
        }

        return renderSlidesToPdf(context, slideTexts)
    }

    /**
     * 16. JPG / IMAGES TO PDF
     */
    fun convertImagesToPdf(
        context: Context,
        imageFiles: List<File>,
        pageSize: String = "A4",
        orientation: String = "Portrait"
    ): File {
        val outputFile = PdfEngine.createOutputFile(context, "images_converted")
        val doc = PdfDocument()

        val (pageW, pageH) = when (pageSize) {
            "Letter" -> if (orientation == "Landscape") Pair(792, 612) else Pair(612, 792)
            else -> if (orientation == "Landscape") Pair(842, 595) else Pair(595, 842) // A4
        }

        for ((idx, imgFile) in imageFiles.withIndex()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath) ?: continue
            val (w, h) = if (pageSize == "Original") Pair(bitmap.width, bitmap.height) else Pair(pageW, pageH)

            val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            // Scale image to fit inside page with margins
            val margin = if (pageSize == "Original") 0f else 20f
            val availW = w - (margin * 2)
            val availH = h - (margin * 2)

            val scale = min(availW / bitmap.width, availH / bitmap.height)
            val scaledW = bitmap.width * scale
            val scaledH = bitmap.height * scale
            val left = margin + (availW - scaledW) / 2f
            val top = margin + (availH - scaledH) / 2f

            val dstRect = android.graphics.RectF(left, top, left + scaledW, top + scaledH)
            canvas.drawBitmap(bitmap, null, dstRect, null)
            bitmap.recycle()
            doc.finishPage(page)
        }

        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
        return outputFile
    }

    /**
     * 17. HTML TO PDF
     */
    fun convertHtmlToPdf(context: Context, htmlContent: String): File {
        val cleanText = android.text.Html.fromHtml(htmlContent, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        val lines = cleanText.split("\n")
        return renderTextLinesToPdf(context, lines, "HTML Document")
    }

    /**
     * 18. TXT TO PDF
     */
    fun convertTxtToPdf(context: Context, textFile: File): File {
        val lines = textFile.readLines()
        return renderTextLinesToPdf(context, lines, textFile.nameWithoutExtension)
    }

    /**
     * 19. RTF TO PDF
     */
    fun convertRtfToPdf(context: Context, rtfFile: File): File {
        val raw = rtfFile.readText()
        // Strip RTF control words
        val stripped = raw.replace(Regex("\\\\[a-zA-Z0-9]+ ?"), "").replace(Regex("[{}]"), "")
        return renderTextLinesToPdf(context, stripped.split("\n"), rtfFile.nameWithoutExtension)
    }

    /**
     * 20. ODT TO PDF
     */
    fun convertOdtToPdf(context: Context, odtFile: File): File {
        val textLines = mutableListOf<String>()
        try {
            val zip = ZipFile(odtFile)
            val entry = zip.getEntry("content.xml")
            if (entry != null) {
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                val pRegex = Regex("<text:p[^>]*>(.*?)</text:p>")
                for (m in pRegex.findAll(xml)) {
                    val clean = m.groupValues[1].replace(Regex("<[^>]+>"), "")
                    if (clean.isNotBlank()) textLines.add(unescapeXml(clean))
                }
            }
            zip.close()
        } catch (e: Exception) {
            textLines.addAll(odtFile.readLines())
        }
        return renderTextLinesToPdf(context, textLines, odtFile.nameWithoutExtension)
    }

    /**
     * 21. EPUB TO PDF
     */
    fun convertEpubToPdf(context: Context, epubFile: File): File {
        val chapterLines = mutableListOf<String>()
        try {
            val zip = ZipFile(epubFile)
            val htmlEntries = zip.entries().asSequence()
                .filter { it.name.endsWith(".html") || it.name.endsWith(".xhtml") }
                .sortedBy { it.name }
                .toList()

            for (entry in htmlEntries) {
                val content = zip.getInputStream(entry).bufferedReader().readText()
                val parsed = android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                chapterLines.addAll(parsed.split("\n"))
            }
            zip.close()
        } catch (e: Exception) {
            chapterLines.addAll(epubFile.readLines())
        }
        return renderTextLinesToPdf(context, chapterLines, epubFile.nameWithoutExtension)
    }

    // --- Helper Renderers ---

    fun renderTextLinesToPdf(context: Context, lines: List<String>, title: String): File {
        val outputFile = PdfEngine.createOutputFile(context, "doc_converted")
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }

        val pageWidth = 595
        val pageHeight = 842
        val margin = 50f
        val lineHeight = 18f
        val linesPerPage = ((pageHeight - (margin * 2) - 60f) / lineHeight).toInt()

        val validLines = lines.filter { it.isNotBlank() }
        val chunks = validLines.chunked(linesPerPage.coerceAtLeast(1))

        for ((pIdx, chunk) in chunks.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Header
            paint.color = Color.rgb(30, 58, 138)
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText(title, margin, margin, paint)

            paint.color = Color.rgb(209, 213, 219)
            paint.strokeWidth = 1f
            canvas.drawLine(margin, margin + 15f, pageWidth - margin, margin + 15f, paint)

            // Content
            paint.color = Color.rgb(31, 41, 55)
            paint.textSize = 11f
            paint.isFakeBoldText = false
            var yPos = margin + 45f

            for (line in chunk) {
                // Wrap text if needed
                val maxChars = 75
                if (line.length > maxChars) {
                    val sub = line.chunked(maxChars)
                    for (s in sub) {
                        canvas.drawText(s, margin, yPos, paint)
                        yPos += lineHeight
                    }
                } else {
                    canvas.drawText(line, margin, yPos, paint)
                    yPos += lineHeight
                }
            }

            // Footer
            paint.color = Color.rgb(156, 163, 175)
            paint.textSize = 9f
            canvas.drawText("Infinity PDF Converter • Page ${pIdx + 1} of ${chunks.size}", margin, pageHeight - 30f, paint)

            doc.finishPage(page)
        }

        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
        return outputFile
    }

    private fun renderTableToPdf(context: Context, rows: List<List<String>>, title: String): File {
        val outputFile = PdfEngine.createOutputFile(context, "table_converted")
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }

        val pageWidth = 842 // Landscape A4
        val pageHeight = 595
        val margin = 40f
        val rowsPerPage = 18

        val chunks = rows.chunked(rowsPerPage)

        for ((pIdx, chunk) in chunks.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            paint.color = Color.rgb(30, 58, 138)
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText(title, margin, margin + 10f, paint)

            var yPos = margin + 40f
            val maxCols = chunk.maxOfOrNull { it.size } ?: 1
            val colWidth = (pageWidth - (margin * 2)) / maxCols.coerceAtLeast(1)

            for ((rIdx, row) in chunk.withIndex()) {
                val isHeader = (pIdx == 0 && rIdx == 0)
                paint.color = if (isHeader) Color.rgb(239, 246, 255) else if (rIdx % 2 == 1) Color.rgb(249, 250, 251) else Color.WHITE
                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 22f, paint)

                paint.color = if (isHeader) Color.rgb(30, 58, 138) else Color.rgb(55, 65, 81)
                paint.textSize = if (isHeader) 11f else 10f
                paint.isFakeBoldText = isHeader

                for ((cIdx, cellText) in row.withIndex()) {
                    val xPos = margin + (cIdx * colWidth) + 8f
                    val truncated = if (cellText.length > 25) cellText.take(22) + "..." else cellText
                    canvas.drawText(truncated, xPos, yPos + 15f, paint)
                }

                // Row divider
                paint.color = Color.rgb(229, 231, 235)
                paint.strokeWidth = 0.8f
                canvas.drawLine(margin, yPos + 22f, pageWidth - margin, yPos + 22f, paint)

                yPos += 22f
            }

            // Footer
            paint.color = Color.rgb(156, 163, 175)
            paint.textSize = 9f
            paint.isFakeBoldText = false
            canvas.drawText("Infinity PDF • Page ${pIdx + 1} of ${chunks.size}", margin, pageHeight - 20f, paint)

            doc.finishPage(page)
        }

        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
        return outputFile
    }

    private fun renderSlidesToPdf(context: Context, slides: List<List<String>>): File {
        val outputFile = PdfEngine.createOutputFile(context, "presentation_converted")
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }

        val pageWidth = 842 // 16:9 or 4:3 presentation landscape
        val pageHeight = 595

        for ((sIdx, slide) in slides.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, sIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Slide Header Bar
            paint.color = Color.rgb(30, 58, 138)
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 70f, paint)

            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.isFakeBoldText = true
            val titleText = slide.firstOrNull() ?: "Slide ${sIdx + 1}"
            canvas.drawText(titleText, 40f, 45f, paint)

            // Content Card
            paint.color = Color.rgb(249, 250, 251)
            canvas.drawRoundRect(40f, 100f, pageWidth - 40f, pageHeight - 60f, 16f, 16f, paint)

            paint.color = Color.rgb(31, 41, 55)
            paint.textSize = 14f
            paint.isFakeBoldText = false
            var yPos = 140f

            for (line in slide.drop(1).take(10)) {
                canvas.drawText("• $line", 70f, yPos, paint)
                yPos += 32f
            }

            // Slide Footer
            paint.color = Color.rgb(156, 163, 175)
            paint.textSize = 10f
            canvas.drawText("Infinity PDF Slide Presentation • Slide ${sIdx + 1} of ${slides.size}", 40f, pageHeight - 30f, paint)

            doc.finishPage(page)
        }

        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
        return outputFile
    }

    private fun getColumnLetter(colIndex: Int): String {
        return if (colIndex < 26) {
            ('A'.code + colIndex).toChar().toString()
        } else {
            val first = ('A'.code + (colIndex / 26) - 1).toChar()
            val second = ('A'.code + (colIndex % 26)).toChar()
            "$first$second"
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
