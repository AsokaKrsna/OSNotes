package com.osnotes.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.artifex.mupdf.fitz.*
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.osnotes.app.domain.model.InkStroke
import com.osnotes.app.domain.model.ShapeAnnotation
import com.osnotes.app.domain.model.ShapeType
import com.osnotes.app.domain.model.TextAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.osnotes.app.data.repository.CustomTemplateRepository
import com.osnotes.app.domain.model.PatternType

/**
 * Service for flattening annotations into PDF (Make Permanent feature) and handling
 * PDF page manipulation operations that require document reconstruction.
 * 
 * This class handles two main responsibilities:
 * 1. Flattening annotations into PDF pages (Make Permanent)
 * 2. PDF page manipulation (add, delete, reorder) that MuPDF doesn't support directly
 * 
 * Uses a bitmap-based approach for both operations since MuPDF documents are read-only.
 */
@Singleton
class PdfAnnotationFlattener @Inject constructor(
    private val context: Context,
    private val customTemplateRepository: CustomTemplateRepository
) {
    private val mutex = Mutex()
    
    /**
     * Result of PDF operations.
     */
    sealed class FlattenResult {
        data class Success(val outputUri: Uri) : FlattenResult()
        data class Error(val message: String) : FlattenResult()
    }
    
    /**
     * Result of page manipulation operations.
     */
    sealed class PageOperationResult {
        object Success : PageOperationResult()
        data class Error(val message: String) : PageOperationResult()
    }
    
    /**
     * Flattens annotations into a PDF document using a simple bitmap-based approach.
     * 
     * @param sourceUri The source PDF URI
     * @param strokes Map of page index to list of strokes on that page
     * @param shapes Map of page index to list of shapes on that page
     * @param textAnnotations Map of page index to list of text annotations on that page
     * @param outputUri Optional output URI (if null, creates new file)
     * @param replaceOriginal If true, replaces the original file
     * @param renderScale The scale factor used when rendering pages (default 2f)
     * @return Result of the operation
     */
    suspend fun flattenAnnotations(
        sourceUri: Uri,
        strokes: Map<Int, List<InkStroke>>,
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
        textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap(),
        outputUri: Uri? = null,
        replaceOriginal: Boolean = false,
        renderScale: Float = 2f
    ): FlattenResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val timeoutMs = 60_000L // 60 second timeout
            
            try {
                android.util.Log.d("PdfAnnotationFlattener", "Starting bitmap-based flattening for ${strokes.size} stroke pages, ${shapes.size} shape pages, ${textAnnotations.size} text pages")
                
                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    return@withContext FlattenResult.Error("Operation timed out")
                }
                
                // Create temp files
                val tempDir = context.cacheDir
                val tempInput = File(tempDir, "flatten_input_${System.currentTimeMillis()}.pdf")
                val tempOutput = File(tempDir, "flatten_output_${System.currentTimeMillis()}.pdf")
                
                // Copy source to temp
                val inputStream = try {
                    if (sourceUri.scheme == "content") {
                        context.contentResolver.openInputStream(sourceUri)
                    } else {
                        val path = sourceUri.path ?: sourceUri.toString()
                        File(path).inputStream()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PdfAnnotationFlattener", "Failed to open input stream for $sourceUri", e)
                    null
                } ?: return@withContext FlattenResult.Error("Cannot read source file: $sourceUri")
                
                inputStream.use { input ->
                    FileOutputStream(tempInput).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Open document with MuPDF
                val document = Document.openDocument(tempInput.absolutePath)
                val pageCount = document.countPages()
                
                android.util.Log.d("PdfAnnotationFlattener", "Document has $pageCount pages")
                
                try {
                    // Create output PDF using Android's PdfDocument
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    
                    // Process each page
                    for (pageIndex in 0 until pageCount) {
                        android.util.Log.d("PdfAnnotationFlattener", "Processing page $pageIndex of $pageCount")
                        
                        try {
                            // Render original page to bitmap
                            val page = document.loadPage(pageIndex)
                            val bounds = page.bounds
                            val currentPageWidth = bounds.x1 - bounds.x0
                            val currentPageHeight = bounds.y1 - bounds.y0
                            
                            // Detect if this page is already at rendered size or needs scaling
                            // If page is already ~1190x1684, it's been pre-rendered, so normalize back to 595x842
                            // If page is ~595x842, it's original size
                            val isAlreadyScaled = currentPageWidth > 1000f // Threshold to detect pre-rendered pages
                            
                            // Always normalize to standard PDF size (595x842 for A4)
                            val standardPageWidth = if (isAlreadyScaled) currentPageWidth / 2f else currentPageWidth
                            val standardPageHeight = if (isAlreadyScaled) currentPageHeight / 2f else currentPageHeight
                            
                            // Render bitmap at 2x for quality
                            val bitmapWidth = (standardPageWidth * renderScale).toInt()
                            val bitmapHeight = (standardPageHeight * renderScale).toInt()
                            
                            android.util.Log.d("PdfAnnotationFlattener", "Page $pageIndex - Current: ${currentPageWidth}x${currentPageHeight}, Standard: ${standardPageWidth}x${standardPageHeight}, Bitmap: ${bitmapWidth}x${bitmapHeight}")
                            
                            // Ensure reasonable bitmap size
                            val maxSize = 4096
                            val actualBitmapWidth = bitmapWidth.coerceAtMost(maxSize)
                            val actualBitmapHeight = bitmapHeight.coerceAtMost(maxSize)
                            
                            // Log detailed coordinate analysis
                            strokes[pageIndex]?.firstOrNull()?.let { stroke ->
                                val firstPoint = stroke.points.firstOrNull()
                                android.util.Log.d("PdfAnnotationFlattener", "=== COORDINATE ANALYSIS PAGE $pageIndex ===")
                                android.util.Log.d("PdfAnnotationFlattener", "Current PDF page size: ${currentPageWidth} x ${currentPageHeight}")
                                android.util.Log.d("PdfAnnotationFlattener", "Standard PDF size: ${standardPageWidth} x ${standardPageHeight}")
                                android.util.Log.d("PdfAnnotationFlattener", "Bitmap size: ${actualBitmapWidth} x ${actualBitmapHeight}")
                                android.util.Log.d("PdfAnnotationFlattener", "Stroke coordinate: (${firstPoint?.x}, ${firstPoint?.y})")
                                android.util.Log.d("PdfAnnotationFlattener", "Stroke width: ${stroke.strokeWidth}")
                                android.util.Log.d("PdfAnnotationFlattener", "Ratio to standard PDF width: ${firstPoint?.x?.div(standardPageWidth)}")
                                android.util.Log.d("PdfAnnotationFlattener", "Ratio to bitmap width: ${firstPoint?.x?.div(actualBitmapWidth)}")
                                android.util.Log.d("PdfAnnotationFlattener", "Ratio to current PDF width: ${firstPoint?.x?.div(currentPageWidth)}")
                                android.util.Log.d("PdfAnnotationFlattener", "Is page pre-scaled: $isAlreadyScaled")
                            }
                            
                            if (actualBitmapWidth <= 0 || actualBitmapHeight <= 0) {
                                android.util.Log.w("PdfAnnotationFlattener", "Invalid page size for page $pageIndex, skipping")
                                page.destroy()
                                continue
                            }
                            
                            val bitmap = Bitmap.createBitmap(
                                actualBitmapWidth,
                                actualBitmapHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            
                            // Render PDF page to bitmap
                            val matrix = Matrix(if (isAlreadyScaled) renderScale / 2f else renderScale)
                            val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.width, bitmap.height)
                            page.run(device, matrix, null)
                            device.close()
                            page.destroy()
                            
                            // Draw annotations on top of the bitmap
                            val canvas = Canvas(bitmap)
                            
                            // Draw strokes
                            strokes[pageIndex]?.let { pageStrokes ->
                                if (pageStrokes.isNotEmpty()) {
                                    android.util.Log.d("PdfAnnotationFlattener", "Drawing ${pageStrokes.size} strokes on page $pageIndex")
                                    drawStrokesOnCanvas(canvas, pageStrokes, renderScale)
                                }
                            }
                            
                            // Draw shapes
                            shapes[pageIndex]?.let { pageShapes ->
                                if (pageShapes.isNotEmpty()) {
                                    android.util.Log.d("PdfAnnotationFlattener", "Drawing ${pageShapes.size} shapes on page $pageIndex")
                                    drawShapesOnCanvas(canvas, pageShapes, renderScale)
                                }
                            }
                            
                            // Draw text annotations
                            textAnnotations[pageIndex]?.let { pageTexts ->
                                if (pageTexts.isNotEmpty()) {
                                    android.util.Log.d("PdfAnnotationFlattener", "Drawing ${pageTexts.size} texts on page $pageIndex")
                                    drawTextsOnCanvas(canvas, pageTexts, renderScale)
                                }
                            }
                            
                            // Create PDF page at STANDARD size (595x842) to match original PDF dimensions
                            // The bitmap will be scaled down to fit
                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                                standardPageWidth.toInt(),
                                standardPageHeight.toInt(),
                                pageIndex + 1
                            ).create()
                            
                            val pdfPage = pdfDocument.startPage(pageInfo)
                            
                            android.util.Log.d("PdfAnnotationFlattener", "Creating PDF page: ${standardPageWidth.toInt()}x${standardPageHeight.toInt()}, Bitmap: ${actualBitmapWidth}x${actualBitmapHeight}")
                            
                            // Scale bitmap down from 1190x1684 to 595x842
                            val destRect = android.graphics.Rect(0, 0, standardPageWidth.toInt(), standardPageHeight.toInt())
                            pdfPage.canvas.drawBitmap(bitmap, null, destRect, null)
                            
                            pdfDocument.finishPage(pdfPage)
                            
                            bitmap.recycle()
                            
                        } catch (e: Exception) {
                            android.util.Log.e("PdfAnnotationFlattener", "Error processing page $pageIndex", e)
                            // Continue with next page instead of failing completely
                        }
                    }
                    
                    // Write output PDF
                    FileOutputStream(tempOutput).use { output ->
                        pdfDocument.writeTo(output)
                    }
                    pdfDocument.close()
                    
                } finally {
                    document.destroy()
                }
                
                // Copy output to destination
                val destUri = if (replaceOriginal) {
                    // Write back to original
                    try {
                        when {
                            sourceUri.scheme == "content" -> {
                                context.contentResolver.openOutputStream(sourceUri, "wt")?.use { output ->
                                    tempOutput.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            sourceUri.scheme == "file" || sourceUri.scheme == null -> {
                                val path = sourceUri.path ?: sourceUri.toString()
                                tempOutput.copyTo(File(path), overwrite = true)
                            }
                            else -> {
                                context.contentResolver.openOutputStream(sourceUri, "wt")?.use { output ->
                                    tempOutput.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PdfAnnotationFlattener", "Failed to write back to $sourceUri", e)
                        return@withContext FlattenResult.Error("Failed to save to original file: ${e.message}")
                    }
                    sourceUri
                } else {
                    // Write to new file or provided URI
                    val finalUri = outputUri ?: createOutputUri(sourceUri)
                    try {
                        when {
                            finalUri.scheme == "content" -> {
                                context.contentResolver.openOutputStream(finalUri)?.use { output ->
                                    tempOutput.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            finalUri.scheme == "file" || finalUri.scheme == null -> {
                                val path = finalUri.path ?: finalUri.toString()
                                tempOutput.copyTo(File(path), overwrite = true)
                            }
                            else -> {
                                context.contentResolver.openOutputStream(finalUri)?.use { output ->
                                    tempOutput.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PdfAnnotationFlattener", "Failed to write to $finalUri", e)
                        return@withContext FlattenResult.Error("Failed to save to output file: ${e.message}")
                    }
                    finalUri
                }
                
                // Cleanup temp files
                tempInput.delete()
                tempOutput.delete()
                
                FlattenResult.Success(destUri)
                
            } catch (e: Exception) {
                android.util.Log.e("PdfAnnotationFlattener", "Error during flattening", e)
                FlattenResult.Error(e.message ?: "Unknown error during flattening")
            }
        }
    }
    
    /**
     * Draws ink strokes on a canvas bitmap.
     * Strokes are stored in bitmap coordinate space (1190x1684 for A4 at 2x scale).
     * Draw them as-is without any scaling, just like ExportManager does.
     */
    private fun drawStrokesOnCanvas(canvas: Canvas, strokes: List<InkStroke>, renderScale: Float = 2f) {
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            
            val paint = Paint().apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
                
                // Set alpha for highlighters
                if (stroke.isHighlighter) {
                    alpha = 128 // 50% opacity
                }
            }
            
            // Use stroke.toPath() exactly like ExportManager does
            val path = stroke.toPath()
            canvas.drawPath(path, paint)
        }
    }
    
    /**
     * Draws shape annotations on a canvas bitmap.
     * Shapes are stored in bitmap coordinate space - draw them as-is like strokes.
     */
    private fun drawShapesOnCanvas(canvas: Canvas, shapes: List<ShapeAnnotation>, renderScale: Float = 2f) {
        for (shape in shapes) {
            val paint = Paint().apply {
                color = shape.color.toArgb()
                strokeWidth = shape.strokeWidth
                style = if (shape.filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            
            when (shape.shapeType) {
                ShapeType.RECTANGLE -> {
                    val rect = RectF(
                        minOf(shape.startPoint.x, shape.endPoint.x),
                        minOf(shape.startPoint.y, shape.endPoint.y),
                        maxOf(shape.startPoint.x, shape.endPoint.x),
                        maxOf(shape.startPoint.y, shape.endPoint.y)
                    )
                    canvas.drawRect(rect, paint)
                }
                ShapeType.CIRCLE -> {
                    val centerX = (shape.startPoint.x + shape.endPoint.x) / 2f
                    val centerY = (shape.startPoint.y + shape.endPoint.y) / 2f
                    val radius = kotlin.math.min(
                        kotlin.math.abs(shape.endPoint.x - shape.startPoint.x),
                        kotlin.math.abs(shape.endPoint.y - shape.startPoint.y)
                    ) / 2f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
                ShapeType.LINE -> {
                    canvas.drawLine(
                        shape.startPoint.x, shape.startPoint.y,
                        shape.endPoint.x, shape.endPoint.y,
                        paint
                    )
                }
                ShapeType.ARROW -> {
                    // Draw line
                    canvas.drawLine(
                        shape.startPoint.x, shape.startPoint.y,
                        shape.endPoint.x, shape.endPoint.y,
                        paint
                    )
                    
                    // Draw arrowhead
                    val angle = kotlin.math.atan2(
                        (shape.endPoint.y - shape.startPoint.y).toDouble(),
                        (shape.endPoint.x - shape.startPoint.x).toDouble()
                    )
                    val arrowLength = 20f
                    val arrowAngle = Math.PI / 6 // 30 degrees
                    
                    val endX = shape.endPoint.x
                    val endY = shape.endPoint.y
                    val x1 = endX - arrowLength * kotlin.math.cos(angle - arrowAngle).toFloat()
                    val y1 = endY - arrowLength * kotlin.math.sin(angle - arrowAngle).toFloat()
                    val x2 = endX - arrowLength * kotlin.math.cos(angle + arrowAngle).toFloat()
                    val y2 = endY - arrowLength * kotlin.math.sin(angle + arrowAngle).toFloat()
                    
                    canvas.drawLine(endX, endY, x1, y1, paint)
                    canvas.drawLine(endX, endY, x2, y2, paint)
                }
                else -> {
                    // Default to line for unknown shapes
                    canvas.drawLine(
                        shape.startPoint.x, shape.startPoint.y,
                        shape.endPoint.x, shape.endPoint.y,
                        paint
                    )
                }
            }
        }
    }
    
    /**
     * Draws text annotations on a canvas bitmap.
     * Text positions are stored in bitmap coordinate space - draw them as-is.
     */
    private fun drawTextsOnCanvas(canvas: Canvas, texts: List<TextAnnotation>, renderScale: Float = 2f) {
        for (text in texts) {
            val paint = Paint().apply {
                color = text.color.toArgb()
                textSize = text.fontSize
                typeface = Typeface.DEFAULT
                isAntiAlias = true
            }
            
            // Skip background drawing - transparent background
            
            // Draw text with proper line wrapping
            val lines = text.text.split('\n')
            val lineHeight = text.fontSize * 1.2f
            val availableWidth = text.width - text.padding * 2
            
            var currentY = text.position.y + text.fontSize
            
            lines.forEach { line ->
                // Word wrapping within the text box width
                val words = line.split(' ')
                var currentLine = ""
                
                words.forEach { word ->
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val textWidth = paint.measureText(testLine)
                    
                    if (textWidth <= availableWidth || currentLine.isEmpty()) {
                        currentLine = testLine
                    } else {
                        // Draw current line and start new line
                        canvas.drawText(
                            currentLine,
                            text.position.x + text.padding,
                            currentY,
                            paint
                        )
                        currentLine = word
                        currentY += lineHeight
                    }
                }
                
                // Draw remaining text
                if (currentLine.isNotEmpty()) {
                    canvas.drawText(
                        currentLine,
                        text.position.x + text.padding,
                        currentY,
                        paint
                    )
                    currentY += lineHeight
                }
            }
        }
    }
    
    /**
     * Creates a new output URI based on the source URI.
     * Appends "_annotated" to the filename.
     */
    private fun createOutputUri(sourceUri: Uri): Uri {
        // For now, create in cache directory
        // In production, should create alongside source
        val timestamp = System.currentTimeMillis()
        val outputFile = File(context.cacheDir, "annotated_$timestamp.pdf")
        return Uri.fromFile(outputFile)
    }
    
    /**
     * Checks the annotation count and warns if high.
     */
    fun shouldWarnAboutAnnotationCount(count: Int): Boolean {
        return count > HIGH_ANNOTATION_THRESHOLD
    }
    
    /**
     * Estimates the file size increase from annotations.
     */
    fun estimateFileSizeIncrease(strokeCount: Int, avgPointsPerStroke: Int = 50): Long {
        // Rough estimate: each point is about 16 bytes (2 floats)
        // Plus overhead per stroke (color, width, etc.) ~100 bytes
        val pointsBytes = strokeCount * avgPointsPerStroke * 16L
        val overheadBytes = strokeCount * 100L
        return pointsBytes + overheadBytes
    }
    
    companion object {
        const val HIGH_ANNOTATION_THRESHOLD = 500
    }
    
    // ==================== Helper Methods for Page Operations ====================
    
    /**
     * Copies a URI to a file.
     */
    private fun copyUriToFile(sourceUri: Uri, targetFile: File) {
        val inputStream = if (sourceUri.scheme == "content") {
            context.contentResolver.openInputStream(sourceUri)
        } else {
            val path = sourceUri.path ?: sourceUri.toString()
            File(path).inputStream()
        } ?: throw IllegalStateException("Cannot read source file: $sourceUri")
        
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * Replaces the original file with the new content.
     */
    private fun replaceOriginalFile(sourceUri: Uri, newFile: File) {
        when {
            sourceUri.scheme == "content" -> {
                context.contentResolver.openOutputStream(sourceUri, "wt")?.use { output ->
                    newFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
            sourceUri.scheme == "file" || sourceUri.scheme == null -> {
                val path = sourceUri.path ?: sourceUri.toString()
                newFile.copyTo(File(path), overwrite = true)
            }
            else -> {
                context.contentResolver.openOutputStream(sourceUri, "wt")?.use { output ->
                    newFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    
    /**
     * Copies a page from source document to output PDF with annotations.
     */
    private fun copyPageWithAnnotations(
        sourceDocument: Document,
        sourcePageIndex: Int,
        outputPdf: android.graphics.pdf.PdfDocument,
        outputPageNumber: Int,
        strokes: Map<Int, List<InkStroke>>,
        shapes: Map<Int, List<ShapeAnnotation>>,
        textAnnotations: Map<Int, List<TextAnnotation>>,
        renderScale: Float = 2f
    ) {
        try {
            // Render source page to bitmap
            val page = sourceDocument.loadPage(sourcePageIndex)
            val bounds = page.bounds
            val originalPageWidth = bounds.x1 - bounds.x0
            val originalPageHeight = bounds.y1 - bounds.y0
            
            // Calculate bitmap size with render scale
            val bitmapWidth = (originalPageWidth * renderScale).toInt()
            val bitmapHeight = (originalPageHeight * renderScale).toInt()
            
            // Ensure reasonable bitmap size
            val maxSize = 4096
            val actualBitmapWidth = bitmapWidth.coerceAtMost(maxSize)
            val actualBitmapHeight = bitmapHeight.coerceAtMost(maxSize)
            
            if (actualBitmapWidth <= 0 || actualBitmapHeight <= 0) {
                android.util.Log.w("PdfAnnotationFlattener", "Invalid page size for page $sourcePageIndex, creating blank page")
                addBlankPage(outputPdf, outputPageNumber, originalPageWidth, originalPageHeight)
                page.destroy()
                return
            }
            
            val bitmap = Bitmap.createBitmap(actualBitmapWidth, actualBitmapHeight, Bitmap.Config.ARGB_8888)
            
            // Render PDF page to bitmap
            val matrix = Matrix(renderScale)
            val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.width, bitmap.height)
            page.run(device, matrix, null)
            device.close()
            page.destroy()
            
            // Draw annotations on top of the bitmap
            val canvas = Canvas(bitmap)
            
            // Draw annotations for this page
            strokes[sourcePageIndex]?.let { pageStrokes ->
                if (pageStrokes.isNotEmpty()) {
                    drawStrokesOnCanvas(canvas, pageStrokes, renderScale)
                }
            }
            
            shapes[sourcePageIndex]?.let { pageShapes ->
                if (pageShapes.isNotEmpty()) {
                    drawShapesOnCanvas(canvas, pageShapes, renderScale)
                }
            }
            
            textAnnotations[sourcePageIndex]?.let { pageTexts ->
                if (pageTexts.isNotEmpty()) {
                    drawTextsOnCanvas(canvas, pageTexts, renderScale)
                }
            }
            
            // Create PDF page at STANDARD size to prevent size growth
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                originalPageWidth.toInt(),
                originalPageHeight.toInt(),
                outputPageNumber
            ).create()
            
            val pdfPage = outputPdf.startPage(pageInfo)
            
            // Scale the bitmap down to fit the standard page size
            val destRect = android.graphics.Rect(0, 0, originalPageWidth.toInt(), originalPageHeight.toInt())
            pdfPage.canvas.drawBitmap(bitmap, null, destRect, null)
            
            outputPdf.finishPage(pdfPage)
            
            bitmap.recycle()
            
        } catch (e: Exception) {
            android.util.Log.e("PdfAnnotationFlattener", "Error copying page $sourcePageIndex", e)
            // Add blank page as fallback
            addBlankPage(outputPdf, outputPageNumber, 595f, 842f)
        }
    }
    
    /**
     * Adds a template page to the output PDF.
     */
    private fun addTemplatePage(
        outputPdf: android.graphics.pdf.PdfDocument,
        templateName: String,
        pageNumber: Int,
        width: Float = 595f,
        height: Float = 842f
    ) {
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
            width.toInt(),
            height.toInt(),
            pageNumber
        ).create()
        
        val page = outputPdf.startPage(pageInfo)
        val canvas = page.canvas
        
        drawTemplate(canvas, templateName, width, height)
        
        outputPdf.finishPage(page)
    }
    
    /**
     * Adds a blank page to the output PDF.
     */
    private fun addBlankPage(
        outputPdf: android.graphics.pdf.PdfDocument,
        pageNumber: Int,
        width: Float,
        height: Float
    ) {
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
            width.toInt(),
            height.toInt(),
            pageNumber
        ).create()
        
        val page = outputPdf.startPage(pageInfo)
        val canvas = page.canvas
        
        // White background
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width, height, paint)
        
        outputPdf.finishPage(page)
    }
    
    /**
     * Draws a template pattern on a canvas.
     * MUST match the templates in MuPdfRenderer for consistency.
     */
    private fun drawTemplate(
        canvas: Canvas,
        templateName: String,
        width: Float,
        height: Float
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        when (templateName.uppercase()) {
            "BLANK" -> {
                // White background
                paint.color = android.graphics.Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
            }
            "LINED" -> {
                // Cream background
                paint.color = android.graphics.Color.rgb(254, 252, 247)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
                
                val lineSpacing = 24f
                val marginLeft = 72f
                val marginTop = 85f
                val marginBottom = 50f
                
                // Red margin line
                paint.color = android.graphics.Color.rgb(204, 51, 51)
                paint.strokeWidth = 0.75f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(marginLeft - 8f, height - marginTop, marginLeft - 8f, marginBottom, paint)
                
                // Blue ruled lines
                paint.color = android.graphics.Color.rgb(153, 187, 217)
                paint.strokeWidth = 0.5f
                var y = height - marginTop - lineSpacing
                while (y > marginBottom) {
                    canvas.drawLine(marginLeft, y, width - 40f, y, paint)
                    y -= lineSpacing
                }
            }
            "GRID" -> {
                // White background
                paint.color = android.graphics.Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
                
                val smallGrid = 14.17f // 5mm
                val largeGrid = 28.35f // 10mm
                val margin = 30f
                
                // Minor grid
                paint.color = android.graphics.Color.rgb(210, 210, 210)
                paint.strokeWidth = 0.25f
                paint.style = Paint.Style.STROKE
                
                var x = margin
                while (x <= width - margin) {
                    canvas.drawLine(x, margin, x, height - margin, paint)
                    x += smallGrid
                }
                var y = margin
                while (y <= height - margin) {
                    canvas.drawLine(margin, y, width - margin, y, paint)
                    y += smallGrid
                }
                
                // Major grid
                paint.color = android.graphics.Color.rgb(166, 166, 166)
                paint.strokeWidth = 0.5f
                x = margin
                while (x <= width - margin) {
                    canvas.drawLine(x, margin, x, height - margin, paint)
                    x += largeGrid
                }
                y = margin
                while (y <= height - margin) {
                    canvas.drawLine(margin, y, width - margin, y, paint)
                    y += largeGrid
                }
            }
            "DOTTED" -> {
                // White background
                paint.color = android.graphics.Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
                
                val dotSpacing = 14.17f
                val margin = 35f
                
                paint.color = android.graphics.Color.rgb(140, 140, 140)
                paint.style = Paint.Style.FILL
                
                var x = margin + dotSpacing
                while (x < width - margin) {
                    var y = margin + dotSpacing
                    while (y < height - margin) {
                        canvas.drawCircle(x, y, 0.6f, paint)
                        y += dotSpacing
                    }
                    x += dotSpacing
                }
            }
            "CORNELL" -> {
                val cueColumnWidth = 150f
                val summaryHeight = 130f
                val topMargin = 70f
                val sideMargin = 25f
                
                // Background - light cream
                paint.color = android.graphics.Color.rgb(252, 250, 245)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
                
                // Summary section background (light blue)
                paint.color = android.graphics.Color.rgb(235, 242, 250)
                canvas.drawRect(sideMargin, sideMargin, width - sideMargin, summaryHeight + sideMargin, paint)
                
                // Cue column background (light warm)
                paint.color = android.graphics.Color.rgb(250, 245, 235)
                canvas.drawRect(sideMargin, summaryHeight + sideMargin, sideMargin + cueColumnWidth, height - topMargin, paint)
                
                // Header background
                paint.color = android.graphics.Color.rgb(242, 242, 247)
                canvas.drawRect(sideMargin, height - topMargin, width - sideMargin, height - sideMargin, paint)
                
                // Main section dividers
                paint.color = android.graphics.Color.rgb(77, 77, 89)
                paint.strokeWidth = 1.5f
                paint.style = Paint.Style.STROKE
                
                // Outer border
                canvas.drawRect(sideMargin, sideMargin, width - sideMargin, height - sideMargin, paint)
                
                // Vertical divider
                val cueRight = sideMargin + cueColumnWidth
                canvas.drawLine(cueRight, summaryHeight + sideMargin, cueRight, height - topMargin, paint)
                
                // Horizontal dividers
                canvas.drawLine(sideMargin, summaryHeight + sideMargin, width - sideMargin, summaryHeight + sideMargin, paint)
                canvas.drawLine(sideMargin, height - topMargin, width - sideMargin, height - topMargin, paint)
                
                // Faint ruled lines
                paint.color = android.graphics.Color.rgb(191, 199, 209)
                paint.strokeWidth = 0.4f
                val lineSpacing = 24f
                var y = height - topMargin - lineSpacing
                while (y > summaryHeight + sideMargin + 15f) {
                    canvas.drawLine(cueRight + 10f, y, width - sideMargin - 10f, y, paint)
                    y -= lineSpacing
                }
            }
            "ISOMETRIC" -> {
                // White background
                paint.color = android.graphics.Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
                
                val spacing = 17f
                val rowHeight = spacing * 0.866f
                val margin = 30f
                
                paint.color = android.graphics.Color.rgb(128, 128, 140)
                paint.style = Paint.Style.FILL
                
                var row = 0
                var y = margin
                while (y < height - margin) {
                    val xOffset = if (row % 2 == 0) 0f else spacing / 2f
                    var x = margin + xOffset
                    while (x < width - margin) {
                        canvas.drawCircle(x, y, 0.55f, paint)
                        x += spacing
                    }
                    y += rowHeight
                    row++
                }
            }
            "MUSIC" -> {
                // Cream background
                paint.color = android.graphics.Color.rgb(254, 253, 249)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
                
                val staffLineSpacing = 7f
                val staffHeight = staffLineSpacing * 4
                val staffGap = 55f
                val margin = 55f
                val clefMargin = 30f
                
                paint.color = android.graphics.Color.rgb(38, 38, 38)
                paint.strokeWidth = 0.4f
                paint.style = Paint.Style.STROKE
                
                var staffTop = height - margin
                while (staffTop - staffHeight > margin) {
                    // Draw 5 lines for one staff
                    for (i in 0 until 5) {
                        val lineY = staffTop - (i * staffLineSpacing)
                        canvas.drawLine(margin + clefMargin, lineY, width - margin, lineY, paint)
                    }
                    
                    // Bar lines
                    paint.strokeWidth = 0.8f
                    canvas.drawLine(margin + clefMargin, staffTop, margin + clefMargin, staffTop - staffHeight, paint)
                    canvas.drawLine(width - margin, staffTop, width - margin, staffTop - staffHeight, paint)
                    paint.strokeWidth = 0.4f
                    
                    staffTop -= (staffHeight + staffGap)
                }
            }
            "ENGINEERING" -> {
                // Light green background
                paint.color = android.graphics.Color.rgb(240, 250, 240)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
                
                val smallGrid = 2.835f // 1mm
                val mediumGrid = smallGrid * 5 // 5mm
                val largeGrid = smallGrid * 10 // 10mm
                val margin = 28f
                
                // Medium grid (5mm)
                paint.color = android.graphics.Color.rgb(179, 217, 179)
                paint.strokeWidth = 0.2f
                paint.style = Paint.Style.STROKE
                
                var x = margin
                while (x <= width - margin) {
                    canvas.drawLine(x, margin, x, height - margin, paint)
                    x += mediumGrid
                }
                var y = margin
                while (y <= height - margin) {
                    canvas.drawLine(margin, y, width - margin, y, paint)
                    y += mediumGrid
                }
                
                // Major grid (10mm)
                paint.color = android.graphics.Color.rgb(115, 179, 115)
                paint.strokeWidth = 0.5f
                x = margin
                while (x <= width - margin) {
                    canvas.drawLine(x, margin, x, height - margin, paint)
                    x += largeGrid
                }
                y = margin
                while (y <= height - margin) {
                    canvas.drawLine(margin, y, width - margin, y, paint)
                    y += largeGrid
                }
                
                // Border
                paint.color = android.graphics.Color.rgb(77, 140, 77)
                paint.strokeWidth = 0.8f
                canvas.drawRect(margin, margin, width - margin, height - margin, paint)
            }
            else -> {
                // Check if it's a custom template
                val customTemplate = customTemplateRepository.getCustomTemplateById(templateName)
                
                if (customTemplate != null) {
                    drawCustomTemplate(canvas, customTemplate, width, height)
                } else {
                    // Default blank white
                    paint.color = android.graphics.Color.WHITE
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
            }
        }
    }
    
    /**
     * Draws a custom user-defined template.
     */
    private fun drawCustomTemplate(
        canvas: Canvas,
        template: com.osnotes.app.domain.model.CustomTemplate,
        width: Float,
        height: Float
    ) {
        val paint = Paint().apply { isAntiAlias = true }
        
        // Background
        paint.color = template.backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width, height, paint)
        
        // Header section
        if (template.hasHeader) {
            paint.color = template.headerColor
            canvas.drawRect(0f, height - template.headerHeight, width, height, paint)
        }
        
        // Footer section
        if (template.hasFooter) {
            paint.color = template.footerColor
            canvas.drawRect(0f, 0f, width, template.footerHeight, paint)
        }
        
        // Side column
        if (template.hasSideColumn) {
            paint.color = template.sideColumnColor
            val footerH = if (template.hasFooter) template.footerHeight else 0f
            val headerH = if (template.hasHeader) template.headerHeight else 0f
            if (template.sideColumnOnLeft) {
                canvas.drawRect(0f, footerH, template.sideColumnWidth, height - headerH, paint)
            } else {
                canvas.drawRect(width - template.sideColumnWidth, footerH, width, height - headerH, paint)
            }
        }
        
        // Pattern
        val marginL = template.marginLeft
        val marginR = template.marginRight
        val marginT = template.marginTop
        val marginB = template.marginBottom
        val spacing = template.lineSpacing
        
        paint.color = adjustColorOpacity(template.lineColor, template.patternOpacity)
        paint.strokeWidth = template.lineThickness
        paint.style = Paint.Style.STROKE
        
        when (template.patternType) {
            PatternType.HORIZONTAL_LINES -> {
                var y = height - marginT - spacing
                while (y > marginB) {
                    canvas.drawLine(marginL, y, width - marginR, y, paint)
                    y -= spacing
                }
            }
            PatternType.VERTICAL_LINES -> {
                var x = marginL + spacing
                while (x < width - marginR) {
                    canvas.drawLine(x, marginB, x, height - marginT, paint)
                    x += spacing
                }
            }
            PatternType.GRID -> {
                var x = marginL + spacing
                while (x < width - marginR) {
                    canvas.drawLine(x, marginB, x, height - marginT, paint)
                    x += spacing
                }
                var y = height - marginT - spacing
                while (y > marginB) {
                    canvas.drawLine(marginL, y, width - marginR, y, paint)
                    y -= spacing
                }
                // Secondary grid
                if (template.hasSecondaryGrid) {
                    paint.color = adjustColorOpacity(template.secondaryLineColor, template.patternOpacity)
                    paint.strokeWidth = template.secondaryLineThickness
                    x = marginL + template.secondaryLineSpacing
                    while (x < width - marginR) {
                        canvas.drawLine(x, marginB, x, height - marginT, paint)
                        x += template.secondaryLineSpacing
                    }
                    y = height - marginT - template.secondaryLineSpacing
                    while (y > marginB) {
                        canvas.drawLine(marginL, y, width - marginR, y, paint)
                        y -= template.secondaryLineSpacing
                    }
                }
            }
            PatternType.DOTS -> {
                paint.style = Paint.Style.FILL
                var x = marginL + spacing
                while (x < width - marginR) {
                    var y = marginB + spacing
                    while (y < height - marginT) {
                        canvas.drawCircle(x, y, template.dotSize, paint)
                        y += spacing
                    }
                    x += spacing
                }
            }
            PatternType.ISOMETRIC_DOTS -> {
                paint.style = Paint.Style.FILL
                var row = 0
                var y = marginB + spacing
                while (y < height - marginT) {
                    val xOffset = if (row % 2 == 0) 0f else spacing / 2f
                    var x = marginL + spacing + xOffset
                    while (x < width - marginR) {
                        canvas.drawCircle(x, y, template.dotSize, paint)
                        x += spacing
                    }
                    y += spacing * 0.866f
                    row++
                }
            }
            PatternType.DIAGONAL_LEFT, PatternType.DIAGONAL_RIGHT, PatternType.CROSSHATCH -> {
                if (template.patternType == PatternType.DIAGONAL_LEFT || template.patternType == PatternType.CROSSHATCH) {
                    var offset = marginL
                    while (offset < width + height) {
                        canvas.drawLine(offset, height - marginT, offset - (height - marginB - marginT), marginB, paint)
                        offset += spacing
                    }
                }
                if (template.patternType == PatternType.DIAGONAL_RIGHT || template.patternType == PatternType.CROSSHATCH) {
                    var offset = marginL - height
                    while (offset < width) {
                        canvas.drawLine(offset, marginB, offset + (height - marginB - marginT), height - marginT, paint)
                        offset += spacing
                    }
                }
            }
            PatternType.NONE -> { /* blank */ }
        }
        
        // Margin line
        if (template.hasMarginLine) {
            paint.color = template.marginLineColor
            paint.strokeWidth = 0.75f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(template.marginLinePosition, marginB, template.marginLinePosition, height - marginT, paint)
        }
    }
    
    private fun adjustColorOpacity(color: Int, opacity: Float): Int {
        val alpha = (opacity * 255).toInt()
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
    
    // ==================== Page Manipulation Methods ====================
    
    /**
     * Adds a new page to the PDF at the specified index with the given template.
     * This creates a new PDF with the inserted page.
     */
    suspend fun addPage(
        sourceUri: Uri,
        pageIndex: Int,
        templateName: String,
        strokes: Map<Int, List<InkStroke>> = emptyMap(),
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
        textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap()
    ): PageOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("PdfAnnotationFlattener", "Adding page at index $pageIndex with template $templateName")
                
                // Create temp files
                val tempDir = context.cacheDir
                val tempInput = File(tempDir, "add_page_input_${System.currentTimeMillis()}.pdf")
                val tempOutput = File(tempDir, "add_page_output_${System.currentTimeMillis()}.pdf")
                
                // Copy source to temp
                copyUriToFile(sourceUri, tempInput)
                
                // Open document with MuPDF
                val document = Document.openDocument(tempInput.absolutePath)
                val originalPageCount = document.countPages()
                val insertIndex = pageIndex.coerceIn(0, originalPageCount)
                
                try {
                    // Create output PDF
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    
                    // Process pages with insertion
                    for (outputPageIndex in 0 until originalPageCount + 1) {
                        if (outputPageIndex == insertIndex) {
                            // Insert new template page
                            addTemplatePage(pdfDocument, templateName, outputPageIndex + 1)
                        } else {
                            // Copy existing page
                            val sourcePageIndex = if (outputPageIndex < insertIndex) outputPageIndex else outputPageIndex - 1
                            copyPageWithAnnotations(
                                document, sourcePageIndex, pdfDocument, outputPageIndex + 1,
                                strokes, shapes, textAnnotations
                            )
                        }
                    }
                    
                    // Write output PDF
                    FileOutputStream(tempOutput).use { output ->
                        pdfDocument.writeTo(output)
                    }
                    pdfDocument.close()
                    
                } finally {
                    document.destroy()
                }
                
                // Replace original file
                replaceOriginalFile(sourceUri, tempOutput)
                
                // Cleanup
                tempInput.delete()
                tempOutput.delete()
                
                PageOperationResult.Success
                
            } catch (e: Exception) {
                android.util.Log.e("PdfAnnotationFlattener", "Error adding page", e)
                PageOperationResult.Error(e.message ?: "Failed to add page")
            }
        }
    }
    
    /**
     * Deletes a page from the PDF at the specified index.
     */
    suspend fun deletePage(
        sourceUri: Uri,
        pageIndex: Int,
        strokes: Map<Int, List<InkStroke>> = emptyMap(),
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
        textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap()
    ): PageOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("PdfAnnotationFlattener", "Deleting page at index $pageIndex")
                
                // Create temp files
                val tempDir = context.cacheDir
                val tempInput = File(tempDir, "delete_page_input_${System.currentTimeMillis()}.pdf")
                val tempOutput = File(tempDir, "delete_page_output_${System.currentTimeMillis()}.pdf")
                
                // Copy source to temp
                copyUriToFile(sourceUri, tempInput)
                
                // Open document with MuPDF
                val document = Document.openDocument(tempInput.absolutePath)
                val originalPageCount = document.countPages()
                
                if (pageIndex < 0 || pageIndex >= originalPageCount) {
                    return@withContext PageOperationResult.Error("Page index $pageIndex out of bounds")
                }
                
                if (originalPageCount <= 1) {
                    return@withContext PageOperationResult.Error("Cannot delete the only page")
                }
                
                try {
                    // Create output PDF
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    var outputPageNum = 1
                    
                    // Copy all pages except the deleted one
                    for (sourcePageIndex in 0 until originalPageCount) {
                        if (sourcePageIndex != pageIndex) {
                            copyPageWithAnnotations(
                                document, sourcePageIndex, pdfDocument, outputPageNum,
                                strokes, shapes, textAnnotations
                            )
                            outputPageNum++
                        }
                    }
                    
                    // Write output PDF
                    FileOutputStream(tempOutput).use { output ->
                        pdfDocument.writeTo(output)
                    }
                    pdfDocument.close()
                    
                } finally {
                    document.destroy()
                }
                
                // Replace original file
                replaceOriginalFile(sourceUri, tempOutput)
                
                // Cleanup
                tempInput.delete()
                tempOutput.delete()
                
                PageOperationResult.Success
                
            } catch (e: Exception) {
                android.util.Log.e("PdfAnnotationFlattener", "Error deleting page", e)
                PageOperationResult.Error(e.message ?: "Failed to delete page")
            }
        }
    }
    
    /**
     * Duplicates a page at the specified index and inserts it at the insert index.
     */
    suspend fun duplicatePage(
        sourceUri: Uri,
        pageIndex: Int,
        insertIndex: Int,
        strokes: Map<Int, List<InkStroke>> = emptyMap(),
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
        textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap()
    ): PageOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("PdfAnnotationFlattener", "Duplicating page $pageIndex to index $insertIndex")
                
                // Create temp files
                val tempDir = context.cacheDir
                val tempInput = File(tempDir, "duplicate_page_input_${System.currentTimeMillis()}.pdf")
                val tempOutput = File(tempDir, "duplicate_page_output_${System.currentTimeMillis()}.pdf")
                
                // Copy source to temp
                copyUriToFile(sourceUri, tempInput)
                
                // Open document with MuPDF
                val document = Document.openDocument(tempInput.absolutePath)
                val originalPageCount = document.countPages()
                
                if (pageIndex < 0 || pageIndex >= originalPageCount) {
                    return@withContext PageOperationResult.Error("Page index $pageIndex out of bounds")
                }
                
                val actualInsertIndex = insertIndex.coerceIn(0, originalPageCount)
                
                try {
                    // Create output PDF
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    
                    // Process pages with duplication
                    for (outputPageIndex in 0 until originalPageCount + 1) {
                        val sourcePageIndex = when {
                            outputPageIndex == actualInsertIndex -> pageIndex // Insert duplicate
                            outputPageIndex < actualInsertIndex -> outputPageIndex // Before insert point
                            else -> outputPageIndex - 1 // After insert point, shift back
                        }
                        
                        copyPageWithAnnotations(
                            document, sourcePageIndex, pdfDocument, outputPageIndex + 1,
                            strokes, shapes, textAnnotations
                        )
                    }
                    
                    // Write output PDF
                    FileOutputStream(tempOutput).use { output ->
                        pdfDocument.writeTo(output)
                    }
                    pdfDocument.close()
                    
                } finally {
                    document.destroy()
                }
                
                // Replace original file
                replaceOriginalFile(sourceUri, tempOutput)
                
                // Cleanup
                tempInput.delete()
                tempOutput.delete()
                
                PageOperationResult.Success
                
            } catch (e: Exception) {
                android.util.Log.e("PdfAnnotationFlattener", "Error duplicating page", e)
                PageOperationResult.Error(e.message ?: "Failed to duplicate page")
            }
        }
    }
    
    /**
     * Moves a page from one index to another (reordering).
     */
    suspend fun movePage(
        sourceUri: Uri,
        fromIndex: Int,
        toIndex: Int,
        strokes: Map<Int, List<InkStroke>> = emptyMap(),
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
        textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap()
    ): PageOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("PdfAnnotationFlattener", "Moving page from $fromIndex to $toIndex")
                
                if (fromIndex == toIndex) {
                    return@withContext PageOperationResult.Success
                }
                
                // Create temp files
                val tempDir = context.cacheDir
                val tempInput = File(tempDir, "move_page_input_${System.currentTimeMillis()}.pdf")
                val tempOutput = File(tempDir, "move_page_output_${System.currentTimeMillis()}.pdf")
                
                // Copy source to temp
                copyUriToFile(sourceUri, tempInput)
                
                // Open document with MuPDF
                val document = Document.openDocument(tempInput.absolutePath)
                val pageCount = document.countPages()
                
                if (fromIndex < 0 || fromIndex >= pageCount || toIndex < 0 || toIndex >= pageCount) {
                    return@withContext PageOperationResult.Error("Invalid page indices: from=$fromIndex, to=$toIndex")
                }
                
                try {
                    // Create output PDF
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    
                    // Create new page order
                    val pageOrder = (0 until pageCount).toMutableList()
                    val movedPage = pageOrder.removeAt(fromIndex)
                    pageOrder.add(toIndex, movedPage)
                    
                    // Copy pages in new order
                    pageOrder.forEachIndexed { outputIndex, sourcePageIndex ->
                        copyPageWithAnnotations(
                            document, sourcePageIndex, pdfDocument, outputIndex + 1,
                            strokes, shapes, textAnnotations
                        )
                    }
                    
                    // Write output PDF
                    FileOutputStream(tempOutput).use { output ->
                        pdfDocument.writeTo(output)
                    }
                    pdfDocument.close()
                    
                } finally {
                    document.destroy()
                }
                
                // Replace original file
                replaceOriginalFile(sourceUri, tempOutput)
                
                // Cleanup
                tempInput.delete()
                tempOutput.delete()
                
                PageOperationResult.Success
                
            } catch (e: Exception) {
                android.util.Log.e("PdfAnnotationFlattener", "Error moving page", e)
                PageOperationResult.Error(e.message ?: "Failed to move page")
            }
        }
    }
    
    /**
     * Executes multiple page operations in a single batch.
     * This is more efficient than executing operations one by one as it only reconstructs the PDF once.
     * 
     * @param sourceUri The source PDF URI
     * @param operations List of operations to execute
     * @param strokes Map of page index to list of strokes (for flattening annotations if needed)
     * @param shapes Map of page index to list of shapes (for flattening annotations if needed)
     * @param textAnnotations Map of page index to list of text annotations (for flattening annotations if needed)
     * @param onProgress Callback for progress updates (progress: 0.0-1.0, currentOperation: String)
     * @return Result of the batch operation
     * 
     * Requirements: 5.3, 11.1, 11.2, 11.3, 11.4, 11.5
     */
    suspend fun executeBatchOperations(
        sourceUri: Uri,
        operations: List<com.osnotes.app.domain.model.PageOperation>,
        strokes: Map<Int, List<InkStroke>> = emptyMap(),
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
        textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap(),
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): PageOperationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("PdfAnnotationFlattener", "Starting batch execution with ${operations.size} operations")
                
                if (operations.isEmpty()) {
                    return@withContext PageOperationResult.Success
                }
                
                onProgress(0.1f, "Validating operations...")
                
                // Validate operations
                val pageCount = getPageCount(sourceUri)
                val validation = com.osnotes.app.domain.model.OperationValidator.validate(operations, pageCount)
                
                if (!validation.isValid) {
                    val errorMessages = validation.errors.joinToString("; ") { it.message }
                    return@withContext PageOperationResult.Error("Validation failed: $errorMessages")
                }
                
                onProgress(0.2f, "Normalizing operation indices...")
                
                // Normalize indices
                val normalizedOperations = com.osnotes.app.domain.model.OperationValidator.normalizeIndices(operations, pageCount)
                
                onProgress(0.3f, "Calculating final page order...")
                
                // Calculate final page order
                val finalPageOrder = calculateFinalPageOrder(pageCount, normalizedOperations)
                
                android.util.Log.d("PdfAnnotationFlattener", "Final page order: $finalPageOrder")
                
                onProgress(0.4f, "Opening source document...")
                
                // Create temp files
                val tempDir = context.cacheDir
                val tempInput = File(tempDir, "batch_input_${System.currentTimeMillis()}.pdf")
                val tempOutput = File(tempDir, "batch_output_${System.currentTimeMillis()}.pdf")
                
                try {
                    // Copy source to temp input
                    val inputStream = if (sourceUri.scheme == "content") {
                        context.contentResolver.openInputStream(sourceUri)
                    } else {
                        val path = sourceUri.path ?: sourceUri.toString()
                        File(path).inputStream()
                    }
                    
                    inputStream?.use { input ->
                        FileOutputStream(tempInput).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    onProgress(0.5f, "Processing ${finalPageOrder.size} pages...")
                    
                    // Open source document
                    val document = Document.openDocument(tempInput.absolutePath)
                    
                    try {
                        // Create output PDF
                        val pdfDocument = android.graphics.pdf.PdfDocument()
                        
                        // Process pages in final order
                        val renderScale = 3f // High quality rendering
                        finalPageOrder.forEachIndexed { index, sourcePageIndex ->
                            val progress = 0.5f + (0.4f * (index.toFloat() / finalPageOrder.size))
                            onProgress(progress, "Processing page ${index + 1} of ${finalPageOrder.size}...")
                            
                            // Load and render source page
                            val page = document.loadPage(sourcePageIndex)
                            val bounds = page.bounds
                            val width = bounds.x1.toInt()
                            val height = bounds.y1.toInt()
                            
                            // Create high-resolution bitmap for rendering
                            val bitmapWidth = (width * renderScale).toInt()
                            val bitmapHeight = (height * renderScale).toInt()
                            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                            
                            // Render at high resolution
                            val matrix = Matrix(renderScale)
                            val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.width, bitmap.height)
                            page.run(device, matrix, null)
                            device.close()
                            
                            // Create output page at original size
                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                            val pdfPage = pdfDocument.startPage(pageInfo)
                            val canvas = pdfPage.canvas
                            
                            // Scale down the high-res bitmap to original size for better quality
                            val destRect = android.graphics.Rect(0, 0, width, height)
                            canvas.drawBitmap(bitmap, null, destRect, null)
                            
                            pdfDocument.finishPage(pdfPage)
                            bitmap.recycle()
                            page.destroy()
                        }
                        
                        onProgress(0.9f, "Saving document...")
                        
                        // Write output PDF
                        FileOutputStream(tempOutput).use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                        
                        pdfDocument.close()
                        
                    } finally {
                        document.destroy()
                    }
                    
                    onProgress(0.95f, "Replacing original file...")
                    
                    // Replace original file
                    when {
                        sourceUri.scheme == "content" -> {
                            context.contentResolver.openOutputStream(sourceUri, "wt")?.use { output ->
                                tempOutput.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        sourceUri.scheme == "file" || sourceUri.scheme == null -> {
                            val path = sourceUri.path ?: sourceUri.toString()
                            tempOutput.copyTo(File(path), overwrite = true)
                        }
                        else -> {
                            throw IllegalArgumentException("Unsupported URI scheme: ${sourceUri.scheme}")
                        }
                    }
                    
                    onProgress(1.0f, "Complete!")
                    
                    android.util.Log.d("PdfAnnotationFlattener", "Batch execution completed successfully")
                    
                    PageOperationResult.Success
                    
                } finally {
                    // Clean up temp files
                    tempInput.delete()
                    tempOutput.delete()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PdfAnnotationFlattener", "Error executing batch operations", e)
                PageOperationResult.Error(e.message ?: "Failed to execute batch operations")
            }
        }
    }
    
    /**
     * Helper function to calculate the final page order after applying all operations.
     * 
     * @param originalPageCount The original number of pages before any operations
     * @param normalizedOperations List of normalized operations to apply
     * @return List of page indices in their final order
     */
    private fun calculateFinalPageOrder(
        originalPageCount: Int,
        normalizedOperations: List<com.osnotes.app.domain.model.NormalizedOperation>
    ): List<Int> {
        // Start with original page order
        val pages = (0 until originalPageCount).toMutableList()
        
        // Apply deletes (from end to start to avoid index shifting issues)
        normalizedOperations
            .filter { it.operation is com.osnotes.app.domain.model.PageOperation.Delete }
            .sortedByDescending { it.normalizedIndex }
            .forEach { pages.removeAt(it.normalizedIndex) }
        
        // Apply duplicates
        normalizedOperations
            .filter { it.operation is com.osnotes.app.domain.model.PageOperation.Duplicate }
            .forEach { 
                val sourcePage = pages[it.normalizedIndex]
                val duplicate = it.operation as com.osnotes.app.domain.model.PageOperation.Duplicate
                val insertPosition = if (duplicate.insertAfter) {
                    it.normalizedIndex + 1
                } else {
                    it.normalizedIndex
                }
                pages.add(insertPosition, sourcePage)
            }
        
        // Apply moves
        normalizedOperations
            .filter { it.operation is com.osnotes.app.domain.model.PageOperation.Move }
            .forEach {
                val page = pages.removeAt(it.normalizedIndex)
                pages.add(it.normalizedTargetIndex!!, page)
            }
        
        return pages
    }
    
    /**
     * Helper function to get page count from a PDF.
     */
    private fun getPageCount(sourceUri: Uri): Int {
        // Handle file:// URIs and plain paths
        val filePath = if (sourceUri.scheme == "file" || sourceUri.scheme == null) {
            sourceUri.path ?: sourceUri.toString()
        } else {
            // For content:// URIs, copy to temp file first
            val tempFile = File(context.cacheDir, "temp_count_${System.currentTimeMillis()}.pdf")
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val doc = Document.openDocument(tempFile.absolutePath)
                try {
                    return doc.countPages()
                } finally {
                    doc.destroy()
                    tempFile.delete()
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }
        
        // Open document directly from file path
        val doc = Document.openDocument(filePath)
        try {
            return doc.countPages()
        } finally {
            doc.destroy()
        }
    }
}
