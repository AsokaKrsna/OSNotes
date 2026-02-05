package com.osnotes.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.osnotes.app.domain.model.AnnotationTool
import com.osnotes.app.domain.model.ShapeType
import com.osnotes.app.domain.model.TextBoxMode
import com.osnotes.app.ui.components.*
import com.osnotes.app.ui.viewmodels.EditorViewModel
import kotlinx.coroutines.launch

/**
 * Super minimal note editor screen.
 * - Nothing at bottom (accidental touch prevention)
 * - Toolbar on edge (configurable: Right/Left/Top)
 * - Waterdrop page indicator
 * - Stylus for writing, finger for navigation
 * - Full tools: Pen, Highlighter, Eraser, Lasso, Text, Shapes, Ruler
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    filePath: String,
    onBack: () -> Unit,
    onOpenPageManager: (Int) -> Unit,
    onReloadDocument: (() -> Unit)? = null,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentColors by viewModel.recentColors.collectAsState()
    val customTemplates by viewModel.customTemplates.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Reload trigger - increments when returning from page manager
    var reloadTrigger by remember { mutableStateOf(0) }
    
    // Load document (reloads when filePath or reloadTrigger changes)
    LaunchedEffect(filePath, reloadTrigger) {
        viewModel.loadDocument(filePath)
    }
    
    // Pager state for page navigation
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { uiState.pageCount }
    )
    
    // Sync pager with viewmodel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }
    
    // Toolbar state
    var showToolbar by remember { mutableStateOf(true) }
    var isToolbarExpanded by remember { mutableStateOf(false) }
    
    // Waterdrop indicator state
    var isWaterdropExpanded by remember { mutableStateOf(false) }
    
    // Add Page dialog state
    var showAddPageDialog by remember { mutableStateOf(false) }
    
    // Shape type picker state
    var showShapePicker by remember { mutableStateOf(false) }
    
    // Zoom state - disable page swiping when zoomed in
    var isZoomedIn by remember { mutableStateOf(false) }
    
    // Show Add Page dialog
    if (showAddPageDialog) {
        AddPageDialog(
            onDismiss = { showAddPageDialog = false },
            onAddPage = { template, position ->
                viewModel.addPage(template.name, position)
                showAddPageDialog = false
            },
            currentPage = pagerState.currentPage + 1,
            totalPages = uiState.pageCount,
            customTemplates = customTemplates,
            onAddCustomTemplatePage = { customTemplate, position ->
                viewModel.addPage(customTemplate.id, position)
                showAddPageDialog = false
            }
        )
    }
    
    // Show Text Input dialog
    if (uiState.showTextInput) {
        TextInputDialog(
            position = uiState.textInputPosition,
            initialColor = uiState.toolState.currentColor,
            initialFontSize = uiState.toolState.textSize,
            recentColors = recentColors,
            onDismiss = { viewModel.hideTextInput() },
            onConfirm = { textAnnotation ->
                viewModel.addTextAnnotation(uiState.currentPage, textAnnotation)
            }
        )
    }
    
    // Show Shape Picker
    if (showShapePicker) {
        ShapePickerDialog(
            currentShape = uiState.toolState.shapeType,
            isFilled = uiState.toolState.shapeFilled,
            onShapeSelected = { shape ->
                viewModel.setShapeType(shape)
                showShapePicker = false
            },
            onFilledChanged = { viewModel.setShapeFilled(it) },
            onDismiss = { showShapePicker = false }
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
    ) {
        // Loading state
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF6366F1))
            }
        } else if (uiState.error != null) {
            // Error state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Red.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        uiState.error ?: "Error loading document",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            // Main editor content
            Column(modifier = Modifier.fillMaxSize()) {
                // Minimal top bar
                EditorTopBar(
                    title = uiState.documentName,
                    hasUnsavedChanges = uiState.hasUnsavedChanges,
                    onBack = {
                        if (uiState.hasUnsavedChanges) {
                            viewModel.saveToDatabase()
                        }
                        onBack()
                    },
                    onSave = { viewModel.saveToDatabase() },
                    onMakePermanent = { viewModel.makePermanent() },
                    onMore = { /* More options */ }
                )
                
                // Page content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Horizontal pager for pages
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !uiState.isStylusActive && !uiState.textBoxState.isActive && !isZoomedIn
                    ) { pageIndex ->
                        PageCanvas(
                            pageIndex = pageIndex,
                            viewModel = viewModel,
                            onStylusActiveChange = { viewModel.setStylusActive(it) },
                            onZoomChanged = { zoomed -> isZoomedIn = zoomed }
                        )
                    }
                    
                    // Glassmorphic toolbar
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showToolbar,
                        modifier = Modifier.fillMaxSize(),
                        enter = when (uiState.toolbarPosition) {
                            "left" -> fadeIn() + slideInHorizontally { -it }
                            "top" -> fadeIn() + slideInVertically { -it }
                            else -> fadeIn() + slideInHorizontally { it }
                        },
                        exit = when (uiState.toolbarPosition) {
                            "left" -> fadeOut() + slideOutHorizontally { -it }
                            "top" -> fadeOut() + slideOutVertically { -it }
                            else -> fadeOut() + slideOutHorizontally { it }
                        }
                    ) {
                        GlassmorphicToolbar(
                            currentTool = uiState.currentTool,
                            currentColor = uiState.toolState.currentColor,
                            currentWidth = uiState.toolState.strokeWidth,
                            recentColors = recentColors,
                            isHorizontal = uiState.toolbarPosition == "top",
                            isLightBackground = true,
                            toolbarPosition = uiState.toolbarPosition,
                            onToolSelected = { tool ->
                                viewModel.setTool(tool)
                                // Show shape picker when shapes tool is selected twice
                                if (tool == AnnotationTool.SHAPES && uiState.currentTool == AnnotationTool.SHAPES) {
                                    showShapePicker = true
                                }
                            },
                            onColorSelected = { viewModel.setColor(it) },
                            onWidthChanged = { viewModel.setStrokeWidth(it) },
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onAddPage = { showAddPageDialog = true },
                            onOpenPageManager = { onOpenPageManager(uiState.annotationCount) },
                            canUndo = uiState.canUndo,
                            canRedo = uiState.canRedo
                        )
                    }
                    
                    // Waterdrop page indicator
                    WaterdropPageIndicator(
                        currentPage = pagerState.currentPage + 1,
                        totalPages = uiState.pageCount,
                        isExpanded = isWaterdropExpanded,
                        isLightBackground = true,
                        onExpandToggle = { isWaterdropExpanded = !isWaterdropExpanded },
                        onPageSelected = { page ->
                            scope.launch {
                                pagerState.animateScrollToPage(page - 1)
                            }
                            isWaterdropExpanded = false
                        },
                        modifier = Modifier
                            .align(
                                when (uiState.toolbarPosition) {
                                    "left" -> Alignment.BottomEnd
                                    "top" -> Alignment.BottomEnd
                                    else -> Alignment.BottomStart // Move to left when toolbar is on right
                                }
                            )
                            .padding(
                                bottom = 16.dp,
                                start = if (uiState.toolbarPosition == "right") 16.dp else 0.dp,
                                end = if (uiState.toolbarPosition == "right") 0.dp else 16.dp
                            )
                    )
                    
                    // Selection action bar
                    if (!uiState.selection.isEmpty) {
                        SelectionActionBar(
                            onDelete = { viewModel.deleteSelection() },
                            onDone = { viewModel.applySelectionTransform() },
                            onCancel = { viewModel.clearSelection() },
                            modifier = Modifier
                                .align(
                                    when (uiState.toolbarPosition) {
                                        "top" -> Alignment.BottomCenter
                                        "left" -> Alignment.BottomCenter
                                        else -> Alignment.BottomCenter
                                    }
                                )
                                .padding(
                                    bottom = when (uiState.toolbarPosition) {
                                        "top" -> 16.dp
                                        else -> 80.dp // More space when toolbar is on sides
                                    },
                                    start = 16.dp,
                                    end = 16.dp
                                )
                        )
                    }
                    
                    // Text box action bar
                    TextBoxActionBar(
                        textBoxState = uiState.textBoxState,
                        onEdit = {
                            // Text editing is handled by the dialog
                        },
                        onDone = { viewModel.finalizeTextBox() },
                        onCancel = { viewModel.cancelTextBox() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    )
                }
            }
        }
        
        // Text box editor dialog
        if (uiState.textBoxState.mode == TextBoxMode.EDITING) {
            TextBoxEditor(
                textBoxState = uiState.textBoxState,
                currentColor = uiState.toolState.currentColor,
                currentFontSize = uiState.toolState.textSize,
                onTextChange = { text ->
                    viewModel.updateTextBoxText(text)
                },
                onColorChange = { color ->
                    viewModel.updateTextBoxColor(color)
                },
                onFontSizeChange = { fontSize ->
                    viewModel.updateTextBoxFontSize(fontSize)
                },
                onDone = { viewModel.finalizeTextBox() },
                onCancel = { viewModel.cancelTextBox() }
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMakePermanent: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        // Save indicator
        IconButton(onClick = onSave) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save",
                tint = if (hasUnsavedChanges) Color.Red else Color.White.copy(alpha = 0.6f)
            )
        }
        
        // More menu
        var showMenu by remember { mutableStateOf(false) }
        
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF2A2A2A))
            ) {
                DropdownMenuItem(
                    text = { Text("Make Permanent", color = Color.White) },
                    onClick = {
                        onMakePermanent()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF6366F1)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PageCanvas(
    pageIndex: Int,
    viewModel: EditorViewModel,
    onStylusActiveChange: (Boolean) -> Unit,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    val bitmap by viewModel.getPageBitmap(pageIndex).collectAsState(initial = null)
    val strokes by viewModel.getPageStrokes(pageIndex).collectAsState()
    val shapes by viewModel.getPageShapes(pageIndex).collectAsState()
    val texts by viewModel.getPageTexts(pageIndex).collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // Zoom and pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    
    // Zoom constraints
    val minScale = 1f
    val maxScale = 5f
    
    // Reset zoom when page changes
    LaunchedEffect(pageIndex) {
        zoomScale = 1f
        panOffset = Offset.Zero
        onZoomChanged(false)
    }
    
    // Notify parent when zoom state changes
    LaunchedEffect(zoomScale) {
        onZoomChanged(zoomScale > 1.01f)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF404040)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            // Zoomable container - wraps both bitmap and inking canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Custom multi-touch zoom gesture that ONLY activates on 2+ fingers
                    // Single finger and stylus events pass through untouched
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for first pointer
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            
                            // Check if it's a stylus - if so, don't handle zoom
                            if (firstDown.type == PointerType.Stylus || firstDown.type == PointerType.Eraser) {
                                return@awaitEachGesture // Let stylus pass through
                            }
                            
                            // Track if we've started a zoom gesture (2+ fingers)
                            var isZooming = false
                            var previousCentroid = Offset.Zero
                            var previousDistance = 0f
                            
                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes.filter { it.pressed }
                                
                                if (pointers.size >= 2) {
                                    // Multi-touch detected - handle zoom/pan
                                    isZooming = true
                                    
                                    // Calculate centroid and distance
                                    val centroid = Offset(
                                        pointers.map { it.position.x }.average().toFloat(),
                                        pointers.map { it.position.y }.average().toFloat()
                                    )
                                    
                                    val distance = if (pointers.size >= 2) {
                                        val dx = pointers[0].position.x - pointers[1].position.x
                                        val dy = pointers[0].position.y - pointers[1].position.y
                                        kotlin.math.sqrt(dx * dx + dy * dy)
                                    } else 0f
                                    
                                    if (previousDistance > 0f && distance > 0f) {
                                        // Calculate zoom factor
                                        val zoomFactor = distance / previousDistance
                                        val newScale = (zoomScale * zoomFactor).coerceIn(minScale, maxScale)
                                        
                                        // Calculate pan
                                        val pan = centroid - previousCentroid
                                        
                                        if (newScale > 1f) {
                                            val maxOffsetX = (size.width * (newScale - 1)) / 2
                                            val maxOffsetY = (size.height * (newScale - 1)) / 2
                                            val newOffsetX = (panOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            val newOffsetY = (panOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            panOffset = Offset(newOffsetX, newOffsetY)
                                        } else {
                                            panOffset = Offset.Zero
                                        }
                                        
                                        zoomScale = newScale
                                    }
                                    
                                    previousCentroid = centroid
                                    previousDistance = distance
                                    
                                    // Consume events to prevent page scrolling during zoom
                                    event.changes.forEach { it.consume() }
                                } else if (isZooming) {
                                    // Was zooming but now only 1 finger - reset tracking
                                    previousDistance = 0f
                                }
                                // Single finger events are NOT consumed - they pass to InkingCanvas
                                
                            } while (event.changes.any { it.pressed })
                        }
                    },
                    // NOTE: graphicsLayer zoom removed! Zoom is now applied inside InkingCanvas
                    // This keeps touch coordinates consistent and predictable
                contentAlignment = Alignment.Center
            ) {
                // PDF page rendering (with zoom applied internally)
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val bmp = bitmap!!
                    val imageBitmap = bmp.asImageBitmap()
                    
                    val scaleX = size.width / bmp.width
                    val scaleY = size.height / bmp.height
                    val scale = minOf(scaleX, scaleY)
                    
                    val scaledWidth = bmp.width * scale
                    val scaledHeight = bmp.height * scale
                    val baseLeft = (size.width - scaledWidth) / 2
                    val baseTop = (size.height - scaledHeight) / 2
                    
                    // Apply zoom transformation
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    
                    // Calculate zoomed position
                    val zoomedLeft = (baseLeft - centerX) * zoomScale + centerX + panOffset.x
                    val zoomedTop = (baseTop - centerY) * zoomScale + centerY + panOffset.y
                    val zoomedWidth = scaledWidth * zoomScale
                    val zoomedHeight = scaledHeight * zoomScale
                    
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(zoomedLeft.toInt(), zoomedTop.toInt()),
                        dstSize = IntSize(zoomedWidth.toInt(), zoomedHeight.toInt())
                    )
                }
                
                // Inking canvas overlay (handles all tools)
                if (uiState.currentTool != AnnotationTool.NONE) {
                    InkingCanvas(
                        modifier = Modifier.fillMaxSize(),
                        strokes = strokes,
                        shapes = shapes,
                        textAnnotations = texts,
                        toolState = uiState.toolState,
                        selection = uiState.selection,
                        lassoPath = uiState.lassoPath,
                        textBoxState = uiState.textBoxState,
                        bitmapWidth = bitmap!!.width,
                        bitmapHeight = bitmap!!.height,
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        enabled = true,
                        onStrokeEnd = { stroke ->
                            viewModel.addStroke(pageIndex, stroke)
                        },
                        onStrokeErase = { strokeId ->
                            viewModel.removeStroke(pageIndex, strokeId)
                        },
                        onShapeEnd = { shape ->
                            viewModel.addShape(pageIndex, shape)
                        },
                        onShapeErase = { shapeId ->
                            viewModel.removeShape(pageIndex, shapeId)
                        },
                        onTextTap = { position ->
                            viewModel.showTextInput(position)
                        },
                        onLassoUpdate = { points ->
                            viewModel.updateLassoPath(points)
                        },
                        onLassoEnd = { lassoPath ->
                            viewModel.completeLassoSelection(lassoPath)
                        },
                        onSelectionDrag = { delta ->
                            viewModel.moveSelection(delta)
                        },
                        // New text box callbacks
                        onTextBoxStart = { position ->
                            viewModel.startTextBoxDrawing(position)
                        },
                        onTextBoxUpdate = { position ->
                            viewModel.updateTextBoxBounds(position)
                        },
                        onTextBoxEnd = {
                            viewModel.finishTextBoxDrawing()
                        },
                        onTextBoxDragStart = { position ->
                            viewModel.startTextBoxDrag(position)
                        },
                        onTextBoxDrag = { delta ->
                            viewModel.updateTextBoxDrag(delta)
                        },
                        onTextBoxDragEnd = {
                            viewModel.finishTextBoxDrag()
                        },
                        onTextBoxResize = { handle, delta ->
                            viewModel.resizeTextBox(handle, delta)
                        },
                        onStylusActiveChange = onStylusActiveChange
                    )
                }
                
                // Text annotations overlay (for proper text rendering and interaction)
                // Only show when text box is not in positioning mode to avoid touch conflicts
                if (uiState.textBoxState.mode != TextBoxMode.POSITIONING) {
                    TextAnnotationOverlay(
                        textAnnotations = texts,
                        selectedIds = uiState.selection.textIds,
                        onTextTap = { textId ->
                            // Allow editing existing text annotations
                            viewModel.selectTextAnnotation(textId)
                        },
                        bitmapWidth = bitmap?.width ?: 0,
                        bitmapHeight = bitmap?.height ?: 0
                    )
                }
            }
        } else {
            // Loading placeholder
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}


