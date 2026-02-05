package com.osnotes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osnotes.app.domain.model.TextAnnotation

/**
 * Dialog for creating and editing text annotations.
 */
@Composable
fun TextInputDialog(
    initialText: String = "",
    initialColor: Color = Color.Black,
    initialFontSize: Float = 16f,
    initialBold: Boolean = false,
    initialItalic: Boolean = false,
    position: Offset,
    recentColors: List<Color> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (TextAnnotation) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var fontSize by remember { mutableFloatStateOf(initialFontSize) }
    var isBold by remember { mutableStateOf(initialBold) }
    var isItalic by remember { mutableStateOf(initialItalic) }
    
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Add Text",
                        color = Color.White,
                        fontSize = 18.sp,
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
                
                // Text input area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = selectedColor,
                            fontSize = fontSize.sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                        ),
                        cursorBrush = SolidColor(Color(0xFF6366F1)),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        "Enter text here...",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = fontSize.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Formatting options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bold toggle
                    FormatButton(
                        icon = Icons.Default.FormatBold,
                        label = "Bold",
                        isSelected = isBold,
                        onClick = { isBold = !isBold }
                    )
                    
                    // Italic toggle
                    FormatButton(
                        icon = Icons.Default.FormatItalic,
                        label = "Italic",
                        isSelected = isItalic,
                        onClick = { isItalic = !isItalic }
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Font size controls
                    IconButton(
                        onClick = { fontSize = (fontSize - 2f).coerceAtLeast(8f) }
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease size",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        "${fontSize.toInt()}",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.width(28.dp)
                    )
                    
                    IconButton(
                        onClick = { fontSize = (fontSize + 2f).coerceAtMost(72f) }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase size",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Color selection
                Text(
                    "Color",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Default colors
                    val defaultColors = listOf(
                        Color.Black,
                        Color.White,
                        Color(0xFF2563EB),
                        Color(0xFFDC2626),
                        Color(0xFF059669),
                        Color(0xFFF59E0B),
                        Color(0xFF7C3AED)
                    )
                    
                    items(defaultColors) { color ->
                        ColorDot(
                            color = color,
                            isSelected = color == selectedColor,
                            onClick = { selectedColor = color }
                        )
                    }
                    
                    // Recent colors
                    items(recentColors.filter { it !in defaultColors }.take(5)) { color ->
                        ColorDot(
                            color = color,
                            isSelected = color == selectedColor,
                            onClick = { selectedColor = color }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                onConfirm(
                                    TextAnnotation(
                                        text = text,
                                        position = position,
                                        color = selectedColor,
                                        fontSize = fontSize,
                                        fontWeight = if (isBold) 700 else 400,
                                        isItalic = isItalic
                                    )
                                )
                            }
                        },
                        enabled = text.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            disabledContainerColor = Color(0xFF6366F1).copy(alpha = 0.3f)
                        )
                    ) {
                        Text("Add Text")
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.1f)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ColorDot(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

/**
 * Overlay that renders text annotations on the canvas.
 * Uses Compose Text for proper font rendering.
 */
/**
 * Overlay for rendering text annotations with proper coordinate transformation.
 */
@Composable
fun TextAnnotationOverlay(
    textAnnotations: List<TextAnnotation>,
    selectedIds: Set<String>,
    onTextTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    bitmapWidth: Int = 0,
    bitmapHeight: Int = 0,
    excludeTextInEditMode: Boolean = false
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Calculate transformation from bitmap space to screen space
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value
        
        val scaleX = if (bitmapWidth > 0) screenWidth / bitmapWidth else 1f
        val scaleY = if (bitmapHeight > 0) screenHeight / bitmapHeight else 1f
        val displayScale = minOf(scaleX, scaleY)
        val scaledWidth = bitmapWidth * displayScale
        val scaledHeight = bitmapHeight * displayScale
        val offsetX = (screenWidth - scaledWidth) / 2
        val offsetY = (screenHeight - scaledHeight) / 2
        
        textAnnotations.forEach { annotation ->
            val isSelected = selectedIds.contains(annotation.id)
            
            // Transform coordinates from bitmap space to screen space
            val transformedX = annotation.position.x * displayScale + offsetX
            val transformedY = annotation.position.y * displayScale + offsetY
            val transformedWidth = annotation.width * displayScale
            val transformedFontSize = annotation.fontSize * displayScale
            val transformedPadding = annotation.padding * displayScale
            
            Box(
                modifier = Modifier
                    .offset(
                        x = transformedX.dp,
                        y = transformedY.dp
                    )
                    .width(transformedWidth.dp)
                    .clickable { onTextTap(annotation.id) }
                    .background(
                        if (annotation.backgroundColor != Color.Transparent) 
                            annotation.backgroundColor 
                        else Color.Transparent
                    )
                    .padding(transformedPadding.dp)
            ) {
                // Selection highlight
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0xFF6366F1).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(4.dp))
                    )
                }
                
                Text(
                    text = annotation.text,
                    color = annotation.color,
                    fontSize = transformedFontSize.sp,
                    fontWeight = if (annotation.fontWeight >= 700) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (annotation.isItalic) FontStyle.Italic else FontStyle.Normal,
                    modifier = Modifier.fillMaxWidth(),
                    overflow = TextOverflow.Visible,
                    softWrap = true
                )
            }
        }
    }
}
