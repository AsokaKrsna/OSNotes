package com.osnotes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.osnotes.app.domain.model.CustomTemplate
import com.osnotes.app.domain.model.PatternType
import com.osnotes.app.ui.viewmodels.TemplateManagerViewModel

/**
 * Screen for managing custom page templates.
 * Allows viewing, editing, duplicating, and deleting templates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateManagerScreen(
    onBack: () -> Unit,
    onEditTemplate: (String) -> Unit,
    onCreateNew: () -> Unit,
    viewModel: TemplateManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val templates by viewModel.templates.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf<CustomTemplate?>(null) }
    
    val backgroundColor = MaterialTheme.colorScheme.background
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "My Templates",
                            fontWeight = FontWeight.Bold,
                            color = onBackgroundColor
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = onBackgroundColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onCreateNew,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Template")
                }
            }
        ) { paddingValues ->
            if (templates.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.GridOn,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = onBackgroundColor.copy(alpha = 0.3f)
                        )
                        Text(
                            "No Custom Templates",
                            style = MaterialTheme.typography.titleLarge,
                            color = onBackgroundColor.copy(alpha = 0.6f)
                        )
                        Text(
                            "Create your first template\nwith the + button",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBackgroundColor.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(templates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onClick = { onEditTemplate(template.id) },
                            onEdit = { onEditTemplate(template.id) },
                            onDuplicate = { viewModel.duplicateTemplate(template.id) },
                            onDelete = { showDeleteDialog = template }
                        )
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        showDeleteDialog?.let { template ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Template?") },
                text = { 
                    Text("Are you sure you want to delete \"${template.name}\"? This action cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteTemplate(template.id)
                            showDeleteDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: CustomTemplate,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Preview area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                // Template preview
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawTemplatePreview(template)
                }
                
                // Menu button with anchored dropdown
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = { showMenu = true }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.DarkGray.copy(alpha = 0.7f)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                ) 
                            }
                        )
                    }
                }
            }
            
            // Name area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun DrawScope.drawTemplatePreview(template: CustomTemplate) {
    val bgColor = Color(template.backgroundColor)
    val lineColor = Color(template.lineColor)
    val spacing = template.lineSpacing * 0.8f
    
    // Background
    drawRect(bgColor)
    
    // Pattern
    when (template.patternType) {
        PatternType.HORIZONTAL_LINES -> {
            var y = spacing
            while (y < size.height) {
                drawLine(lineColor, 
                    androidx.compose.ui.geometry.Offset(4f, y),
                    androidx.compose.ui.geometry.Offset(size.width - 4f, y),
                    strokeWidth = template.lineThickness)
                y += spacing
            }
        }
        PatternType.VERTICAL_LINES -> {
            var x = spacing
            while (x < size.width) {
                drawLine(lineColor,
                    androidx.compose.ui.geometry.Offset(x, 4f),
                    androidx.compose.ui.geometry.Offset(x, size.height - 4f),
                    strokeWidth = template.lineThickness)
                x += spacing
            }
        }
        PatternType.GRID -> {
            var y = spacing
            while (y < size.height) {
                drawLine(lineColor,
                    androidx.compose.ui.geometry.Offset(4f, y),
                    androidx.compose.ui.geometry.Offset(size.width - 4f, y),
                    strokeWidth = template.lineThickness)
                y += spacing
            }
            var x = spacing
            while (x < size.width) {
                drawLine(lineColor,
                    androidx.compose.ui.geometry.Offset(x, 4f),
                    androidx.compose.ui.geometry.Offset(x, size.height - 4f),
                    strokeWidth = template.lineThickness)
                x += spacing
            }
        }
        PatternType.DOTS -> {
            var y = spacing
            while (y < size.height) {
                var x = spacing
                while (x < size.width) {
                    drawCircle(lineColor, template.dotSize * 1.5f,
                        androidx.compose.ui.geometry.Offset(x, y))
                    x += spacing
                }
                y += spacing
            }
        }
        PatternType.ISOMETRIC_DOTS -> {
            var row = 0
            var y = spacing
            while (y < size.height) {
                val offset = if (row % 2 == 0) 0f else spacing / 2
                var x = spacing + offset
                while (x < size.width) {
                    drawCircle(lineColor, template.dotSize * 1.5f,
                        androidx.compose.ui.geometry.Offset(x, y))
                    x += spacing
                }
                y += spacing * 0.866f
                row++
            }
        }
        PatternType.DIAGONAL_LEFT -> {
            // \ pattern: from top-left to bottom-right (as Y increases, X increases)
            var offset = -size.height
            while (offset < size.width) {
                drawLine(lineColor,
                    androidx.compose.ui.geometry.Offset(offset, 0f),
                    androidx.compose.ui.geometry.Offset(offset + size.height, size.height),
                    strokeWidth = template.lineThickness)
                offset += spacing
            }
        }
        PatternType.DIAGONAL_RIGHT -> {
            // / pattern: from bottom-left to top-right (as Y decreases, X increases)
            var offset = 0f
            while (offset < size.width + size.height) {
                drawLine(lineColor,
                    androidx.compose.ui.geometry.Offset(offset - size.height, size.height),
                    androidx.compose.ui.geometry.Offset(offset, 0f),
                    strokeWidth = template.lineThickness)
                offset += spacing
            }
        }
        PatternType.CROSSHATCH -> {
            // \ lines
            var offset = -size.height
            while (offset < size.width) {
                drawLine(lineColor,
                    androidx.compose.ui.geometry.Offset(offset, 0f),
                    androidx.compose.ui.geometry.Offset(offset + size.height, size.height),
                    strokeWidth = template.lineThickness)
                offset += spacing
            }
            // / lines
            offset = 0f
            while (offset < size.width + size.height) {
                drawLine(lineColor,
                    androidx.compose.ui.geometry.Offset(offset - size.height, size.height),
                    androidx.compose.ui.geometry.Offset(offset, 0f),
                    strokeWidth = template.lineThickness)
                offset += spacing
            }
        }
        PatternType.NONE -> { /* blank */ }
    }
    
    // Header hint
    if (template.hasHeader) {
        drawRect(
            Color(template.headerColor),
            size = androidx.compose.ui.geometry.Size(size.width, template.headerHeight * 0.5f)
        )
    }
    
    // Footer hint
    if (template.hasFooter) {
        drawRect(
            Color(template.footerColor),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - template.footerHeight * 0.5f),
            size = androidx.compose.ui.geometry.Size(size.width, template.footerHeight * 0.5f)
        )
    }
}
