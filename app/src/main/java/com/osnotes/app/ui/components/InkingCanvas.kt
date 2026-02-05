package com.osnotes.app.ui.components

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.osnotes.app.domain.model.*
import com.osnotes.app.data.pdf.TextLine
import com.osnotes.app.ui.input.PalmRejectionManager
import kotlin.math.*

/**
 * Result of snapping a highlighter stroke to a text line.
 */
private data class HighlighterSnapResult(
    val points: List<StrokePoint>,
    val strokeWidth: Float
)

// ==================== Helper Functions ====================

/**
 * Checks if the tool is a drawing tool (pen or highlighter).
 */
private fun AnnotationTool.isDrawingTool(): Boolean = when (this) {
    AnnotationTool.PEN, AnnotationTool.PEN_2,
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> true
    else -> false
}

/**
 * Checks if the tool is a highlighter variant.
 */
private fun AnnotationTool.isHighlighter(): Boolean = when (this) {
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> true
    else -> false
}

/**
 * Checks if the tool is the smart highlighter that snaps to text.
 */
private fun AnnotationTool.isSmartHighlighter(): Boolean = this == AnnotationTool.HIGHLIGHTER_2

/**
 * Finds the nearest text line to a given Y position.
 */
private fun findNearestTextLine(
    y: Float,
    textLines: List<TextLine>
): TextLine? {
    return textLines.minByOrNull { line ->
        val lineCenter = line.y + line.height / 2
        abs(lineCenter - y)
    }?.takeIf { line ->
        y >= line.y - line.height && y <= line.y + line.height * 2
    }
}

/**
 * Snaps a highlighter stroke to the nearest text line.
 */
private fun snapHighlighterToTextLine(
    points: List<StrokePoint>,
    textLines: List<TextLine>,
    defaultWidth: Float
): HighlighterSnapResult {
    if (points.size < 2) {
        return HighlighterSnapResult(points, defaultWidth)
    }
    
    val startX = points.first().x
    val endX = points.last().x
    val avgY = (points.first().y + points.last().y) / 2
    
    val nearestLine = findNearestTextLine(avgY, textLines)
    
    return if (nearestLine != null) {
        val lineY = nearestLine.y + nearestLine.height / 2
        HighlighterSnapResult(
            points = listOf(
                StrokePoint(x = startX, y = lineY, pressure = 1f),
                StrokePoint(x = endX, y = lineY, pressure = 1f)
            ),
            strokeWidth = nearestLine.height * 0.9f
        )
    } else {
        val straightY = points.first().y
        HighlighterSnapResult(
            points = listOf(
                StrokePoint(x = startX, y = straightY, pressure = 1f),
                StrokePoint(x = endX, y = straightY, pressure = 1f)
            ),
            strokeWidth = defaultWidth
        )
    }
}

/**
 * Creates a StrokePoint from a PointerInputChange.
 */
private fun createStrokePoint(change: PointerInputChange): StrokePoint {
    return StrokePoint(
        x = change.position.x,
        y = change.position.y,
        pressure = change.pressure,
        timestamp = change.uptimeMillis
    )
}

/**
 * Creates a finalized InkStroke from drawing state.
 */
private fun createFinalStroke(
    points: List<StrokePoint>,
    tool: AnnotationTool,
    toolState: ToolState,
    textLines: List<TextLine>
): InkStroke {
    val isHighlighterTool = tool.isHighlighter()
    
    val (finalPoints, finalWidth) = if (tool.isSmartHighlighter()) {
        val result = snapHighlighterToTextLine(points, textLines, toolState.strokeWidth)
        result.points to result.strokeWidth
    } else {
        points to toolState.strokeWidth
    }
    
    return InkStroke(
        points = finalPoints,
        color = toolState.currentColor,
        strokeWidth = finalWidth,
        isHighlighter = isHighlighterTool
    )
}

/**
 * High-performance inking canvas supporting all annotation tools.
 * 
 * Features:
 * - Pen/Highlighter: Pressure-sensitive ink drawing
 * - Eraser: Removes strokes and shapes
 * - Shapes: Rectangle, circle, line, arrow, triangle
 * - Lasso: Select, move, resize annotations
 * - Text: Draw text box, edit text, reposition
 */
