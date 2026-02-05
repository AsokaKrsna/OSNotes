package com.osnotes.app.data.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.osnotes.app.data.models.NoteInfo
import com.osnotes.app.data.models.FolderInfo
import com.osnotes.app.data.pdf.MuPdfRenderer
import com.osnotes.app.data.repository.CustomTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages tracked folders and file operations.
 * Uses simple file paths with MANAGE_EXTERNAL_STORAGE permission.
 */
@Singleton
class StorageManager @Inject constructor(
    private val context: Context,
    private val pdfRenderer: MuPdfRenderer,
    private val customTemplateRepository: CustomTemplateRepository,
    private val annotationRepository: com.osnotes.app.data.repository.AnnotationRepository
) {
    companion object {
        private const val PREFS_NAME = "osnotes_storage"
        private const val KEY_TRACKED_FOLDERS = "tracked_folders"
        private const val KEY_RECENT_NOTES = "recent_notes"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_TOOLBAR_POSITION = "toolbar_position"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get toolbar position setting
     */
    fun getToolbarPosition(): String {
        return prefs.getString(KEY_TOOLBAR_POSITION, "right") ?: "right"
    }
    
    /**
     * Set toolbar position setting
     */
    fun setToolbarPosition(position: String) {
        prefs.edit().putString(KEY_TOOLBAR_POSITION, position).apply()
        _foldersChanged.value = System.currentTimeMillis() // Reuse trigger or add new one
    }
    
    // Used to notify observers when folders change
    private val _foldersChanged = MutableStateFlow(0L)
    val foldersChanged: StateFlow<Long> = _foldersChanged.asStateFlow()
    
    // Callback for requesting folder selection from Activity
    var onRequestFolderSelection: (() -> Unit)? = null
    
    fun requestFolderSelection() {
        onRequestFolderSelection?.invoke()
    }
    
    /**
     * Check if we have all files access permission (Android 11+)
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
    
    /**
     * Get all tracked folder paths
     */
    fun getTrackedFolders(): Set<String> {
        return prefs.getStringSet(KEY_TRACKED_FOLDERS, emptySet()) ?: emptySet()
    }
    
    /**
     * Add a folder to tracking
     */
    suspend fun addTrackedFolder(path: String) {
        val folders = getTrackedFolders().toMutableSet()
        folders.add(path)
        prefs.edit().putStringSet(KEY_TRACKED_FOLDERS, folders).apply()
        _foldersChanged.value = System.currentTimeMillis()
    }
    
    /**
     * Add a folder from URI (for folder picker compatibility)
     */
    suspend fun addTrackedFolderFromUri(uri: Uri) {
        val path = getPathFromUri(uri)
        if (path != null && File(path).exists()) {
            addTrackedFolder(path)
        }
    }
    
    /**
     * Remove a folder from tracking
     */
    suspend fun removeTrackedFolder(path: String) {
        val folders = getTrackedFolders().toMutableSet()
        folders.remove(path)
        prefs.edit().putStringSet(KEY_TRACKED_FOLDERS, folders).apply()
        _foldersChanged.value = System.currentTimeMillis()
    }
    
    /**
     * Check if any folders are being tracked
     */
    fun hasTrackedFolders(): Boolean = getTrackedFolders().isNotEmpty()
    
    /**
     * Try to get file path from URI
     */
    private fun getPathFromUri(uri: Uri): String? {
        // For file:// URIs
        if (uri.scheme == "file") {
            return uri.path
        }
        
        // For content:// URIs from file pickers
        if (uri.scheme == "content") {
            val path = uri.path
            if (path != null) {
                // Handle patterns like /tree/primary:folder or /document/primary:folder
                val colonIndex = path.indexOf(':')
                if (colonIndex != -1) {
                    val relativePath = path.substring(colonIndex + 1)
                    val externalStorage = Environment.getExternalStorageDirectory()
                    val file = File(externalStorage, relativePath)
                    if (file.exists()) {
                        return file.absolutePath
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Get tracked folders as FolderInfo list
     */
    suspend fun getTrackedFoldersInfo(): List<FolderInfo> = withContext(Dispatchers.IO) {
        getTrackedFolders().mapNotNull { path ->
            val folder = File(path)
            if (folder.exists() && folder.isDirectory) {
                val pdfCount = folder.listFiles()?.count { 
                    it.isFile && it.extension.equals("pdf", ignoreCase = true) 
                } ?: 0
                
                FolderInfo(
                    name = folder.name,
                    path = folder.absolutePath,
                    noteCount = pdfCount,
                    lastModified = folder.lastModified()
                )
            } else null
        }.sortedBy { it.name.lowercase() }
    }
    
    /**
     * List all subfolders in the given folder path
     */
    suspend fun listFolders(folderPath: String): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        
        if (!folder.exists() || !folder.isDirectory) {
            return@withContext emptyList()
        }
        
        folder.listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.map { dir ->
                val pdfCount = dir.listFiles()?.count { 
                    it.isFile && it.extension.equals("pdf", ignoreCase = true) 
                } ?: 0
                
                FolderInfo(
                    name = dir.name,
                    path = dir.absolutePath,
                    noteCount = pdfCount,
                    lastModified = dir.lastModified()
                )
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }
    
    /**
     * List all PDF notes in the given folder path
     */
    suspend fun listNotes(folderPath: String): List<NoteInfo> = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        
        if (!folder.exists() || !folder.isDirectory) {
            return@withContext emptyList()
        }
        
        val favoritesList = getFavoritesList()
        
        folder.listFiles()
            ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) && !it.isHidden }
            ?.map { file ->
                NoteInfo(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    pageCount = getPageCount(file),
                    lastModified = file.lastModified(),
                    isFavorite = favoritesList.contains(file.absolutePath)
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }
    
    /**
     * Get favorites list
     */
    fun getFavoritesList(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }
    
    /**
     * Get recent notes list
     */
    fun getRecentList(): Set<String> {
        return prefs.getStringSet(KEY_RECENT_NOTES, emptySet()) ?: emptySet()
    }
    
    /**
     * Get favorite notes as NoteInfo list
     */
    suspend fun getFavorites(): List<NoteInfo> = withContext(Dispatchers.IO) {
        val favoritePaths = getFavoritesList()
        favoritePaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists() && file.isFile) {
                NoteInfo(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    pageCount = getPageCount(file),
                    lastModified = file.lastModified(),
                    isFavorite = true
                )
            } else null
        }.sortedByDescending { it.lastModified }
    }
    
    /**
     * Get recent notes as NoteInfo list
     */
    suspend fun getRecentNotes(): List<NoteInfo> = withContext(Dispatchers.IO) {
        val recentPaths = getRecentList()
        val favoritesList = getFavoritesList()
        recentPaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists() && file.isFile) {
                NoteInfo(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    pageCount = getPageCount(file),
                    lastModified = file.lastModified(),
                    isFavorite = favoritesList.contains(path)
                )
            } else null
        }.sortedByDescending { it.lastModified }
    }
    
    /**
     * Add a note to recent list
     */
    fun addToRecent(notePath: String) {
        val recent = getRecentList().toMutableList()
        recent.remove(notePath)
        recent.add(notePath)
        val trimmed = recent.takeLast(20).toSet()
        prefs.edit().putStringSet(KEY_RECENT_NOTES, trimmed).apply()
    }
    
    /**
     * Toggle favorite status for a note
     */
    fun toggleFavorite(notePath: String) {
        val favorites = getFavoritesList().toMutableSet()
        if (favorites.contains(notePath)) {
            favorites.remove(notePath)
        } else {
            favorites.add(notePath)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }
    
    /**
     * Create a new PDF note in the specified folder with a template.
     * @param name Name of the note
     * @param folderPath Path to the folder
     * @param template Template name (BLANK, LINED, GRID, DOTTED, CORNELL, ISOMETRIC, MUSIC, ENGINEERING)
     */
    suspend fun createNote(name: String, folderPath: String, template: String = "BLANK"): String? = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        
        if (!folder.exists()) {
            folder.mkdirs()
        }
        
        val fileName = if (name.endsWith(".pdf")) name else "$name.pdf"
        val newFile = File(folder, fileName)
        
        try {
            // Check if this is a custom template ID
            android.util.Log.d("StorageManager", "Looking up template: '$template'")
            val customTemplate = customTemplateRepository.getCustomTemplateById(template)
            android.util.Log.d("StorageManager", "Found custom template: ${customTemplate?.name ?: "null"} (id=${customTemplate?.id})")
            
            if (customTemplate != null) {
                // Create PDF with custom template
                android.util.Log.d("StorageManager", "Creating PDF with custom template: ${customTemplate.name}, patternType=${customTemplate.patternType}")
                pdfRenderer.createCustomTemplatePdf(Uri.fromFile(newFile), 595f, 842f, customTemplate)
            } else {
                // Create PDF with predefined template
                android.util.Log.d("StorageManager", "Creating PDF with predefined template: $template")
                pdfRenderer.createTemplatePdf(Uri.fromFile(newFile), 595f, 842f, template)
            }
            newFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error creating note", e)
            null
        }
    }
    
    /**
     * Create a new folder
     */
    suspend fun createFolder(name: String, parentPath: String): String? = withContext(Dispatchers.IO) {
        val parentFolder = File(parentPath)
        
        if (!parentFolder.exists()) {
            parentFolder.mkdirs()
        }
        
        val newFolder = File(parentFolder, name)
        if (newFolder.mkdirs()) {
            newFolder.absolutePath
        } else {
            null
        }
    }
    
    /**
     * Get page count for a PDF file
     */
    private suspend fun getPageCount(file: File): Int {
        return try {
            pdfRenderer.getPageCount(Uri.fromFile(file))
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get annotation count for a document
     */
    suspend fun getAnnotationCount(documentPath: String): Int {
        return try {
            annotationRepository.getAnnotationCount(documentPath)
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Delete a note file
     */
    suspend fun deleteNote(notePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(notePath)
            if (file.exists() && file.isFile) {
                // Remove from favorites and recent if present
                val favorites = getFavoritesList().toMutableSet()
                favorites.remove(notePath)
                prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
                
                val recent = getRecentList().toMutableSet()
                recent.remove(notePath)
                prefs.edit().putStringSet(KEY_RECENT_NOTES, recent).apply()
                
                // Delete the file
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error deleting note", e)
            false
        }
    }
    
    /**
     * Delete a folder (must be empty)
     */
    suspend fun deleteFolder(folderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                // Check if folder is empty
                val files = folder.listFiles()
                if (files.isNullOrEmpty()) {
                    folder.delete()
                } else {
                    false // Folder not empty
                }
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error deleting folder", e)
            false
        }
    }
    
    /**
     * Rename a note file
     */
    suspend fun renameNote(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(oldPath)
            if (!oldFile.exists() || !oldFile.isFile) return@withContext false
            
            val fileName = if (newName.endsWith(".pdf")) newName else "$newName.pdf"
            val newFile = File(oldFile.parent, fileName)
            
            if (newFile.exists()) return@withContext false // Name already exists
            
            val renamed = oldFile.renameTo(newFile)
            
            if (renamed) {
                // Update database annotations and pages
                annotationRepository.updateDocumentUri(oldPath, newFile.absolutePath)
                
                // Update favorites if present
                val favorites = getFavoritesList().toMutableSet()
                if (favorites.contains(oldPath)) {
                    favorites.remove(oldPath)
                    favorites.add(newFile.absolutePath)
                    prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
                }
                
                // Update recent if present
                val recent = getRecentList().toMutableList()
                val index = recent.indexOf(oldPath)
                if (index != -1) {
                    recent[index] = newFile.absolutePath
                    prefs.edit().putStringSet(KEY_RECENT_NOTES, recent.toSet()).apply()
                }
            }
            
            renamed
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error renaming note", e)
            false
        }
    }
    
    /**
     * Rename a folder
     */
    suspend fun renameFolder(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldFolder = File(oldPath)
            if (!oldFolder.exists() || !oldFolder.isDirectory) return@withContext false
            
            val newFolder = File(oldFolder.parent, newName)
            
            if (newFolder.exists()) return@withContext false // Name already exists
            
            oldFolder.renameTo(newFolder)
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Error renaming folder", e)
            false
        }
    }
}
