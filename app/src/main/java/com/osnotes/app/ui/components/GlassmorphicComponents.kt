package com.osnotes.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Glassmorphic surface with frosted glass effect.
 * Adapts to current theme colors for light/dark mode support.
 */
@Composable
fun GlassmorphicSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Get theme-aware colors
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    val surfaceColors = if (isDarkTheme) {
        listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.02f))
    }
    
    val borderColors = if (isDarkTheme) {
        listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.02f))
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(colors = surfaceColors)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(colors = borderColors),
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
    ) {
        content()
    }
}

/**
 * Note card for displaying in favorites/recent sections.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    name: String,
    pageCount: Int,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    
    GlassmorphicSurface(
        modifier = Modifier
            .width(140.dp)
            .height(180.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(onBackgroundColor.copy(alpha = 0.03f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = onBackgroundColor.copy(alpha = 0.3f)
                )
                
                // Favorite toggle button
                if (onFavoriteToggle != null) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp)
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            modifier = Modifier.size(20.dp),
                            tint = if (isFavorite) Color(0xFFFFD700) else onBackgroundColor.copy(alpha = 0.5f)
                        )
                    }
                } else if (isFavorite) {
                    // Just show star icon without toggle
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorite",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(16.dp),
                        tint = Color(0xFFFFD700)
                    )
                }
            }
            
            // Info area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = onBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$pageCount pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = onBackgroundColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Folder item for collections list.
 */
@Composable
fun FolderItem(
    name: String,
    noteCount: Int,
    onClick: () -> Unit
) {
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primaryColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = onBackgroundColor
                )
                Text(
                    "$noteCount notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = onBackgroundColor.copy(alpha = 0.6f)
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = onBackgroundColor.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * Dialog for creating new note or folder.
 */
@Composable
fun CreateNewDialog(
    onDismiss: () -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateFolder: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        title = {
            Text(
                "Create New",
                color = onSurfaceColor,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(onSurfaceColor.copy(alpha = 0.1f))
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = "Note",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = "Folder",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { 
                        Text(
                            if (selectedTab == 0) "Note name" else "Folder name",
                            color = onSurfaceColor.copy(alpha = 0.6f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = onSurfaceColor,
                        unfocusedTextColor = onSurfaceColor,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = onSurfaceColor.copy(alpha = 0.3f),
                        cursorColor = primaryColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        if (selectedTab == 0) onCreateNote(name)
                        else onCreateFolder(name)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = primaryColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = onSurfaceColor.copy(alpha = 0.6f))
            }
        }
    )
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) primaryColor
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) onPrimaryColor else onSurfaceColor.copy(alpha = 0.6f),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// Extension to get luminance
private fun Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}
