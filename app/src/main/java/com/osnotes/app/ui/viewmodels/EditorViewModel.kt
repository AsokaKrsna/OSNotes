package com.osnotes.app.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osnotes.app.data.pdf.MuPdfRenderer
import com.osnotes.app.data.pdf.PdfAnnotationFlattener
import com.osnotes.app.data.repository.AnnotationRepository
import com.osnotes.app.data.repository.CustomTemplateRepository
import com.osnotes.app.data.storage.StorageManager
import com.osnotes.app.data.export.ExportManager
import com.osnotes.app.domain.model.*
import com.osnotes.app.ui.components.InsertPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditorUiState(
    val documentName: String = "",
    val documentPath: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isFlattening: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val currentTool: AnnotationTool = AnnotationTool.PEN,
    val toolState: ToolState = ToolState(),
    val toolbarPosition: String = "right", // "right", "left", "top"
    val isStylusActive: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val annotationCount: Int = 0,
    // New states for additional tools
    val selection: Selection = Selection(),
    val lassoPath: LassoPath = LassoPath(),
    val showTextInput: Boolean = false,
    val textInputPosition: Offset = Offset.Zero,
    val isExporting: Boolean = false,
    // Advanced text box states
    val textBoxState: TextBoxState = TextBoxState()
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfRenderer: MuPdfRenderer,
    private val storageManager: StorageManager,
    private val annotationRepository: AnnotationRepository,
    private val pdfFlattener: PdfAnnotationFlattener,
    private val exportManager: ExportManager,
    private val customTemplateRepository: CustomTemplateRepository
) : ViewModel() {
    
    companion object {
        private const val AUTO_SAVE_DELAY_MS = 10_000L // 10 seconds
        private const val HIGH_ANNOTATION_WARNING_THRESHOLD = 500
        const val RENDER_SCALE = 2f // Consistent scale factor for PDF rendering
        
        // Tool settings persistence keys
        private const val PREFS_NAME = "osnotes_tool_settings"
        private const val KEY_LAST_TOOL = "last_tool"
        private const val KEY_PEN_COLOR = "pen_color"
        private const val KEY_PEN_WIDTH = "pen_width"
        private const val KEY_HIGHLIGHTER_COLOR = "highlighter_color"
        private const val KEY_HIGHLIGHTER_WIDTH = "highlighter_width"
        private const val KEY_SHAPE_COLOR = "shape_color"
        private const val KEY_SHAPE_WIDTH = "shape_width"
        private const val KEY_SHAPE_TYPE = "shape_type"
        private const val KEY_SHAPE_FILLED = "shape_filled"
    }
    
    // SharedPreferences for tool settings
    private val toolPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    
    // Custom templates exposed for UI
    val customTemplates: StateFlow<List<CustomTemplate>> = customTemplateRepository.customTemplates
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // Page bitmaps cache
    private val pageBitmaps = mutableMapOf<Int, MutableStateFlow<Bitmap?>>()
    
    // Page UUIDs for database linkage
    private val pageUuids = mutableMapOf<Int, String>()
    
    // Strokes per page (linked to page IDs)
    private val pageStrokes = mutableMapOf<Int, MutableStateFlow<List<InkStroke>>>()
    
    // Shapes per page
    private val pageShapes = mutableMapOf<Int, MutableStateFlow<List<ShapeAnnotation>>>()
    
    // Text annotations per page
    private val pageTexts = mutableMapOf<Int, MutableStateFlow<List<TextAnnotation>>>()
    
    // Undo/Redo stacks
    private val undoStack = mutableListOf<UndoAction>()
    private val redoStack = mutableListOf<UndoAction>()
    
    // Auto-save job
    private var autoSaveJob: Job? = null
    
    // Last two tools for double-tap switching
    private var lastTwoTools = mutableListOf(AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER)
    
    // Recent colors
    private val _recentColors = MutableStateFlow(listOf(
        Color.Black,
        Color(0xFF2563EB),
        Color(0xFFDC2626),
        Color(0xFF059669),
        Color(0xFFF59E0B)
    ))
    val recentColors: StateFlow<List<Color>> = _recentColors.asStateFlow()
    
    init {
        // Load saved tool settings on init
        val savedToolState = loadSavedToolSettings()
        _uiState.update { state ->
            state.copy(
                currentTool = savedToolState.currentTool,
                toolState = savedToolState
            )
        }
    }
    
    /**
     * Loads tool settings from SharedPreferences
     */
    private fun loadSavedToolSettings(): ToolState {
        val lastToolName = toolPrefs.getString(KEY_LAST_TOOL, AnnotationTool.PEN.name)
        val lastTool = try {
            AnnotationTool.valueOf(lastToolName ?: AnnotationTool.PEN.name)
        } catch (e: Exception) {
            AnnotationTool.PEN
        }
        
        val penColor = Color(toolPrefs.getInt(KEY_PEN_COLOR, Color.Black.toArgb()))
        val penWidth = toolPrefs.getFloat(KEY_PEN_WIDTH, 3f)
        val highlighterColor = Color(toolPrefs.getInt(KEY_HIGHLIGHTER_COLOR, Color.Yellow.toArgb()))
        val highlighterWidth = toolPrefs.getFloat(KEY_HIGHLIGHTER_WIDTH, 20f)
        val shapeColor = Color(toolPrefs.getInt(KEY_SHAPE_COLOR, Color.Black.toArgb()))
        val shapeWidth = toolPrefs.getFloat(KEY_SHAPE_WIDTH, 3f)
        val shapeTypeName = toolPrefs.getString(KEY_SHAPE_TYPE, ShapeType.RECTANGLE.name)
        val shapeType = try {
            ShapeType.valueOf(shapeTypeName ?: ShapeType.RECTANGLE.name)
        } catch (e: Exception) {
            ShapeType.RECTANGLE
        }
        val shapeFilled = toolPrefs.getBoolean(KEY_SHAPE_FILLED, false)
        
        // Build initial ToolState with saved settings
        val baseToolState = ToolState(
            currentTool = lastTool,
            penColor = penColor,
            penWidth = penWidth,
            highlighterColor = highlighterColor,
            highlighterWidth = highlighterWidth,
            shapeColor = shapeColor,
            shapeWidth = shapeWidth,
            shapeType = shapeType,
            shapeFilled = shapeFilled
        )
        
        // Load the current tool's settings as active
        return loadToolSettings(baseToolState, lastTool)
    }
    
    /**
     * Saves current tool settings to SharedPreferences
     */
    private fun saveToolSettings() {
        val toolState = _uiState.value.toolState
        toolPrefs.edit()
            .putString(KEY_LAST_TOOL, toolState.currentTool.name)
            .putInt(KEY_PEN_COLOR, toolState.penColor.toArgb())
            .putFloat(KEY_PEN_WIDTH, toolState.penWidth)
            .putInt(KEY_HIGHLIGHTER_COLOR, toolState.highlighterColor.toArgb())
            .putFloat(KEY_HIGHLIGHTER_WIDTH, toolState.highlighterWidth)
            .putInt(KEY_SHAPE_COLOR, toolState.shapeColor.toArgb())
            .putFloat(KEY_SHAPE_WIDTH, toolState.shapeWidth)
            .putString(KEY_SHAPE_TYPE, toolState.shapeType.name)
            .putBoolean(KEY_SHAPE_FILLED, toolState.shapeFilled)
            .apply()
    }
    
    fun loadDocument(filePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Handle both file paths and content URIs
                val uri = if (filePath.startsWith("/")) {
                    Uri.fromFile(java.io.File(filePath))
                } else {
                    Uri.parse(filePath)
                }
                val documentName = java.io.File(filePath).nameWithoutExtension
                
                // Open document with MuPDF
                withContext(Dispatchers.IO) {
                    pdfRenderer.openDocument(uri)
                }
                
                val pageCount = pdfRenderer.getPageCount(uri)
                
                // Add to recent notes
                storageManager.addToRecent(filePath)
                
                // Get toolbar position from settings
                val toolbarPos = storageManager.getToolbarPosition()
                
                // Initialize page UUIDs and load saved annotations
                initializePages(filePath, pageCount)
                
                _uiState.update {
                    it.copy(
                        documentName = documentName,
                        documentPath = filePath,
                        pageCount = pageCount,
                        currentPage = 0,
                        toolbarPosition = toolbarPos,
                        isLoading = false
                    )
                }
                
                // Start auto-save timer
                startAutoSave()
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load document"
                    )
                }
            }
        }
    }
    
    private suspend fun initializePages(documentPath: String, pageCount: Int) {
        // Initialize pages in database if needed
        annotationRepository.initializePages(documentPath, pageCount)
        
        // Load annotations for each page
        for (i in 0 until pageCount) {
            val pageUuid = annotationRepository.getOrCreatePageUuid(documentPath, i)
            pageUuids[i] = pageUuid
            
            // Load existing strokes
            val existingStrokes = annotationRepository.getStrokesForPageIndex(documentPath, i)
            pageStrokes[i] = MutableStateFlow(existingStrokes)
            
            // Load existing shapes
            val existingShapes = annotationRepository.getShapesForPageIndex(documentPath, i)
            pageShapes[i] = MutableStateFlow(existingShapes)
            
            // Load existing text annotations
            val existingTexts = annotationRepository.getTextAnnotationsForPageIndex(documentPath, i)
            pageTexts[i] = MutableStateFlow(existingTexts)
        }
        
        updateAnnotationCount()
    }
    
    fun getPageBitmap(pageIndex: Int): StateFlow<Bitmap?> {
        return pageBitmaps.getOrPut(pageIndex) {
            MutableStateFlow<Bitmap?>(null).also { flow ->
                viewModelScope.launch {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            // Use consistent scale factor for coordinate transformation
                            pdfRenderer.renderPage(pageIndex, RENDER_SCALE)
                        }
                        flow.value = bitmap
                    } catch (e: Exception) {
                        // Handle rendering error
                    }
                }
            }
        }
    }
    
    fun getPageStrokes(pageIndex: Int): StateFlow<List<InkStroke>> {
        return pageStrokes.getOrPut(pageIndex) {
            MutableStateFlow(emptyList())
        }
    }
    
    fun getPageShapes(pageIndex: Int): StateFlow<List<ShapeAnnotation>> {
        return pageShapes.getOrPut(pageIndex) {
            MutableStateFlow(emptyList())
        }
    }
    
    fun getPageTexts(pageIndex: Int): StateFlow<List<TextAnnotation>> {
        return pageTexts.getOrPut(pageIndex) {
            MutableStateFlow(emptyList())
        }
    }
    
    fun onPageChanged(pageIndex: Int) {
        _uiState.update { it.copy(currentPage = pageIndex) }
        
        // Clear selection when changing pages
        clearSelection()
        
        // Preload adjacent pages
        preloadPage(pageIndex - 1)
        preloadPage(pageIndex + 1)
    }
    
    private fun preloadPage(pageIndex: Int) {
        if (pageIndex < 0 || pageIndex >= _uiState.value.pageCount) return
        if (pageBitmaps.containsKey(pageIndex)) return
        
        getPageBitmap(pageIndex) // This will trigger loading
    }
    
    // ==================== Tool Management ====================
    
    fun setTool(tool: AnnotationTool) {
        val currentTool = _uiState.value.currentTool
        
        // Clear selection when switching from lasso
        if (currentTool == AnnotationTool.LASSO && tool != AnnotationTool.LASSO) {
            clearSelection()
        }
        
        // Clear text box when switching away from text tool
        if (currentTool == AnnotationTool.TEXT && tool != AnnotationTool.TEXT) {
            cancelTextBox()
        }
        
        // Track last two tools for double-tap switching
        if (tool != currentTool && tool != AnnotationTool.NONE) {
            lastTwoTools.add(0, currentTool)
            if (lastTwoTools.size > 2) lastTwoTools.removeAt(2)
        }
        
        _uiState.update { state ->
            // First, save current tool's settings
            val savedToolState = saveCurrentToolSettings(state.toolState)
            
            // Then, load the new tool's settings
            val newToolState = loadToolSettings(savedToolState, tool)
            
            state.copy(
                currentTool = tool,
                toolState = newToolState
            )
        }
        saveToolSettings()
    }
    
    /**
     * Saves current tool color/width to the appropriate per-tool fields
     */
    private fun saveCurrentToolSettings(toolState: ToolState): ToolState {
        return when (toolState.currentTool) {
            AnnotationTool.PEN, AnnotationTool.PEN_2 -> toolState.copy(
                penColor = toolState.currentColor,
                penWidth = toolState.strokeWidth
            )
            AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> toolState.copy(
                highlighterColor = toolState.currentColor,
                highlighterWidth = toolState.strokeWidth
            )
            AnnotationTool.SHAPES -> toolState.copy(
                shapeColor = toolState.currentColor,
                shapeWidth = toolState.strokeWidth
            )
            else -> toolState // No color/width settings for other tools
        }
    }
    
    /**
     * Loads tool-specific settings as currentColor/strokeWidth for a given tool
     */
    private fun loadToolSettings(toolState: ToolState, tool: AnnotationTool): ToolState {
        return when (tool) {
            AnnotationTool.PEN, AnnotationTool.PEN_2 -> toolState.copy(
                currentTool = tool,
                currentColor = toolState.penColor,
                strokeWidth = toolState.penWidth
            )
            AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> toolState.copy(
                currentTool = tool,
                currentColor = toolState.highlighterColor,
                strokeWidth = toolState.highlighterWidth
            )
            AnnotationTool.SHAPES -> toolState.copy(
                currentTool = tool,
                currentColor = toolState.shapeColor,
                strokeWidth = toolState.shapeWidth
            )
            else -> toolState.copy(currentTool = tool) // Keep current settings for other tools
        }
    }
    
    fun setColor(color: Color) {
        _uiState.update { state ->
            val toolState = state.toolState
            // Save to both currentColor and the per-tool color field
            val newToolState = when (toolState.currentTool) {
                AnnotationTool.PEN, AnnotationTool.PEN_2 -> toolState.copy(
                    currentColor = color,
                    penColor = color
                )
                AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> toolState.copy(
                    currentColor = color,
                    highlighterColor = color
                )
                AnnotationTool.SHAPES -> toolState.copy(
                    currentColor = color,
                    shapeColor = color
                )
                else -> toolState.copy(currentColor = color)
            }
            state.copy(toolState = newToolState)
        }
        addRecentColor(color)
        saveToolSettings()
    }
    
    fun setStrokeWidth(width: Float) {
        _uiState.update { state ->
            val toolState = state.toolState
            // Save to both strokeWidth and the per-tool width field
            val newToolState = when (toolState.currentTool) {
                AnnotationTool.PEN, AnnotationTool.PEN_2 -> toolState.copy(
                    strokeWidth = width,
                    penWidth = width
                )
                AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> toolState.copy(
                    strokeWidth = width,
                    highlighterWidth = width
                )
                AnnotationTool.SHAPES -> toolState.copy(
                    strokeWidth = width,
                    shapeWidth = width
                )
                else -> toolState.copy(strokeWidth = width)
            }
            state.copy(toolState = newToolState)
        }
        saveToolSettings()
    }
    
    fun setShapeType(shapeType: ShapeType) {
        _uiState.update {
            it.copy(toolState = it.toolState.copy(shapeType = shapeType))
        }
        saveToolSettings()
    }
    
    fun setShapeFilled(filled: Boolean) {
        _uiState.update {
            it.copy(toolState = it.toolState.copy(shapeFilled = filled))
        }
        saveToolSettings()
    }
    
    fun setTextSize(size: Float) {
        _uiState.update {
            it.copy(toolState = it.toolState.copy(textSize = size))
        }
    }
    
    fun switchToLastTool() {
        val lastTool = lastTwoTools.getOrNull(1) ?: AnnotationTool.PEN
        setTool(lastTool)
    }
    
    fun setStylusActive(active: Boolean) {
        _uiState.update { it.copy(isStylusActive = active) }
    }
    
    // ==================== Stroke Operations ====================
    
    fun addStroke(pageIndex: Int, stroke: InkStroke) {
        val flow = pageStrokes.getOrPut(pageIndex) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + stroke
        
        undoStack.add(UndoAction.AddStroke(pageIndex, stroke))
        redoStack.clear()
        
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
        checkAnnotationCount()
    }
    
    fun removeStroke(pageIndex: Int, strokeId: String) {
        val flow = pageStrokes[pageIndex] ?: return
        val stroke = flow.value.find { it.id == strokeId } ?: return
        
        flow.value = flow.value.filter { it.id != strokeId }
        
        undoStack.add(UndoAction.RemoveStroke(pageIndex, stroke))
        redoStack.clear()
        
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
    }
    
    // ==================== Shape Operations ====================
    
    fun addShape(pageIndex: Int, shape: ShapeAnnotation) {
        val flow = pageShapes.getOrPut(pageIndex) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + shape
        
        undoStack.add(UndoAction.AddShape(pageIndex, shape))
        redoStack.clear()
        
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
    }
    
    fun removeShape(pageIndex: Int, shapeId: String) {
        val flow = pageShapes[pageIndex] ?: return
        val shape = flow.value.find { it.id == shapeId } ?: return
        
        flow.value = flow.value.filter { it.id != shapeId }
        
        undoStack.add(UndoAction.RemoveShape(pageIndex, shape))
        redoStack.clear()
        
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
    }
    
    // ==================== Text Operations ====================
    
    fun showTextInput(position: Offset) {
        _uiState.update {
            it.copy(
                showTextInput = true,
                textInputPosition = position
            )
        }
    }
    
    fun hideTextInput() {
        _uiState.update { it.copy(showTextInput = false) }
    }
    
    // ==================== Advanced Text Box Operations ====================
    
    fun startTextBoxDrawing(startPoint: Offset) {
        _uiState.update {
            it.copy(
                textBoxState = TextBoxState(
                    isActive = true,
                    mode = TextBoxMode.DRAWING,
                    bounds = Rect(startPoint, startPoint)
                )
            )
        }
    }
    
    fun updateTextBoxBounds(endPoint: Offset) {
        val currentState = _uiState.value.textBoxState
        if (currentState.mode != TextBoxMode.DRAWING) return
        
        val startPoint = Offset(currentState.bounds.left, currentState.bounds.top)
        val newBounds = Rect(
            left = minOf(startPoint.x, endPoint.x),
            top = minOf(startPoint.y, endPoint.y),
            right = maxOf(startPoint.x, endPoint.x),
            bottom = maxOf(startPoint.y, endPoint.y)
        )
        
        _uiState.update {
            it.copy(
                textBoxState = currentState.copy(bounds = newBounds)
            )
        }
    }
    
    fun finishTextBoxDrawing() {
        val currentState = _uiState.value.textBoxState
        if (currentState.mode != TextBoxMode.DRAWING) return
        
        // Ensure minimum size for text box
        val minSize = 50f
        val bounds = currentState.bounds
        if (bounds.width < minSize || bounds.height < minSize) {
            // Cancel if too small
            cancelTextBox()
            return
        }
        
        // Automatically transition to editing mode
        _uiState.update {
            it.copy(
                textBoxState = currentState.copy(mode = TextBoxMode.EDITING)
            )
        }
    }
    
    fun updateTextBoxText(text: String) {
        val currentState = _uiState.value.textBoxState
        _uiState.update {
            it.copy(
                textBoxState = currentState.copy(text = text)
            )
        }
    }
    
    fun updateTextBoxColor(color: Color) {
        _uiState.update {
            it.copy(toolState = it.toolState.copy(currentColor = color))
        }
    }
    
    fun updateTextBoxFontSize(fontSize: Float) {
        _uiState.update {
            it.copy(toolState = it.toolState.copy(textSize = fontSize))
        }
    }
    
    fun startTextBoxDrag(position: Offset) {
        val currentState = _uiState.value.textBoxState
        if (currentState.mode != TextBoxMode.EDITING) return
        
        val bounds = currentState.getTransformedBounds()
        if (bounds.contains(position)) {
            _uiState.update {
                it.copy(
                    textBoxState = currentState.copy(
                        mode = TextBoxMode.POSITIONING,
                        isDragging = true
                    )
                )
            }
        }
    }
    
    fun updateTextBoxDrag(delta: Offset) {
        val currentState = _uiState.value.textBoxState
        if (currentState.mode != TextBoxMode.POSITIONING) return
        
        // Apply drag offset incrementally for smoother movement
        _uiState.update {
            it.copy(
                textBoxState = currentState.copy(
                    dragOffset = currentState.dragOffset + delta
                )
            )
        }
    }
    
    fun resizeTextBox(handle: String, delta: Offset) {
        val currentState = _uiState.value.textBoxState
        if (currentState.mode != TextBoxMode.POSITIONING) return
        
        android.util.Log.d("TextBox", "resizeTextBox called with handle: $handle, delta: $delta")
        
        val currentBounds = currentState.bounds
        val minSize = 30f // Further reduced minimum size for easier testing
        
        android.util.Log.d("TextBox", "Current bounds: $currentBounds, minSize: $minSize")
        
        val newBounds = when (handle) {
            "top-left" -> {
                val newLeft = currentBounds.left + delta.x
                val newTop = currentBounds.top + delta.y
                val newWidth = currentBounds.right - newLeft
                val newHeight = currentBounds.bottom - newTop
                
                android.util.Log.d("TextBox", "top-left: newLeft=$newLeft, newTop=$newTop, newWidth=$newWidth, newHeight=$newHeight")
                
                if (newWidth >= minSize && newHeight >= minSize) {
                    Rect(newLeft, newTop, currentBounds.right, currentBounds.bottom)
                } else {
                    android.util.Log.d("TextBox", "top-left: Size too small, keeping current bounds")
                    currentBounds
                }
            }
            "top-right" -> {
                val newRight = currentBounds.right + delta.x
                val newTop = currentBounds.top + delta.y
                val newWidth = newRight - currentBounds.left
                val newHeight = currentBounds.bottom - newTop
                
                android.util.Log.d("TextBox", "top-right: newRight=$newRight, newTop=$newTop, newWidth=$newWidth, newHeight=$newHeight")
                
                if (newWidth >= minSize && newHeight >= minSize) {
                    Rect(currentBounds.left, newTop, newRight, currentBounds.bottom)
                } else {
                    android.util.Log.d("TextBox", "top-right: Size too small, keeping current bounds")
                    currentBounds
                }
            }
            "bottom-left" -> {
                val newLeft = currentBounds.left + delta.x
                val newBottom = currentBounds.bottom + delta.y
                val newWidth = currentBounds.right - newLeft
                val newHeight = newBottom - currentBounds.top
                
                android.util.Log.d("TextBox", "bottom-left: newLeft=$newLeft, newBottom=$newBottom, newWidth=$newWidth, newHeight=$newHeight")
                
                if (newWidth >= minSize && newHeight >= minSize) {
                    Rect(newLeft, currentBounds.top, currentBounds.right, newBottom)
                } else {
                    android.util.Log.d("TextBox", "bottom-left: Size too small, keeping current bounds")
                    currentBounds
                }
            }
            "bottom-right" -> {
                val newRight = currentBounds.right + delta.x
                val newBottom = currentBounds.bottom + delta.y
                val newWidth = newRight - currentBounds.left
                val newHeight = newBottom - currentBounds.top
                
                android.util.Log.d("TextBox", "bottom-right: newRight=$newRight, newBottom=$newBottom, newWidth=$newWidth, newHeight=$newHeight, minSize=$minSize")
                
                if (newWidth >= minSize && newHeight >= minSize) {
                    Rect(currentBounds.left, currentBounds.top, newRight, newBottom)
                } else {
                    android.util.Log.d("TextBox", "bottom-right: Size too small, keeping current bounds")
                    currentBounds
                }
            }
            else -> {
                android.util.Log.d("TextBox", "Unknown handle: $handle")
                currentBounds
            }
        }
        
        android.util.Log.d("TextBox", "Old bounds: $currentBounds, New bounds: $newBounds")
        
        if (newBounds != currentBounds) {
            android.util.Log.d("TextBox", "✅ Bounds changed - updating UI state")
            _uiState.update {
                it.copy(
                    textBoxState = currentState.copy(bounds = newBounds)
                )
            }
        } else {
            android.util.Log.d("TextBox", "❌ Bounds unchanged - no update")
        }
    }
    
    fun finishTextBoxDrag() {
        val currentState = _uiState.value.textBoxState
        if (currentState.mode != TextBoxMode.POSITIONING) return
        
        // Apply the drag offset to the bounds
        val newBounds = Rect(
            currentState.bounds.left + currentState.dragOffset.x,
            currentState.bounds.top + currentState.dragOffset.y,
            currentState.bounds.right + currentState.dragOffset.x,
            currentState.bounds.bottom + currentState.dragOffset.y
        )
        
        _uiState.update {
            it.copy(
                textBoxState = currentState.copy(
                    mode = TextBoxMode.EDITING,
                    bounds = newBounds,
                    dragOffset = Offset.Zero,
                    isDragging = false
                )
            )
        }
    }
    
    fun finalizeTextBox() {
        val currentState = _uiState.value.textBoxState
        if (!currentState.isActive || currentState.text.isEmpty()) {
            cancelTextBox()
            return
        }
        
        val pageIndex = _uiState.value.currentPage
        val finalBounds = currentState.getTransformedBounds()
        val toolState = _uiState.value.toolState
        
        val textAnnotation = TextAnnotation(
            text = currentState.text,
            position = Offset(finalBounds.left, finalBounds.top),
            color = toolState.currentColor,
            fontSize = toolState.textSize,
            width = finalBounds.width,
            pageNumber = pageIndex,
            hasMarkdown = false, // No markdown support
            backgroundColor = Color.Transparent, // Transparent background
            padding = 8f
        )
        
        addTextAnnotation(pageIndex, textAnnotation)
        cancelTextBox()
    }
    
    fun cancelTextBox() {
        _uiState.update {
            it.copy(textBoxState = TextBoxState())
        }
    }
    
    fun addTextAnnotation(pageIndex: Int, text: TextAnnotation) {
        val flow = pageTexts.getOrPut(pageIndex) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + text
        
        undoStack.add(UndoAction.AddText(pageIndex, text))
        redoStack.clear()
        
        hideTextInput()
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
    }
    
    fun removeTextAnnotation(pageIndex: Int, textId: String) {
        val flow = pageTexts[pageIndex] ?: return
        val text = flow.value.find { it.id == textId } ?: return
        
        flow.value = flow.value.filter { it.id != textId }
        
        undoStack.add(UndoAction.RemoveText(pageIndex, text))
        redoStack.clear()
        
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
    }
    
    // ==================== Lasso Selection ====================
    
    fun updateLassoPath(points: List<Offset>) {
        _uiState.update {
            it.copy(lassoPath = LassoPath(points, isClosed = false))
        }
    }
    
    fun completeLassoSelection(lassoPath: LassoPath) {
        val pageIndex = _uiState.value.currentPage
        
        // Find all items inside the lasso
        val selectedStrokes = mutableSetOf<String>()
        val selectedShapes = mutableSetOf<String>()
        val selectedTexts = mutableSetOf<String>()
        
        // Check strokes
        pageStrokes[pageIndex]?.value?.forEach { stroke ->
            val center = stroke.points.let { points ->
                if (points.isEmpty()) Offset.Zero
                else Offset(
                    points.map { it.x }.average().toFloat(),
                    points.map { it.y }.average().toFloat()
                )
            }
            if (lassoPath.containsPoint(center)) {
                selectedStrokes.add(stroke.id)
            }
        }
        
        // Check shapes
        pageShapes[pageIndex]?.value?.forEach { shape ->
            if (lassoPath.containsPoint(shape.getCenter())) {
                selectedShapes.add(shape.id)
            }
        }
        
        // Check texts
        pageTexts[pageIndex]?.value?.forEach { text ->
            if (lassoPath.containsPoint(text.position)) {
                selectedTexts.add(text.id)
            }
        }
        
        // Calculate bounds
        val bounds = calculateSelectionBounds(pageIndex, selectedStrokes, selectedShapes, selectedTexts)
        
        _uiState.update {
            it.copy(
                selection = Selection(
                    strokeIds = selectedStrokes,
                    shapeIds = selectedShapes,
                    textIds = selectedTexts,
                    bounds = bounds
                ),
                lassoPath = LassoPath() // Clear lasso path
            )
        }
    }
    
    private fun calculateSelectionBounds(
        pageIndex: Int,
        strokeIds: Set<String>,
        shapeIds: Set<String>,
        textIds: Set<String>
    ): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        // Strokes
        pageStrokes[pageIndex]?.value?.filter { strokeIds.contains(it.id) }?.forEach { stroke ->
            stroke.points.forEach { point ->
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }
        }
        
        // Shapes
        pageShapes[pageIndex]?.value?.filter { shapeIds.contains(it.id) }?.forEach { shape ->
            val bounds = shape.getBounds()
            minX = minOf(minX, bounds.left)
            minY = minOf(minY, bounds.top)
            maxX = maxOf(maxX, bounds.right)
            maxY = maxOf(maxY, bounds.bottom)
        }
        
        // Texts
        pageTexts[pageIndex]?.value?.filter { textIds.contains(it.id) }?.forEach { text ->
            minX = minOf(minX, text.position.x)
            minY = minOf(minY, text.position.y)
            maxX = maxOf(maxX, text.position.x + text.width)
            maxY = maxOf(maxY, text.position.y + text.fontSize * 1.5f)
        }
        
        return if (minX == Float.MAX_VALUE) Rect.Zero
        else Rect(minX - 10f, minY - 10f, maxX + 10f, maxY + 10f)
    }
    
    fun moveSelection(delta: Offset) {
        val selection = _uiState.value.selection
        if (selection.isEmpty) return
        
        _uiState.update {
            it.copy(selection = selection.copy(offset = selection.offset + delta))
        }
    }
    
    fun applySelectionTransform() {
        val selection = _uiState.value.selection
        if (selection.isEmpty) return
        
        val pageIndex = _uiState.value.currentPage
        val offset = selection.offset
        
        // Move strokes
        if (selection.strokeIds.isNotEmpty()) {
            val flow = pageStrokes[pageIndex] ?: return
            flow.value = flow.value.map { stroke ->
                if (selection.strokeIds.contains(stroke.id)) {
                    stroke.copy(
                        points = stroke.points.map { point ->
                            StrokePoint(
                                x = point.x + offset.x,
                                y = point.y + offset.y,
                                pressure = point.pressure,
                                timestamp = point.timestamp
                            )
                        }
                    )
                } else stroke
            }
        }
        
        // Move shapes
        if (selection.shapeIds.isNotEmpty()) {
            val flow = pageShapes[pageIndex] ?: return
            flow.value = flow.value.map { shape ->
                if (selection.shapeIds.contains(shape.id)) {
                    shape.copy(
                        startPoint = shape.startPoint + offset,
                        endPoint = shape.endPoint + offset
                    )
                } else shape
            }
        }
        
        // Move texts
        if (selection.textIds.isNotEmpty()) {
            val flow = pageTexts[pageIndex] ?: return
            flow.value = flow.value.map { text ->
                if (selection.textIds.contains(text.id)) {
                    text.copy(position = text.position + offset)
                } else text
            }
        }
        
        markUnsaved()
        clearSelection()
    }
    
    fun deleteSelection() {
        val selection = _uiState.value.selection
        if (selection.isEmpty) return
        
        val pageIndex = _uiState.value.currentPage
        
        // Delete strokes
        selection.strokeIds.forEach { id ->
            removeStroke(pageIndex, id)
        }
        
        // Delete shapes
        selection.shapeIds.forEach { id ->
            removeShape(pageIndex, id)
        }
        
        // Delete texts
        selection.textIds.forEach { id ->
            removeTextAnnotation(pageIndex, id)
        }
        
        clearSelection()
    }
    
    fun clearSelection() {
        _uiState.update {
            it.copy(
                selection = Selection(),
                lassoPath = LassoPath()
            )
        }
    }
    
    // ==================== Undo/Redo ====================
    
    fun undo() {
        if (undoStack.isEmpty()) return
        
        val action = undoStack.removeLast()
        when (action) {
            is UndoAction.AddStroke -> {
                val flow = pageStrokes[action.pageIndex] ?: return
                flow.value = flow.value.filter { it.id != action.stroke.id }
            }
            is UndoAction.RemoveStroke -> {
                val flow = pageStrokes.getOrPut(action.pageIndex) { MutableStateFlow(emptyList()) }
                flow.value = flow.value + action.stroke
            }
            is UndoAction.AddShape -> {
                val flow = pageShapes[action.pageIndex] ?: return
                flow.value = flow.value.filter { it.id != action.shape.id }
            }
            is UndoAction.RemoveShape -> {
                val flow = pageShapes.getOrPut(action.pageIndex) { MutableStateFlow(emptyList()) }
                flow.value = flow.value + action.shape
            }
            is UndoAction.AddText -> {
                val flow = pageTexts[action.pageIndex] ?: return
                flow.value = flow.value.filter { it.id != action.text.id }
            }
            is UndoAction.RemoveText -> {
                val flow = pageTexts.getOrPut(action.pageIndex) { MutableStateFlow(emptyList()) }
                flow.value = flow.value + action.text
            }
        }
        
        redoStack.add(action)
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
    }
    
    fun redo() {
        if (redoStack.isEmpty()) return
        
        val action = redoStack.removeLast()
        when (action) {
            is UndoAction.AddStroke -> {
                val flow = pageStrokes.getOrPut(action.pageIndex) { MutableStateFlow(emptyList()) }
                flow.value = flow.value + action.stroke
            }
            is UndoAction.RemoveStroke -> {
                val flow = pageStrokes[action.pageIndex] ?: return
                flow.value = flow.value.filter { it.id != action.stroke.id }
            }
            is UndoAction.AddShape -> {
                val flow = pageShapes.getOrPut(action.pageIndex) { MutableStateFlow(emptyList()) }
                flow.value = flow.value + action.shape
            }
            is UndoAction.RemoveShape -> {
                val flow = pageShapes[action.pageIndex] ?: return
                flow.value = flow.value.filter { it.id != action.shape.id }
            }
            is UndoAction.AddText -> {
                val flow = pageTexts.getOrPut(action.pageIndex) { MutableStateFlow(emptyList()) }
                flow.value = flow.value + action.text
            }
            is UndoAction.RemoveText -> {
                val flow = pageTexts[action.pageIndex] ?: return
                flow.value = flow.value.filter { it.id != action.text.id }
            }
        }
        
        undoStack.add(action)
        markUnsaved()
        updateUndoRedoState()
        updateAnnotationCount()
    }
    
    private fun updateUndoRedoState() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }
    
    private fun updateAnnotationCount() {
        val strokeCount = pageStrokes.values.sumOf { it.value.size }
        val shapeCount = pageShapes.values.sumOf { it.value.size }
        val textCount = pageTexts.values.sumOf { it.value.size }
        _uiState.update { it.copy(annotationCount = strokeCount + shapeCount + textCount) }
    }
    
    private fun markUnsaved() {
        _uiState.update { it.copy(hasUnsavedChanges = true) }
    }
    
    // ==================== Save/Load ====================
    
    fun saveToDatabase() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            try {
                val documentPath = _uiState.value.documentPath
                
                // 1. Save PDF structure changes (like added pages)
                pdfRenderer.saveToWorkingFile().getOrThrow()
                
                // 2. Save all strokes to database (delete old ones first to handle undo)
                for ((pageIndex, strokesFlow) in pageStrokes) {
                    val pageUuid = pageUuids[pageIndex] ?: continue
                    val strokes = strokesFlow.value
                    
                    // Delete existing strokes for this page before saving current state
                    annotationRepository.deleteAnnotationsForPage(pageUuid)
                    
                    if (strokes.isNotEmpty()) {
                        annotationRepository.saveStrokes(
                            documentUri = documentPath,
                            pageUuid = pageUuid,
                            pageIndex = pageIndex,
                            strokes = strokes
                        )
                    }
                }
                
                // 3. Save all shapes to database
                for ((pageIndex, shapesFlow) in pageShapes) {
                    val pageUuid = pageUuids[pageIndex] ?: continue
                    val shapes = shapesFlow.value
                    
                    if (shapes.isNotEmpty()) {
                        annotationRepository.saveShapes(
                            documentUri = documentPath,
                            pageUuid = pageUuid,
                            pageIndex = pageIndex,
                            shapes = shapes
                        )
                    }
                }
                
                // 4. Save all text annotations to database
                for ((pageIndex, textsFlow) in pageTexts) {
                    val pageUuid = pageUuids[pageIndex] ?: continue
                    val texts = textsFlow.value
                    
                    if (texts.isNotEmpty()) {
                        annotationRepository.saveTextAnnotations(
                            documentUri = documentPath,
                            pageUuid = pageUuid,
                            pageIndex = pageIndex,
                            textAnnotations = texts
                        )
                    }
                }
                
                _uiState.update { it.copy(hasUnsavedChanges = false, isSaving = false) }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSaving = false, 
                        error = "Failed to save: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun makePermanent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFlattening = true) }
            
            try {
                val uri = Uri.parse(_uiState.value.documentPath)
                
                // Collect all annotations by page
                val strokesByPage = pageStrokes.mapValues { it.value.value }
                val shapesByPage = pageShapes.mapValues { it.value.value }
                val textsByPage = pageTexts.mapValues { it.value.value }
                
                // Flatten annotations into PDF
                val result = pdfFlattener.flattenAnnotations(
                    sourceUri = uri,
                    strokes = strokesByPage,
                    shapes = shapesByPage,
                    textAnnotations = textsByPage,
                    replaceOriginal = true,
                    renderScale = RENDER_SCALE // Use the same scale as rendering
                )
                
                when (result) {
                    is PdfAnnotationFlattener.FlattenResult.Success -> {
                        // Reopen the document in the renderer to pick up the changes
                        val uri = Uri.parse(_uiState.value.documentPath)
                        pdfRenderer.openDocument(uri)
                        
                        // Clear local strokes and database annotations
                        val documentPath = _uiState.value.documentPath
                        annotationRepository.deleteAnnotationsForDocument(documentPath)
                        
                        pageStrokes.values.forEach { it.value = emptyList() }
                        pageShapes.values.forEach { it.value = emptyList() }
                        pageTexts.values.forEach { it.value = emptyList() }
                        undoStack.clear()
                        redoStack.clear()
                        
                        _uiState.update { 
                            it.copy(
                                hasUnsavedChanges = false,
                                isFlattening = false,
                                canUndo = false,
                                canRedo = false,
                                annotationCount = 0,
                                warning = null,
                                selection = Selection()
                            )
                        }
                        
                        // Reload page bitmaps to show flattened content
                        pageBitmaps.clear()
                        getPageBitmap(_uiState.value.currentPage)
                    }
                    is PdfAnnotationFlattener.FlattenResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isFlattening = false,
                                error = result.message
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isFlattening = false,
                        error = "Failed to make permanent: ${e.message}"
                    )
                }
            }
        }
    }
    
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_SAVE_DELAY_MS)
                if (_uiState.value.hasUnsavedChanges) {
                    saveToDatabase()
                }
            }
        }
    }
    
    private fun checkAnnotationCount() {
        val totalAnnotations = _uiState.value.annotationCount
        if (totalAnnotations > HIGH_ANNOTATION_WARNING_THRESHOLD) {
            _uiState.update {
                it.copy(
                    warning = "High annotation count ($totalAnnotations). Consider using 'Make Permanent' to flatten annotations into the PDF."
                )
            }
        }
    }
    
    fun dismissWarning() {
        _uiState.update { it.copy(warning = null) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    // ==================== Page Management ====================
    
    /**
     * Adds a new page to the document.
     * @param templateName The template to use for the new page
     * @param position Where to insert the new page
     */
    fun addPage(templateName: String, position: InsertPosition) {
        viewModelScope.launch {
            try {
                val currentPage = _uiState.value.currentPage
                val pageCount = _uiState.value.pageCount
                
                // Calculate target index
                val targetIndex = when (position) {
                    InsertPosition.START -> 0
                    InsertPosition.AFTER_CURRENT -> currentPage + 1
                    InsertPosition.END -> pageCount
                }
                
                // Collect current annotations for preservation during page operation
                val strokesByPage = pageStrokes.mapValues { it.value.value }
                val shapesByPage = pageShapes.mapValues { it.value.value }
                val textsByPage = pageTexts.mapValues { it.value.value }
                
                // Add page via PdfAnnotationFlattener (which handles PDF reconstruction)
                val uri = Uri.parse(_uiState.value.documentPath)
                val result = withContext(Dispatchers.IO) {
                    pdfFlattener.addPage(
                        sourceUri = uri,
                        pageIndex = targetIndex,
                        templateName = templateName,
                        strokes = strokesByPage,
                        shapes = shapesByPage,
                        textAnnotations = textsByPage
                    )
                }
                
                when (result) {
                    is PdfAnnotationFlattener.PageOperationResult.Success -> {
                        // Reopen document to pick up changes
                        pdfRenderer.openDocument(uri)
                        
                        // Initialize new page data
                        val newPageUuid = java.util.UUID.randomUUID().toString()
                        
                        // Shift existing page mappings (from end to target)
                        for (i in pageCount - 1 downTo targetIndex) {
                            // Shift UUIDs
                            pageUuids[i]?.let { pageUuids[i + 1] = it }
                            
                            // Shift annotation flows
                            pageStrokes[i]?.let { pageStrokes[i + 1] = it }
                            pageShapes[i]?.let { pageShapes[i + 1] = it }
                            pageTexts[i]?.let { pageTexts[i + 1] = it }
                            
                            // Shift bitmap flows (if cached)
                            if (pageBitmaps.containsKey(i)) {
                                pageBitmaps[i]?.let { pageBitmaps[i + 1] = it }
                            } else {
                                pageBitmaps.remove(i + 1)
                            }
                        }
                        
                        // Set up new page
                        pageUuids[targetIndex] = newPageUuid
                        pageStrokes[targetIndex] = MutableStateFlow(emptyList())
                        pageShapes[targetIndex] = MutableStateFlow(emptyList())
                        pageTexts[targetIndex] = MutableStateFlow(emptyList())
                        pageBitmaps.remove(targetIndex)
                        
                        // Update state
                        _uiState.update {
                            it.copy(
                                pageCount = pageCount + 1,
                                currentPage = targetIndex
                            )
                        }
                        
                        // Load the new page bitmap
                        getPageBitmap(targetIndex)
                    }
                    is PdfAnnotationFlattener.PageOperationResult.Error -> {
                        _uiState.update {
                            it.copy(error = "Failed to add page: ${result.message}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to add page: ${e.message}")
                }
            }
        }
    }
    
    fun addRecentColor(color: Color) {
        val current = _recentColors.value.toMutableList()
        current.remove(color)
        current.add(0, color)
        if (current.size > 8) current.removeAt(current.lastIndex)
        _recentColors.value = current
    }
    
    // For compatibility with existing setToolColor usage
    fun setToolColor(color: Color) = setColor(color)
    
    // ==================== Export/Share ====================
    
    fun exportDocument(
        format: ExportManager.ExportFormat,
        outputName: String? = null,
        includeAnnotations: Boolean = true,
        pageRange: IntRange? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            
            try {
                val result = exportManager.exportDocument(
                    documentPath = _uiState.value.documentPath,
                    format = format,
                    outputName = outputName,
                    includeAnnotations = includeAnnotations,
                    pageRange = pageRange
                )
                
                when (result) {
                    is ExportManager.ExportResult.Success -> {
                        // Start share intent if available
                        result.shareIntent?.let { intent ->
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(Intent.createChooser(intent, "Share exported document"))
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isExporting = false,
                                error = null
                            ) 
                        }
                    }
                    is ExportManager.ExportResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isExporting = false,
                                error = result.message
                            ) 
                        }
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isExporting = false,
                        error = "Export failed: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun exportToPdf(includeAnnotations: Boolean = true) {
        exportDocument(
            format = if (includeAnnotations) ExportManager.ExportFormat.PDF else ExportManager.ExportFormat.PDF_FLATTENED
        )
    }
    
    fun exportToImages(format: ExportManager.ExportFormat = ExportManager.ExportFormat.PNG) {
        exportDocument(format = format)
    }
    
    fun exportCurrentPage(format: ExportManager.ExportFormat = ExportManager.ExportFormat.PNG) {
        val currentPage = _uiState.value.currentPage
        exportDocument(
            format = format,
            pageRange = currentPage..currentPage
        )
    }
    
    // ==================== Text Box Selection and Editing ====================
    
    fun selectTextAnnotation(textId: String) {
        val pageIndex = _uiState.value.currentPage
        val textFlow = pageTexts[pageIndex] ?: return
        val textAnnotation = textFlow.value.find { it.id == textId } ?: return
        
        // Calculate proper text height based on content
        val lines = textAnnotation.text.split('\n')
        val lineHeight = textAnnotation.fontSize * 1.2f
        val textHeight = lines.size * lineHeight + textAnnotation.padding * 2
        
        // Convert text annotation back to text box for editing
        val bounds = Rect(
            textAnnotation.position.x,
            textAnnotation.position.y,
            textAnnotation.position.x + textAnnotation.width,
            textAnnotation.position.y + textHeight
        )
        
        _uiState.update {
            it.copy(
                textBoxState = TextBoxState(
                    isActive = true,
                    mode = TextBoxMode.POSITIONING,
                    bounds = bounds,
                    text = textAnnotation.text
                ),
                currentTool = AnnotationTool.TEXT
            )
        }
        
        // Remove the original text annotation temporarily
        textFlow.value = textFlow.value.filter { it.id != textId }
    }
    
    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        
        // Save annotations before closing (must be synchronous since ViewModel is being destroyed)
        if (_uiState.value.hasUnsavedChanges) {
            kotlinx.coroutines.runBlocking {
                try {
                    val documentPath = _uiState.value.documentPath
                    
                    // Save PDF structure changes
                    pdfRenderer.saveToWorkingFile()
                    
                    // Save all annotations to database
                    for ((pageIndex, strokesFlow) in pageStrokes) {
                        val pageUuid = pageUuids[pageIndex] ?: continue
                        val strokes = strokesFlow.value
                        
                        // Delete existing annotations for this page before saving current state
                        annotationRepository.deleteAnnotationsForPage(pageUuid)
                        
                        if (strokes.isNotEmpty()) {
                            annotationRepository.saveStrokes(
                                documentUri = documentPath,
                                pageUuid = pageUuid,
                                pageIndex = pageIndex,
                                strokes = strokes
                            )
                        }
                    }
                    
                    for ((pageIndex, shapesFlow) in pageShapes) {
                        val pageUuid = pageUuids[pageIndex] ?: continue
                        val shapes = shapesFlow.value
                        
                        if (shapes.isNotEmpty()) {
                            annotationRepository.saveShapes(
                                documentUri = documentPath,
                                pageUuid = pageUuid,
                                pageIndex = pageIndex,
                                shapes = shapes
                            )
                        }
                    }
                    
                    for ((pageIndex, textsFlow) in pageTexts) {
                        val pageUuid = pageUuids[pageIndex] ?: continue
                        val texts = textsFlow.value
                        
                        if (texts.isNotEmpty()) {
                            annotationRepository.saveTextAnnotations(
                                documentUri = documentPath,
                                pageUuid = pageUuid,
                                pageIndex = pageIndex,
                                textAnnotations = texts
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "Failed to save on exit: ${e.message}")
                }
            }
        }
        
        kotlinx.coroutines.runBlocking {
            pdfRenderer.closeDocument()
        }
    }
}

private sealed class UndoAction {
    data class AddStroke(val pageIndex: Int, val stroke: InkStroke) : UndoAction()
    data class RemoveStroke(val pageIndex: Int, val stroke: InkStroke) : UndoAction()
    data class AddShape(val pageIndex: Int, val shape: ShapeAnnotation) : UndoAction()
    data class RemoveShape(val pageIndex: Int, val shape: ShapeAnnotation) : UndoAction()
    data class AddText(val pageIndex: Int, val text: TextAnnotation) : UndoAction()
    data class RemoveText(val pageIndex: Int, val text: TextAnnotation) : UndoAction()
}

