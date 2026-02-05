package com.osnotes.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.osnotes.app.data.models.FolderInfo
import com.osnotes.app.data.models.NoteInfo
import com.osnotes.app.ui.components.GlassmorphicSurface
import com.osnotes.app.ui.components.NoteCard
import com.osnotes.app.ui.components.FolderItem
import com.osnotes.app.ui.components.CreateNewWithTemplateDialog
import com.osnotes.app.ui.components.CreateNewWithTemplateDialog
import com.osnotes.app.ui.theme.AppColors
import com.osnotes.app.ui.viewmodels.FolderViewModel

/**
 * Folder view displaying notes within a collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderPath: String,
    onBack: () -> Unit,
    onNoteClick: (String) -> Unit,
    onSubfolderClick: (String) -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val customTemplates = viewModel.customTemplates.collectAsState().value
    var showCreateDialog by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NoteInfo?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderInfo?>(null) }
    var noteToRename by remember { mutableStateOf<NoteInfo?>(null) }
    var folderToRename by remember { mutableStateOf<FolderInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    
    LaunchedEffect(folderPath) {
        viewModel.loadFolder(folderPath)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(backgroundColor, surfaceColor)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            uiState.folderName,
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
                    actions = {
                        IconButton(onClick = { /* Search */ }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = onBackgroundColor.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = { /* More options */ }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = onBackgroundColor.copy(alpha = 0.8f)
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
                    onClick = { showCreateDialog = true },
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create")
                }
            }
        ) { paddingValues ->
            val contentColor = onBackgroundColor
            
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else if (uiState.notes.isEmpty() && uiState.subfolders.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = contentColor.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No notes yet",
                            color = contentColor.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap + to create your first note",
                            color = contentColor.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Subfolders first
                    items(uiState.subfolders) { folder ->
                        var showFolderMenu by remember { mutableStateOf(false) }
                        
                        Box {
                            FolderGridItem(
                                name = folder.name,
                                noteCount = folder.noteCount,
                                onClick = { onSubfolderClick(folder.path) },
                                onLongClick = { showFolderMenu = true },
                                primaryColor = primaryColor,
                                textColor = contentColor
                            )
                            
                            DropdownMenu(
                                expanded = showFolderMenu,
                                onDismissRequest = { showFolderMenu = false },
                                modifier = Modifier.background(Color(0xFF2A2A2A))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = Color.White) },
                                    onClick = {
                                        folderToRename = folder
                                        renameText = folder.name
                                        showFolderMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color.Red) },
                                    onClick = {
                                        folderToDelete = folder
                                        showFolderMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                    }
                                )
                            }
                        }
                    }
                    
                    // Notes
                    items(uiState.notes) { note ->
                        var showNoteMenu by remember { mutableStateOf(false) }
                        
                        Box {
                            NoteCard(
                                name = note.name,
                                pageCount = note.pageCount,
                                isFavorite = note.isFavorite,
                                onClick = { onNoteClick(note.path) },
                                onFavoriteToggle = { viewModel.toggleFavorite(note.path) },
                                onLongClick = { showNoteMenu = true }
                            )
                            
                            DropdownMenu(
                                expanded = showNoteMenu,
                                onDismissRequest = { showNoteMenu = false },
                                modifier = Modifier.background(Color(0xFF2A2A2A))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = Color.White) },
                                    onClick = {
                                        noteToRename = note
                                        renameText = note.name
                                        showNoteMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color.Red) },
                                    onClick = {
                                        noteToDelete = note
                                        showNoteMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Create New Dialog with Template Selection
        if (showCreateDialog) {
            CreateNewWithTemplateDialog(
                onDismiss = { showCreateDialog = false },
                onCreateNote = { name, template ->
                    viewModel.createNote(name, template.name)
                    showCreateDialog = false
                },
                onCreateFolder = { name ->
                    viewModel.createFolder(name)
                    showCreateDialog = false
                },
                customTemplates = customTemplates,
                onCreateNoteWithCustomTemplate = { name, customTemplate ->
                    viewModel.createNote(name, customTemplate.id)
                    showCreateDialog = false
                }
            )
        }
        
        // Delete Note Confirmation
        noteToDelete?.let { note ->
            AlertDialog(
                onDismissRequest = { noteToDelete = null },
                title = { Text("Delete Note?", color = onBackgroundColor) },
                text = { 
                    Text(
                        "Are you sure you want to delete \"${note.name}\"? This cannot be undone.",
                        color = onBackgroundColor.copy(alpha = 0.8f)
                    ) 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteNote(note.path)
                            noteToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteToDelete = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = surfaceColor,
                titleContentColor = onBackgroundColor,
                textContentColor = onBackgroundColor
            )
        }
        
        // Delete Folder Confirmation
        folderToDelete?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderToDelete = null },
                title = { Text("Delete Folder?", color = onBackgroundColor) },
                text = { 
                    Text(
                        "Are you sure you want to delete \"${folder.name}\"? Folder must be empty.",
                        color = onBackgroundColor.copy(alpha = 0.8f)
                    ) 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteFolder(folder.path)
                            folderToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToDelete = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = surfaceColor,
                titleContentColor = onBackgroundColor,
                textContentColor = onBackgroundColor
            )
        }
        
        // Rename Note Dialog
        noteToRename?.let { note ->
            AlertDialog(
                onDismissRequest = { 
                    noteToRename = null
                    renameText = ""
                },
                title = { Text("Rename Note", color = onBackgroundColor) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = onBackgroundColor,
                            unfocusedTextColor = onBackgroundColor,
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = onBackgroundColor.copy(alpha = 0.3f)
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.renameNote(note.path, renameText)
                                noteToRename = null
                                renameText = ""
                            }
                        }
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        noteToRename = null
                        renameText = ""
                    }) {
                        Text("Cancel")
                    }
                },
                containerColor = surfaceColor,
                titleContentColor = onBackgroundColor,
                textContentColor = onBackgroundColor
            )
        }
        
        // Rename Folder Dialog
        folderToRename?.let { folder ->
            AlertDialog(
                onDismissRequest = { 
                    folderToRename = null
                    renameText = ""
                },
                title = { Text("Rename Folder", color = onBackgroundColor) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = onBackgroundColor,
                            unfocusedTextColor = onBackgroundColor,
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = onBackgroundColor.copy(alpha = 0.3f)
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.renameFolder(folder.path, renameText)
                                folderToRename = null
                                renameText = ""
                            }
                        }
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        folderToRename = null
                        renameText = ""
                    }) {
                        Text("Cancel")
                    }
                },
                containerColor = surfaceColor,
                titleContentColor = onBackgroundColor,
                textContentColor = onBackgroundColor
            )
        }
        
        // Error Snackbar
        uiState.error?.let { error ->
            LaunchedEffect(error) {
                // Show error for 3 seconds then clear
                kotlinx.coroutines.delay(3000)
                // Clear error - you'll need to add this method to ViewModel
            }
            
            androidx.compose.material3.Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Text(error)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGridItem(
    name: String,
    noteCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    primaryColor: Color,
    textColor: Color
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                name,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
            Text(
                "$noteCount notes",
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