/**
 * Action bar for selection operations.
 */
@Composable
private fun SelectionActionBar(
    onDelete: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1A1A1A).copy(alpha = 0.95f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Delete
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color(0xFFEF4444)
            )
        }
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(Color.White.copy(alpha = 0.2f))
        )
        
        // Cancel
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
        }
        
        // Done (apply transform)
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
        ) {
            Text("Done")
        }
    }
}

/**
 * Shape picker dialog.
 */
@Composable
private fun ShapePickerDialog(
    currentShape: ShapeType,
    isFilled: Boolean,
    onShapeSelected: (ShapeType) -> Unit,
    onFilledChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text("Select Shape", color = Color.White)
        },
        text = {
            Column {
                // Shape options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ShapeOption(Icons.Outlined.CropSquare, "Rectangle", currentShape == ShapeType.RECTANGLE) {
                        onShapeSelected(ShapeType.RECTANGLE)
                    }
                    ShapeOption(Icons.Outlined.Circle, "Circle", currentShape == ShapeType.CIRCLE) {
                        onShapeSelected(ShapeType.CIRCLE)
                    }
                    ShapeOption(Icons.Outlined.Remove, "Line", currentShape == ShapeType.LINE) {
                        onShapeSelected(ShapeType.LINE)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ShapeOption(Icons.Outlined.ArrowForward, "Arrow", currentShape == ShapeType.ARROW) {
                        onShapeSelected(ShapeType.ARROW)
                    }
                    ShapeOption(Icons.Outlined.ChangeHistory, "Triangle", currentShape == ShapeType.TRIANGLE) {
                        onShapeSelected(ShapeType.TRIANGLE)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Fill toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fill shape", color = Color.White)
                    Switch(
                        checked = isFilled,
                        onCheckedChange = onFilledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF6366F1),
                            checkedTrackColor = Color(0xFF6366F1).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF6366F1))
            }
        }
    )
}

@Composable
private fun ShapeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
