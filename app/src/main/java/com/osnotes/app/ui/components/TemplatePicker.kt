package com.osnotes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Template types for new note pages.
 */
enum class PageTemplate(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
) {
    BLANK("Blank", "Plain white page", Icons.Outlined.Description, Color(0xFFF5F5F5)),
    LINED("Lined", "College ruled lines", Icons.Outlined.FormatListBulleted, Color(0xFFE3F2FD)),
    GRID("Grid", "Square grid pattern", Icons.Outlined.GridOn, Color(0xFFE8F5E9)),
    DOTTED("Dotted", "Dot grid pattern", Icons.Outlined.MoreHoriz, Color(0xFFFCE4EC)),
    CORNELL("Cornell", "Cornell notes layout", Icons.Outlined.ViewAgenda, Color(0xFFFFF3E0)),
    ISOMETRIC("Isometric", "Isometric dot grid", Icons.Outlined.Hexagon, Color(0xFFF3E5F5)),
    MUSIC("Music", "Music staff lines", Icons.Outlined.MusicNote, Color(0xFFE0F7FA)),
    ENGINEERING("Engineering", "Engineering paper", Icons.Outlined.Engineering, Color(0xFFE8EAF6))
}

/**
 * Dialog to pick a template when creating a new note or adding a page.
 * Now supports both predefined templates and custom user-created templates.
 */
