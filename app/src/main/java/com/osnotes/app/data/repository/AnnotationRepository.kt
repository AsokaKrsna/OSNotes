package com.osnotes.app.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.osnotes.app.data.database.AnnotationDao
import com.osnotes.app.data.database.AnnotationEntity
import com.osnotes.app.data.database.DocumentDao
import com.osnotes.app.data.database.PageDao
import com.osnotes.app.data.database.PageEntity
import com.osnotes.app.domain.model.InkStroke
import com.osnotes.app.domain.model.StrokePoint
import com.osnotes.app.domain.model.ShapeAnnotation
import com.osnotes.app.domain.model.TextAnnotation
import com.osnotes.app.domain.model.ShapeType
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing annotations.
 */
@Singleton
class AnnotationRepository @Inject constructor(
    private val annotationDao: AnnotationDao,
    private val pageDao: PageDao,
    private val documentDao: DocumentDao
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    /**
     * Get all strokes for a document as a Flow.
     */
    fun getStrokesForDocument(documentUri: String): Flow<List<InkStroke>> {
        return annotationDao.getAnnotationsForDocument(documentUri).map { annotations ->
            annotations.filter { it.type == "ink" }.map { it.toInkStroke() }
        }
    }
    
    /**
     * Get shapes for a document as a Flow.
     */
    fun getShapesForDocument(documentUri: String): Flow<List<ShapeAnnotation>> {
        return annotationDao.getAnnotationsForDocument(documentUri).map { annotations ->
            annotations.filter { it.type == "shape" }.map { it.toShapeAnnotation() }
        }
    }
    
    /**
     * Get text annotations for a document as a Flow.
     */
    fun getTextAnnotationsForDocument(documentUri: String): Flow<List<TextAnnotation>> {
        return annotationDao.getAnnotationsForDocument(documentUri).map { annotations ->
            annotations.filter { it.type == "text" }.map { it.toTextAnnotation() }
        }
    }
    
    /**
     * Get strokes for a specific page.
     */
    fun getStrokesForPage(pageUuid: String): Flow<List<InkStroke>> {
        return annotationDao.getAnnotationsForPage(pageUuid).map { annotations ->
            annotations.filter { it.type == "ink" }.map { it.toInkStroke() }
        }
    }
    
    /**
     * Get shapes for a specific page.
     */
    fun getShapesForPage(pageUuid: String): Flow<List<ShapeAnnotation>> {
        return annotationDao.getAnnotationsForPage(pageUuid).map { annotations ->
            annotations.filter { it.type == "shape" }.map { it.toShapeAnnotation() }
        }
    }
    
    /**
     * Get text annotations for a specific page.
     */
    fun getTextAnnotationsForPage(pageUuid: String): Flow<List<TextAnnotation>> {
        return annotationDao.getAnnotationsForPage(pageUuid).map { annotations ->
            annotations.filter { it.type == "text" }.map { it.toTextAnnotation() }
        }
    }
    
    /**
     * Get strokes for a page by index (synchronous).
     */
    suspend fun getStrokesForPageIndex(documentUri: String, pageIndex: Int): List<InkStroke> {
        return annotationDao.getAnnotationsForPageIndex(documentUri, pageIndex)
            .filter { it.type == "ink" }
            .map { it.toInkStroke() }
    }
    
    /**
     * Get shapes for a page by index (synchronous).
     */
    suspend fun getShapesForPageIndex(documentUri: String, pageIndex: Int): List<ShapeAnnotation> {
        return annotationDao.getAnnotationsForPageIndex(documentUri, pageIndex)
            .filter { it.type == "shape" }
            .map { it.toShapeAnnotation() }
    }
    
    /**
     * Get text annotations for a page by index (synchronous).
     */
    suspend fun getTextAnnotationsForPageIndex(documentUri: String, pageIndex: Int): List<TextAnnotation> {
        return annotationDao.getAnnotationsForPageIndex(documentUri, pageIndex)
            .filter { it.type == "text" }
            .map { it.toTextAnnotation() }
    }
    
    /**
     * Save a stroke to the database.
     */
    suspend fun saveStroke(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int,
        stroke: InkStroke
    ) {
        val entity = stroke.toEntity(documentUri, pageUuid, pageIndex)
        annotationDao.insertAnnotation(entity)
        
        // Update annotation count
        val count = annotationDao.getAnnotationCount(documentUri)
        documentDao.updateAnnotationCount(documentUri, count)
    }
    
    /**
     * Save a shape annotation to the database.
     */
    suspend fun saveShape(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int,
        shape: ShapeAnnotation
    ) {
        val entity = shape.toEntity(documentUri, pageUuid, pageIndex)
        annotationDao.insertAnnotation(entity)
        
        // Update annotation count
        val count = annotationDao.getAnnotationCount(documentUri)
        documentDao.updateAnnotationCount(documentUri, count)
    }
    
    /**
     * Save a text annotation to the database.
     */
    suspend fun saveTextAnnotation(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int,
        textAnnotation: TextAnnotation
    ) {
        val entity = textAnnotation.toEntity(documentUri, pageUuid, pageIndex)
        annotationDao.insertAnnotation(entity)
        
        // Update annotation count
        val count = annotationDao.getAnnotationCount(documentUri)
        documentDao.updateAnnotationCount(documentUri, count)
    }
    
    /**
     * Save multiple strokes.
     */
    suspend fun saveStrokes(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int,
        strokes: List<InkStroke>
    ) {
        val entities = strokes.map { it.toEntity(documentUri, pageUuid, pageIndex) }
        annotationDao.insertAnnotations(entities)
        
        // Update annotation count
        val count = annotationDao.getAnnotationCount(documentUri)
        documentDao.updateAnnotationCount(documentUri, count)
    }
    
    /**
     * Save multiple shapes.
     */
    suspend fun saveShapes(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int,
        shapes: List<ShapeAnnotation>
    ) {
        val entities = shapes.map { it.toEntity(documentUri, pageUuid, pageIndex) }
        annotationDao.insertAnnotations(entities)
        
        // Update annotation count
        val count = annotationDao.getAnnotationCount(documentUri)
        documentDao.updateAnnotationCount(documentUri, count)
    }
    
    /**
     * Save multiple text annotations.
     */
    suspend fun saveTextAnnotations(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int,
        textAnnotations: List<TextAnnotation>
    ) {
        val entities = textAnnotations.map { it.toEntity(documentUri, pageUuid, pageIndex) }
        annotationDao.insertAnnotations(entities)
        
        // Update annotation count
        val count = annotationDao.getAnnotationCount(documentUri)
        documentDao.updateAnnotationCount(documentUri, count)
    }
    
    /**
     * Delete a stroke.
     */
    suspend fun deleteStroke(strokeId: String) {
        annotationDao.deleteAnnotationById(strokeId)
    }
    
    /**
     * Delete a shape.
     */
    suspend fun deleteShape(shapeId: String) {
        annotationDao.deleteAnnotationById(shapeId)
    }
    
    /**
     * Delete a text annotation.
     */
    suspend fun deleteTextAnnotation(textId: String) {
        annotationDao.deleteAnnotationById(textId)
    }
    
    /**
     * Delete all annotations for a page.
     */
    suspend fun deleteAnnotationsForPage(pageUuid: String) {
        annotationDao.deleteAnnotationsForPage(pageUuid)
    }
    
    /**
     * Delete all annotations for a document.
     */
    suspend fun deleteAnnotationsForDocument(documentUri: String) {
        annotationDao.deleteAnnotationsForDocument(documentUri)
    }
    
    /**
     * Get annotation count for a document.
     */
    suspend fun getAnnotationCount(documentUri: String): Int {
        return annotationDao.getAnnotationCount(documentUri)
    }
    
    // ==================== Page Operations ====================
    
    /**
     * Get pages for a document.
     */
    fun getPagesForDocument(documentUri: String): Flow<List<PageEntity>> {
        return pageDao.getPagesForDocument(documentUri)
    }
    
    /**
     * Initialize pages for a new document.
     */
    suspend fun initializePages(documentUri: String, pageCount: Int) {
        val existingPages = pageDao.getPagesForDocument(documentUri)
        
        // Create page entries if they don't exist
        val pages = (0 until pageCount).map { index ->
            PageEntity(
                uuid = java.util.UUID.randomUUID().toString(),
                documentUri = documentUri,
                pageIndex = index
            )
        }
        pageDao.insertPages(pages)
    }
    
    /**
     * Get or create page UUID for a given index.
     */
    suspend fun getOrCreatePageUuid(documentUri: String, pageIndex: Int): String {
        val existing = pageDao.getPageByIndex(documentUri, pageIndex)
        if (existing != null) {
            return existing.uuid
        }
        
        val newPage = PageEntity(
            uuid = java.util.UUID.randomUUID().toString(),
            documentUri = documentUri,
            pageIndex = pageIndex
        )
        pageDao.insertPage(newPage)
        return newPage.uuid
    }
    
    /**
     * Add a new page.
     */
    suspend fun addPage(
        documentUri: String, 
        pageIndex: Int, 
        templateId: String? = null
    ): String {
        val pageUuid = java.util.UUID.randomUUID().toString()
        val page = PageEntity(
            uuid = pageUuid,
            documentUri = documentUri,
            pageIndex = pageIndex,
            templateId = templateId
        )
        pageDao.insertPage(page)
        return pageUuid
    }
    
    /**
     * Delete a page and its annotations.
     */
    suspend fun deletePage(pageUuid: String) {
        annotationDao.deleteAnnotationsForPage(pageUuid)
        pageDao.getPage(pageUuid)?.let { pageDao.deletePage(it) }
    }
    
    /**
     * Reindex pages after add/delete.
     */
    suspend fun reindexPages(documentUri: String, pages: List<Pair<String, Int>>) {
        pages.forEach { (uuid, newIndex) ->
            pageDao.updatePageIndex(uuid, newIndex)
        }
    }
    
    /**
     * Update document URI for all annotations and pages (for file rename).
     */
    suspend fun updateDocumentUri(oldUri: String, newUri: String) {
        annotationDao.updateDocumentUri(oldUri, newUri)
        pageDao.updateDocumentUri(oldUri, newUri)
        documentDao.updateDocumentUri(oldUri, newUri)
    }
    
    // ==================== Conversion Helpers ====================
    
    private fun InkStroke.toEntity(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int
    ): AnnotationEntity {
        val pointsData = StrokeData(
            points = points.map { SerializablePoint(it.x, it.y, it.pressure, it.timestamp) }
        )
        
        return AnnotationEntity(
            id = id,
            documentUri = documentUri,
            pageUuid = pageUuid,
            pageIndex = pageIndex,
            type = "ink",
            data = json.encodeToString(pointsData),
            color = color.toArgb(),
            strokeWidth = strokeWidth,
            isHighlighter = isHighlighter
        )
    }
    
    private fun ShapeAnnotation.toEntity(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int
    ): AnnotationEntity {
        val shapeData = ShapeData(
            shapeType = shapeType.name,
            startX = startPoint.x,
            startY = startPoint.y,
            endX = endPoint.x,
            endY = endPoint.y,
            filled = filled
        )
        
        return AnnotationEntity(
            id = id,
            documentUri = documentUri,
            pageUuid = pageUuid,
            pageIndex = pageIndex,
            type = "shape",
            data = json.encodeToString(shapeData),
            color = color.toArgb(),
            strokeWidth = strokeWidth,
            isHighlighter = false
        )
    }
    
    private fun TextAnnotation.toEntity(
        documentUri: String,
        pageUuid: String,
        pageIndex: Int
    ): AnnotationEntity {
        val textData = TextData(
            text = text,
            x = position.x,
            y = position.y,
            fontSize = fontSize,
            fontWeight = fontWeight,
            isItalic = isItalic,
            width = width,
            rotation = rotation
        )
        
        return AnnotationEntity(
            id = id,
            documentUri = documentUri,
            pageUuid = pageUuid,
            pageIndex = pageIndex,
            type = "text",
            data = json.encodeToString(textData),
            color = color.toArgb(),
            strokeWidth = 0f, // Not applicable for text
            isHighlighter = false
        )
    }
    
    private fun AnnotationEntity.toInkStroke(): InkStroke {
        val pointsData = try {
            json.decodeFromString<StrokeData>(data)
        } catch (e: Exception) {
            StrokeData(emptyList())
        }
        
        return InkStroke(
            id = id,
            points = pointsData.points.map { 
                StrokePoint(it.x, it.y, it.pressure, it.timestamp) 
            },
            color = Color(color),
            strokeWidth = strokeWidth,
            isHighlighter = isHighlighter,
            pageNumber = pageIndex
        )
    }
    
    private fun AnnotationEntity.toShapeAnnotation(): ShapeAnnotation {
        val shapeData = try {
            json.decodeFromString<ShapeData>(data)
        } catch (e: Exception) {
            ShapeData("RECTANGLE", 0f, 0f, 100f, 100f, false)
        }
        
        return ShapeAnnotation(
            id = id,
            shapeType = ShapeType.valueOf(shapeData.shapeType),
            startPoint = Offset(shapeData.startX, shapeData.startY),
            endPoint = Offset(shapeData.endX, shapeData.endY),
            color = Color(color),
            strokeWidth = strokeWidth,
            filled = shapeData.filled,
            pageNumber = pageIndex
        )
    }
    
    private fun AnnotationEntity.toTextAnnotation(): TextAnnotation {
        val textData = try {
            json.decodeFromString<TextData>(data)
        } catch (e: Exception) {
            TextData("", 0f, 0f, 16f, 400, false, 200f, 0f)
        }
        
        return TextAnnotation(
            id = id,
            text = textData.text,
            position = Offset(textData.x, textData.y),
            color = Color(color),
            fontSize = textData.fontSize,
            fontWeight = textData.fontWeight,
            isItalic = textData.isItalic,
            pageNumber = pageIndex,
            width = textData.width,
            rotation = textData.rotation
        )
    }
}

@Serializable
private data class StrokeData(
    val points: List<SerializablePoint>
)

@Serializable
private data class SerializablePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timestamp: Long
)

@Serializable
private data class ShapeData(
    val shapeType: String,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val filled: Boolean
)

@Serializable
private data class TextData(
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val fontWeight: Int,
    val isItalic: Boolean,
    val width: Float,
    val rotation: Float
)
