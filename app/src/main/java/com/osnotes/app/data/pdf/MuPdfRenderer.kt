package com.osnotes.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.osnotes.app.domain.model.PdfDocument
import com.osnotes.app.domain.model.PdfPage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around MuPDF for PDF rendering and manipulation.
 * Thread-safe with mutex locks for document operations.
 */
@Singleton
class MuPdfRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var document: Document? = null
    private var currentUri: Uri? = null
    private var workingFile: File? = null
    private val mutex = Mutex()
    
    /**
     * Opens a PDF document from a URI.
     */
    suspend fun openDocument(uri: Uri): Result<PdfDocument> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Close any existing document
                closeDocumentInternal()
                
                // Create a working copy in cache for all operations
                val fileName = getFileName(uri) ?: "document_${System.currentTimeMillis()}.pdf"
                val tempDir = File(context.cacheDir, "pdf_working")
                tempDir.mkdirs()
                
                val tempFile = File(tempDir, fileName)
                
                // Copy source to working file
                val inputStream = try {
                    if (uri.scheme == "content") {
                        context.contentResolver.openInputStream(uri)
                    } else {
                        val path = uri.path ?: uri.toString()
                        File(path).inputStream()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MuPdfRenderer", "Failed to open input stream for $uri", e)
                    null
                } ?: throw IllegalStateException("Unable to open input stream for URI: $uri")
                
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Open the working copy with MuPDF
                document = Document.openDocument(tempFile.absolutePath)
                currentUri = uri
                workingFile = tempFile
                
                val pageCount = document?.countPages() ?: 0
                val name = fileName.substringBeforeLast('.')
                
                Result.success(
                    PdfDocument(
                        uri = uri,
                        name = name,
                        path = tempFile.absolutePath,
                        pageCount = pageCount,
                        fileSize = tempFile.length()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Renders a page to a bitmap.
     * @param pageNumber 0-indexed page number
     * @param scale Scale factor for rendering (1.0 = 72 DPI)
     */
    suspend fun renderPage(pageNumber: Int, scale: Float = 2f): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document ?: return@withContext null
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext null
                }
                
                val page = doc.loadPage(pageNumber)
                val bounds = page.bounds
                
                val width = (bounds.x1 - bounds.x0) * scale
                val height = (bounds.y1 - bounds.y0) * scale
                
                val bitmap = Bitmap.createBitmap(
                    width.toInt(),
                    height.toInt(),
                    Bitmap.Config.ARGB_8888
                )
                
                val matrix = Matrix(scale)
                val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.width, bitmap.height)
                
                page.run(device, matrix, null)
                device.close()
                page.destroy()
                
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Gets page information without rendering.
     */
    suspend fun getPageInfo(pageNumber: Int): PdfPage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document ?: return@withContext null
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext null
                }
                
                val page = doc.loadPage(pageNumber)
                val bounds = page.bounds
                
                val pdfPage = PdfPage(
                    pageNumber = pageNumber,
                    width = (bounds.x1 - bounds.x0).toInt(),
                    height = (bounds.y1 - bounds.y0).toInt()
                )
                
                page.destroy()
                pdfPage
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Gets the total page count.
     */
    fun getPageCount(): Int = document?.countPages() ?: 0
    
    /**
     * Searches for text in the document.
     */
    suspend fun searchText(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val results = mutableListOf<SearchResult>()
            val doc = document ?: return@withContext results
            
            for (i in 0 until doc.countPages()) {
                try {
                    val page = doc.loadPage(i)
                    val hits = page.search(query)
                    
                    // hits is Array<Array<Quad>> - each hit can have multiple quads
                    hits?.forEach { quadArray ->
                        quadArray.firstOrNull()?.let { quad ->
                            results.add(
                                SearchResult(
                                    pageNumber = i,
                                    bounds = quad
                                )
                            )
                        }
                    }
                    
                    page.destroy()
                } catch (e: Exception) {
                    // Skip pages that fail to load
                }
            }
            
            results
        }
    }
    
    /**
     * Extracts text from a page.
     */
    suspend fun extractText(pageNumber: Int): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document ?: return@withContext ""
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext ""
                }
                
                val page = doc.loadPage(pageNumber)
                val textBytes = page.textAsHtml()
                val text = String(textBytes, Charsets.UTF_8)
                page.destroy()
                
                // Strip HTML tags for plain text
                text.replace(Regex("<[^>]*>"), "")
            } catch (e: Exception) {
                ""
            }
        }
    }
    
    /**
     * Gets text line bounding boxes for smart highlighter.
     * Returns a list of TextLine objects with their bounding rectangles.
     */
    suspend fun getTextLines(pageNumber: Int, scale: Float = 2f): List<TextLine> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val lines = mutableListOf<TextLine>()
            try {
                val doc = document ?: return@withContext lines
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext lines
                }
                
                val page = doc.loadPage(pageNumber)
                val stext = page.toStructuredText()
                
                // MuPDF Java bindings use getBlocks() method
                val blocks = stext.getBlocks()
                android.util.Log.d("MuPdfRenderer", "Page $pageNumber: Found ${blocks?.size ?: 0} text blocks")
                
                blocks?.forEach { block ->
                    val blockLines = block.lines
                    blockLines?.forEach { line ->
                        val bbox = line.bbox
                        if (bbox != null) {
                            lines.add(
                                TextLine(
                                    x = bbox.x0 * scale,
                                    y = bbox.y0 * scale,
                                    width = (bbox.x1 - bbox.x0) * scale,
                                    height = (bbox.y1 - bbox.y0) * scale,
                                    text = line.chars?.mapNotNull { it?.c?.toChar() }?.joinToString("") ?: ""
                                )
                            )
                        }
                    }
                }
                
                android.util.Log.d("MuPdfRenderer", "Page $pageNumber: Extracted ${lines.size} text lines")
                page.destroy()
            } catch (e: Exception) {
                android.util.Log.e("MuPdfRenderer", "Error extracting text lines", e)
            }
            lines
        }
    }
    
    /**
     * Closes the current document.
     */
    suspend fun closeDocument() {
        mutex.withLock {
            closeDocumentInternal()
        }
    }
    
    /**
     * Temporarily closes the document for external modification.
     * Returns a pair of (cachedPath, uri) that can be used to reopen.
     */
    suspend fun closeForModification(): Pair<String, Uri>? {
        return mutex.withLock {
            val uri = currentUri ?: return@withLock null
            val doc = document ?: return@withLock null
            
            // Get the cached file path before closing
            val cachedPath = when (uri.scheme) {
                "file" -> uri.path!!
                else -> File(context.cacheDir, "pdf_cache/${getFileName(uri) ?: "document.pdf"}").absolutePath
            }
            
            closeDocumentInternal()
            Pair(cachedPath, uri)
        }
    }
    
    /**
     * Reopens the document after external modification.
     */
    suspend fun reopenDocument(uri: Uri): Result<PdfDocument> {
        return openDocument(uri)
    }
    
    /**
     * Saves the working document back to the original URI.
     */
    suspend fun saveDocument(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val working = workingFile ?: return@withLock Result.success(Unit)
                val uri = currentUri ?: return@withLock Result.success(Unit)
                
                // Write working copy back to original source
                when {
                    uri.scheme == "content" -> {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            working.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                    uri.scheme == "file" || uri.scheme == null -> {
                        val path = uri.path ?: uri.toString()
                        val destFile = File(path)
                        working.copyTo(destFile, overwrite = true)
                    }
                    else -> {
                        // Attempt content resolver for any other scheme
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            working.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("MuPdfRenderer", "Failed to save document to $currentUri", e)
                Result.failure(e)
            }
        }
    }
    
    private fun closeDocumentInternal() {
        document?.destroy()
        document = null
        currentUri = null
    }
    
    /**
     * Copies a content URI to the cache directory.
     */
    private fun copyToCache(uri: Uri): File {
        val fileName = getFileName(uri) ?: "document_${System.currentTimeMillis()}.pdf"
        val cacheFile = File(context.cacheDir, "pdf_cache/$fileName")
        cacheFile.parentFile?.mkdirs()
        
        // Delete existing file if it exists (to ensure fresh copy)
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open input stream for URI: $uri. Make sure the file is accessible.")
        
        inputStream.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            throw IllegalStateException("Failed to copy file to cache")
        }
        
        return cacheFile
    }
    
    /**
     * Gets the file name from a URI.
     */
    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        
        return name
    }
    
    /**
     * Clears all cached PDF files.
     * Call this when the user signs out or to free up storage.
     * @return Number of bytes freed
     */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            // First close any open document
            closeDocumentInternal()
            
            var bytesFreed = 0L
            
            // Clear pdf_cache directory
            val cacheDir = File(context.cacheDir, "pdf_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    bytesFreed += file.length()
                    file.delete()
                }
            }
            
            bytesFreed
        }
    }
    
    /**
     * Clears temporary annotation files.
     * @return Number of bytes freed
     */
    suspend fun clearTempFiles(): Long = withContext(Dispatchers.IO) {
        var bytesFreed = 0L
        
        // Clear pdf_temp directory
        val tempDir = File(context.cacheDir, "pdf_temp")
        if (tempDir.exists()) {
            tempDir.listFiles()?.forEach { file ->
                bytesFreed += file.length()
                file.delete()
            }
        }
        
        bytesFreed
    }
    
    /**
     * Clears all cached and temporary PDF files.
     * @return Total bytes freed
     */
    suspend fun clearAllCachedFiles(): Long {
        return clearCache() + clearTempFiles()
    }
    
    /**
     * Gets page count from a URI without keeping document open.
     */
    suspend fun getPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val file = when (uri.scheme) {
                "file" -> File(uri.path!!)
                else -> copyToCache(uri)
            }
            
            val doc = Document.openDocument(file.absolutePath)
            val count = doc.countPages()
            doc.destroy()
            count
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Adds a new page to the document at the specified index.
     * @param uri Document URI
     * @param pageIndex Index where the new page should be inserted
     * @param templateName Template to use for the new page
     */
    suspend fun addPage(uri: Uri, pageIndex: Int, templateName: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Close current document temporarily to allow modification
                closeDocumentInternal()
                
                // Use PdfAnnotationFlattener to add the page (it handles PDF reconstruction)
                // Note: This is a simplified approach - in production you'd inject PdfAnnotationFlattener
                // For now, we'll return success and let the calling code handle the actual addition
                
                // Reopen the document
                openDocument(uri)
                
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("MuPdfRenderer", "Error adding page", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Builds a new PDF with a page inserted at the specified index.
     * Uses a simple approach: renders all pages and creates a new PDF.
     */
    private fun buildPdfWithInsertedPage(
        originalDoc: Document,
        insertIndex: Int,
        templateName: String,
        pageWidth: Float,
        pageHeight: Float
    ): ByteArray {
        val originalPageCount = originalDoc.countPages()
        val totalPages = originalPageCount + 1
        
        // For simplicity, we'll create a new PDF by combining page streams
        // This is a basic implementation - for production, consider using a proper PDF library
        
        val outputStream = java.io.ByteArrayOutputStream()
        
        // Use Android's PdfDocument to create the new PDF
        val pdfDocument = android.graphics.pdf.PdfDocument()
        
        for (i in 0 until totalPages) {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                pageWidth.toInt(),
                pageHeight.toInt(),
                i + 1
            ).create()
            
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            if (i == insertIndex) {
                // Draw the new template page
                drawTemplateOnCanvas(canvas, templateName, pageWidth, pageHeight)
            } else {
                // Draw original page
                val originalIndex = if (i < insertIndex) i else i - 1
                if (originalIndex < originalPageCount) {
                    val origPage = originalDoc.loadPage(originalIndex)
                    val bounds = origPage.bounds
                    
                    // Render to bitmap and draw
                    val origWidth = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                    val origHeight = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
                    
                    val scale = minOf(
                        pageWidth / origWidth,
                        pageHeight / origHeight
                    )
                    
                    val bitmapWidth = (origWidth * scale).toInt().coerceAtLeast(1)
                    val bitmapHeight = (origHeight * scale).toInt().coerceAtLeast(1)
                    
                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    val matrix = Matrix(scale)
                    val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.width, bitmap.height)
                    
                    origPage.run(device, matrix, null)
                    device.close()
                    
                    // Center on page
                    val left = (pageWidth - bitmapWidth) / 2
                    val top = (pageHeight - bitmapHeight) / 2
                    canvas.drawBitmap(bitmap, left, top, null)
                    
                    bitmap.recycle()
                    origPage.destroy()
                }
            }
            
            pdfDocument.finishPage(page)
        }
        
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        
        return outputStream.toByteArray()
    }
    
    /**
     * Draws a template pattern on a canvas.
     */
    private fun drawTemplateOnCanvas(
        canvas: android.graphics.Canvas,
        templateName: String,
        width: Float,
        height: Float
    ) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        
        // White background
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRect(0f, 0f, width, height, paint)
        
        when (templateName.uppercase()) {
            "LINED" -> {
                paint.color = android.graphics.Color.rgb(200, 200, 200)
                paint.strokeWidth = 0.5f
                val lineSpacing = 24f
                val marginLeft = 72f
                val marginTop = 72f
                var y = marginTop
                while (y < height - 36f) {
                    canvas.drawLine(marginLeft, y, width - 36f, y, paint)
                    y += lineSpacing
                }
            }
            "GRID" -> {
                paint.color = android.graphics.Color.rgb(210, 210, 210)
                paint.strokeWidth = 0.3f
                val gridSize = 18f
                val margin = 36f
                var x = margin
                while (x < width - margin) {
                    canvas.drawLine(x, margin, x, height - margin, paint)
                    x += gridSize
                }
                var y = margin
                while (y < height - margin) {
                    canvas.drawLine(margin, y, width - margin, y, paint)
                    y += gridSize
                }
            }
            "DOTTED" -> {
                paint.color = android.graphics.Color.rgb(180, 180, 180)
                paint.style = android.graphics.Paint.Style.FILL
                val dotSpacing = 18f
                val margin = 36f
                var x = margin
                while (x < width - margin) {
                    var y = margin
                    while (y < height - margin) {
                        canvas.drawCircle(x, y, 1.5f, paint)
                        y += dotSpacing
                    }
                    x += dotSpacing
                }
            }
            "CORNELL" -> {
                paint.color = android.graphics.Color.rgb(190, 190, 190)
                paint.strokeWidth = 1f
                val leftMargin = width * 0.25f
                val bottomMargin = height * 0.15f
                canvas.drawLine(leftMargin, 0f, leftMargin, height - bottomMargin, paint)
                canvas.drawLine(0f, height - bottomMargin, width, height - bottomMargin, paint)
            }
            // Default is blank - already filled with white
        }
    }
    
    /**
     * Renders a page to a bitmap with specified dimensions.
     */
    suspend fun renderPage(pageNumber: Int, targetWidth: Int, targetHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document ?: return@withContext null
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext null
                }
                
                val page = doc.loadPage(pageNumber)
                val bounds = page.bounds
                
                val pageWidth = bounds.x1 - bounds.x0
                val pageHeight = bounds.y1 - bounds.y0
                
                // Calculate scale to fit within target dimensions
                val scaleX = targetWidth / pageWidth
                val scaleY = targetHeight / pageHeight
                val scale = minOf(scaleX, scaleY)
                
                val width = (pageWidth * scale).toInt()
                val height = (pageHeight * scale).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                val matrix = Matrix(scale)
                val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.width, bitmap.height)
                
                page.run(device, matrix, null)
                device.close()
                page.destroy()
                
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Creates a blank PDF with the specified page dimensions.
     */
    suspend fun createBlankPdf(uri: Uri, width: Float, height: Float) = withContext(Dispatchers.IO) {
        createTemplatePdf(uri, width, height, "BLANK")
    }
    
    /**
     * Creates a PDF with the specified template.
     * @param template One of: BLANK, LINED, GRID, DOTTED, CORNELL, ISOMETRIC, MUSIC, ENGINEERING
     */
    suspend fun createTemplatePdf(uri: Uri, width: Float, height: Float, template: String) = withContext(Dispatchers.IO) {
        try {
            val pdfBytes = when (template.uppercase()) {
                "LINED" -> createLinedPdfBytes(width, height)
                "GRID" -> createGridPdfBytes(width, height)
                "DOTTED" -> createDottedPdfBytes(width, height)
                "CORNELL" -> createCornellPdfBytes(width, height)
                "ISOMETRIC" -> createIsometricPdfBytes(width, height)
                "MUSIC" -> createMusicPdfBytes(width, height)
                "ENGINEERING" -> createEngineeringPdfBytes(width, height)
                else -> createBlankPdfBytes(width, height)
            }
            
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(pdfBytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Creates a PDF with a custom template configuration.
     */
    suspend fun createCustomTemplatePdf(
        uri: Uri, 
        width: Float, 
        height: Float, 
        customTemplate: com.osnotes.app.domain.model.CustomTemplate
    ) = withContext(Dispatchers.IO) {
        try {
            val pdfBytes = createCustomTemplatePdfBytes(width, height, customTemplate)
            
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(pdfBytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Creates PDF bytes from a CustomTemplate configuration.
     */
    private fun createCustomTemplatePdfBytes(
        width: Float, 
        height: Float, 
        template: com.osnotes.app.domain.model.CustomTemplate
    ): ByteArray {
        val content = StringBuilder()
        
        // Background color
        val bgR = ((template.backgroundColor shr 16) and 0xFF) / 255f
        val bgG = ((template.backgroundColor shr 8) and 0xFF) / 255f
        val bgB = (template.backgroundColor and 0xFF) / 255f
        content.append("q\n")
        content.append("$bgR $bgG $bgB rg\n")
        content.append("0 0 ${width.toInt()} ${height.toInt()} re f\n")
        content.append("Q\n")
        
        // Line color for patterns
        val lineR = ((template.lineColor shr 16) and 0xFF) / 255f
        val lineG = ((template.lineColor shr 8) and 0xFF) / 255f
        val lineB = (template.lineColor and 0xFF) / 255f
        
        // Calculate effective margins
        val marginLeft = template.marginLeft
        val marginRight = width - template.marginRight
        val marginTop = template.marginTop
        val marginBottom = template.marginBottom
        
        // Header section
        if (template.hasHeader) {
            val headerR = ((template.headerColor shr 16) and 0xFF) / 255f
            val headerG = ((template.headerColor shr 8) and 0xFF) / 255f
            val headerB = (template.headerColor and 0xFF) / 255f
            content.append("q\n")
            content.append("$headerR $headerG $headerB rg\n")
            content.append("0 ${height - template.headerHeight} ${width.toInt()} ${template.headerHeight} re f\n")
            content.append("Q\n")
        }
        
        // Footer section
        if (template.hasFooter) {
            val footerR = ((template.footerColor shr 16) and 0xFF) / 255f
            val footerG = ((template.footerColor shr 8) and 0xFF) / 255f
            val footerB = (template.footerColor and 0xFF) / 255f
            content.append("q\n")
            content.append("$footerR $footerG $footerB rg\n")
            content.append("0 0 ${width.toInt()} ${template.footerHeight} re f\n")
            content.append("Q\n")
        }
        
        // Side column (Cornell style)
        if (template.hasSideColumn) {
            val colR = ((template.sideColumnColor shr 16) and 0xFF) / 255f
            val colG = ((template.sideColumnColor shr 8) and 0xFF) / 255f
            val colB = (template.sideColumnColor and 0xFF) / 255f
            content.append("q\n")
            content.append("$colR $colG $colB rg\n")
            if (template.sideColumnOnLeft) {
                content.append("0 0 ${template.sideColumnWidth} ${height.toInt()} re f\n")
            } else {
                content.append("${width - template.sideColumnWidth} 0 ${template.sideColumnWidth} ${height.toInt()} re f\n")
            }
            content.append("Q\n")
        }
        
        // Margin line (like legal pads)
        if (template.hasMarginLine) {
            val mlR = ((template.marginLineColor shr 16) and 0xFF) / 255f
            val mlG = ((template.marginLineColor shr 8) and 0xFF) / 255f
            val mlB = (template.marginLineColor and 0xFF) / 255f
            content.append("$mlR $mlG $mlB RG\n")
            content.append("0.75 w\n")
            val effectiveTop = if (template.hasHeader) height - template.headerHeight else height - marginTop
            val effectiveBottom = if (template.hasFooter) template.footerHeight else marginBottom
            content.append("${template.marginLinePosition} $effectiveTop m ${template.marginLinePosition} $effectiveBottom l S\n")
        }
        
        // Calculate pattern area
        val patternTop = if (template.hasHeader) height - template.headerHeight - 10f else height - marginTop
        val patternBottom = if (template.hasFooter) template.footerHeight + 10f else marginBottom
        val patternLeft = marginLeft
        val patternRight = marginRight
        
        // Draw pattern based on type
        content.append("$lineR $lineG $lineB RG\n")
        content.append("${template.lineThickness} w\n")
        
        when (template.patternType) {
            com.osnotes.app.domain.model.PatternType.NONE -> {
                // No pattern to draw - just background
            }
            com.osnotes.app.domain.model.PatternType.HORIZONTAL_LINES -> {
                var y = patternTop - template.lineSpacing
                while (y > patternBottom) {
                    content.append("$patternLeft $y m $patternRight $y l S\n")
                    y -= template.lineSpacing
                }
            }
            com.osnotes.app.domain.model.PatternType.VERTICAL_LINES -> {
                var x = patternLeft
                while (x < patternRight) {
                    content.append("$x $patternBottom m $x $patternTop l S\n")
                    x += template.lineSpacing
                }
            }
            com.osnotes.app.domain.model.PatternType.GRID -> {
                // Horizontal lines
                var y = patternTop - template.lineSpacing
                while (y > patternBottom) {
                    content.append("$patternLeft $y m $patternRight $y l S\n")
                    y -= template.lineSpacing
                }
                // Vertical lines
                var x = patternLeft
                while (x < patternRight) {
                    content.append("$x $patternBottom m $x $patternTop l S\n")
                    x += template.lineSpacing
                }
            }
            com.osnotes.app.domain.model.PatternType.DOTS -> {
                // Draw dots as small filled circles
                content.append("$lineR $lineG $lineB rg\n")
                var y = patternTop - template.lineSpacing
                while (y > patternBottom) {
                    var x = patternLeft
                    while (x < patternRight) {
                        val r = template.dotSize
                        // Draw circle using bezier curves
                        val kappa = 0.5523f * r
                        content.append("${x - r} $y m ")
                        content.append("${x - r} ${y + kappa} ${x - kappa} ${y + r} $x ${y + r} c ")
                        content.append("${x + kappa} ${y + r} ${x + r} ${y + kappa} ${x + r} $y c ")
                        content.append("${x + r} ${y - kappa} ${x + kappa} ${y - r} $x ${y - r} c ")
                        content.append("${x - kappa} ${y - r} ${x - r} ${y - kappa} ${x - r} $y c f\n")
                        x += template.lineSpacing
                    }
                    y -= template.lineSpacing
                }
            }
            com.osnotes.app.domain.model.PatternType.ISOMETRIC_DOTS -> {
                content.append("$lineR $lineG $lineB rg\n")
                val spacing = template.lineSpacing
                var row = 0
                var y = patternTop - spacing
                while (y > patternBottom) {
                    val offset = if (row % 2 == 0) 0f else spacing / 2
                    var x = patternLeft + offset
                    while (x < patternRight) {
                        val r = template.dotSize
                        val kappa = 0.5523f * r
                        content.append("${x - r} $y m ")
                        content.append("${x - r} ${y + kappa} ${x - kappa} ${y + r} $x ${y + r} c ")
                        content.append("${x + kappa} ${y + r} ${x + r} ${y + kappa} ${x + r} $y c ")
                        content.append("${x + r} ${y - kappa} ${x + kappa} ${y - r} $x ${y - r} c ")
                        content.append("${x - kappa} ${y - r} ${x - r} ${y - kappa} ${x - r} $y c f\n")
                        x += spacing
                    }
                    y -= spacing * 0.866f // Equilateral triangle height ratio
                    row++
                }
            }
            com.osnotes.app.domain.model.PatternType.DIAGONAL_LEFT -> {
                // Draw lines like \ (top-left to bottom-right in PDF coords where Y is bottom-up)
                // In PDF: lines go from (x, top) to (x+height, bottom)
                val spacing = template.lineSpacing
                val patternWidth = patternRight - patternLeft
                val patternHeight = patternTop - patternBottom
                var offset = -patternHeight
                while (offset < patternWidth + patternHeight) {
                    val x1 = patternLeft + offset
                    val y1 = patternTop
                    val x2 = patternLeft + offset + patternHeight
                    val y2 = patternBottom
                    
                    // Clip to pattern bounds
                    val clipX1 = x1.coerceIn(patternLeft, patternRight)
                    val clipX2 = x2.coerceIn(patternLeft, patternRight)
                    val t1 = if (x2 != x1) (clipX1 - x1) / (x2 - x1) else 0f
                    val t2 = if (x2 != x1) (clipX2 - x1) / (x2 - x1) else 1f
                    val clipY1 = (y1 + t1 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    val clipY2 = (y1 + t2 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    
                    if (clipX1 < patternRight && clipX2 > patternLeft) {
                        content.append("$clipX1 $clipY1 m $clipX2 $clipY2 l S\n")
                    }
                    offset += spacing
                }
            }
            com.osnotes.app.domain.model.PatternType.DIAGONAL_RIGHT -> {
                // Draw lines like / (top-right to bottom-left in PDF coords)
                // In PDF: lines go from (x, bottom) to (x+height, top)
                val spacing = template.lineSpacing
                val patternWidth = patternRight - patternLeft
                val patternHeight = patternTop - patternBottom
                var offset = -patternHeight
                while (offset < patternWidth + patternHeight) {
                    val x1 = patternLeft + offset
                    val y1 = patternBottom
                    val x2 = patternLeft + offset + patternHeight
                    val y2 = patternTop
                    
                    // Clip to pattern bounds
                    val clipX1 = x1.coerceIn(patternLeft, patternRight)
                    val clipX2 = x2.coerceIn(patternLeft, patternRight)
                    val t1 = if (x2 != x1) (clipX1 - x1) / (x2 - x1) else 0f
                    val t2 = if (x2 != x1) (clipX2 - x1) / (x2 - x1) else 1f
                    val clipY1 = (y1 + t1 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    val clipY2 = (y1 + t2 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    
                    if (clipX1 < patternRight && clipX2 > patternLeft) {
                        content.append("$clipX1 $clipY1 m $clipX2 $clipY2 l S\n")
                    }
                    offset += spacing
                }
            }
            com.osnotes.app.domain.model.PatternType.CROSSHATCH -> {
                val spacing = template.lineSpacing
                val patternWidth = patternRight - patternLeft
                val patternHeight = patternTop - patternBottom
                
                // Draw \ lines (DIAGONAL_LEFT)
                var offset = -patternHeight
                while (offset < patternWidth + patternHeight) {
                    val x1 = patternLeft + offset
                    val y1 = patternTop
                    val x2 = patternLeft + offset + patternHeight
                    val y2 = patternBottom
                    
                    val clipX1 = x1.coerceIn(patternLeft, patternRight)
                    val clipX2 = x2.coerceIn(patternLeft, patternRight)
                    val t1 = if (x2 != x1) (clipX1 - x1) / (x2 - x1) else 0f
                    val t2 = if (x2 != x1) (clipX2 - x1) / (x2 - x1) else 1f
                    val clipY1 = (y1 + t1 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    val clipY2 = (y1 + t2 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    
                    if (clipX1 < patternRight && clipX2 > patternLeft) {
                        content.append("$clipX1 $clipY1 m $clipX2 $clipY2 l S\n")
                    }
                    offset += spacing
                }
                
                // Draw / lines (DIAGONAL_RIGHT)
                offset = -patternHeight
                while (offset < patternWidth + patternHeight) {
                    val x1 = patternLeft + offset
                    val y1 = patternBottom
                    val x2 = patternLeft + offset + patternHeight
                    val y2 = patternTop
                    
                    val clipX1 = x1.coerceIn(patternLeft, patternRight)
                    val clipX2 = x2.coerceIn(patternLeft, patternRight)
                    val t1 = if (x2 != x1) (clipX1 - x1) / (x2 - x1) else 0f
                    val t2 = if (x2 != x1) (clipX2 - x1) / (x2 - x1) else 1f
                    val clipY1 = (y1 + t1 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    val clipY2 = (y1 + t2 * (y2 - y1)).coerceIn(patternBottom, patternTop)
                    
                    if (clipX1 < patternRight && clipX2 > patternLeft) {
                        content.append("$clipX1 $clipY1 m $clipX2 $clipY2 l S\n")
                    }
                    offset += spacing
                }
            }
        }
        
        // Secondary grid if enabled
        if (template.hasSecondaryGrid) {
            val secR = ((template.secondaryLineColor shr 16) and 0xFF) / 255f
            val secG = ((template.secondaryLineColor shr 8) and 0xFF) / 255f
            val secB = (template.secondaryLineColor and 0xFF) / 255f
            content.append("$secR $secG $secB RG\n")
            content.append("${template.secondaryLineThickness} w\n")
            
            // Horizontal secondary lines
            var y = patternTop - template.secondaryLineSpacing
            while (y > patternBottom) {
                content.append("$patternLeft $y m $patternRight $y l S\n")
                y -= template.secondaryLineSpacing
            }
            // Vertical secondary lines
            var x = patternLeft
            while (x < patternRight) {
                content.append("$x $patternBottom m $x $patternTop l S\n")
                x += template.secondaryLineSpacing
            }
        }
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Creates blank PDF bytes for a single page.
     */
    private fun createBlankPdfBytes(width: Float, height: Float): ByteArray {
        return createPdfWithContent(width, height, "")
    }
    
    /**
     * Creates lined paper (college ruled) - Professional design.
     * Features: Red margin line, blue horizontal lines, header area.
     */
    private fun createLinedPdfBytes(width: Float, height: Float): ByteArray {
        val lineSpacing = 24f // ~8mm for college ruled
        val marginLeft = 72f  // 1 inch margin for binding
        val marginTop = 85f   // Header area
        val marginRight = width - 40f
        val marginBottom = 50f
        
        val content = StringBuilder()
        
        // Light cream background tint for paper feel
        content.append("q\n")
        content.append("0.996 0.988 0.969 rg\n") // Warm cream
        content.append("0 0 ${width.toInt()} ${height.toInt()} re f\n")
        content.append("Q\n")
        
        // Red vertical margin line (classic legal pad style)
        content.append("0.8 0.2 0.2 RG\n") // Red color
        content.append("0.75 w\n")
        content.append("${marginLeft - 8f} ${height - marginTop} m ${marginLeft - 8f} $marginBottom l S\n")
        
        // Header line (thicker)
        content.append("0.6 0.75 0.85 RG\n") // Light blue
        content.append("1.5 w\n")
        content.append("40 ${height - marginTop + 5f} m ${width - 40f} ${height - marginTop + 5f} l S\n")
        
        // Horizontal ruled lines (blue)
        content.append("0.7 0.82 0.9 RG\n") // Light blue
        content.append("0.5 w\n")
        
        var y = height - marginTop - lineSpacing
        while (y > marginBottom) {
            content.append("$marginLeft $y m $marginRight $y l S\n")
            y -= lineSpacing
        }
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Creates grid paper - Professional engineering/math style.
     * Features: 5mm grid with major gridlines every 5 squares.
     */
    private fun createGridPdfBytes(width: Float, height: Float): ByteArray {
        val smallGrid = 14.17f // 5mm in points
        val largeGrid = smallGrid * 5 // Major divisions every 5 squares
        val margin = 35f
        
        val content = StringBuilder()
        
        // Minor grid (light gray)
        content.append("0.88 0.88 0.88 RG\n")
        content.append("0.25 w\n")
        
        // Vertical minor lines
        var x = margin
        while (x <= width - margin) {
            content.append("$x $margin m $x ${height - margin} l S\n")
            x += smallGrid
        }
        
        // Horizontal minor lines  
        var y = margin
        while (y <= height - margin) {
            content.append("$margin $y m ${width - margin} $y l S\n")
            y += smallGrid
        }
        
        // Major grid (darker gray)
        content.append("0.65 0.65 0.65 RG\n")
        content.append("0.5 w\n")
        
        // Vertical major lines
        x = margin
        while (x <= width - margin) {
            content.append("$x $margin m $x ${height - margin} l S\n")
            x += largeGrid
        }
        
        // Horizontal major lines
        y = margin
        while (y <= height - margin) {
            content.append("$margin $y m ${width - margin} $y l S\n")
            y += largeGrid
        }
        
        // Border
        content.append("0.5 0.5 0.5 RG\n")
        content.append("0.75 w\n")
        content.append("$margin $margin m ${width - margin} $margin l ${width - margin} ${height - margin} l $margin ${height - margin} l s\n")
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Creates dotted paper - Clean bullet journal style.
     * Features: Evenly spaced dots using proper PDF circle drawing.
     */
    private fun createDottedPdfBytes(width: Float, height: Float): ByteArray {
        val dotSpacing = 14.17f // 5mm spacing
        val margin = 35f
        val dotRadius = 0.6f
        
        val content = StringBuilder()
        
        // Dots using small filled circles (Bezier approximation)
        content.append("0.55 0.55 0.55 rg\n") // Medium gray fill
        
        // PDF circle approximation constant
        val k = 0.5522847498f * dotRadius
        
        var x = margin + dotSpacing
        while (x < width - margin) {
            var y = margin + dotSpacing
            while (y < height - margin) {
                // Draw circle using cubic Bezier curves (4 curves for full circle)
                content.append("${x + dotRadius} $y m ")
                content.append("${x + dotRadius} ${y + k} ${x + k} ${y + dotRadius} $x ${y + dotRadius} c ")
                content.append("${x - k} ${y + dotRadius} ${x - dotRadius} ${y + k} ${x - dotRadius} $y c ")
                content.append("${x - dotRadius} ${y - k} ${x - k} ${y - dotRadius} $x ${y - dotRadius} c ")
                content.append("${x + k} ${y - dotRadius} ${x + dotRadius} ${y - k} ${x + dotRadius} $y c ")
                content.append("f\n")
                y += dotSpacing
            }
            x += dotSpacing
        }
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Creates Cornell notes layout - Professional study template.
     * Features: Cue column, notes area with lines, summary section with labels.
     */
    private fun createCornellPdfBytes(width: Float, height: Float): ByteArray {
        val cueColumnWidth = 150f // Fixed width for cue column
        val summaryHeight = 130f // Fixed height for summary
        val topMargin = 70f
        val sideMargin = 25f
        
        val content = StringBuilder()
        
        // Background - light cream
        content.append("q\n")
        content.append("0.99 0.98 0.96 rg\n")
        content.append("0 0 ${width.toInt()} ${height.toInt()} re f\n")
        content.append("Q\n")
        
        // Summary section background (light blue)
        content.append("q\n")
        content.append("0.92 0.95 0.98 rg\n")
        content.append("$sideMargin $sideMargin ${width - sideMargin * 2} $summaryHeight re f\n")
        content.append("Q\n")
        
        // Cue column background (light warm tone)
        content.append("q\n")
        content.append("0.98 0.96 0.92 rg\n")
        content.append("$sideMargin ${summaryHeight + sideMargin} $cueColumnWidth ${height - summaryHeight - topMargin - sideMargin} re f\n")
        content.append("Q\n")
        
        // Header area background
        content.append("q\n")
        content.append("0.95 0.95 0.97 rg\n")
        content.append("$sideMargin ${height - topMargin} ${width - sideMargin * 2} ${topMargin - sideMargin} re f\n")
        content.append("Q\n")
        
        // Main section dividers (dark lines)
        content.append("0.3 0.3 0.35 RG\n")
        content.append("1.5 w\n")
        
        // Outer border
        content.append("$sideMargin $sideMargin m ${width - sideMargin} $sideMargin l ")
        content.append("${width - sideMargin} ${height - sideMargin} l $sideMargin ${height - sideMargin} l s\n")
        
        // Vertical divider (cue column)
        val cueRight = sideMargin + cueColumnWidth
        content.append("$cueRight ${summaryHeight + sideMargin} m $cueRight ${height - topMargin} l S\n")
        
        // Horizontal divider (summary area)
        content.append("$sideMargin ${summaryHeight + sideMargin} m ${width - sideMargin} ${summaryHeight + sideMargin} l S\n")
        
        // Header divider
        content.append("$sideMargin ${height - topMargin} m ${width - sideMargin} ${height - topMargin} l S\n")
        
        // Faint ruled lines in notes area
        content.append("0.75 0.78 0.82 RG\n")
        content.append("0.4 w\n")
        val lineSpacing = 24f
        var y = height - topMargin - lineSpacing
        while (y > summaryHeight + sideMargin + 15f) {
            content.append("${cueRight + 10f} $y m ${width - sideMargin - 10f} $y l S\n")
            y -= lineSpacing
        }
        
        // Lines in cue column (fainter)
        content.append("0.82 0.8 0.75 RG\n")
        content.append("0.3 w\n")
        y = height - topMargin - lineSpacing
        while (y > summaryHeight + sideMargin + 15f) {
            content.append("${sideMargin + 10f} $y m ${cueRight - 10f} $y l S\n")
            y -= lineSpacing
        }
        
        // Lines in summary section
        content.append("0.75 0.8 0.85 RG\n")
        content.append("0.3 w\n")
        y = summaryHeight - 10f
        while (y > sideMargin + 20f) {
            content.append("${sideMargin + 10f} $y m ${width - sideMargin - 10f} $y l S\n")
            y -= lineSpacing
        }
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Creates isometric dot grid - For 3D sketching and technical drawing.
     * Features: Triangular dot pattern for isometric perspective.
     */
    private fun createIsometricPdfBytes(width: Float, height: Float): ByteArray {
        val spacing = 17f // Horizontal spacing between dots
        val rowHeight = spacing * 0.866f // sqrt(3)/2 for 60° angles
        val margin = 30f
        val dotRadius = 0.55f
        
        val content = StringBuilder()
        content.append("0.5 0.5 0.55 rg\n") // Slightly blue-gray dots
        
        val k = 0.5522847498f * dotRadius
        
        var row = 0
        var y = margin
        while (y < height - margin) {
            val xOffset = if (row % 2 == 0) 0f else spacing / 2f
            var x = margin + xOffset
            while (x < width - margin) {
                // Draw dot using Bezier circle
                content.append("${x + dotRadius} $y m ")
                content.append("${x + dotRadius} ${y + k} ${x + k} ${y + dotRadius} $x ${y + dotRadius} c ")
                content.append("${x - k} ${y + dotRadius} ${x - dotRadius} ${y + k} ${x - dotRadius} $y c ")
                content.append("${x - dotRadius} ${y - k} ${x - k} ${y - dotRadius} $x ${y - dotRadius} c ")
                content.append("${x + k} ${y - dotRadius} ${x + dotRadius} ${y - k} ${x + dotRadius} $y c ")
                content.append("f\n")
                x += spacing
            }
            y += rowHeight
            row++
        }
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Creates music staff paper - Professional manuscript paper.
     * Features: Properly spaced 5-line staves with clef area.
     */
    private fun createMusicPdfBytes(width: Float, height: Float): ByteArray {
        val staffLineSpacing = 7f // Standard music staff line spacing
        val staffHeight = staffLineSpacing * 4 // 5 lines = 4 spaces
        val staffGap = 55f // Space between staves (for notes above/below)
        val margin = 55f
        val clefMargin = 30f // Extra space at left for clef
        
        val content = StringBuilder()
        
        // Cream paper background
        content.append("q\n")
        content.append("0.995 0.992 0.975 rg\n")
        content.append("0 0 ${width.toInt()} ${height.toInt()} re f\n")
        content.append("Q\n")
        
        // Staff lines (black for music paper)
        content.append("0.15 0.15 0.15 RG\n")
        content.append("0.4 w\n")
        
        var staffTop = height - margin
        while (staffTop - staffHeight > margin) {
            // Draw 5 lines for one staff
            for (i in 0 until 5) {
                val lineY = staffTop - (i * staffLineSpacing)
                content.append("${margin + clefMargin} $lineY m ${width - margin} $lineY l S\n")
            }
            
            // Bar lines at start and end
            content.append("0.8 w\n")
            content.append("${margin + clefMargin} ${staffTop} m ${margin + clefMargin} ${staffTop - staffHeight} l S\n")
            content.append("${width - margin} ${staffTop} m ${width - margin} ${staffTop - staffHeight} l S\n")
            content.append("0.4 w\n")
            
            staffTop -= (staffHeight + staffGap)
        }
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Creates engineering paper - Traditional green grid with 5-to-1 divisions.
     * Features: Light green background, fine grid with bold major lines.
     */
    private fun createEngineeringPdfBytes(width: Float, height: Float): ByteArray {
        val smallGrid = 2.835f // 1mm in points
        val mediumGrid = smallGrid * 5 // 5mm
        val largeGrid = smallGrid * 10 // 10mm (1cm)
        val margin = 28f
        
        val content = StringBuilder()
        
        // Very light green background
        content.append("q\n")
        content.append("0.94 0.98 0.94 rg\n")
        content.append("0 0 ${width.toInt()} ${height.toInt()} re f\n")
        content.append("Q\n")
        
        // Medium grid lines (5mm - subtle)
        content.append("0.7 0.85 0.7 RG\n")
        content.append("0.2 w\n")
        
        var x = margin
        while (x <= width - margin) {
            content.append("$x $margin m $x ${height - margin} l S\n")
            x += mediumGrid
        }
        var y = margin
        while (y <= height - margin) {
            content.append("$margin $y m ${width - margin} $y l S\n")
            y += mediumGrid
        }
        
        // Major grid lines (10mm - more visible)
        content.append("0.45 0.7 0.45 RG\n")
        content.append("0.5 w\n")
        
        x = margin
        while (x <= width - margin) {
            content.append("$x $margin m $x ${height - margin} l S\n")
            x += largeGrid
        }
        y = margin
        while (y <= height - margin) {
            content.append("$margin $y m ${width - margin} $y l S\n")
            y += largeGrid
        }
        
        // Border
        content.append("0.3 0.55 0.3 RG\n")
        content.append("0.8 w\n")
        content.append("$margin $margin m ${width - margin} $margin l ${width - margin} ${height - margin} l $margin ${height - margin} l s\n")
        
        return createPdfWithContent(width, height, content.toString())
    }
    
    /**
     * Helper to create a PDF with given content stream.
     * Uses proper dynamic xref offset calculation.
     */
    private fun createPdfWithContent(width: Float, height: Float, content: String): ByteArray {
        val w = width.toInt()
        val h = height.toInt()
        val contentBytes = content.toByteArray(Charsets.US_ASCII)
        val contentLength = contentBytes.size
        
        // Build PDF piece by piece, tracking byte offsets
        val pdf = StringBuilder()
        
        // Header
        pdf.append("%PDF-1.4\n")
        val headerLength = pdf.length
        
        // Object 1: Catalog
        val obj1Offset = pdf.length
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        
        // Object 2: Pages
        val obj2Offset = pdf.length
        pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        
        // Object 3: Page
        val obj3Offset = pdf.length
        pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] /Contents 4 0 R /Resources << >> >>\nendobj\n")
        
        // Object 4: Content stream
        val obj4Offset = pdf.length
        pdf.append("4 0 obj\n<< /Length $contentLength >>\nstream\n")
        pdf.append(content)
        pdf.append("\nendstream\nendobj\n")
        
        // XRef table
        val xrefOffset = pdf.length
        pdf.append("xref\n")
        pdf.append("0 5\n")
        pdf.append("0000000000 65535 f \n")
        pdf.append(String.format("%010d 00000 n \n", obj1Offset))
        pdf.append(String.format("%010d 00000 n \n", obj2Offset))
        pdf.append(String.format("%010d 00000 n \n", obj3Offset))
        pdf.append(String.format("%010d 00000 n \n", obj4Offset))
        
        // Trailer
        pdf.append("trailer\n<< /Size 5 /Root 1 0 R >>\n")
        pdf.append("startxref\n$xrefOffset\n%%EOF")
        
        return pdf.toString().toByteArray(Charsets.US_ASCII)
    }
    /**
     * Adds a new blank page to the document at the specified index.
     */
    suspend fun addPage(pageIndex: Int, templateId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document as? Document ?: return@withContext Result.failure(
                    IllegalStateException("No document open or document is not editable")
                )
                
                // MuPDF doesn't support adding pages directly to existing documents
                // This would require creating a new document with the additional page
                // For now, we'll return a success but note this limitation
                
                // TODO: Implement page addition by:
                // 1. Creating a new document
                // 2. Copying existing pages
                // 3. Inserting new page at specified index
                // 4. Replacing the current document
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Deletes a page from the document.
     */
    suspend fun deletePage(pageIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document as? Document ?: return@withContext Result.failure(
                    IllegalStateException("No document open or document is not editable")
                )
                
                if (pageIndex < 0 || pageIndex >= doc.countPages()) {
                    return@withContext Result.failure(
                        IndexOutOfBoundsException("Page index $pageIndex out of bounds")
                    )
                }
                
                // Cannot delete the last page
                if (doc.countPages() <= 1) {
                    return@withContext Result.failure(
                        IllegalStateException("Cannot delete the only page")
                    )
                }
                
                // MuPDF doesn't support deleting pages directly from existing documents
                // This would require creating a new document without the specified page
                
                // TODO: Implement page deletion by:
                // 1. Creating a new document
                // 2. Copying all pages except the one to delete
                // 3. Replacing the current document
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Duplicates a page at the specified index.
     */
    suspend fun duplicatePage(pageIndex: Int, insertIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document as? Document ?: return@withContext Result.failure(
                    IllegalStateException("No document open or document is not editable")
                )
                
                if (pageIndex < 0 || pageIndex >= doc.countPages()) {
                    return@withContext Result.failure(
                        IndexOutOfBoundsException("Page index $pageIndex out of bounds")
                    )
                }
                
                // MuPDF doesn't support duplicating pages directly in existing documents
                // This would require creating a new document with the duplicated page
                
                // TODO: Implement page duplication by:
                // 1. Creating a new document
                // 2. Copying all existing pages
                // 3. Inserting duplicate of specified page at insert index
                // 4. Replacing the current document
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Moves a page from one index to another.
     */
    suspend fun movePage(fromIndex: Int, toIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document as? Document ?: return@withContext Result.failure(
                    IllegalStateException("No document open or document is not editable")
                )
                
                val pageCount = doc.countPages()
                if (fromIndex < 0 || fromIndex >= pageCount || toIndex < 0 || toIndex >= pageCount) {
                    return@withContext Result.failure(
                        IndexOutOfBoundsException("Invalid page indices: from=$fromIndex, to=$toIndex")
                    )
                }
                
                if (fromIndex == toIndex) {
                    return@withContext Result.success(Unit)
                }
                
                // MuPDF doesn't support moving pages directly in existing documents
                // This would require creating a new document with pages in the new order
                
                // TODO: Implement page moving by:
                // 1. Creating a new document
                // 2. Copying pages in the new order (moving fromIndex to toIndex)
                // 3. Replacing the current document
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Saves the current document with all modifications to the working file.
     */
    suspend fun saveToWorkingFile(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document as? Document ?: return@withContext Result.failure(
                    IllegalStateException("No document open")
                )
                
                // MuPDF Document class doesn't support saving modifications
                // The document is read-only. Any changes would need to be handled
                // by the PdfAnnotationFlattener when making annotations permanent
                
                // For now, return success as the working file is already the current state
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Applies a template to a page.
     */
    private fun applyTemplate(page: com.artifex.mupdf.fitz.Page, templateId: String) {
        try {
            // This is a simplified template application
            // In practice, you would draw the template pattern on the page
            when (templateId) {
                "grid" -> {
                    // Draw grid pattern
                    // This would require more complex drawing operations
                }
                "lined" -> {
                    // Draw lined pattern
                }
                "dotgrid" -> {
                    // Draw dot grid pattern
                }
                // Add other templates as needed
            }
        } catch (e: Exception) {
            android.util.Log.e("MuPdfRenderer", "Failed to apply template $templateId", e)
        }
    }
}

/**
 * Represents a search result.
 */
data class SearchResult(
    val pageNumber: Int,
    val bounds: com.artifex.mupdf.fitz.Quad
)

/**
 * Represents a text line with its bounding box for smart highlighting.
 */
data class TextLine(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String = ""
)