@Composable
fun InkingCanvas(
    modifier: Modifier = Modifier,
    strokes: List<InkStroke>,
    shapes: List<ShapeAnnotation> = emptyList(),
    textAnnotations: List<TextAnnotation> = emptyList(),
    toolState: ToolState,
    textLines: List<TextLine> = emptyList(),
    selection: Selection = Selection(),
    lassoPath: LassoPath = LassoPath(),
    textBoxState: TextBoxState = TextBoxState(),
    bitmapWidth: Int = 0,
    bitmapHeight: Int = 0,
    zoomScale: Float = 1f,
    panOffset: Offset = Offset.Zero,
    enabled: Boolean = true,
    onStrokeStart: () -> Unit = {},
    onStrokeEnd: (InkStroke) -> Unit = { _ -> },
    onStrokeErase: (String) -> Unit = { _ -> },
    onShapeStart: () -> Unit = {},
    onShapeEnd: (ShapeAnnotation) -> Unit = { _ -> },
    onShapeErase: (String) -> Unit = { _ -> },
    onTextTap: (Offset) -> Unit = { _ -> },
    onLassoStart: () -> Unit = {},
    onLassoUpdate: (List<Offset>) -> Unit = { _ -> },
    onLassoEnd: (LassoPath) -> Unit = { _ -> },
    onSelectionDrag: (Offset) -> Unit = { _ -> },
    // New text box callbacks
    onTextBoxStart: (Offset) -> Unit = { _ -> },
    onTextBoxUpdate: (Offset) -> Unit = { _ -> },
    onTextBoxEnd: () -> Unit = {},
    onTextBoxDragStart: (Offset) -> Unit = { _ -> },
    onTextBoxDrag: (Offset) -> Unit = { _ -> },
    onTextBoxDragEnd: () -> Unit = {},
    onTextBoxResize: (String, Offset) -> Unit = { _, _ -> },
    onTap: () -> Unit = {},
    onStylusActiveChange: (Boolean) -> Unit = { _ -> }
) {
    // Current stroke/shape being drawn
    var currentPoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var currentShapeStart by remember { mutableStateOf<Offset?>(null) }
    var currentShapeEnd by remember { mutableStateOf<Offset?>(null) }
    var currentLassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }
    var currentTouchPosition by remember { mutableStateOf<Offset?>(null) }
    
    // Selection dragging state
    var isDraggingSelection by remember { mutableStateOf(false) }
    var isDraggingTextBox by remember { mutableStateOf(false) }
    var isResizingTextBox by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf<Offset?>(null) }
    var resizeHandle by remember { mutableStateOf<String?>(null) }
    
    // Palm rejection manager
    val palmRejectionManager = remember { PalmRejectionManager() }
    
    // Use rememberUpdatedState for callbacks
    val currentToolState by rememberUpdatedState(toolState)
    val currentStrokes by rememberUpdatedState(strokes)
    val currentShapes by rememberUpdatedState(shapes)
    val currentTextAnnotations by rememberUpdatedState(textAnnotations)
    val currentTextLines by rememberUpdatedState(textLines)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentSelection by rememberUpdatedState(selection)
    val currentTextBoxState by rememberUpdatedState(textBoxState)
    val currentBitmapWidth by rememberUpdatedState(bitmapWidth)
    val currentBitmapHeight by rememberUpdatedState(bitmapHeight)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentPanOffset by rememberUpdatedState(panOffset)
    
    Box(modifier = modifier.fillMaxSize()) {
        // Drawing canvas (with zoom applied to rendering)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Calculate base scale factor from bitmap space to screen space
            val scaleX = if (currentBitmapWidth > 0) size.width / currentBitmapWidth else 1f
            val scaleY = if (currentBitmapHeight > 0) size.height / currentBitmapHeight else 1f
            val baseDisplayScale = minOf(scaleX, scaleY)
            
            // Apply zoom to the display scale
            val displayScale = baseDisplayScale * currentZoomScale
            
            // Calculate base offsets (unzoomed)
            val baseScaledWidth = currentBitmapWidth * baseDisplayScale
            val baseScaledHeight = currentBitmapHeight * baseDisplayScale
            val baseOffsetX = (size.width - baseScaledWidth) / 2
            val baseOffsetY = (size.height - baseScaledHeight) / 2
            
            // Apply zoom transformation to offsets (scale around center)
            val centerX = size.width / 2
            val centerY = size.height / 2
            val offsetX = (baseOffsetX - centerX) * currentZoomScale + centerX + currentPanOffset.x
            val offsetY = (baseOffsetY - centerY) * currentZoomScale + centerY + currentPanOffset.y
            
            // Draw existing strokes (transform from bitmap space to zoomed display space)
            currentStrokes.forEach { stroke ->
                drawStroke(stroke, currentSelection, displayScale, offsetX, offsetY)
            }
            
            // Draw existing shapes
            currentShapes.forEach { shape ->
                drawShape(shape, currentSelection, displayScale, offsetX, offsetY)
            }
            
            // Draw existing text annotations (placeholder boxes only - actual text rendered by TextAnnotationOverlay)
            currentTextAnnotations.forEach { text ->
                drawTextAnnotationPlaceholder(text, currentSelection, displayScale, offsetX, offsetY)
            }
            
            // Draw current stroke being drawn
            if (isDrawing && currentPoints.isNotEmpty()) {
                val previewStroke = InkStroke(
                    points = currentPoints,
                    color = currentToolState.currentColor,
                    strokeWidth = currentToolState.strokeWidth,
                    isHighlighter = currentToolState.currentTool.isHighlighter()
                )
                drawStrokeWithAlpha(previewStroke, alpha = 0.7f, displayScale, offsetX, offsetY)
            }
            
            // Draw current shape being drawn
            if (currentShapeStart != null && currentShapeEnd != null) {
                val previewShape = ShapeAnnotation(
                    shapeType = currentToolState.shapeType,
                    startPoint = currentShapeStart!!,
                    endPoint = currentShapeEnd!!,
                    color = currentToolState.currentColor,
                    strokeWidth = currentToolState.strokeWidth,
                    filled = currentToolState.shapeFilled
                )
                drawShapeWithAlpha(previewShape, alpha = 0.7f, displayScale, offsetX, offsetY)
            }
            
            // Draw lasso path
            if (currentLassoPoints.isNotEmpty()) {
                drawLassoPath(currentLassoPoints, displayScale, offsetX, offsetY)
            }
            
            // Draw text box
            if (currentTextBoxState.isActive) {
                drawTextBox(currentTextBoxState, displayScale, offsetX, offsetY)
            }
            
            // Draw selection
            if (!currentSelection.isEmpty) {
                drawSelection(currentSelection, displayScale, offsetX, offsetY)
            }
            
            // Draw eraser cursor (circle following stylus/finger when eraser is active)
            if (currentToolState.currentTool == AnnotationTool.ERASER && currentTouchPosition != null) {
                val screenPos = Offset(
                    currentTouchPosition!!.x * displayScale + offsetX,
                    currentTouchPosition!!.y * displayScale + offsetY
                )
                
                // Draw outer circle (white with black border for visibility on any background)
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = (currentToolState.eraserWidth * displayScale) / 2,
                    center = screenPos,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Draw black border for better visibility
                drawCircle(
                    color = Color.Black.copy(alpha = 0.8f),
                    radius = (currentToolState.eraserWidth * displayScale) / 2,
                    center = screenPos,
                    style = Stroke(width = 4.dp.toPx())
                )
                
                // Draw white circle on top
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f),
                    radius = (currentToolState.eraserWidth * displayScale) / 2,
                    center = screenPos,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Draw inner crosshair for precision
                val crosshairSize = 10.dp.toPx()
                // Black outline for crosshair
                drawLine(
                    color = Color.Black.copy(alpha = 0.8f),
                    start = Offset(screenPos.x - crosshairSize, screenPos.y),
                    end = Offset(screenPos.x + crosshairSize, screenPos.y),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.8f),
                    start = Offset(screenPos.x, screenPos.y - crosshairSize),
                    end = Offset(screenPos.x, screenPos.y + crosshairSize),
                    strokeWidth = 3.dp.toPx()
                )
                // White crosshair on top
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(screenPos.x - crosshairSize, screenPos.y),
                    end = Offset(screenPos.x + crosshairSize, screenPos.y),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(screenPos.x, screenPos.y - crosshairSize),
                    end = Offset(screenPos.x, screenPos.y + crosshairSize),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
        
        // Touch interceptor with palm rejection
        AndroidView(
            factory = { context ->
                object : View(context) {
                    // Helper function to transform screen coordinates to bitmap coordinates
                    // Reverses the zoom transformation applied in Canvas rendering.
                    // Rendering formula: screenPos = bitmapPos * displayScale * zoom + zoomedOffset
                    // Reverse: bitmapPos = (screenPos - zoomedOffset) / (displayScale * zoom)
                    private fun transformToBitmapSpace(screenX: Float, screenY: Float): Offset {
                        if (currentBitmapWidth <= 0 || currentBitmapHeight <= 0) {
                            return Offset(screenX, screenY)
                        }
                        
                        // Calculate base scale (before zoom)
                        val scaleX = width.toFloat() / currentBitmapWidth
                        val scaleY = height.toFloat() / currentBitmapHeight
                        val baseDisplayScale = minOf(scaleX, scaleY)
                        
                        // Combined scale including zoom
                        val displayScale = baseDisplayScale * currentZoomScale
                        
                        // Calculate base offsets (unzoomed)
                        val baseScaledWidth = currentBitmapWidth * baseDisplayScale
                        val baseScaledHeight = currentBitmapHeight * baseDisplayScale
                        val baseOffsetX = (width - baseScaledWidth) / 2f
                        val baseOffsetY = (height - baseScaledHeight) / 2f
                        
                        // Apply zoom transformation to offsets (same as rendering)
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val offsetX = (baseOffsetX - centerX) * currentZoomScale + centerX + currentPanOffset.x
                        val offsetY = (baseOffsetY - centerY) * currentZoomScale + centerY + currentPanOffset.y
                        
                        // Transform from screen space to bitmap space (reverse of rendering)
                        val bitmapX = (screenX - offsetX) / displayScale
                        val bitmapY = (screenY - offsetY) / displayScale
                        
                        return Offset(bitmapX, bitmapY)
                    }
                    
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        android.util.Log.d("TextBox", "InkingCanvas onTouchEvent: action=${event.actionMasked}, enabled=$currentEnabled")
                        
                        if (!currentEnabled) return false
                        
                        // Apply palm rejection
                        if (!palmRejectionManager.shouldProcessEvent(event)) {
                            return true // Consume rejected events
                        }
                        
                        // Update stylus active state
                        onStylusActiveChange(palmRejectionManager.isStylusActive())
                        
                        // If text box is active, consume all touch events to prevent page scrolling
                        val consumeEvent = currentTextBoxState.isActive && 
                                          (currentTextBoxState.mode == TextBoxMode.POSITIONING || 
                                           currentTextBoxState.mode == TextBoxMode.EDITING)
                        
                        android.util.Log.d("TextBox", "TextBox state: ${currentTextBoxState.mode}, isActive: ${currentTextBoxState.isActive}, consumeEvent: $consumeEvent")
                        
                        val handled = when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                android.util.Log.d("TextBox", "ACTION_DOWN detected")
                                handleTouchDown(event, 0)
                            }
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                val pointerIndex = event.actionIndex
                                handleTouchDown(event, pointerIndex)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                handleTouchMove(event)
                            }
                            MotionEvent.ACTION_UP -> {
                                android.util.Log.d("TextBox", "ACTION_UP detected")
                                handleTouchUp(event, 0)
                            }
                            MotionEvent.ACTION_POINTER_UP -> {
                                val pointerIndex = event.actionIndex
                                handleTouchUp(event, pointerIndex)
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                handleTouchCancel()
                            }
                            else -> false
                        }
                        
                        android.util.Log.d("TextBox", "Touch handled: $handled, returning: ${handled || consumeEvent}")
                        return handled || consumeEvent
                    }
                    
                    private fun handleTouchDown(event: MotionEvent, pointerIndex: Int): Boolean {
                        val toolType = event.getToolType(pointerIndex)
                        val screenPos = Offset(event.getX(pointerIndex), event.getY(pointerIndex))
                        val position = transformToBitmapSpace(screenPos.x, screenPos.y)
                        
                        android.util.Log.d("TextBox", "handleTouchDown: tool=${currentToolState.currentTool}, toolType=$toolType, screenPos=$screenPos, bitmapPos=$position")
                        
                        when (currentToolState.currentTool) {
                            AnnotationTool.PEN, AnnotationTool.PEN_2,
                            AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> {
                                if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                                    onStrokeStart()
                                    isDrawing = true
                                    currentPoints = listOf(
                                        StrokePoint(
                                            x = position.x,
                                            y = position.y,
                                            pressure = event.getPressure(pointerIndex),
                                            timestamp = event.eventTime
                                        )
                                    )
                                    return true
                                }
                            }
                            
                            AnnotationTool.ERASER -> {
                                if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                                    checkEraserHit(position.x, position.y, currentStrokes, onStrokeErase, currentToolState.eraserWidth)
                                    checkShapeEraserHit(position, currentShapes, onShapeErase, currentToolState.eraserWidth)
                                    return true
                                }
                            }
                            
                            AnnotationTool.SHAPES -> {
                                if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                                    onShapeStart()
                                    currentShapeStart = position
                                    currentShapeEnd = position
                                    return true
                                }
                            }
                            
                            AnnotationTool.TEXT -> {
                                android.util.Log.d("TextBox", "TEXT tool detected, textBoxState.mode: ${currentTextBoxState.mode}")
                                
                                when (currentTextBoxState.mode) {
                                    TextBoxMode.NONE -> {
                                        android.util.Log.d("TextBox", "TextBoxMode.NONE - checking for existing text annotations")
                                        // Check if tapping on existing text annotation first
                                        var tappedText = false
                                        currentTextAnnotations.forEach { textAnnotation ->
                                            val textBounds = Rect(
                                                textAnnotation.position.x,
                                                textAnnotation.position.y,
                                                textAnnotation.position.x + textAnnotation.width,
                                                textAnnotation.position.y + textAnnotation.fontSize * 2f
                                            )
                                            if (textBounds.contains(position)) {
                                                onTextTap(position)
                                                tappedText = true
                                                return@forEach
                                            }
                                        }
                                        
                                        // If no text was tapped, start drawing new text box
                                        if (!tappedText) {
                                            onTextBoxStart(position)
                                        }
                                        return true
                                    }
                                    TextBoxMode.EDITING, TextBoxMode.POSITIONING -> {
                                        android.util.Log.d("TextBox", "TextBoxMode.EDITING/POSITIONING - checking for resize handles")
                                        
                                        val bounds = currentTextBoxState.getTransformedBounds()
                                        
                                        // Transform bounds to display space for hit testing
                                        val scaleX = if (currentBitmapWidth > 0) width.toFloat() / currentBitmapWidth else 1f
                                        val scaleY = if (currentBitmapHeight > 0) height.toFloat() / currentBitmapHeight else 1f
                                        val displayScale = minOf(scaleX, scaleY)
                                        val scaledWidth = currentBitmapWidth * displayScale
                                        val scaledHeight = currentBitmapHeight * displayScale
                                        val offsetX = (width - scaledWidth) / 2
                                        val offsetY = (height - scaledHeight) / 2
                                        
                                        val displayBounds = Rect(
                                            bounds.left * displayScale + offsetX,
                                            bounds.top * displayScale + offsetY,
                                            bounds.right * displayScale + offsetX,
                                            bounds.bottom * displayScale + offsetY
                                        )
                                        
                                        // Check for resize handle hits first (using screen coordinates)
                                        // These positions must match exactly with the drawing positions
                                        val handleTouchRadius = 50f // Very large touch area for testing
                                        val handles = mapOf(
                                            "top-left" to Offset(displayBounds.left, displayBounds.top),
                                            "top-right" to Offset(displayBounds.right, displayBounds.top),
                                            "bottom-left" to Offset(displayBounds.left, displayBounds.bottom),
                                            "bottom-right" to Offset(displayBounds.right, displayBounds.bottom)
                                        )
                                        
                                        android.util.Log.d("TextBox", "Touch at screen: $screenPos, bitmap: $position")
                                        android.util.Log.d("TextBox", "Display bounds: $displayBounds")
                                        android.util.Log.d("TextBox", "Transform: scale=$displayScale, offset=($offsetX, $offsetY)")
                                        
                                        var hitHandle: String? = null
                                        var minDistance = Float.MAX_VALUE
                                        
                                        handles.forEach { (handle, handlePos) ->
                                            val distance = kotlin.math.sqrt(
                                                (screenPos.x - handlePos.x) * (screenPos.x - handlePos.x) +
                                                (screenPos.y - handlePos.y) * (screenPos.y - handlePos.y)
                                            )
                                            android.util.Log.d("TextBox", "Handle $handle at $handlePos, distance: $distance")
                                            
                                            if (distance <= handleTouchRadius && distance < minDistance) {
                                                hitHandle = handle
                                                minDistance = distance
                                            }
                                        }
                                        
                                        if (hitHandle != null) {
                                            // Start resizing
                                            isResizingTextBox = true
                                            isDraggingTextBox = false
                                            resizeHandle = hitHandle
                                            dragStartPosition = position
                                            android.util.Log.d("TextBox", "✅ Starting resize with handle: $hitHandle at distance: $minDistance")
                                            return true
                                        } else if (displayBounds.contains(screenPos)) {
                                            // Start dragging
                                            isDraggingTextBox = true
                                            isResizingTextBox = false
                                            dragStartPosition = position
                                            onTextBoxDragStart(position)
                                            android.util.Log.d("TextBox", "✅ Starting drag")
                                            return true
                                        } else {
                                            android.util.Log.d("TextBox", "❌ Touch outside text box")
                                        }
                                    }
                                    else -> {
                                        android.util.Log.d("TextBox", "Other TextBoxMode: ${currentTextBoxState.mode}")
                                        // Other modes handled in move/up events
                                    }
                                }
                            }
                            
                            AnnotationTool.LASSO -> {
                                if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                                    // Check if tapping inside existing selection
                                    if (!currentSelection.isEmpty) {
                                        val bounds = currentSelection.getTransformedBounds()
                                        if (bounds.contains(position)) {
                                            // Start dragging the selection
                                            isDraggingSelection = true
                                            dragStartPosition = position
                                            return true
                                        }
                                    }
                                    
                                    // Otherwise, start a new lasso selection
                                    onLassoStart()
                                    currentLassoPoints = listOf(position)
                                    return true
                                }
                            }
                            
                            else -> {
                                // For other tools or finger navigation
                                if (toolType == MotionEvent.TOOL_TYPE_FINGER) {
                                    onTap()
                                    return false // Let parent handle navigation
                                }
                            }
                        }
                        
                        return false
                    }
                    
                    private fun handleTouchMove(event: MotionEvent): Boolean {
                        for (i in 0 until event.pointerCount) {
                            val pointerId = event.getPointerId(i)
                            if (palmRejectionManager.isPointerRejected(pointerId)) continue
                            
                            val screenPos = Offset(event.getX(i), event.getY(i))
                            val position = transformToBitmapSpace(screenPos.x, screenPos.y)
                            val toolType = event.getToolType(i)
                            
                            when (currentToolState.currentTool) {
                                AnnotationTool.PEN, AnnotationTool.PEN_2,
                                AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> {
                                    if (isDrawing && (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER)) {
                                        val newPoint = StrokePoint(
                                            x = position.x,
                                            y = position.y,
                                            pressure = event.getPressure(i),
                                            timestamp = event.eventTime
                                        )
                                        currentPoints = currentPoints + newPoint
                                        return true
                                    }
                                }
                                
                                AnnotationTool.ERASER -> {
                                    // Update touch position for cursor feedback
                                    currentTouchPosition = position
                                    
                                    if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                                        checkEraserHit(position.x, position.y, currentStrokes, onStrokeErase, currentToolState.eraserWidth)
                                        checkShapeEraserHit(position, currentShapes, onShapeErase, currentToolState.eraserWidth)
                                        return true
                                    }
                                }
                                
                                AnnotationTool.SHAPES -> {
                                    if (currentShapeStart != null && (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER)) {
                                        currentShapeEnd = position
                                        return true
                                    }
                                }
                                
                                AnnotationTool.TEXT -> {
                                    when (currentTextBoxState.mode) {
                                        TextBoxMode.DRAWING -> {
                                            // Update text box bounds while drawing
                                            onTextBoxUpdate(position)
                                            return true
                                        }
                                        TextBoxMode.POSITIONING -> {
                                            if (isResizingTextBox && dragStartPosition != null && resizeHandle != null) {
                                                // Handle resizing with smaller, more controlled movements
                                                val delta = (position - dragStartPosition!!) * 0.8f // Less damping
                                                android.util.Log.d("TextBox", "Resizing with handle: $resizeHandle, delta: $delta")
                                                onTextBoxResize(resizeHandle!!, delta)
                                                dragStartPosition = position
                                                return true
                                            } else if (isDraggingTextBox && dragStartPosition != null) {
                                                // Handle dragging with controlled movement
                                                val delta = position - dragStartPosition!!
                                                onTextBoxDrag(delta)
                                                dragStartPosition = position
                                                return true
                                            }
                                        }
                                        else -> {
                                            // No action for other modes
                                        }
                                    }
                                }
                                
                                AnnotationTool.LASSO -> {
                                    // If dragging a selection
                                    if (isDraggingSelection && dragStartPosition != null) {
                                        val delta = position - dragStartPosition!!
                                        onSelectionDrag(delta)
                                        dragStartPosition = position
                                        return true
                                    }
                                    
                                    // Otherwise, continue drawing lasso path
                                    if (currentLassoPoints.isNotEmpty() && (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER)) {
                                        currentLassoPoints = currentLassoPoints + position
                                        onLassoUpdate(currentLassoPoints)
                                        return true
                                    }
                                }
                                
                                else -> {
                                    // Handle NONE, TEXT and any other tools
                                    // No action needed for move events on these tools
                                }
                            }
                        }
                        
                        return false
                    }
                    
                    private fun handleTouchUp(event: MotionEvent, pointerIndex: Int): Boolean {
                        val toolType = event.getToolType(pointerIndex)
                        
                        when (currentToolState.currentTool) {
                            AnnotationTool.PEN, AnnotationTool.PEN_2,
                            AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> {
                                if (isDrawing && (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER)) {
                                    isDrawing = false
                                    if (currentPoints.isNotEmpty()) {
                                        val stroke = createFinalStroke(
                                            points = currentPoints,
                                            tool = currentToolState.currentTool,
                                            toolState = currentToolState,
                                            textLines = currentTextLines
                                        )
                                        onStrokeEnd(stroke)
                                    }
                                    currentPoints = emptyList()
                                    return true
                                }
                            }
                            
                            AnnotationTool.SHAPES -> {
                                if (currentShapeStart != null && currentShapeEnd != null && (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER)) {
                                    val shape = ShapeAnnotation(
                                        shapeType = currentToolState.shapeType,
                                        startPoint = currentShapeStart!!,
                                        endPoint = currentShapeEnd!!,
                                        color = currentToolState.currentColor,
                                        strokeWidth = currentToolState.strokeWidth,
                                        filled = currentToolState.shapeFilled
                                    )
                                    onShapeEnd(shape)
                                    currentShapeStart = null
                                    currentShapeEnd = null
                                    return true
                                }
                            }
                            
                            AnnotationTool.TEXT -> {
                                when (currentTextBoxState.mode) {
                                    TextBoxMode.DRAWING -> {
                                        // Finish drawing text box
                                        onTextBoxEnd()
                                        return true
                                    }
                                    TextBoxMode.POSITIONING -> {
                                        // Finish dragging or resizing text box
                                        if (isResizingTextBox || isDraggingTextBox) {
                                            android.util.Log.d("TextBox", "Finishing resize/drag - NOT triggering edit")
                                            onTextBoxDragEnd()
                                            isResizingTextBox = false
                                            isDraggingTextBox = false
                                            resizeHandle = null
                                            dragStartPosition = null
                                            return true
                                        } else {
                                            // Only trigger edit if we weren't resizing/dragging
                                            android.util.Log.d("TextBox", "Touch up without resize/drag - could trigger edit")
                                            // Don't automatically trigger edit here - let user double-tap or use action bar
                                        }
                                    }
                                    else -> {
                                        // No action for other modes
                                    }
                                }
                            }
                            
                            AnnotationTool.LASSO -> {
                                // If we were dragging a selection, finalize it
                                if (isDraggingSelection) {
                                    isDraggingSelection = false
                                    dragStartPosition = null
                                    return true
                                }
                                
                                // Otherwise, complete the lasso path
                                if (currentLassoPoints.isNotEmpty() && (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER)) {
                                    val lassoPath = LassoPath(currentLassoPoints, true)
                                    onLassoEnd(lassoPath)
                                    currentLassoPoints = emptyList()
                                    return true
                                }
                            }
                            
                            else -> {
                                // Handle NONE, TEXT, ERASER and any other tools
                                // Clear touch position for eraser cursor
                                if (currentToolState.currentTool == AnnotationTool.ERASER) {
                                    currentTouchPosition = null
                                }
                                // No action needed for touch up events on these tools
                            }
                        }
                        
                        return false
                    }
                    
                    private fun handleTouchCancel(): Boolean {
                        isDrawing = false
                        currentPoints = emptyList()
                        currentShapeStart = null
                        currentShapeEnd = null
                        currentLassoPoints = emptyList()
                        isDraggingSelection = false
                        isDraggingTextBox = false
                        isResizingTextBox = false
                        dragStartPosition = null
                        resizeHandle = null
                        palmRejectionManager.reset()
                        return true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Creates a preview stroke for the current drawing.
 */
private fun createPreviewStroke(
    points: List<StrokePoint>,
    tool: AnnotationTool,
    toolState: ToolState,
    textLines: List<TextLine>
): InkStroke {
    val isHighlighterTool = tool.isHighlighter()
    
    val (previewPoints, previewWidth) = if (tool.isSmartHighlighter() && points.size > 1) {
        val result = snapHighlighterToTextLine(points, textLines, toolState.strokeWidth)
        result.points to result.strokeWidth
    } else {
        points to toolState.strokeWidth
    }
    
    return InkStroke(
        points = previewPoints,
        color = toolState.currentColor,
        strokeWidth = previewWidth,
        isHighlighter = isHighlighterTool
    )
}

// ==================== Drawing Functions ====================

/**
 * Draws a stroke with optional selection highlight.
 * Transforms coordinates from bitmap space to display space.
 */
private fun DrawScope.drawStroke(
    stroke: InkStroke,
    selection: Selection = Selection(),
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    if (stroke.points.isEmpty()) return
    
    val isSelected = selection.strokeIds.contains(stroke.id)
    
    // Apply selection offset if this stroke is selected
    val selectionOffsetX = if (isSelected) selection.offset.x else 0f
    val selectionOffsetY = if (isSelected) selection.offset.y else 0f
    
    val path = Path()
    val points = stroke.points
    
    // Transform first point from bitmap space to display space, with selection offset
    val firstX = (points.first().x + selectionOffsetX) * scale + offsetX
    val firstY = (points.first().y + selectionOffsetY) * scale + offsetY
    path.moveTo(firstX, firstY)
    
    if (points.size == 1) {
        drawCircle(
            color = stroke.color,
            radius = stroke.strokeWidth * scale / 2,
            center = Offset(firstX, firstY)
        )
        return
    }
    
    for (i in 1 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val midX = ((p1.x + selectionOffsetX) + (p2.x + selectionOffsetX)) / 2 * scale + offsetX
        val midY = ((p1.y + selectionOffsetY) + (p2.y + selectionOffsetY)) / 2 * scale + offsetY
        path.quadraticBezierTo(
            (p1.x + selectionOffsetX) * scale + offsetX, 
            (p1.y + selectionOffsetY) * scale + offsetY, 
            midX, 
            midY
        )
    }
    path.lineTo(
        (points.last().x + selectionOffsetX) * scale + offsetX, 
        (points.last().y + selectionOffsetY) * scale + offsetY
    )
    
    val alpha = if (stroke.isHighlighter) 0.4f else 1f
    
    // Selection glow
    if (isSelected) {
        drawPath(
            path = path,
            color = Color(0xFF6366F1).copy(alpha = 0.3f),
            style = Stroke(width = stroke.strokeWidth * scale + 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
    
    drawPath(
        path = path,
        color = stroke.color.copy(alpha = alpha),
        style = Stroke(width = stroke.strokeWidth * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (stroke.isHighlighter) BlendMode.Darken else BlendMode.SrcOver
    )
}

/**
 * Draws a shape annotation.
 * Transforms coordinates from bitmap space to display space.
 */
private fun DrawScope.drawShape(
    shape: ShapeAnnotation,
    selection: Selection = Selection(),
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val isSelected = selection.shapeIds.contains(shape.id)
    
    // Apply selection offset if this shape is selected
    val selectionOffsetX = if (isSelected) selection.offset.x else 0f
    val selectionOffsetY = if (isSelected) selection.offset.y else 0f
    
    // Transform shape coordinates from bitmap space to display space, with selection offset
    val transformedStart = Offset(
        (shape.startPoint.x + selectionOffsetX) * scale + offsetX,
        (shape.startPoint.y + selectionOffsetY) * scale + offsetY
    )
    val transformedEnd = Offset(
        (shape.endPoint.x + selectionOffsetX) * scale + offsetX,
        (shape.endPoint.y + selectionOffsetY) * scale + offsetY
    )
    
    val left = minOf(transformedStart.x, transformedEnd.x)
    val top = minOf(transformedStart.y, transformedEnd.y)
    val right = maxOf(transformedStart.x, transformedEnd.x)
    val bottom = maxOf(transformedStart.y, transformedEnd.y)
    val width = right - left
    val height = bottom - top
    
    // Selection glow
    if (isSelected) {
        drawRect(
            color = Color(0xFF6366F1).copy(alpha = 0.2f),
            topLeft = Offset(left - 4f, top - 4f),
            size = androidx.compose.ui.geometry.Size(width + 8f, height + 8f)
        )
    }
    
    val style = if (shape.filled) null else Stroke(width = shape.strokeWidth * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
    
    when (shape.shapeType) {
        ShapeType.RECTANGLE -> {
            if (shape.filled) {
                drawRect(color = shape.color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height))
            } else {
                drawRect(color = shape.color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height), style = style!!)
            }
        }
        ShapeType.CIRCLE -> {
            if (shape.filled) {
                drawOval(
                    color = shape.color,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, height)
                )
            } else {
                drawOval(
                    color = shape.color,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, height),
                    style = style!!
                )
            }
        }
        ShapeType.LINE -> {
            drawLine(color = shape.color, start = transformedStart, end = transformedEnd, strokeWidth = shape.strokeWidth * scale, cap = StrokeCap.Round)
        }
        ShapeType.ARROW -> {
            drawArrow(transformedStart, transformedEnd, shape.color, shape.strokeWidth * scale)
        }
        ShapeType.TRIANGLE -> {
            val path = Path().apply {
                moveTo(left + width / 2, top)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }
            if (shape.filled) {
                drawPath(path, shape.color)
            } else {
                drawPath(path, shape.color, style = style!!)
            }
        }
    }
}

/**
 * Draws an arrow from start to end.
 */
private fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    
    // Arrow head
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowLength = 20f
    val arrowAngle = PI / 6 // 30 degrees
    
    val x1 = end.x - arrowLength * cos(angle - arrowAngle).toFloat()
    val y1 = end.y - arrowLength * sin(angle - arrowAngle).toFloat()
    val x2 = end.x - arrowLength * cos(angle + arrowAngle).toFloat()
    val y2 = end.y - arrowLength * sin(angle + arrowAngle).toFloat()
    
    drawLine(color = color, start = end, end = Offset(x1, y1), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = end, end = Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

/**
 * Draws a text annotation placeholder (just the outline).
 * Actual text is rendered by TextAnnotationOverlay.
 */
private fun DrawScope.drawTextAnnotationPlaceholder(
    text: TextAnnotation,
    selection: Selection = Selection(),
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val isSelected = selection.textIds.contains(text.id)
    
    // Apply selection offset if this text is selected
    val selectionOffsetX = if (isSelected) selection.offset.x else 0f
    val selectionOffsetY = if (isSelected) selection.offset.y else 0f
    
    // Transform position from bitmap space to display space, with selection offset
    val transformedPosition = Offset(
        (text.position.x + selectionOffsetX) * scale + offsetX,
        (text.position.y + selectionOffsetY) * scale + offsetY
    )
    
    val transformedWidth = text.width * scale
    val scaledFontSize = text.fontSize * scale
    val scaledPadding = text.padding * scale
    
    // Calculate text bounds
    val lines = text.text.split('\n')
    val lineHeight = scaledFontSize * 1.2f
    val textHeight = lines.size * lineHeight
    
    // Skip background drawing - transparent background
    
    // Draw selection background only
    if (isSelected) {
        drawRect(
            color = Color(0xFF6366F1).copy(alpha = 0.15f),
            topLeft = transformedPosition - Offset(4f, 4f),
            size = androidx.compose.ui.geometry.Size(transformedWidth + 8f, textHeight + 8f)
        )
    }
}

/**
 * Draws the lasso selection path.
 * Transforms coordinates from bitmap space to display space.
 */
private fun DrawScope.drawLassoPath(
    points: List<Offset>,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    if (points.size < 2) return
    
    val path = Path().apply {
        val firstPoint = points.first()
        moveTo(firstPoint.x * scale + offsetX, firstPoint.y * scale + offsetY)
        for (i in 1 until points.size) {
            val point = points[i]
            lineTo(point.x * scale + offsetX, point.y * scale + offsetY)
        }
    }
    
    // Dashed line
    drawPath(
        path = path,
        color = Color(0xFF6366F1),
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
        )
    )
}

// ==================== Utility Functions ====================

/**
 * Draws the text box overlay.
 * Transforms coordinates from bitmap space to display space.
 */
private fun DrawScope.drawTextBox(
    textBoxState: TextBoxState,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val bounds = textBoxState.getTransformedBounds()
    
    // Transform bounds from bitmap space to display space
    val transformedLeft = bounds.left * scale + offsetX
    val transformedTop = bounds.top * scale + offsetY
    val transformedWidth = bounds.width * scale
    val transformedHeight = bounds.height * scale
    
    when (textBoxState.mode) {
        TextBoxMode.DRAWING -> {
            // Draw dashed rectangle while drawing
            drawRect(
                color = Color(0xFF6366F1).copy(alpha = 0.6f),
                topLeft = Offset(transformedLeft, transformedTop),
                size = androidx.compose.ui.geometry.Size(transformedWidth, transformedHeight),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                )
            )
            
            // Show instruction text
            if (transformedWidth > 100f && transformedHeight > 30f) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = Color(0xFF6366F1).toArgb()
                        textSize = 14f * scale
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    
                    canvas.nativeCanvas.drawText(
                        "Text Box",
                        transformedLeft + transformedWidth / 2,
                        transformedTop + transformedHeight / 2,
                        paint
                    )
                }
            }
        }
        TextBoxMode.EDITING, TextBoxMode.POSITIONING -> {
            // Draw solid rectangle border only (no background)
            drawRect(
                color = Color(0xFF6366F1),
                topLeft = Offset(transformedLeft, transformedTop),
                size = androidx.compose.ui.geometry.Size(transformedWidth, transformedHeight),
                style = Stroke(width = 2f)
            )
            
            // Draw text content if available
            if (textBoxState.text.isNotEmpty()) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = Color.Black.toArgb()
                        textSize = 16f * scale
                        isAntiAlias = true
                    }
                    
                    // Proper text wrapping within the text box
                    val lines = textBoxState.text.split('\n')
                    val lineHeight = 20f * scale
                    val padding = 8f * scale
                    val availableWidth = transformedWidth - padding * 2
                    
                    var currentY = transformedTop + padding + lineHeight
                    
                    lines.forEach { line ->
                        if (currentY > transformedTop + transformedHeight - padding) return@forEach // Stop if out of bounds
                        
                        // Word wrapping
                        val words = line.split(' ')
                        var currentLine = ""
                        
                        words.forEach { word ->
                            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                            val textWidth = paint.measureText(testLine)
                            
                            if (textWidth <= availableWidth || currentLine.isEmpty()) {
                                currentLine = testLine
                            } else {
                                // Draw current line and start new line
                                canvas.nativeCanvas.drawText(
                                    currentLine,
                                    transformedLeft + padding,
                                    currentY,
                                    paint
                                )
                                currentLine = word
                                currentY += lineHeight
                                if (currentY > transformedTop + transformedHeight - padding) return@forEach
                            }
                        }
                        
                        // Draw remaining text
                        if (currentLine.isNotEmpty() && currentY <= transformedTop + transformedHeight - padding) {
                            canvas.nativeCanvas.drawText(
                                currentLine,
                                transformedLeft + padding,
                                currentY,
                                paint
                            )
                            currentY += lineHeight
                        }
                    }
                }
            } else {
                // Show placeholder text
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = Color.Gray.toArgb()
                        textSize = 14f * scale
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    
                    canvas.nativeCanvas.drawText(
                        "Tap to edit text",
                        transformedLeft + transformedWidth / 2,
                        transformedTop + transformedHeight / 2,
                        paint
                    )
                }
            }
            
            // Draw resize handles for editing and positioning modes
            if (textBoxState.mode == TextBoxMode.EDITING || textBoxState.mode == TextBoxMode.POSITIONING) {
                val handleSize = 24f // Much larger handles
                val handles = listOf(
                    Pair("top-left", Offset(transformedLeft, transformedTop)),
                    Pair("top-right", Offset(transformedLeft + transformedWidth, transformedTop)),
                    Pair("bottom-left", Offset(transformedLeft, transformedTop + transformedHeight)),
                    Pair("bottom-right", Offset(transformedLeft + transformedWidth, transformedTop + transformedHeight))
                )
                
                // Debug: Log handle positions
                handles.forEach { (name, pos) ->
                    android.util.Log.d("TextBox", "Drawing handle $name at display position: $pos")
                }
                
                handles.forEach { (handleName, handle) ->
                    // Draw large touch area indicator (semi-transparent)
                    drawCircle(
                        color = Color(0xFF6366F1).copy(alpha = 0.1f),
                        radius = 30f, // Large touch area
                        center = handle
                    )
                    
                    // Draw handle shadow for better visibility
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.4f),
                        radius = handleSize/2 + 2f,
                        center = handle + Offset(2f, 2f)
                    )
                    // Draw handle background (larger for easier touch)
                    drawCircle(
                        color = Color.White,
                        radius = handleSize/2,
                        center = handle
                    )
                    // Draw handle border
                    drawCircle(
                        color = Color(0xFF6366F1),
                        radius = handleSize/2,
                        center = handle,
                        style = Stroke(width = 4f)
                    )
                    // Draw inner circle for better visibility
                    drawCircle(
                        color = Color(0xFF6366F1),
                        radius = 4f,
                        center = handle
                    )
                    
                    // Draw resize arrows to indicate direction
                    when (handleName) {
                        "top-left" -> {
                            // Draw diagonal arrows
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(-8f, -8f),
                                end = handle + Offset(-4f, -4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(8f, 8f),
                                end = handle + Offset(4f, 4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        "top-right" -> {
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(8f, -8f),
                                end = handle + Offset(4f, -4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(-8f, 8f),
                                end = handle + Offset(-4f, 4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        "bottom-left" -> {
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(-8f, 8f),
                                end = handle + Offset(-4f, 4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(8f, -8f),
                                end = handle + Offset(4f, -4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        "bottom-right" -> {
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(8f, 8f),
                                end = handle + Offset(4f, 4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color(0xFF6366F1),
                                start = handle + Offset(-8f, -8f),
                                end = handle + Offset(-4f, -4f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
        else -> {
            // No drawing for NONE mode
        }
    }
}

/**
 * Checks if the eraser position hits any stroke.
 */
private fun checkEraserHit(
    x: Float,
    y: Float,
    strokes: List<InkStroke>,
    onStrokeErase: (String) -> Unit,
    eraserRadius: Float
) {
    val hitPoint = Offset(x, y)
    
    for (stroke in strokes) {
        var isHit = false
        val points = stroke.points
        
        for (point in points) {
            val dx = abs(point.x - x)
            val dy = abs(point.y - y)
            if (dx < eraserRadius && dy < eraserRadius) {
                isHit = true
                break
            }
        }
        
        if (!isHit && points.size > 1) {
            for (i in 0 until points.size - 1) {
                val p1 = Offset(points[i].x, points[i].y)
                val p2 = Offset(points[i + 1].x, points[i + 1].y)
                val dist = distanceToLineSegment(hitPoint, p1, p2)
                if (dist < eraserRadius + stroke.strokeWidth / 2) {
                    isHit = true
                    break
                }
            }
        }
        
        if (isHit) {
            onStrokeErase(stroke.id)
            return
        }
    }
}

/**
 * Checks if eraser hits any shape.
 */
private fun checkShapeEraserHit(
    position: Offset,
    shapes: List<ShapeAnnotation>,
    onShapeErase: (String) -> Unit,
    eraserRadius: Float
) {
    for (shape in shapes) {
        val bounds = shape.getBounds()
        val expandedBounds = Rect(
            bounds.left - eraserRadius,
            bounds.top - eraserRadius,
            bounds.right + eraserRadius,
            bounds.bottom + eraserRadius
        )
        
        if (expandedBounds.contains(position)) {
            onShapeErase(shape.id)
            return
        }
    }
}

/**
 * Calculates the minimum distance from a point to a line segment.
 */
private fun distanceToLineSegment(point: Offset, lineStart: Offset, lineEnd: Offset): Float {
    val lineLength = (lineEnd - lineStart).getDistance()
    if (lineLength < 0.001f) {
        return (point - lineStart).getDistance()
    }
    
    val t = ((point - lineStart).dotProduct(lineEnd - lineStart) / (lineLength * lineLength)).coerceIn(0f, 1f)
    val projection = lineStart + (lineEnd - lineStart) * t
    
    return (point - projection).getDistance()
}

/**
 * Dot product of two 2D vectors.
 */
private fun Offset.dotProduct(other: Offset): Float = this.x * other.x + this.y * other.y

// ==================== Drawing Functions ====================

/**
 * Draws a stroke on the canvas with specified alpha.
 */
private fun DrawScope.drawStrokeWithAlpha(
    stroke: InkStroke,
    alpha: Float = 1f,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    if (stroke.points.isEmpty()) return
    
    val path = Path()
    val points = stroke.points
    
    // Transform first point from bitmap space to display space
    val firstX = points.first().x * scale + offsetX
    val firstY = points.first().y * scale + offsetY
    path.moveTo(firstX, firstY)
    
    if (points.size == 1) {
        drawCircle(
            color = stroke.color.copy(alpha = alpha),
            radius = stroke.strokeWidth * scale / 2,
            center = Offset(firstX, firstY)
        )
        return
    }
    
    for (i in 1 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val midX = (p1.x + p2.x) / 2 * scale + offsetX
        val midY = (p1.y + p2.y) / 2 * scale + offsetY
        path.quadraticBezierTo(p1.x * scale + offsetX, p1.y * scale + offsetY, midX, midY)
    }
    path.lineTo(points.last().x * scale + offsetX, points.last().y * scale + offsetY)
    
    val finalAlpha = if (stroke.isHighlighter) alpha * 0.4f else alpha
    
    drawPath(
        path = path,
        color = stroke.color.copy(alpha = finalAlpha),
        style = Stroke(width = stroke.strokeWidth * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (stroke.isHighlighter) BlendMode.Darken else BlendMode.SrcOver
    )
}

/**
 * Draws a shape annotation on the canvas with specified alpha.
 * Transforms coordinates from bitmap space to display space.
 */
private fun DrawScope.drawShapeWithAlpha(
    shape: ShapeAnnotation,
    alpha: Float = 1f,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val color = shape.color.copy(alpha = alpha)
    val style = if (shape.filled) {
        androidx.compose.ui.graphics.drawscope.Fill
    } else {
        Stroke(width = shape.strokeWidth * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    
    // Transform coordinates from bitmap space to display space
    val transformedStart = Offset(
        shape.startPoint.x * scale + offsetX,
        shape.startPoint.y * scale + offsetY
    )
    val transformedEnd = Offset(
        shape.endPoint.x * scale + offsetX,
        shape.endPoint.y * scale + offsetY
    )
    
    when (shape.shapeType) {
        com.osnotes.app.domain.model.ShapeType.RECTANGLE -> {
            drawRect(
                color = color,
                topLeft = Offset(
                    kotlin.math.min(transformedStart.x, transformedEnd.x),
                    kotlin.math.min(transformedStart.y, transformedEnd.y)
                ),
                size = androidx.compose.ui.geometry.Size(
                    kotlin.math.abs(transformedEnd.x - transformedStart.x),
                    kotlin.math.abs(transformedEnd.y - transformedStart.y)
                ),
                style = style
            )
        }
        com.osnotes.app.domain.model.ShapeType.CIRCLE -> {
            val left = kotlin.math.min(transformedStart.x, transformedEnd.x)
            val top = kotlin.math.min(transformedStart.y, transformedEnd.y)
            val width = kotlin.math.abs(transformedEnd.x - transformedStart.x)
            val height = kotlin.math.abs(transformedEnd.y - transformedStart.y)
            
            drawOval(
                color = color,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                style = style
            )
        }
        com.osnotes.app.domain.model.ShapeType.TRIANGLE -> {
            val left = kotlin.math.min(transformedStart.x, transformedEnd.x)
            val top = kotlin.math.min(transformedStart.y, transformedEnd.y)
            val right = kotlin.math.max(transformedStart.x, transformedEnd.x)
            val bottom = kotlin.math.max(transformedStart.y, transformedEnd.y)
            val width = right - left
            
            val path = Path().apply {
                moveTo(left + width / 2, top)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(path, color, style = style)
        }
        com.osnotes.app.domain.model.ShapeType.ARROW -> {
            drawArrow(transformedStart, transformedEnd, color, shape.strokeWidth * scale)
        }
        else -> {
            // Draw as line for other shapes (LINE)
            drawLine(
                color = color,
                start = transformedStart,
                end = transformedEnd,
                strokeWidth = shape.strokeWidth * scale,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Draws a text annotation on the canvas with specified alpha.
 */
private fun DrawScope.drawTextAnnotationWithAlpha(text: TextAnnotation, alpha: Float = 1f) {
    // This is a simplified text drawing - in practice you'd use proper text measurement
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = text.color.copy(alpha = alpha).toArgb()
            textSize = text.fontSize
            isAntiAlias = true
            isFakeBoldText = text.fontWeight >= 700
            textSkewX = if (text.isItalic) -0.25f else 0f
        }
        
        canvas.nativeCanvas.drawText(
            text.text,
            text.position.x,
            text.position.y + text.fontSize, // Adjust for baseline
            paint
        )
    }
}

/**
 * Draws the selection bounds.
 * Transforms coordinates from bitmap space to display space.
 */
private fun DrawScope.drawSelection(
    selection: Selection,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    if (selection.isEmpty) return
    
    val bounds = selection.getTransformedBounds()
    
    // Transform bounds from bitmap space to display space
    val transformedLeft = bounds.left * scale + offsetX
    val transformedTop = bounds.top * scale + offsetY
    val transformedWidth = bounds.width * scale
    val transformedHeight = bounds.height * scale
    
    // Draw selection rectangle only (no resize handles)
    drawRect(
        color = Color.Blue.copy(alpha = 0.3f),
        topLeft = Offset(transformedLeft, transformedTop),
        size = androidx.compose.ui.geometry.Size(transformedWidth, transformedHeight),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
    )
}