@Composable
fun TemplatePickerDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (PageTemplate) -> Unit,
    title: String = "Choose Template",
    customTemplates: List<com.osnotes.app.domain.model.CustomTemplate> = emptyList(),
    onCustomTemplateSelected: ((com.osnotes.app.domain.model.CustomTemplate) -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(max = 500.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Predefined templates section
                    item {
                        Text(
                            "Standard Templates",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.height(200.dp)
                        ) {
                            items(PageTemplate.values().toList()) { template ->
                                TemplateCard(
                                    template = template,
                                    onClick = { 
                                        onTemplateSelected(template)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                    
                    // Custom templates section
                    if (customTemplates.isNotEmpty() && onCustomTemplateSelected != null) {
                        item {
                            Text(
                                "My Custom Templates",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.height(
                                    ((customTemplates.size + 2) / 3 * 95).coerceAtMost(200).dp
                                )
                            ) {
                                items(customTemplates) { customTemplate ->
                                    CustomTemplateCard(
                                        template = customTemplate,
                                        onClick = {
                                            onCustomTemplateSelected(customTemplate)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomTemplateCard(
    template: com.osnotes.app.domain.model.CustomTemplate,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor = Color(template.backgroundColor)
    
    Column(
        modifier = Modifier
            .clip(shape)
            .background(bgColor.copy(alpha = 0.3f))
            .border(1.dp, bgColor.copy(alpha = 0.5f), shape)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Template preview
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Dashboard,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (bgColor.luminance() > 0.5f) Color.DarkGray else Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            template.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun Color.luminance(): Float {
    val red = this.red
    val green = this.green
    val blue = this.blue
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

@Composable
private fun TemplateCard(
    template: PageTemplate,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    
    Column(
        modifier = Modifier
            .clip(shape)
            .background(template.color)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Template preview
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Color.White.copy(alpha = 0.9f)
                ),
            contentAlignment = Alignment.Center
        ) {
            when (template) {
                PageTemplate.BLANK -> {
                    // Just white
                }
                PageTemplate.LINED -> {
                    // Lines preview
                    Column(
                        modifier = Modifier.padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(5) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFBDBDBD))
                            )
                        }
                    }
                }
                PageTemplate.GRID -> {
                    // Grid preview
                    Icon(
                        Icons.Outlined.GridOn,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFFBDBDBD)
                    )
                }
                PageTemplate.DOTTED -> {
                    // Dots preview
                    Column(
                        modifier = Modifier.padding(4.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(4) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(4) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFFBDBDBD))
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Icon(
                        template.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFF757575)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            template.displayName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Create New Note Dialog - simplified version that uses template picker.
 */
@Composable
fun CreateNewNoteDialog(
    onDismiss: () -> Unit,
    onCreateNote: (String, PageTemplate) -> Unit
) {
    var noteName by remember { mutableStateOf("") }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf(PageTemplate.BLANK) }
    
    if (showTemplatePicker) {
        TemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onTemplateSelected = { 
                selectedTemplate = it
                showTemplatePicker = false
            },
            title = "Choose Page Template"
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Note") },
        text = {
            Column {
                OutlinedTextField(
                    value = noteName,
                    onValueChange = { noteName = it },
                    label = { Text("Note Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Template selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showTemplatePicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        selectedTemplate.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedTemplate.displayName,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            selectedTemplate.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteName.isNotBlank()) {
                        onCreateNote(noteName.trim(), selectedTemplate)
                        onDismiss()
                    }
                },
                enabled = noteName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Insert position for adding a new page.
 */
enum class InsertPosition {
    START,
    AFTER_CURRENT,
    END
}

/**
 * Add Page Dialog - shows template picker and insert position.
 * Now supports both predefined and custom templates.
 */
@Composable
fun AddPageDialog(
    onDismiss: () -> Unit,
    onAddPage: (PageTemplate, InsertPosition) -> Unit,
    currentPage: Int,
    totalPages: Int,
    customTemplates: List<com.osnotes.app.domain.model.CustomTemplate> = emptyList(),
    onAddCustomTemplatePage: ((com.osnotes.app.domain.model.CustomTemplate, InsertPosition) -> Unit)? = null
) {
    var showTemplatePicker by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<PageTemplate?>(PageTemplate.BLANK) }
    var selectedCustomTemplate by remember { mutableStateOf<com.osnotes.app.domain.model.CustomTemplate?>(null) }
    var insertPosition by remember { mutableStateOf(InsertPosition.AFTER_CURRENT) }
    
    if (showTemplatePicker) {
        TemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onTemplateSelected = { 
                selectedTemplate = it
                selectedCustomTemplate = null
                showTemplatePicker = false
            },
            title = "Choose Page Template",
            customTemplates = customTemplates,
            onCustomTemplateSelected = { customTemplate ->
                selectedCustomTemplate = customTemplate
                selectedTemplate = null
                showTemplatePicker = false
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Page") },
        text = {
            Column {
                // Template selector
                Text(
                    "Template",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showTemplatePicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedCustomTemplate != null) {
                        // Show custom template
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(selectedCustomTemplate!!.backgroundColor))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedCustomTemplate!!.name,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Custom template",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Show predefined template
                        val template = selectedTemplate ?: PageTemplate.BLANK
                        Icon(
                            template.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                template.displayName,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Insert position
                Text(
                    "Insert at",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Column {
                    InsertOption(
                        label = "Start of document",
                        selected = insertPosition == InsertPosition.START,
                        onClick = { insertPosition = InsertPosition.START }
                    )
                    InsertOption(
                        label = "After page $currentPage",
                        selected = insertPosition == InsertPosition.AFTER_CURRENT,
                        onClick = { insertPosition = InsertPosition.AFTER_CURRENT }
                    )
                    InsertOption(
                        label = "End of document",
                        selected = insertPosition == InsertPosition.END,
                        onClick = { insertPosition = InsertPosition.END }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedCustomTemplate != null && onAddCustomTemplatePage != null) {
                        onAddCustomTemplatePage(selectedCustomTemplate!!, insertPosition)
                    } else {
                        onAddPage(selectedTemplate ?: PageTemplate.BLANK, insertPosition)
                    }
                }
            ) {
                Text("Add Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun InsertOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * Combined dialog for creating new Note (with template selection) or Folder.
 * Used by FolderScreen to replace the basic CreateNewDialog.
 * Now supports both predefined and custom templates.
 */
@Composable
fun CreateNewWithTemplateDialog(
    onDismiss: () -> Unit,
    onCreateNote: (String, PageTemplate) -> Unit,
    onCreateFolder: (String) -> Unit,
    onNavigateToTemplateBuilder: (() -> Unit)? = null,
    customTemplates: List<com.osnotes.app.domain.model.CustomTemplate> = emptyList(),
    onCreateNoteWithCustomTemplate: ((String, com.osnotes.app.domain.model.CustomTemplate) -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<PageTemplate?>(PageTemplate.BLANK) }
    var selectedCustomTemplate by remember { mutableStateOf<com.osnotes.app.domain.model.CustomTemplate?>(null) }
    
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // Template picker dialog
    if (showTemplatePicker) {
        TemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onTemplateSelected = { 
                selectedTemplate = it
                selectedCustomTemplate = null
                showTemplatePicker = false
            },
            title = "Choose Template",
            customTemplates = customTemplates,
            onCustomTemplateSelected = { customTemplate ->
                selectedCustomTemplate = customTemplate
                selectedTemplate = null
                showTemplatePicker = false
            }
        )
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Create New",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceColor
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(onSurfaceColor.copy(alpha = 0.08f))
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = "Note",
                        icon = Icons.Outlined.Description,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = "Folder",
                        icon = Icons.Outlined.Folder,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Name input
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
                
                // Template selector (only for notes)
                if (selectedTab == 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Template",
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceColor.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showTemplatePicker = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedCustomTemplate != null) {
                            // Show custom template
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(selectedCustomTemplate!!.backgroundColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Dashboard,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    selectedCustomTemplate!!.name,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurfaceColor
                                )
                                Text(
                                    "Custom template",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceColor.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            // Show predefined template
                            val template = selectedTemplate ?: PageTemplate.BLANK
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(template.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    template.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = Color.DarkGray
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    template.displayName,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurfaceColor
                                )
                                Text(
                                    template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Choose template",
                            tint = onSurfaceColor.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = onSurfaceColor.copy(alpha = 0.7f))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                if (selectedTab == 0) {
                                    if (selectedCustomTemplate != null && onCreateNoteWithCustomTemplate != null) {
                                        onCreateNoteWithCustomTemplate(name.trim(), selectedCustomTemplate!!)
                                    } else {
                                        onCreateNote(name.trim(), selectedTemplate ?: PageTemplate.BLANK)
                                    }
                                } else {
                                    onCreateFolder(name.trim())
                                }
                                onDismiss()
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor
        )
    }
}
