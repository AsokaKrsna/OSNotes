package com.osnotes.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osnotes.app.domain.model.AnnotationTool

/**
 * Per-tool preset storage.
 * Each tool remembers its own color and stroke width.
 */
data class ToolPresetState(
    val penColor: Color = Color.Black,
    val penWidth: Float = 3f,
    val penRecentColors: List<Color> = listOf(Color.Black, Color(0xFF2563EB), Color(0xFFDC2626)),
    
    val highlighterColor: Color = Color(0xFFFFEB3B),
    val highlighterWidth: Float = 20f,
    val highlighterRecentColors: List<Color> = listOf(Color(0xFFFFEB3B), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFE91E63)),
    
    val eraserWidth: Float = 20f,
    
    val shapeColor: Color = Color.Black,
    val shapeWidth: Float = 2f,
    val shapeFill: Boolean = false
)

/**
 * Color palette for tool customization.
 */
val PRESET_COLORS = listOf(
    Color.Black,
    Color(0xFF424242),
    Color(0xFF795548),
    Color(0xFFDC2626), // Red
    Color(0xFFF97316), // Orange
    Color(0xFFFBBF24), // Amber
    Color(0xFFFFEB3B), // Yellow (highlighter)
    Color(0xFF84CC16), // Lime
    Color(0xFF22C55E), // Green
    Color(0xFF06B6D4), // Cyan
    Color(0xFF2563EB), // Blue
    Color(0xFF7C3AED), // Violet
    Color(0xFFEC4899), // Pink
    Color.White
)

/**
 * Tool preset popup that appears when tapping a selected tool.
 * Shows color palette and stroke width slider.
 */
@Composable
fun ToolPresetPopup(
    tool: AnnotationTool,
    currentColor: Color,
    currentWidth: Float,
    recentColors: List<Color>,
    isVisible: Boolean,
    isHorizontal: Boolean,
    onColorSelected: (Color) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        val shape = RoundedCornerShape(16.dp)
        
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
                .padding(12.dp)
                .widthIn(max = if (isHorizontal) 320.dp else 280.dp) // Responsive width
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tool name header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        getToolDisplayName(tool),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    
                    // Close button for better UX
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Recent colors ribbon
                if (recentColors.isNotEmpty()) {
                    Text(
                        "Recent",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentColors) { color ->
                            ColorCircle(
                                color = color,
                                isSelected = color == currentColor,
                                onClick = { onColorSelected(color) }
                            )
                        }
                    }
                }
                
                // Color palette
                Text(
                    "Colors",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                
                // Color grid - responsive layout
                if (isHorizontal) {
                    // Single row for horizontal toolbar
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(PRESET_COLORS) { color ->
                            ColorCircle(
                                color = color,
                                isSelected = color == currentColor,
                                onClick = { onColorSelected(color) }
                            )
                        }
                    }
                } else {
                    // Grid layout for vertical toolbars
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PRESET_COLORS.take(7).forEach { color ->
                                ColorCircle(
                                    color = color,
                                    isSelected = color == currentColor,
                                    onClick = { onColorSelected(color) },
                                    size = 28
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PRESET_COLORS.drop(7).forEach { color ->
                                ColorCircle(
                                    color = color,
                                    isSelected = color == currentColor,
                                    onClick = { onColorSelected(color) },
                                    size = 28
                                )
                            }
                        }
                    }
                }
                
                // Stroke width slider
                if (tool != AnnotationTool.ERASER) {
                    Text(
                        "Size: ${currentWidth.toInt()}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    
                    val widthRange = when (tool) {
                        AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> 10f..40f
                        AnnotationTool.PEN, AnnotationTool.PEN_2 -> 1f..10f
                        else -> 1f..20f
                    }
                    
                    Slider(
                        value = currentWidth,
                        onValueChange = onWidthChanged,
                        valueRange = widthRange,
                        modifier = Modifier.width(180.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6366F1),
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                } else {
                    // Eraser width slider
                    Text(
                        "Eraser Size: ${currentWidth.toInt()}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    
                    Slider(
                        value = currentWidth,
                        onValueChange = onWidthChanged,
                        valueRange = 10f..60f,
                        modifier = Modifier.width(180.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6366F1),
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    size: Int = 32
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color(0xFF6366F1), CircleShape)
                } else if (color == Color.White || color.luminance() > 0.9f) {
                    Modifier.border(1.dp, Color.Gray, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
    )
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

private fun getToolDisplayName(tool: AnnotationTool): String = when (tool) {
    AnnotationTool.PEN -> "Pen"
    AnnotationTool.PEN_2 -> "Pen 2"
    AnnotationTool.HIGHLIGHTER -> "Highlighter"
    AnnotationTool.HIGHLIGHTER_2 -> "Smart Highlighter"
    AnnotationTool.ERASER -> "Eraser"
    AnnotationTool.LASSO -> "Lasso"
    AnnotationTool.TEXT -> "Text"
    AnnotationTool.SHAPES -> "Shapes"
    else -> "Tool"
}
