package com.osnotes.app.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osnotes.app.data.pdf.MuPdfRenderer
import com.osnotes.app.data.repository.CustomTemplateRepository
import com.osnotes.app.domain.model.BatchModeState
import com.osnotes.app.domain.model.CustomTemplate
import com.osnotes.app.domain.model.PageInfo
import com.osnotes.app.ui.screens.PageLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class PageManagerUiState(
    val documentPath: String = "",
    val pages: List<PageInfo> = emptyList(),
    val selectedPages: Set<Int> = emptySet(),
    val currentPageIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val customTemplates: List<CustomTemplate> = emptyList(),
    val isDragging: Boolean = false,
    val draggedPageIndex: Int? = null,
    val batchMode: BatchModeState = BatchModeState()
)

@HiltViewModel
class PageManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfRenderer: MuPdfRenderer,
    private val pdfAnnotationFlattener: com.osnotes.app.data.pdf.PdfAnnotationFlattener,
    private val customTemplateRepository: CustomTemplateRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PageManagerUiState())
    val uiState: StateFlow<PageManagerUiState> = _uiState.asStateFlow()
    
    // Thumbnail cache
    private val thumbnailCache = mutableMapOf<Int, Bitmap?>()
    
    init {
        // Collect custom templates
        viewModelScope.launch {
            customTemplateRepository.customTemplates.collect { templates ->
                _uiState.update { it.copy(customTemplates = templates) }
            }
        }
    }
    
    fun loadDocument(documentPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, documentPath = documentPath) }
            
            try {
                val uri = Uri.parse(documentPath)
                
                withContext(Dispatchers.IO) {
                    pdfRenderer.openDocument(uri)
                }
                
                val pageCount = pdfRenderer.getPageCount()
                
                // Create page info for each page
                val pages = (0 until pageCount).map { index ->
                    PageInfo(
                        id = UUID.randomUUID().toString(),
                        index = index
                    )
                }
                
                _uiState.update {
                    it.copy(
                        pages = pages,
                        isLoading = false
                    )
                }
                
                // Start loading thumbnails
                loadThumbnails(pageCount)
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    private fun loadThumbnails(pageCount: Int) {
        viewModelScope.launch {
            for (i in 0 until pageCount) {
                if (!thumbnailCache.containsKey(i)) {
                    val thumbnail = withContext(Dispatchers.IO) {
                        pdfRenderer.renderPage(i, 200, 280) // Small thumbnail size
                    }
                    thumbnailCache[i] = thumbnail
                }
            }
        }
    }
    
    fun getPageThumbnail(pageIndex: Int): Bitmap? {
        return thumbnailCache[pageIndex]
    }
    
    fun togglePageSelection(pageIndex: Int) {
        val currentSelection = _uiState.value.selectedPages.toMutableSet()
        if (currentSelection.contains(pageIndex)) {
            currentSelection.remove(pageIndex)
        } else {
            currentSelection.add(pageIndex)
        }
        _uiState.update { it.copy(selectedPages = currentSelection) }
    }
    
    fun clearSelection() {
        _uiState.update { it.copy(selectedPages = emptySet()) }
    }
    
    fun setDragState(isDragging: Boolean, draggedPageIndex: Int? = null) {
        _uiState.update { 
            it.copy(
                isDragging = isDragging, 
                draggedPageIndex = draggedPageIndex
            ) 
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun addPage(templateId: String, location: PageLocation) {
        viewModelScope.launch {
            try {
                val currentPages = _uiState.value.pages.toMutableList()
                val insertIndex = when (location) {
                    PageLocation.START -> 0
                    PageLocation.CURRENT -> _uiState.value.currentPageIndex + 1
                    PageLocation.END -> currentPages.size
                }
                
                // Add page to PDF using MuPDF
                pdfRenderer.addPage(insertIndex, templateId).getOrThrow()
                
                // Create new page info
                val newPage = PageInfo(
                    id = UUID.randomUUID().toString(),
                    index = insertIndex,
                    templateId = templateId
                )
                
                // Insert and reindex
                currentPages.add(insertIndex, newPage)
                val reindexedPages = currentPages.mapIndexed { index, page ->
                    page.copy(index = index)
                }
                
                _uiState.update { it.copy(pages = reindexedPages) }
                
                // Clear thumbnail cache for affected pages
                for (i in insertIndex until thumbnailCache.size) {
                    thumbnailCache.remove(i)
                }
                
                // Reload thumbnails
                loadThumbnails(reindexedPages.size)
                
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deletePage(pageIndex: Int) {
        viewModelScope.launch {
            try {
                val currentPages = _uiState.value.pages.toMutableList()
                
                if (currentPages.size <= 1) {
                    _uiState.update { it.copy(error = "Cannot delete the only page") }
                    return@launch
                }
                
                // Delete page from PDF using PdfAnnotationFlattener
                val documentUri = Uri.parse(_uiState.value.documentPath)
                val result = pdfAnnotationFlattener.deletePage(documentUri, pageIndex)
                
                when (result) {
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Success -> {
                        // Reload the document in the PDF renderer to reflect changes
                        val documentUri = Uri.parse(_uiState.value.documentPath)
                        withContext(Dispatchers.IO) {
                            pdfRenderer.openDocument(documentUri)
                        }
                        
                        currentPages.removeAt(pageIndex)
                        
                        // Reindex remaining pages
                        val reindexedPages = currentPages.mapIndexed { index, page ->
                            page.copy(index = index)
                        }
                        
                        // Clear thumbnail cache for affected pages
                        for (i in pageIndex until thumbnailCache.size) {
                            thumbnailCache.remove(i)
                        }
                        
                        _uiState.update { it.copy(pages = reindexedPages, error = null) }
                        
                        // Reload thumbnails
                        loadThumbnails(reindexedPages.size)
                    }
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Error -> {
                        _uiState.update { it.copy(error = result.message) }
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun duplicatePage(pageIndex: Int) {
        viewModelScope.launch {
            try {
                val currentPages = _uiState.value.pages.toMutableList()
                val originalPage = currentPages[pageIndex]
                
                // Duplicate page in PDF using PdfAnnotationFlattener
                val documentUri = Uri.parse(_uiState.value.documentPath)
                val result = pdfAnnotationFlattener.duplicatePage(documentUri, pageIndex, pageIndex + 1)
                
                when (result) {
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Success -> {
                        // Reload the document in the PDF renderer to reflect changes
                        val documentUri = Uri.parse(_uiState.value.documentPath)
                        withContext(Dispatchers.IO) {
                            pdfRenderer.openDocument(documentUri)
                        }
                        
                        // Create duplicate page info after original
                        val duplicatePage = PageInfo(
                            id = UUID.randomUUID().toString(),
                            index = pageIndex + 1,
                            templateId = originalPage.templateId
                        )
                        
                        currentPages.add(pageIndex + 1, duplicatePage)
                        
                        // Reindex
                        val reindexedPages = currentPages.mapIndexed { index, page ->
                            page.copy(index = index)
                        }
                        
                        _uiState.update { it.copy(pages = reindexedPages, error = null) }
                        
                        // Copy thumbnail and clear cache for affected pages
                        thumbnailCache[pageIndex]?.let { original ->
                            thumbnailCache[pageIndex + 1] = original.copy(original.config, true)
                        }
                        for (i in pageIndex + 2 until thumbnailCache.size + 1) {
                            thumbnailCache.remove(i)
                        }
                        
                        // Reload thumbnails
                        loadThumbnails(reindexedPages.size)
                    }
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Error -> {
                        _uiState.update { it.copy(error = result.message) }
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun reorderPages(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            try {
                val currentPages = _uiState.value.pages.toMutableList()
                
                if (fromIndex == toIndex) return@launch
                
                // Bounds checking
                val validToIndex = toIndex.coerceIn(0, currentPages.size - 1)
                if (fromIndex < 0 || fromIndex >= currentPages.size) return@launch
                
                android.util.Log.d("PageManager", "Reordering page from $fromIndex to $validToIndex")
                
                // Set loading state
                _uiState.update { it.copy(error = null) }
                
                // Move page in PDF using PdfAnnotationFlattener
                val documentUri = Uri.parse(_uiState.value.documentPath)
                val result = pdfAnnotationFlattener.movePage(documentUri, fromIndex, validToIndex)
                
                when (result) {
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Success -> {
                        android.util.Log.d("PageManager", "PDF page move successful")
                        
                        // Update UI state first (optimistic update)
                        val page = currentPages.removeAt(fromIndex)
                        currentPages.add(validToIndex, page)
                        
                        // Reindex
                        val reindexedPages = currentPages.mapIndexed { index, p ->
                            p.copy(index = index)
                        }
                        
                        _uiState.update { it.copy(pages = reindexedPages, error = null) }
                        
                        // Reload document and thumbnails in background
                        withContext(Dispatchers.IO) {
                            try {
                                pdfRenderer.openDocument(documentUri)
                                android.util.Log.d("PageManager", "Document reloaded successfully")
                            } catch (e: Exception) {
                                android.util.Log.e("PageManager", "Failed to reload document", e)
                            }
                        }
                        
                        // Clear and reload thumbnails
                        thumbnailCache.clear()
                        loadThumbnails(reindexedPages.size)
                        
                    }
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Error -> {
                        android.util.Log.e("PageManager", "PDF page move failed: ${result.message}")
                        _uiState.update { it.copy(error = result.message) }
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PageManager", "Exception during page reorder", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    // Batch mode management methods
    
    /**
     * Enters batch mode, allowing operations to be queued without immediate execution.
     * Requirements: 1.2, 1.3
     */
    fun enterBatchMode() {
        _uiState.update { 
            it.copy(
                batchMode = it.batchMode.copy(isActive = true)
            )
        }
    }
    
    /**
     * Exits batch mode and clears all queued operations.
     * Requirements: 7.1, 7.3
     */
    fun exitBatchMode() {
        _uiState.update { 
            it.copy(
                batchMode = BatchModeState() // Reset to default state
            )
        }
    }
    
    /**
     * Returns whether batch mode is currently active.
     * @return true if batch mode is active, false otherwise
     */
    fun isBatchModeActive(): Boolean {
        return _uiState.value.batchMode.isActive
    }
    
    /**
     * Queues a delete operation for the specified page.
     * Requirements: 2.1, 12.1, 12.2
     */
    fun queueDeleteOperation(pageIndex: Int) {
        val currentBatchMode = _uiState.value.batchMode
        val pageCount = _uiState.value.pages.size
        
        // Validate: cannot delete last page
        if (pageCount <= 1) {
            _uiState.update { it.copy(error = "Cannot delete the only page") }
            return
        }
        
        // Validate: page index in bounds
        if (pageIndex < 0 || pageIndex >= pageCount) {
            _uiState.update { it.copy(error = "Invalid page index: $pageIndex") }
            return
        }
        
        val operation = com.osnotes.app.domain.model.PageOperation.Delete(
            originalPageIndex = pageIndex
        )
        
        val updatedOperations = currentBatchMode.operations + operation
        _uiState.update {
            it.copy(
                batchMode = currentBatchMode.copy(operations = updatedOperations),
                error = null
            )
        }
    }
    
    /**
     * Queues a duplicate operation for the specified page.
     * Requirements: 2.2
     */
    fun queueDuplicateOperation(pageIndex: Int) {
        val currentBatchMode = _uiState.value.batchMode
        val pageCount = _uiState.value.pages.size
        
        // Validate: page index in bounds
        if (pageIndex < 0 || pageIndex >= pageCount) {
            _uiState.update { it.copy(error = "Invalid page index: $pageIndex") }
            return
        }
        
        val operation = com.osnotes.app.domain.model.PageOperation.Duplicate(
            originalPageIndex = pageIndex
        )
        
        val updatedOperations = currentBatchMode.operations + operation
        _uiState.update {
            it.copy(
                batchMode = currentBatchMode.copy(operations = updatedOperations),
                error = null
            )
        }
    }
    
    /**
     * Queues a move operation for the specified page.
     * Requirements: 2.3
     */
    fun queueMoveOperation(fromIndex: Int, toIndex: Int) {
        val currentBatchMode = _uiState.value.batchMode
        val pageCount = _uiState.value.pages.size
        
        // Validate: page indices in bounds
        if (fromIndex < 0 || fromIndex >= pageCount || toIndex < 0 || toIndex >= pageCount) {
            _uiState.update { it.copy(error = "Invalid page indices") }
            return
        }
        
        if (fromIndex == toIndex) {
            return // No-op
        }
        
        val operation = com.osnotes.app.domain.model.PageOperation.Move(
            originalPageIndex = fromIndex,
            targetIndex = toIndex
        )
        
        val updatedOperations = currentBatchMode.operations + operation
        _uiState.update {
            it.copy(
                batchMode = currentBatchMode.copy(operations = updatedOperations),
                error = null
            )
        }
    }
    
    /**
     * Removes a specific operation from the queue.
     * Requirements: 4.3
     */
    fun removeOperation(operationId: String) {
        val currentBatchMode = _uiState.value.batchMode
        val updatedOperations = currentBatchMode.operations.filter { it.id != operationId }
        
        _uiState.update {
            it.copy(
                batchMode = currentBatchMode.copy(operations = updatedOperations)
            )
        }
    }
    
    /**
     * Clears all queued operations.
     * Requirements: 4.4
     */
    fun clearAllOperations() {
        val currentBatchMode = _uiState.value.batchMode
        _uiState.update {
            it.copy(
                batchMode = currentBatchMode.copy(operations = emptyList())
            )
        }
    }
    
    /**
     * Gets all operations affecting a specific page.
     * Requirements: 3.4, 4.1
     */
    fun getOperationsForPage(pageIndex: Int): List<com.osnotes.app.domain.model.PageOperation> {
        return _uiState.value.batchMode.operations.filter { it.originalPageIndex == pageIndex }
    }
    
    /**
     * Executes all queued operations in a single batch.
     * Requirements: 5.2, 5.3, 5.4, 5.5
     */
    fun executeBatch() {
        viewModelScope.launch {
            val currentBatchMode = _uiState.value.batchMode
            val operations = currentBatchMode.operations
            
            if (operations.isEmpty()) {
                return@launch
            }
            
            // Set executing state
            _uiState.update {
                it.copy(
                    batchMode = currentBatchMode.copy(
                        isExecuting = true,
                        executionProgress = 0f,
                        currentOperation = "Starting batch execution...",
                        error = null
                    )
                )
            }
            
            try {
                val documentUri = Uri.parse(_uiState.value.documentPath)
                
                // Execute batch operations using PdfAnnotationFlattener
                val result = pdfAnnotationFlattener.executeBatchOperations(
                    sourceUri = documentUri,
                    operations = operations,
                    strokes = emptyMap(),
                    shapes = emptyMap(),
                    textAnnotations = emptyMap(),
                    onProgress = { progress, operation ->
                        _uiState.update {
                            it.copy(
                                batchMode = it.batchMode.copy(
                                    executionProgress = progress,
                                    currentOperation = operation
                                )
                            )
                        }
                    }
                )
                
                when (result) {
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Success -> {
                        // Reload document
                        withContext(Dispatchers.IO) {
                            pdfRenderer.openDocument(documentUri)
                        }
                        
                        // Reload pages
                        loadDocument(_uiState.value.documentPath)
                        
                        // Exit batch mode
                        _uiState.update {
                            it.copy(
                                batchMode = BatchModeState(),
                                error = null
                            )
                        }
                    }
                    is com.osnotes.app.data.pdf.PdfAnnotationFlattener.PageOperationResult.Error -> {
                        // Keep batch mode active with error
                        _uiState.update {
                            it.copy(
                                batchMode = currentBatchMode.copy(
                                    isExecuting = false,
                                    error = result.message
                                )
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        batchMode = currentBatchMode.copy(
                            isExecuting = false,
                            error = e.message ?: "Unknown error occurred"
                        )
                    )
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        thumbnailCache.clear()
    }
}
