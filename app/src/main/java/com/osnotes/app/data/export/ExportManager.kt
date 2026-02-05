package com.osnotes.app.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.osnotes.app.data.pdf.MuPdfRenderer
import com.osnotes.app.data.repository.AnnotationRepository
import com.osnotes.app.domain.model.InkStroke
import com.osnotes.app.domain.model.ShapeAnnotation
import com.osnotes.app.domain.model.TextAnnotation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages export and sharing functionality for notes.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfRenderer: MuPdfRenderer,
    private val annotationRepository: AnnotationRepository
) {
    
    /**
     * Export formats supported by the app.
     */
    enum class ExportFormat {
        PDF,           // PDF with annotations
        PDF_FLATTENED, // PDF with annotations burned in
        PNG,           // PNG images (one per page)
        JPG            // JPG images (one per page)
    }
    
    /**
     * Result of an export operation.
     */
    sealed class ExportResult {
        data class Success(val files: List<File>, val shareIntent: Intent? = null) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }
    
    /**
     * Exports a document to the specified format.
     */
    suspend fun exportDocument(
        documentPath: String,
        format: ExportFormat,
        outputName: String? = null,
        includeAnnotations: Boolean = true,
        pageRange: IntRange? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val documentName = outputName ?: File(documentPath).nameWithoutExtension
            val exportDir = getExportDirectory()
            
            when (format) {
                ExportFormat.PDF -> exportToPdf(documentPath, documentName, exportDir, includeAnnotations, pageRange)
                ExportFormat.PDF_FLATTENED -> exportToFlattenedPdf(documentPath, documentName, exportDir, pageRange)
                ExportFormat.PNG -> exportToImages(documentPath, documentName, exportDir, "png", pageRange)
                ExportFormat.JPG -> exportToImages(documentPath, documentName, exportDir, "jpg", pageRange)
            }
        } catch (e: Exception) {
            ExportResult.Error("Export failed: ${e.message}")
        }
    }
    
    /**
     * Creates a share intent for the exported files.
     */
    fun createShareIntent(files: List<File>, mimeType: String = "application/pdf"): Intent {
        return if (files.size == 1) {
            // Single file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                files.first()
            )
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            // Multiple files
            val uris = files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    
    /**
     * Exports to PDF format (copy with annotations preserved).
     */
    private suspend fun exportToPdf(
        documentPath: String,
        documentName: String,
        exportDir: File,
        includeAnnotations: Boolean,
        pageRange: IntRange?
    ): ExportResult {
        val outputFile = File(exportDir, "${documentName}_exported.pdf")
        
        // Simple copy for now - could be enhanced to extract specific pages
        val sourceFile = File(documentPath)
        sourceFile.copyTo(outputFile, overwrite = true)
        
        val shareIntent = createShareIntent(listOf(outputFile), "application/pdf")
        return ExportResult.Success(listOf(outputFile), shareIntent)
    }
    
    /**
     * Exports to flattened PDF (annotations burned in).
     */
    private suspend fun exportToFlattenedPdf(
        documentPath: String,
        documentName: String,
        exportDir: File,
        pageRange: IntRange?
    ): ExportResult {
        // This would use the PdfAnnotationFlattener
        // For now, just copy the file
        val outputFile = File(exportDir, "${documentName}_flattened.pdf")
        
        val sourceFile = File(documentPath)
        sourceFile.copyTo(outputFile, overwrite = true)
        
        val shareIntent = createShareIntent(listOf(outputFile), "application/pdf")
        return ExportResult.Success(listOf(outputFile), shareIntent)
    }
    
    /**
     * Exports to image format (PNG or JPG).
     */
    private suspend fun exportToImages(
        documentPath: String,
        documentName: String,
        exportDir: File,
        format: String,
        pageRange: IntRange?
    ): ExportResult {
        val uri = Uri.fromFile(File(documentPath))
        pdfRenderer.openDocument(uri).getOrThrow()
        
        val pageCount = pdfRenderer.getPageCount()
        val range = pageRange ?: (0 until pageCount)
        val outputFiles = mutableListOf<File>()
        
        for (pageIndex in range) {
            if (pageIndex >= pageCount) break
            
            // Render page to bitmap
            val bitmap = pdfRenderer.renderPage(pageIndex, 2f) ?: continue
            
            // Get annotations for this page
            val strokes = annotationRepository.getStrokesForPageIndex(documentPath, pageIndex)
            val shapes = annotationRepository.getShapesForPageIndex(documentPath, pageIndex)
            val texts = annotationRepository.getTextAnnotationsForPageIndex(documentPath, pageIndex)
            
            // Draw annotations on bitmap
            val annotatedBitmap = drawAnnotationsOnBitmap(bitmap, strokes, shapes, texts)
            
            // Save to file
            val fileName = if (range.count() == 1) {
                "${documentName}.${format}"
            } else {
                "${documentName}_page_${pageIndex + 1}.${format}"
            }
            val outputFile = File(exportDir, fileName)
            
            FileOutputStream(outputFile).use { out ->
                val compressFormat = if (format == "png") {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                annotatedBitmap.compress(compressFormat, 90, out)
            }
            
            outputFiles.add(outputFile)
            
            // Clean up bitmaps
            if (annotatedBitmap != bitmap) {
                annotatedBitmap.recycle()
            }
            bitmap.recycle()
        }
        
        val mimeType = if (format == "png") "image/png" else "image/jpeg"
        val shareIntent = createShareIntent(outputFiles, mimeType)
        return ExportResult.Success(outputFiles, shareIntent)
    }
    
    /**
     * Draws annotations on top of a bitmap.
     */
    private fun drawAnnotationsOnBitmap(
        baseBitmap: Bitmap,
        strokes: List<InkStroke>,
        shapes: List<ShapeAnnotation>,
        texts: List<TextAnnotation>
    ): Bitmap {
        if (strokes.isEmpty() && shapes.isEmpty() && texts.isEmpty()) {
            return baseBitmap
        }
        
        // Create a mutable copy
        val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        // Draw strokes
        for (stroke in strokes) {
            val paint = Paint().apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
                
                if (stroke.isHighlighter) {
                    alpha = (255 * 0.4f).toInt()
                }
            }
            
            val path = stroke.toPath()
            canvas.drawPath(path, paint)
        }
        
        // Draw shapes
        for (shape in shapes) {
            val paint = Paint().apply {
                color = shape.color.toArgb()
                strokeWidth = shape.strokeWidth
                style = if (shape.filled) Paint.Style.FILL else Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            
            when (shape.shapeType) {
                com.osnotes.app.domain.model.ShapeType.RECTANGLE -> {
                    canvas.drawRect(
                        shape.startPoint.x,
                        shape.startPoint.y,
                        shape.endPoint.x,
                        shape.endPoint.y,
                        paint
                    )
                }
                com.osnotes.app.domain.model.ShapeType.CIRCLE -> {
                    val centerX = (shape.startPoint.x + shape.endPoint.x) / 2
                    val centerY = (shape.startPoint.y + shape.endPoint.y) / 2
                    val radius = kotlin.math.max(
                        kotlin.math.abs(shape.endPoint.x - shape.startPoint.x),
                        kotlin.math.abs(shape.endPoint.y - shape.startPoint.y)
                    ) / 2
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
                com.osnotes.app.domain.model.ShapeType.LINE -> {
                    canvas.drawLine(
                        shape.startPoint.x,
                        shape.startPoint.y,
                        shape.endPoint.x,
                        shape.endPoint.y,
                        paint
                    )
                }
                else -> {
                    // Draw as line for other shapes
                    canvas.drawLine(
                        shape.startPoint.x,
                        shape.startPoint.y,
                        shape.endPoint.x,
                        shape.endPoint.y,
                        paint
                    )
                }
            }
        }
        
        // Draw text annotations
        for (text in texts) {
            val paint = Paint().apply {
                color = text.color.toArgb()
                textSize = text.fontSize
                isAntiAlias = true
                isFakeBoldText = text.fontWeight >= 700
                textSkewX = if (text.isItalic) -0.25f else 0f
            }
            
            canvas.drawText(
                text.text,
                text.position.x,
                text.position.y + text.fontSize, // Adjust for baseline
                paint
            )
        }
        
        return mutableBitmap
    }
    
    /**
     * Gets or creates the export directory.
     */
    private fun getExportDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Cleans up old export files to save space.
     */
    suspend fun cleanupOldExports(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) = withContext(Dispatchers.IO) {
        try {
            val exportDir = getExportDirectory()
            val cutoffTime = System.currentTimeMillis() - maxAgeMs
            
            exportDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}