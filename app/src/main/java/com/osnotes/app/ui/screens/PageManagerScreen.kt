package com.osnotes.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.osnotes.app.ui.viewmodels.PageManagerViewModel
import kotlin.math.roundToInt

/**
 * Page Manager screen for viewing, reordering, and managing pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(
    documentPath: String,
    annotationCount: Int = 0,
    onBack: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onDocumentChanged: () -> Unit = {},
    viewModel: PageManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf<Int?>(null) }
    var showUnsavedChangesWarning by remember { mutableStateOf(false) }
    var showReorderDialog by remember { mutableStateOf<Int?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedPagePosition by remember { mutableStateOf(Offset.Zero) }
    
    // Check if annotations are flattened (annotation count is 0)
    val hasNonFlattenedAnnotations = annotationCount > 0
    
    // Always enter batch mode when annotations are flattened
    LaunchedEffect(hasNonFlattenedAnnotations) {
        if (!hasNonFlattenedAnnotations && !uiState.batchMode.isActive) {
            viewModel.enterBatchMode()
        } else if (hasNonFlattenedAnnotations && uiState.batchMode.isActive) {
            viewModel.exitBatchMode()
        }
    }
    
    // Track when batch operations complete successfully
    var lastOperationCount by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.batchMode.isActive, uiState.batchMode.operationCount) {
        // If we had operations and now we don't (and not executing), operations were applied
        if (lastOperationCount > 0 && uiState.batchMode.operationCount == 0 && !uiState.batchMode.isExecuting) {
            onDocumentChanged()
        }
        lastOperationCount = uiState.batchMode.operationCount
    }
    
    LaunchedEffect(documentPath) {
        viewModel.loadDocument(documentPath)
    }
    
    // Helper function to check for non-flattened annotations before performing actions
    fun performActionWithCheck(action: () -> Unit) {
        if (hasNonFlattenedAnnotations) {
            pendingAction = action
            showUnsavedChangesWarning = true
        } else {
            action()
        }
    }
    
    // Show error messages
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Error will be shown in a snackbar or similar
            // For now, we'll just log it
            android.util.Log.e("PageManager", error)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A2E)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Page Manager",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (hasNonFlattenedAnnotations) {
                                // Show warning when annotations are not flattened
                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        "${uiState.pages.size} pages",
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "Make permanent to enable page operations",
                                        color = Color(0xFFFF9800).copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            } else {
                                // Always show batch mode controls when annotations are flattened
                                // Operation count badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF6366F1).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFF6366F1))
                                ) {
                                    Text(
                                        "${uiState.batchMode.operationCount} queued",
                                        color = Color(0xFF6366F1),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Apply Changes button (only shown when there are operations)
                                if (uiState.batchMode.hasOperations) {
                                    Button(
                                        onClick = { viewModel.executeBatch() },
                                        enabled = !uiState.batchMode.isExecuting,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF10B981),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        if (uiState.batchMode.isExecuting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Apply", fontSize = 12.sp)
                                    }
                                    
                                    // Clear All button
                                    IconButton(
                                        onClick = { viewModel.clearAllOperations() },
                                        enabled = !uiState.batchMode.isExecuting
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear all",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6366F1))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.pages,
                        key = { it.id }
                    ) { page ->
                        PageThumbnailCard(
                            pageIndex = page.index,
                            thumbnail = viewModel.getPageThumbnail(page.index),
                            isSelected = uiState.selectedPages.contains(page.index),
                            isDragging = uiState.isDragging && uiState.draggedPageIndex == page.index,
                            isBatchMode = uiState.batchMode.isActive,
                            operationsForPage = if (uiState.batchMode.isActive) {
                                viewModel.getOperationsForPage(page.index)
                            } else {
                                emptyList()
                            },
                            onTap = { onPageSelected(page.index) },
                            onLongPress = {
                                viewModel.togglePageSelection(page.index)
                            },
                            onDelete = {
                                performActionWithCheck {
                                    if (uiState.batchMode.isActive) {
                                        viewModel.queueDeleteOperation(page.index)
                                    } else {
                                        showDeleteConfirmation = page.index
                                    }
                                }
                            },
                            onDuplicate = {
                                performActionWithCheck {
                                    if (uiState.batchMode.isActive) {
                                        viewModel.queueDuplicateOperation(page.index)
                                    } else {
                                        viewModel.duplicatePage(page.index)
                                    }
                                }
                            },
                            onReorder = {
                                performActionWithCheck {
                                    showReorderDialog = page.index
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // Delete Confirmation
        showDeleteConfirmation?.let { pageIndex ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = null },
                title = { Text("Delete Page?") },
                text = { Text("Are you sure you want to delete page ${pageIndex + 1}? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deletePage(pageIndex)
                            showDeleteConfirmation = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF2A2A2A),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // Reorder Dialog
        showReorderDialog?.let { pageIndex ->
            ReorderDialog(
                currentPageIndex = pageIndex,
                totalPages = uiState.pages.size,
                isBatchMode = uiState.batchMode.isActive,
                onDismiss = { showReorderDialog = null },
                onReorder = { newIndex ->
                    if (uiState.batchMode.isActive) {
                        viewModel.queueMoveOperation(pageIndex, newIndex)
                    } else {
                        viewModel.reorderPages(pageIndex, newIndex)
                    }
                    showReorderDialog = null
                }
            )
        }
        
        // Non-Flattened Annotations Warning
        if (showUnsavedChangesWarning) {
            AlertDialog(
                onDismissRequest = { 
                    showUnsavedChangesWarning = false
                    pendingAction = null
                },
                title = { Text("Non-Permanent Annotations", color = Color.White) },
                text = { 
                    Text(
                        "Page manipulation is not allowed while having non-permanent edits. Please make your note permanent before proceeding.",
                        color = Color.White.copy(alpha = 0.8f)
                    ) 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showUnsavedChangesWarning = false
                            pendingAction = null
                        }
                    ) {
                        Text("OK", color = Color(0xFF6366F1))
                    }
                },
                containerColor = Color(0xFF2A2A2A),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // Error display
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { viewModel.clearError() },
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.9f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Error",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = error,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Batch execution progress dialog
        if (uiState.batchMode.isExecuting) {
            AlertDialog(
                onDismissRequest = { /* Cannot dismiss while executing */ },
                title = { Text("Applying Changes", color = Color.White) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = uiState.batchMode.executionProgress,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF6366F1),
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            uiState.batchMode.currentOperation,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${(uiState.batchMode.executionProgress * 100).toInt()}%",
                            color = Color(0xFF6366F1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = { /* No button while executing */ },
                containerColor = Color(0xFF2A2A2A),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // Info note at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1A1A2E).copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "If you notice any unsynced changes in editor after page manipulation, use 'Make Permanent' to sync",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumbnailCard(
    pageIndex: Int,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    isDragging: Boolean = false,
    isBatchMode: Boolean = false,
    operationsForPage: List<com.osnotes.app.domain.model.PageOperation> = emptyList(),
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onReorder: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        label = "elevation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.707f) // A4 ratio
            .shadow(elevation, RoundedCornerShape(8.dp))
            .clickable { onTap() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color(0xFF2A2A2A)
        ),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF6366F1)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF3A3A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White.copy(alpha = 0.3f),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            // Operation badges (shown in batch mode)
            if (isBatchMode && operationsForPage.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    operationsForPage.forEach { operation ->
                        val (badgeColor, badgeText) = when (operation) {
                            is com.osnotes.app.domain.model.PageOperation.Delete -> 
                                Color(0xFFEF4444) to "Delete"
                            is com.osnotes.app.domain.model.PageOperation.Duplicate -> 
                                Color(0xFF3B82F6) to "Duplicate"
                            is com.osnotes.app.domain.model.PageOperation.Move -> 
                                Color(0xFF9C27B0) to "Move to ${operation.targetIndex + 1}"
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor,
                            modifier = Modifier.shadow(2.dp, RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                badgeText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            
            // Page number badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "${pageIndex + 1}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color(0xFF6366F1), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            
            // Drag indicator
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color(0xFF6366F1), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Dragging",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            
            // Context menu trigger (separate from drag)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .clickable { 
                        onLongPress()
                        showMenu = true 
                    }
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF2A2A2A))
            ) {
                DropdownMenuItem(
                    text = { Text("Reorder", color = Color.White) },
                    onClick = {
                        onReorder()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.SwapVert, contentDescription = null, tint = Color.White)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate", color = Color.White) },
                    onClick = {
                        onDuplicate()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.Red) },
                    onClick = {
                        onDelete()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    }
                )
            }
        }
    }
}

