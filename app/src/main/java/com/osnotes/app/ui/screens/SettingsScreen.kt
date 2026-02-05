package com.osnotes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.osnotes.app.ui.components.GlassmorphicSurface
import com.osnotes.app.ui.theme.AppColors
import com.osnotes.app.ui.viewmodels.SettingsViewModel
import androidx.compose.material.icons.outlined.GridOn

/**
 * Settings screen with glassmorphic design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTemplateBuilderClick: () -> Unit = {},
    onTemplateManagerClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
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
                            "Settings",
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
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Storage Section
                SettingsSection(title = "Storage") {
                    SettingsItem(
                        icon = Icons.Default.Folder,
                        title = "Tracked Folders",
                        subtitle = "${uiState.trackedFoldersCount} folders tracked",
                        onClick = { /* Add folders from home screen */ }
                    )
                }
                
                // Toolbar Section
                SettingsSection(title = "Editor") {
                    SettingsItem(
                        icon = Icons.Default.ViewSidebar,
                        title = "Toolbar Position",
                        subtitle = when (uiState.toolbarPosition) {
                            "left" -> "Left Edge"
                            "top" -> "Top"
                            else -> "Right Edge"
                        },
                        onClick = { viewModel.cycleToolbarPosition() }
                    )
                    
                    SettingsToggleItem(
                        icon = Icons.Default.TouchApp,
                        title = "Palm Rejection",
                        subtitle = "Ignore palm touches while writing",
                        checked = uiState.palmRejection,
                        onCheckedChange = { viewModel.setPalmRejection(it) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = "Auto-save Interval",
                        subtitle = "${uiState.autoSaveInterval} seconds",
                        onClick = { /* TODO: Interval picker */ }
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.GridOn,
                        title = "Template Builder",
                        subtitle = "Create custom page templates",
                        onClick = onTemplateBuilderClick
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.ViewList,
                        title = "My Templates",
                        subtitle = "Manage saved templates",
                        onClick = onTemplateManagerClick
                    )
                }
                
                // Appearance Section
                SettingsSection(title = "Appearance") {
                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = "Theme",
                        subtitle = when (uiState.theme) {
                            "light" -> "Light"
                            "amoled" -> "AMOLED Dark"
                            else -> "Dark"
                        },
                        onClick = { viewModel.cycleTheme() }
                    )
                    
                    SettingsToggleItem(
                        icon = Icons.Default.Palette,
                        title = "Dynamic Colors",
                        subtitle = "Use system accent colors",
                        checked = uiState.dynamicColors,
                        onCheckedChange = { viewModel.setDynamicColors(it) }
                    )
                }
                
                // About Section
                SettingsSection(title = "About") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "OSNotes",
                        subtitle = "Version 1.0.0 - Open Source",
                        onClick = { /* TODO: About dialog */ }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Code,
                        title = "Source Code",
                        subtitle = "View on GitHub",
                        onClick = { /* TODO: Open GitHub */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = primaryColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = onBackgroundColor.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = onBackgroundColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    color = onBackgroundColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = onBackgroundColor.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = onBackgroundColor.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = onBackgroundColor,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                color = onBackgroundColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = primaryColor,
                uncheckedThumbColor = onBackgroundColor.copy(alpha = 0.6f),
                uncheckedTrackColor = onBackgroundColor.copy(alpha = 0.2f)
            )
        )
    }
}

