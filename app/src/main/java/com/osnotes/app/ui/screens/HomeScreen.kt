package com.osnotes.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.osnotes.app.ui.components.GlassmorphicSurface
import com.osnotes.app.ui.components.NoteCard
import com.osnotes.app.ui.components.FolderItem
import com.osnotes.app.ui.viewmodels.HomeViewModel

/**
 * Home screen with favorites, recent notes, and tracked folders.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onFolderClick: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var folderToRemove by remember { mutableStateOf<String?>(null) }
    
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    
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
                            "OSNotes",
                            fontWeight = FontWeight.Bold,
                            color = onBackgroundColor
                        )
                    },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
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
                    onClick = { viewModel.addFolder() },
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Folder")
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Favorites Section
                if (uiState.favorites.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Favorites",
                            icon = Icons.Default.Star
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.favorites.forEach { note ->
                                NoteCard(
                                    name = note.name,
                                    pageCount = note.pageCount,
                                    isFavorite = true,
                                    onClick = { onNoteClick(note.path) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(note.path) }
                                )
                            }
                        }
                    }
                }
                
                // Recent Section
                if (uiState.recentNotes.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Recent",
                            icon = Icons.Default.History
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.recentNotes.forEach { note ->
                                NoteCard(
                                    name = note.name,
                                    pageCount = note.pageCount,
                                    isFavorite = note.isFavorite,
                                    onClick = { onNoteClick(note.path) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(note.path) }
                                )
                            }
                        }
                    }
                }
                
                // Tracked Folders Section
                item {
                    SectionHeader(
                        title = "Folders",
                        icon = Icons.Default.Folder
                    )
                }
                
                if (uiState.trackedFolders.isEmpty()) {
                    item {
                        GlassmorphicSurface(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = onBackgroundColor.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "No folders added yet",
                                    color = onBackgroundColor.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tap + to add a folder",
                                    color = onBackgroundColor.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.trackedFolders) { folder ->
                        FolderItemWithLongPress(
                            name = folder.name,
                            noteCount = folder.noteCount,
                            onClick = { onFolderClick(folder.path) },
                            onLongClick = { folderToRemove = folder.path }
                        )
                    }
                }
            }
        }
        
        // Remove Folder Confirmation Dialog
        if (folderToRemove != null) {
            AlertDialog(
                onDismissRequest = { folderToRemove = null },
                title = { Text("Remove Folder") },
                text = { Text("Remove this folder from tracking? (Files will not be deleted)") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            folderToRemove?.let { viewModel.removeFolder(it) }
                            folderToRemove = null
                        }
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToRemove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItemWithLongPress(
    name: String,
    noteCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
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
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    name,
                    fontWeight = FontWeight.Medium,
                    color = onBackgroundColor
                )
                Text(
                    "$noteCount notes",
                    color = onBackgroundColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = onBackgroundColor.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = onBackgroundColor
        )
    }
}
