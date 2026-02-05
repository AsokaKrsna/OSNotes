package com.osnotes.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osnotes.app.domain.model.AnnotationTool

/**
 * Adaptive glassmorphic toolbar with frosted glass effect.
 * 
 * Interactions:
 * - Tap tool = select it
 * - Tap selected tool again = show preset popup
 * - Long press = show customization panel
 * 
 * @param isLightBackground Set to true when toolbar is over a light/white PDF page
 * @param showPresetPopup Callback when user wants to see presets (taps selected tool)
 */
@Composable
fun GlassmorphicToolbar(
    currentTool: AnnotationTool,
    currentColor: Color = Color.Black,
    currentWidth: Float = 3f,
    recentColors: List<Color> = emptyList(),
    isHorizontal: Boolean = false,
    isLightBackground: Boolean = true,
    toolbarPosition: String = "right", // "left", "right", "top"
    onToolSelected: (AnnotationTool) -> Unit,
    onColorSelected: (Color) -> Unit = {},
    onWidthChanged: (Float) -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    onOpenPageManager: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    
    // Track which tool's popup is visible (quick presets)
    var showPopupForTool by remember { mutableStateOf<AnnotationTool?>(null) }
    
    // Track which tool's customization panel is visible (full customization)
    var showCustomizeFor by remember { mutableStateOf<AnnotationTool?>(null) }
    
    // Expand/collapse state
    var isExpanded by remember { mutableStateOf(false) }
    
    // Adaptive colors based on background
    val containerBrush = if (isLightBackground) {
        Brush.linearGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.75f),
                Color.Black.copy(alpha = 0.65f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }
    
    val borderBrush = if (isLightBackground) {
        Brush.linearGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.4f),
                Color.Black.copy(alpha = 0.2f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.1f)
            )
        )
    }
    
    // Container with popup - restructured to prevent overlap
    Box(modifier = modifier.fillMaxSize()) {
        // Frosted glass container for toolbar
        Box(
            modifier = Modifier
                .align(
                    when {
                        isHorizontal -> Alignment.TopCenter
                        toolbarPosition == "left" -> Alignment.CenterStart
                        else -> Alignment.CenterEnd
                    }
                )
                .padding(8.dp)
                .clip(shape)
                .background(containerBrush)
                .border(
                    width = 1.dp,
                    brush = borderBrush,
                    shape = shape
                )
                .padding(8.dp)
        ) {
            if (isHorizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarContent(
                        currentTool = currentTool,
                        currentColor = currentColor,
                        showPopupForTool = showPopupForTool,
                        isExpanded = isExpanded,
                        onExpandToggle = { isExpanded = !isExpanded },
                        onToolClick = { tool ->
                            // Always call onToolSelected for SHAPES (EditorScreen handles shape picker)
                            // For Pen/Highlighter, show color preset popup on second tap
                            if (tool == currentTool && tool.hasColorPresets()) {
                                showPopupForTool = if (showPopupForTool == tool) null else tool
                                showCustomizeFor = null
                            } else {
                                onToolSelected(tool)
                                showPopupForTool = null
                            }
                        },
                        onToolLongPress = { tool ->
                            // Long press opens full customization panel
                            if (tool.isCustomizable()) {
                                showCustomizeFor = if (showCustomizeFor == tool) null else tool
                                showPopupForTool = null
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
                    ToolbarContent(
                        currentTool = currentTool,
                        currentColor = currentColor,
                        showPopupForTool = showPopupForTool,
                        isExpanded = isExpanded,
                        onExpandToggle = { isExpanded = !isExpanded },
                        onToolClick = { tool ->
                            // Always call onToolSelected for SHAPES (EditorScreen handles shape picker)
                            // For Pen/Highlighter, show color preset popup on second tap
                            if (tool == currentTool && tool.hasColorPresets()) {
                                showPopupForTool = if (showPopupForTool == tool) null else tool
                                showCustomizeFor = null
                            } else {
                                onToolSelected(tool)
                                showPopupForTool = null
                            }
                        },
                        onToolLongPress = { tool ->
                            // Long press opens full customization panel
                            if (tool.isCustomizable()) {
                                showCustomizeFor = if (showCustomizeFor == tool) null else tool
                                showPopupForTool = null
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
        
        // Popup appears outside toolbar container to prevent overlap (tap on selected tool)
        showPopupForTool?.let { tool ->
            // Scrim to detect outside clicks (behind popup)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent) // Transparent but still catches clicks
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showPopupForTool = null
                        }
                    }
            )
            
            // Popup on top of scrim
            Box(
                modifier = Modifier
                    .align(
                        when {
                            isHorizontal -> Alignment.TopCenter
                            toolbarPosition == "left" -> Alignment.CenterStart
                            else -> Alignment.CenterEnd
                        }
                    )
                    .offset(
                        x = when {
                            isHorizontal -> 0.dp
                            toolbarPosition == "left" -> 70.dp
                            else -> (-80).dp
                        },
                        y = when {
                            isHorizontal -> 70.dp
                            else -> 0.dp
                        }
                    )
                    .padding(4.dp)
                    .wrapContentSize()
                    .pointerInput(Unit) {
                        // Consume all gestures on popup to prevent scrim from catching them
                        detectTapGestures { /* Consume but don't dismiss */ }
                    }
            ) {
                ToolPresetPopup(
                    tool = tool,
                    currentColor = currentColor,
                    currentWidth = currentWidth,
                    recentColors = recentColors,
                    isVisible = true,
                    isHorizontal = isHorizontal,
                    onColorSelected = { color ->
                        onColorSelected(color)
                        showPopupForTool = null // Auto-close on selection
                    },
                    onWidthChanged = { width ->
                        onWidthChanged(width)
                        // Don't auto-close on width change - let user adjust
                    },
                    onDismiss = { showPopupForTool = null },
                    modifier = Modifier
                )
            }
        }
        
        // Customization panel (triggered by long press)
        showCustomizeFor?.let { tool ->
            // Scrim to detect outside clicks (behind popup)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent) // Transparent but still catches clicks
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showCustomizeFor = null
                        }
                    }
            )
            
            // Popup on top of scrim
            Box(
                modifier = Modifier
                    .align(
                        when {
                            isHorizontal -> Alignment.TopCenter
                            toolbarPosition == "left" -> Alignment.CenterStart
                            else -> Alignment.CenterEnd
                        }
                    )
                    .offset(
                        x = when {
                            isHorizontal -> 0.dp
                            toolbarPosition == "left" -> 70.dp
                            else -> (-80).dp
                        },
                        y = when {
                            isHorizontal -> 70.dp
                            else -> 0.dp
                        }
                    )
                    .padding(4.dp)
                    .wrapContentSize()
                    .pointerInput(Unit) {
                        // Consume all gestures on popup to prevent scrim from catching them
                        detectTapGestures { /* Consume but don't dismiss */ }
                    }
            ) {
                ToolPresetPopup(
                    tool = tool,
                    currentColor = currentColor,
                    currentWidth = currentWidth,
                    recentColors = recentColors,
                    isVisible = true,
                    isHorizontal = isHorizontal,
                    onColorSelected = { color ->
                        onColorSelected(color)
                        showCustomizeFor = null // Auto-close on selection
                    },
                    onWidthChanged = { width ->
                        onWidthChanged(width)
                        // Don't auto-close on width change - let user adjust
                    },
                    onDismiss = { showCustomizeFor = null },
                    modifier = Modifier
                )
            }
        }
    }
}

/**
 * Check if a tool has color/size presets (shown in toolbar popup).
 * SHAPES is excluded because it has its own picker dialog in EditorScreen.
 */
private fun AnnotationTool.hasColorPresets(): Boolean = when (this) {
    AnnotationTool.PEN, AnnotationTool.PEN_2,
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> true
    else -> false
}

/**
 * Check if a tool has any kind of presets (for showing popup indicator).
 */
private fun AnnotationTool.hasPresets(): Boolean = when (this) {
    AnnotationTool.PEN, AnnotationTool.PEN_2,
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2,
    AnnotationTool.SHAPES -> true
    else -> false
}

/**
 * Check if a tool can be customized (color, stroke width).
 */
private fun AnnotationTool.isCustomizable(): Boolean = when (this) {
    AnnotationTool.PEN, AnnotationTool.PEN_2,
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2,
    AnnotationTool.ERASER, AnnotationTool.SHAPES -> true
    else -> false
}

@Composable
private fun ToolbarContent(
    currentTool: AnnotationTool,
    currentColor: Color,
    showPopupForTool: AnnotationTool?,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onToolClick: (AnnotationTool) -> Unit,
    onToolLongPress: (AnnotationTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    onOpenPageManager: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    // Expand/Collapse button with rotation animation
    val expandRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring()
    )
    
    ToolButton(
        icon = Icons.Default.ExpandMore,
        label = if (isExpanded) "Collapse" else "Expand",
        isSelected = false,
        onClick = onExpandToggle,
        rotation = expandRotation
    )
    
    Spacer(modifier = Modifier.height(4.dp).width(4.dp))
    
    // Pen (always visible)
    ToolButton(
        icon = Icons.Default.Edit,
        label = "Pen",
        isSelected = currentTool == AnnotationTool.PEN,
        hasPopupOpen = showPopupForTool == AnnotationTool.PEN,
        colorIndicator = if (currentTool == AnnotationTool.PEN) currentColor else null,
        onClick = { onToolClick(AnnotationTool.PEN) },
        onLongPress = { onToolLongPress(AnnotationTool.PEN) }
    )
    
    // Highlighter (only when expanded)
    AnimatedVisibility(visible = isExpanded) {
        ToolButton(
            icon = Icons.Default.Highlight,
            label = "Highlighter",
            isSelected = currentTool == AnnotationTool.HIGHLIGHTER,
            hasPopupOpen = showPopupForTool == AnnotationTool.HIGHLIGHTER,
            colorIndicator = if (currentTool == AnnotationTool.HIGHLIGHTER) currentColor else null,
            onClick = { onToolClick(AnnotationTool.HIGHLIGHTER) },
            onLongPress = { onToolLongPress(AnnotationTool.HIGHLIGHTER) }
        )
    }
    
    // Eraser (always visible)
    ToolButton(
        icon = Icons.Outlined.CleaningServices,
        label = "Eraser",
        isSelected = currentTool == AnnotationTool.ERASER,
        hasPopupOpen = showPopupForTool == AnnotationTool.ERASER,
        onClick = { onToolClick(AnnotationTool.ERASER) },
        onLongPress = { onToolLongPress(AnnotationTool.ERASER) }
    )
    
    // Lasso (only when expanded)
    AnimatedVisibility(visible = isExpanded) {
        ToolButton(
            icon = Icons.Outlined.Gesture,
            label = "Lasso",
            isSelected = currentTool == AnnotationTool.LASSO,
            onClick = { onToolClick(AnnotationTool.LASSO) }
        )
    }
    
    // Text (only when expanded)
    AnimatedVisibility(visible = isExpanded) {
        ToolButton(
            icon = Icons.Default.TextFields,
            label = "Text",
            isSelected = currentTool == AnnotationTool.TEXT,
            onClick = { onToolClick(AnnotationTool.TEXT) }
        )
    }
    
    // Shapes (only when expanded)
    AnimatedVisibility(visible = isExpanded) {
        ToolButton(
            icon = Icons.Outlined.CropSquare,
            label = "Shapes",
            isSelected = currentTool == AnnotationTool.SHAPES,
            hasPopupOpen = showPopupForTool == AnnotationTool.SHAPES,
            colorIndicator = if (currentTool == AnnotationTool.SHAPES) currentColor else null,
            onClick = { onToolClick(AnnotationTool.SHAPES) },
            onLongPress = { onToolLongPress(AnnotationTool.SHAPES) }
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp).width(8.dp))
    
    // Divider
    val dividerColor = Color.White.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(1.dp)
            .background(dividerColor)
    )
    
    Spacer(modifier = Modifier.height(8.dp).width(8.dp))
    
    // Undo (always visible)
    ToolButton(
        icon = Icons.Default.Undo,
        label = "Undo",
        isSelected = false,
        enabled = canUndo,
        onClick = onUndo
    )
    
    // Redo (always visible)
    ToolButton(
        icon = Icons.Default.Redo,
        label = "Redo",
        isSelected = false,
        enabled = canRedo,
        onClick = onRedo
    )
    
    // Add Page & Page Manager (only when expanded)
    AnimatedVisibility(visible = isExpanded) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp).width(8.dp))
            
            // Add Page
            ToolButton(
                icon = Icons.Default.Add,
                label = "Add Page",
                isSelected = false,
                onClick = onAddPage,
                tint = Color(0xFF6366F1)
            )
            
            // Page Manager
            ToolButton(
                icon = Icons.Outlined.GridView,
                label = "Pages",
                isSelected = false,
                onClick = onOpenPageManager
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    hasPopupOpen: Boolean = false,
    enabled: Boolean = true,
    colorIndicator: Color? = null,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    tint: Color? = null,
    rotation: Float = 0f
) {
    val backgroundColor = when {
        hasPopupOpen -> Color(0xFF818CF8) // Lighter when popup open
        isSelected -> Color(0xFF6366F1)
        else -> Color.Transparent
    }
    
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.3f)
        tint != null -> tint
        isSelected || hasPopupOpen -> Color.White
        else -> Color.White.copy(alpha = 0.9f)
    }
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (enabled && onLongPress != null) {
                    Modifier.pointerInput(enabled, onLongPress, onClick) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { onLongPress() }
                        )
                    }
                } else {
                    Modifier.clickable(enabled = enabled) { onClick() }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation)
        )
        
        // Color indicator dot (shows current color for drawing tools)
        if (colorIndicator != null && isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorIndicator)
                    .border(1.dp, Color.White, CircleShape)
            )
        }
    }
}

