package com.osnotes.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osnotes.app.domain.model.AnnotationTool
import com.osnotes.app.domain.model.ToolPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Advanced toolbar with preset system.
 * - Tap: Select tool
 * - Tap again (on selected tool): Show presets popup
 * - Long press: Open customization panel
 */
@Composable
fun AdvancedToolbar(
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    recentColors: List<Color>,
    presets: Map<AnnotationTool, List<ToolPreset>>,
    isHorizontal: Boolean = false,
    onToolSelected: (AnnotationTool) -> Unit,
    onPresetSelected: (ToolPreset) -> Unit,
    onCustomize: (AnnotationTool) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    onOpenPageManager: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    var showPresetsFor by remember { mutableStateOf<AnnotationTool?>(null) }
    var showCustomizeFor by remember { mutableStateOf<AnnotationTool?>(null) }
    val scope = rememberCoroutineScope()
    
    Box(modifier = modifier) {
        // Main toolbar
        Box(
            modifier = Modifier
                .padding(8.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = shape
                )
                .padding(8.dp)
        ) {
            if (isHorizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarButtons(
                        currentTool = currentTool,
                        currentColor = currentColor,
                        onToolTap = { tool ->
                            if (tool == currentTool && tool.hasPresets()) {
                                showPresetsFor = tool
                            } else {
                                onToolSelected(tool)
                                showPresetsFor = null
                            }
                        },
                        onToolLongPress = { tool ->
                            if (tool.isCustomizable()) {
                                showCustomizeFor = tool
                            }
                        },
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onAddPage = onAddPage,
                        onOpenPageManager = onOpenPageManager,
                        canUndo = canUndo,
                        canRedo = canRedo
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ToolbarButtons(
                        currentTool = currentTool,
                        currentColor = currentColor,
                        onToolTap = { tool ->
                            if (tool == currentTool && tool.hasPresets()) {
                                showPresetsFor = tool
                            } else {
                                onToolSelected(tool)
                                showPresetsFor = null
                            }
                        },
                        onToolLongPress = { tool ->
                            if (tool.isCustomizable()) {
                                showCustomizeFor = tool
                            }
                        },
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onAddPage = onAddPage,
                        onOpenPageManager = onOpenPageManager,
                        canUndo = canUndo,
                        canRedo = canRedo
                    )
                }
            }
        }
        
        // Presets popup
        AnimatedVisibility(
            visible = showPresetsFor != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(if (isHorizontal) Alignment.BottomCenter else Alignment.CenterStart)
        ) {
            showPresetsFor?.let { tool ->
                PresetsPopup(
                    tool = tool,
                    presets = presets[tool] ?: emptyList(),
                    recentColors = recentColors,
                    onPresetSelected = { preset ->
                        onPresetSelected(preset)
                        showPresetsFor = null
                    },
                    onColorSelected = { color ->
                        onColorSelected(color)
                        showPresetsFor = null
                    },
                    onDismiss = { showPresetsFor = null }
                )
            }
        }
        
        // Customization panel
        if (showCustomizeFor != null) {
            CustomizationPanel(
                tool = showCustomizeFor!!,
                currentColor = currentColor,
                currentStrokeWidth = currentStrokeWidth,
                recentColors = recentColors,
                onColorSelected = onColorSelected,
                onStrokeWidthChanged = onStrokeWidthChanged,
                onDismiss = { showCustomizeFor = null }
            )
        }
    }
}