@Composable
private fun ReorderDialog(
    currentPageIndex: Int,
    totalPages: Int,
    isBatchMode: Boolean = false,
    onDismiss: () -> Unit,
    onReorder: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Move Page ${currentPageIndex + 1}", 
                color = Color.White
            ) 
        },
        text = {
            Column {
                Text(
                    "Choose where to move this page (changes will be applied when you click Apply):",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Quick options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Move to beginning
                    if (currentPageIndex > 0) {
                        OutlinedButton(
                            onClick = { onReorder(0) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Default.FirstPage,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("First", fontSize = 12.sp)
                        }
                    }
                    
                    // Move one position back
                    if (currentPageIndex > 0) {
                        OutlinedButton(
                            onClick = { onReorder(currentPageIndex - 1) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back", fontSize = 12.sp)
                        }
                    }
                    
                    // Move one position forward
                    if (currentPageIndex < totalPages - 1) {
                        OutlinedButton(
                            onClick = { onReorder(currentPageIndex + 1) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Forward", fontSize = 12.sp)
                        }
                    }
                    
                    // Move to end
                    if (currentPageIndex < totalPages - 1) {
                        OutlinedButton(
                            onClick = { onReorder(totalPages - 1) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Default.LastPage,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Last", fontSize = 12.sp)
                        }
                    }
                }
                
                // Custom position input
                if (totalPages > 3) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var customPosition by remember { mutableStateOf("") }
                    
                    OutlinedTextField(
                        value = customPosition,
                        onValueChange = { customPosition = it },
                        label = { Text("Or enter position (1-$totalPages)", color = Color.White.copy(alpha = 0.6f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val position = customPosition.toIntOrNull()
                                    if (position != null && position in 1..totalPages) {
                                        onReorder(position - 1) // Convert to 0-based index
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Move to position",
                                    tint = Color(0xFF6366F1)
                                )
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF2A2A2A),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.8f)
    )
}

// Helper function to calculate target index based on drag position
private fun calculateTargetIndex(dragPosition: Offset, currentIndex: Int): Int? {
    // Simple grid-based calculation
    // Each card is approximately 120dp + 12dp spacing = 132dp wide
    // Grid has 2-3 columns typically on most screens
    
    val cardWidth = 132f // Approximate card width including spacing
    val cardHeight = 180f // Approximate card height including spacing
    
    // Calculate how many positions moved horizontally and vertically
    val horizontalMove = (dragPosition.x / cardWidth).roundToInt()
    val verticalMove = (dragPosition.y / cardHeight).roundToInt()
    
    // For a 2-column grid, moving down 1 row = +2 positions, moving right 1 = +1 position
    // This is a simplified calculation - in reality you'd need to know the actual grid layout
    val positionChange = verticalMove * 2 + horizontalMove
    
    if (positionChange == 0) return null // No significant movement
    
    val newIndex = currentIndex + positionChange
    return if (newIndex != currentIndex) newIndex else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPageDialog(
    onDismiss: () -> Unit,
    onAddPage: (template: String, location: PageLocation) -> Unit,
    customTemplates: List<com.osnotes.app.domain.model.CustomTemplate> = emptyList()
) {
    var selectedTemplate by remember { mutableStateOf("blank") }
    var selectedLocation by remember { mutableStateOf(PageLocation.END) }
    
    val predefinedTemplates = listOf(
        "blank" to "Blank",
        "grid" to "Grid",
        "dotgrid" to "Dot Grid",
        "lined" to "Lined",
        "cornell" to "Cornell Notes",
        "meeting" to "Meeting Minutes"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Page") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Template selection
                Text(
                    "Templates",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Predefined templates
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(150.dp)
                ) {
                    items(predefinedTemplates.size) { index ->
                        val (id, name) = predefinedTemplates[index]
                        TemplateOption(
                            name = name,
                            isSelected = selectedTemplate == id,
                            onClick = { selectedTemplate = id }
                        )
                    }
                }
                
                // Custom templates section
                if (customTemplates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "My Custom Templates",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(
                            ((customTemplates.size + 1) / 2 * 60).coerceAtMost(150).dp
                        )
                    ) {
                        items(customTemplates.size) { index ->
                            val template = customTemplates[index]
                            TemplateOption(
                                name = template.name,
                                isSelected = selectedTemplate == template.id,
                                onClick = { selectedTemplate = template.id },
                                backgroundColor = androidx.compose.ui.graphics.Color(template.backgroundColor)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Location selection
                Text(
                    "Insert Location",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Column {
                    PageLocation.values().forEach { location ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLocation = location }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLocation == location,
                                onClick = { selectedLocation = location },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF6366F1)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (location) {
                                    PageLocation.START -> "At the beginning"
                                    PageLocation.CURRENT -> "After current page"
                                    PageLocation.END -> "At the end"
                                },
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAddPage(selectedTemplate, selectedLocation) }
            ) {
                Text("Add", color = Color(0xFF6366F1))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
private fun TemplateOption(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    backgroundColor: Color? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) 
                else backgroundColor?.copy(alpha = 0.3f) ?: Color.White.copy(alpha = 0.1f),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF6366F1)) else null,
        modifier = Modifier.aspectRatio(0.707f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name,
                color = if (isSelected) Color(0xFF6366F1) else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

enum class PageLocation {
    START,
    CURRENT,
    END
}
