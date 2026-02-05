package com.osnotes.app.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * Annotation tools available in the editor.
 */
enum class AnnotationTool {
    NONE,
    PEN,
    PEN_2,           // Secondary pen preset
    HIGHLIGHTER,
    HIGHLIGHTER_2,   // Smart highlighter that snaps to text
    ERASER,
    LASSO,
    TEXT,
    SHAPES
}

/**
 * Tool state with current settings.
 */
data class ToolState(
    val currentTool: AnnotationTool = AnnotationTool.PEN,
    val currentColor: Color = Color.Black,      // Current active color (for toolbar display)
    val strokeWidth: Float = 3f,                // Current active width (for toolbar display)
    val eraserWidth: Float = 20f,
    val opacity: Float = 1f,
    val pressureSensitivity: Boolean = true,
    val shapeType: ShapeType = ShapeType.RECTANGLE,
    val shapeFilled: Boolean = false,
    val textSize: Float = 16f,
    // Per-tool settings (persist independently)
    val penColor: Color = Color.Black,
    val penWidth: Float = 3f,
    val highlighterColor: Color = Color.Yellow,
    val highlighterWidth: Float = 20f,
    val shapeColor: Color = Color.Black,
    val shapeWidth: Float = 3f
)

/**
 * Shape types for shape tool.
 */
enum class ShapeType {
    RECTANGLE,
    CIRCLE,
    LINE,
    ARROW,
    TRIANGLE
}

/**
 * Preset configuration for a tool.
 */
data class ToolPreset(
    val id: String,
    val name: String,
    val tool: AnnotationTool,
    val color: Int,
    val strokeWidth: Float,
    val opacity: Float = 1f,
    val isDefault: Boolean = false
)

/**
 * Page information with UUID for stable identification.
 */
data class PageInfo(
    val id: String = java.util.UUID.randomUUID().toString(),
    val index: Int,
    val templateId: String? = null,
    val width: Float = 595f,  // A4 width in points
    val height: Float = 842f  // A4 height in points
)

/**
 * Represents a shape annotation (rectangle, circle, line, arrow, triangle).
 */
data class ShapeAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val shapeType: ShapeType,
    val startPoint: Offset,
    val endPoint: Offset,
    val color: Color,
    val strokeWidth: Float,
    val filled: Boolean = false,
    val pageNumber: Int = 0
) {
    /**
     * Returns the bounding rectangle of the shape.
     */
    fun getBounds(): Rect {
        val left = minOf(startPoint.x, endPoint.x)
        val top = minOf(startPoint.y, endPoint.y)
        val right = maxOf(startPoint.x, endPoint.x)
        val bottom = maxOf(startPoint.y, endPoint.y)
        return Rect(left, top, right, bottom)
    }
    
    /**
     * Returns the center point of the shape.
     */
    fun getCenter(): Offset {
        return Offset(
            (startPoint.x + endPoint.x) / 2,
            (startPoint.y + endPoint.y) / 2
        )
    }
}

/**
 * Represents a text annotation with markdown support.
 */
data class TextAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val position: Offset,
    val color: Color,
    val fontSize: Float = 16f,
    val fontWeight: Int = 400, // 400 = normal, 700 = bold
    val isItalic: Boolean = false,
    val pageNumber: Int = 0,
    val width: Float = 200f, // Max width before wrap
    val rotation: Float = 0f,
    val hasMarkdown: Boolean = false, // Whether text contains markdown formatting
    val backgroundColor: Color = Color.Transparent, // Background color for text box
    val padding: Float = 8f // Padding around text
) {
    /**
     * Returns approximate bounding rectangle.
     */
    fun getBounds(measuredWidth: Float, measuredHeight: Float): Rect {
        return Rect(
            position.x - padding,
            position.y - padding,
            position.x + measuredWidth + padding,
            position.y + measuredHeight + padding
        )
    }
    
    /**
     * Returns the actual text bounds without padding.
     */
    fun getTextBounds(measuredWidth: Float, measuredHeight: Float): Rect {
        return Rect(
            position.x,
            position.y,
            position.x + measuredWidth,
            position.y + measuredHeight
        )
    }
}

/**
 * Represents a selection of annotations (for lasso tool).
 */
data class Selection(
    val strokeIds: Set<String> = emptySet(),
    val shapeIds: Set<String> = emptySet(),
    val textIds: Set<String> = emptySet(),
    val bounds: Rect = Rect.Zero,
    val offset: Offset = Offset.Zero // Current drag offset
) {
    val isEmpty: Boolean get() = strokeIds.isEmpty() && shapeIds.isEmpty() && textIds.isEmpty()
    val itemCount: Int get() = strokeIds.size + shapeIds.size + textIds.size
    
    /**
     * Returns the transformed bounds accounting for offset.
     */
    fun getTransformedBounds(): Rect {
        return Rect(
            bounds.left + offset.x,
            bounds.top + offset.y,
            bounds.right + offset.x,
            bounds.bottom + offset.y
        )
    }
}

/**
 * Lasso selection path points.
 */
data class LassoPath(
    val points: List<Offset> = emptyList(),
    val isClosed: Boolean = false
) {
    /**
     * Returns the bounding rectangle of the lasso path.
     */
    fun getBounds(): Rect {
        if (points.isEmpty()) return Rect.Zero
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return Rect(minX, minY, maxX, maxY)
    }
    
    /**
     * Checks if a point is inside the lasso polygon using ray casting.
     */
    fun containsPoint(point: Offset): Boolean {
        if (points.size < 3) return false
        
        var inside = false
        var j = points.size - 1
        
        for (i in points.indices) {
            val pi = points[i]
            val pj = points[j]
            
            if ((pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x) {
                inside = !inside
            }
            j = i
        }
        
        return inside
    }
}

/**
 * Text box state for advanced text editing.
 */
data class TextBoxState(
    val isActive: Boolean = false,
    val mode: TextBoxMode = TextBoxMode.NONE,
    val bounds: Rect = Rect.Zero,
    val text: String = "",
    val isDragging: Boolean = false,
    val dragOffset: Offset = Offset.Zero
) {
    val isEmpty: Boolean get() = !isActive || text.isEmpty()
    
    /**
     * Returns the transformed bounds accounting for drag offset.
     */
    fun getTransformedBounds(): Rect {
        return Rect(
            bounds.left + dragOffset.x,
            bounds.top + dragOffset.y,
            bounds.right + dragOffset.x,
            bounds.bottom + dragOffset.y
        )
    }
}

/**
 * Text box interaction modes.
 */
enum class TextBoxMode {
    NONE,           // No text box active
    DRAWING,        // Drawing the text box bounds
    EDITING,        // Editing text inside the box
    POSITIONING     // Dragging to reposition the box
}