@Composable
private fun ToolbarButtons(
    currentTool: AnnotationTool,
    currentColor: Color,
    onToolTap: (AnnotationTool) -> Unit,
    onToolLongPress: (AnnotationTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    onOpenPageManager: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    val tools = listOf(
        AnnotationTool.PEN to Icons.Default.Edit,
        AnnotationTool.HIGHLIGHTER to Icons.Default.Highlight,
        AnnotationTool.ERASER to Icons.Outlined.CleaningServices,
        AnnotationTool.LASSO to Icons.Outlined.Gesture,
        AnnotationTool.TEXT to Icons.Default.TextFields,
        AnnotationTool.SHAPES to Icons.Outlined.CropSquare
    )
    
    tools.forEach { (tool, icon) ->
        AdvancedToolButton(
            icon = icon,
            label = tool.name.lowercase().replaceFirstChar { it.uppercase() },
            isSelected = currentTool == tool,
            indicatorColor = if (tool == AnnotationTool.PEN || tool == AnnotationTool.HIGHLIGHTER) currentColor else null,
            onTap = { onToolTap(tool) },
            onLongPress = { onToolLongPress(tool) }
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp).width(8.dp))
    
    // Divider
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.2f))
    )
    
    Spacer(modifier = Modifier.height(8.dp).width(8.dp))
    
    // Undo/Redo
    SimpleToolButton(
        icon = Icons.Default.Undo,
        label = "Undo",
        enabled = canUndo,
        onClick = onUndo
    )
    
    SimpleToolButton(
        icon = Icons.Default.Redo,
        label = "Redo",
        enabled = canRedo,
        onClick = onRedo
    )
    
    Spacer(modifier = Modifier.height(8.dp).width(8.dp))
    
    // Add Page & Page Manager
    SimpleToolButton(
        icon = Icons.Default.Add,
        label = "Add Page",
        tint = Color(0xFF6366F1),
        onClick = onAddPage
    )
    
    SimpleToolButton(
        icon = Icons.Outlined.GridView,
        label = "Pages",
        onClick = onOpenPageManager
    )
}

@Composable
private fun AdvancedToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    indicatorColor: Color? = null,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF6366F1) else Color.Transparent
    val iconTint = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        
        // Color indicator dot
        if (indicatorColor != null && isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
                    .border(1.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun SimpleToolButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.3f)
        tint != null -> tint
        else -> Color.White.copy(alpha = 0.8f)
    }
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Presets popup showing available presets and recent colors.
 */
@Composable
private fun PresetsPopup(
    tool: AnnotationTool,
    presets: List<ToolPreset>,
    recentColors: List<Color>,
    onPresetSelected: (ToolPreset) -> Unit,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    
    Surface(
        shape = shape,
        color = Color(0xFF2A2A2A),
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(4.dp)
            .widthIn(max = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Presets row
            if (presets.isNotEmpty()) {
                Text(
                    "Presets",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { preset ->
                        PresetChip(
                            preset = preset,
                            onClick = { onPresetSelected(preset) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Recent colors row (mandatory)
            Text(
                "Recent Colors",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentColors) { color ->
                    ColorDot(
                        color = color,
                        onClick = { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: ToolPreset,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(preset.color))
            )
            Text(
                preset.name,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ColorDot(
    color: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

/**
 * Full customization panel for a tool.
 */
@Composable
private fun CustomizationPanel(
    tool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    recentColors: List<Color>,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    
    // Full color palette
    val colorPalette = listOf(
        Color.Black, Color.White,
        Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF60A5FA),
        Color(0xFFDC2626), Color(0xFFEF4444), Color(0xFFF87171),
        Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399),
        Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFFCD34D),
        Color(0xFF7C3AED), Color(0xFF8B5CF6), Color(0xFFA78BFA),
        Color(0xFFEC4899), Color(0xFFF472B6), Color(0xFFF9A8D4)
    )
    
    Surface(
        shape = shape,
        color = Color(0xFF1A1A1A),
        shadowElevation = 16.dp,
        modifier = Modifier
            .padding(16.dp)
            .widthIn(max = 320.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Customize ${tool.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recent Colors
            Text(
                "Recent Colors",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentColors) { color ->
                    ColorDot(
                        color = color,
                        isSelected = color == currentColor,
                        onClick = { onColorSelected(color) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Full Color Palette
            Text(
                "All Colors",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Color grid (5 columns)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colorPalette.chunked(5).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { color ->
                            ColorDot(
                                color = color,
                                isSelected = color == currentColor,
                                onClick = { onColorSelected(color) }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stroke Width
            Text(
                "Stroke Width",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Thin preview
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                )
                
                Slider(
                    value = currentStrokeWidth,
                    onValueChange = onStrokeWidthChanged,
                    valueRange = 1f..20f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6366F1),
                        activeTrackColor = Color(0xFF6366F1),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                
                // Thick preview
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                )
            }
            
            Text(
                "${currentStrokeWidth.toInt()}px",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// Extension functions
private fun AnnotationTool.hasPresets(): Boolean = when (this) {
    AnnotationTool.PEN, AnnotationTool.PEN_2,
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> true
    else -> false
}

private fun AnnotationTool.isCustomizable(): Boolean = when (this) {
    AnnotationTool.PEN, AnnotationTool.PEN_2,
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2,
    AnnotationTool.ERASER, AnnotationTool.SHAPES -> true
    else -> false
}