/**
 * Waterdrop page indicator - small notch that expands on tap.
 */
@Composable
fun WaterdropPageIndicator(
    currentPage: Int,
    totalPages: Int,
    isExpanded: Boolean,
    isLightBackground: Boolean = true,
    onExpandToggle: () -> Unit,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        bottomStart = 20.dp,
        topEnd = 4.dp,
        bottomEnd = 4.dp
    )
    
    val containerBrush = if (isLightBackground) {
        Brush.linearGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.6f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.1f)
            )
        )
    }
    
    val borderColor = if (isLightBackground) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f)
    val textColor = Color.White
    
    AnimatedContent(
        targetState = isExpanded,
        modifier = modifier,
        transitionSpec = {
            fadeIn() + expandHorizontally(expandFrom = Alignment.End) togetherWith
                fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
        }
    ) { expanded ->
        if (expanded) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(containerBrush)
                    .border(1.dp, borderColor, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$currentPage / $totalPages",
                        color = textColor,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { onPageSelected(it.toInt().coerceIn(1, totalPages)) },
                        valueRange = 1f..totalPages.toFloat().coerceAtLeast(1.1f),
                        steps = (totalPages - 2).coerceAtLeast(0),
                        modifier = Modifier.width(150.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6366F1),
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(onClick = onExpandToggle) {
                        Text("Done", color = Color(0xFF6366F1))
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (isLightBackground) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.1f))
                    .border(1.dp, borderColor, shape)
                    .clickable { onExpandToggle() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$currentPage",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "/$totalPages",
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
