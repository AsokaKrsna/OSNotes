package com.osnotes.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room entity for storing annotations.
 */
@Entity(
    tableName = "annotations",
    indices = [
        Index(value = ["document_uri"]),
        Index(value = ["page_uuid"])
    ]
)
data class AnnotationEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "document_uri")
    val documentUri: String,
    
    @ColumnInfo(name = "page_uuid")
    val pageUuid: String,
    
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    
    @ColumnInfo(name = "type")
    val type: String, // "ink", "text", "shape"
    
    @ColumnInfo(name = "data")
    val data: String, // JSON serialized stroke/text/shape data
    
    @ColumnInfo(name = "color")
    val color: Int,
    
    @ColumnInfo(name = "stroke_width")
    val strokeWidth: Float,
    
    @ColumnInfo(name = "is_highlighter")
    val isHighlighter: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for storing page information.
 */
@Entity(
    tableName = "pages",
    indices = [Index(value = ["document_uri"])]
)
data class PageEntity(
    @PrimaryKey
    val uuid: String,
    
    @ColumnInfo(name = "document_uri")
    val documentUri: String,
    
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    
    @ColumnInfo(name = "template_id")
    val templateId: String? = null,
    
    @ColumnInfo(name = "width")
    val width: Float = 595f,
    
    @ColumnInfo(name = "height")
    val height: Float = 842f,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for storing document metadata.
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val uri: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "page_count")
    val pageCount: Int,
    
    @ColumnInfo(name = "last_opened")
    val lastOpened: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_modified")
    val lastModified: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "annotation_count")
    val annotationCount: Int = 0,
    
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null
)

/**
 * DAO for annotation operations.
 */
@Dao
interface AnnotationDao {
    
    @Query("SELECT * FROM annotations WHERE document_uri = :documentUri ORDER BY page_index, created_at")
    fun getAnnotationsForDocument(documentUri: String): Flow<List<AnnotationEntity>>
    
    @Query("SELECT * FROM annotations WHERE page_uuid = :pageUuid ORDER BY created_at")
    fun getAnnotationsForPage(pageUuid: String): Flow<List<AnnotationEntity>>
    
    @Query("SELECT * FROM annotations WHERE document_uri = :documentUri AND page_index = :pageIndex ORDER BY created_at")
    suspend fun getAnnotationsForPageIndex(documentUri: String, pageIndex: Int): List<AnnotationEntity>
    
    @Query("SELECT COUNT(*) FROM annotations WHERE document_uri = :documentUri")
    suspend fun getAnnotationCount(documentUri: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<AnnotationEntity>)
    
    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)
    
    @Query("DELETE FROM annotations WHERE id = :annotationId")
    suspend fun deleteAnnotationById(annotationId: String)
    
    @Query("DELETE FROM annotations WHERE page_uuid = :pageUuid")
    suspend fun deleteAnnotationsForPage(pageUuid: String)
    
    @Query("DELETE FROM annotations WHERE document_uri = :documentUri")
    suspend fun deleteAnnotationsForDocument(documentUri: String)
    
    @Query("UPDATE annotations SET document_uri = :newUri WHERE document_uri = :oldUri")
    suspend fun updateDocumentUri(oldUri: String, newUri: String)
    
    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)
}

/**
 * DAO for page operations.
 */
@Dao
interface PageDao {
    
    @Query("SELECT * FROM pages WHERE document_uri = :documentUri ORDER BY page_index")
    fun getPagesForDocument(documentUri: String): Flow<List<PageEntity>>
    
    @Query("SELECT * FROM pages WHERE uuid = :uuid")
    suspend fun getPage(uuid: String): PageEntity?
    
    @Query("SELECT * FROM pages WHERE document_uri = :documentUri AND page_index = :pageIndex")
    suspend fun getPageByIndex(documentUri: String, pageIndex: Int): PageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)
    
    @Delete
    suspend fun deletePage(page: PageEntity)
    
    @Query("DELETE FROM pages WHERE document_uri = :documentUri")
    suspend fun deletePagesForDocument(documentUri: String)
    
    @Query("UPDATE pages SET document_uri = :newUri WHERE document_uri = :oldUri")
    suspend fun updateDocumentUri(oldUri: String, newUri: String)
    
    @Update
    suspend fun updatePage(page: PageEntity)
    
    @Query("UPDATE pages SET page_index = :newIndex WHERE uuid = :uuid")
    suspend fun updatePageIndex(uuid: String, newIndex: Int)
}

/**
 * DAO for document operations.
 */
@Dao
interface DocumentDao {
    
    @Query("SELECT * FROM documents ORDER BY last_opened DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE is_favorite = 1 ORDER BY last_opened DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents ORDER BY last_opened DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int = 10): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE uri = :uri")
    suspend fun getDocument(uri: String): DocumentEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)
    
    @Update
    suspend fun updateDocument(document: DocumentEntity)
    
    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
    
    @Query("UPDATE documents SET last_opened = :timestamp WHERE uri = :uri")
    suspend fun updateLastOpened(uri: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE documents SET is_favorite = :isFavorite WHERE uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)
    
    @Query("UPDATE documents SET annotation_count = :count WHERE uri = :uri")
    suspend fun updateAnnotationCount(uri: String, count: Int)
    
    @Query("UPDATE documents SET uri = :newUri WHERE uri = :oldUri")
    suspend fun updateDocumentUri(oldUri: String, newUri: String)
}

/**
 * Room database for OSNotes.
 */
@Database(
    entities = [
        AnnotationEntity::class,
        PageEntity::class,
        DocumentEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OSNotesDatabase : RoomDatabase() {
    abstract fun annotationDao(): AnnotationDao
    abstract fun pageDao(): PageDao
    abstract fun documentDao(): DocumentDao
}
