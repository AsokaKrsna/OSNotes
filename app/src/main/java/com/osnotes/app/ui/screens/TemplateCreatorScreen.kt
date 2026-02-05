@file:OptIn(ExperimentalMaterial3Api::class)
package com.osnotes.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.osnotes.app.domain.model.CustomTemplate
import com.osnotes.app.domain.model.PatternType
import com.osnotes.app.ui.viewmodels.TemplateCreatorViewModel
import kotlin.math.sqrt

/**
 * Enhanced Template Creator Screen with tabbed interface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateCreatorScreen(
    onBack: () -> Unit,
    templateId: String? = null,
    viewModel: TemplateCreatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val tabs = listOf("Pattern", "Lines", "Colors", "Margins", "Sections", "Presets")
    
    LaunchedEffect(templateId) {
        templateId?.let { viewModel.loadTemplate(it) }
    }
    
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Template saved!")
            onBack()
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (templateId != null) "Edit Template" else "Create Template",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetToDefault() }) {
                        Text("Reset")
                    }
                    Button(
                        onClick = { viewModel.saveTemplate() },
                        enabled = !uiState.isLoading && uiState.templateName.isNotBlank()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Left side - Preview
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Live Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    EnhancedTemplatePreview(
                        uiState = uiState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
            
            // Right side - Controls with Tabs
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                // Template Name at top
                OutlinedTextField(
                    value = uiState.templateName,
                    onValueChange = { viewModel.updateTemplateName(it) },
                    label = { Text("Template Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 13.sp) }
                        )
                    }
                }
                
                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> PatternTab(uiState, viewModel)
                        1 -> LinesTab(uiState, viewModel)
                        2 -> ColorsTab(uiState, viewModel)
                        3 -> MarginsTab(uiState, viewModel)
                        4 -> SectionsTab(uiState, viewModel)
                        5 -> PresetsTab(viewModel)
                    }
                }
            }
        }
    }
}

// ==================== TAB: Pattern ====================

@Composable
private fun PatternTab(
    uiState: com.osnotes.app.ui.viewmodels.TemplateCreatorUiState,
    viewModel: TemplateCreatorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pattern Type", fontWeight = FontWeight.SemiBold)
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PatternType.entries) { pattern ->
                PatternTypeCard(
                    patternType = pattern,
                    isSelected = uiState.patternType == pattern,
                    onClick = { viewModel.updatePatternType(pattern) }
                )
            }
        }
        
        Divider()
        
        // Spacing
        Text("Spacing: ${uiState.lineSpacing.toInt()} pt", fontWeight = FontWeight.Medium)
        Slider(
            value = uiState.lineSpacing,
            onValueChange = { viewModel.updateLineSpacing(it) },
            valueRange = 8f..60f
        )
        
        // Secondary Grid Toggle (for grid patterns)
        if (uiState.patternType == PatternType.GRID) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Major Grid Lines")
                Switch(
                    checked = uiState.hasSecondaryGrid,
                    onCheckedChange = { viewModel.toggleSecondaryGrid() }
                )
            }
            
            if (uiState.hasSecondaryGrid) {
                Text("Major Grid Spacing: ${uiState.secondaryLineSpacing.toInt()} pt")
                Slider(
                    value = uiState.secondaryLineSpacing,
                    onValueChange = { viewModel.updateSecondaryLineSpacing(it) },
                    valueRange = 16f..120f
                )
            }
        }
    }
}

@Composable
private fun PatternTypeCard(
    patternType: PatternType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Mini preview
                Canvas(modifier = Modifier.size(36.dp)) {
                    drawPatternPreview(patternType)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    patternType.name.replace("_", "\n").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

private fun DrawScope.drawPatternPreview(patternType: PatternType) {
    val color = Color.Gray
    val spacing = 8f
    
    when (patternType) {
        PatternType.NONE -> { /* blank */ }
        PatternType.HORIZONTAL_LINES -> {
            var y = spacing
            while (y < size.height) {
                drawLine(color, Offset(2f, y), Offset(size.width - 2f, y), strokeWidth = 0.5f)
                y += spacing
            }
        }
        PatternType.VERTICAL_LINES -> {
            var x = spacing
            while (x < size.width) {
                drawLine(color, Offset(x, 2f), Offset(x, size.height - 2f), strokeWidth = 0.5f)
                x += spacing
            }
        }
        PatternType.GRID -> {
            var x = spacing
            while (x < size.width) {
                drawLine(color, Offset(x, 2f), Offset(x, size.height - 2f), strokeWidth = 0.5f)
                x += spacing
            }
            var y = spacing
            while (y < size.height) {
                drawLine(color, Offset(2f, y), Offset(size.width - 2f, y), strokeWidth = 0.5f)
                y += spacing
            }
        }
        PatternType.DOTS -> {
            var x = spacing
            while (x < size.width) {
                var y = spacing
                while (y < size.height) {
                    drawCircle(color, 1f, Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }
        PatternType.ISOMETRIC_DOTS -> {
            var row = 0
            var y = spacing
            while (y < size.height) {
                val xOffset = if (row % 2 == 0) 0f else spacing / 2f
                var x = spacing + xOffset
                while (x < size.width) {
                    drawCircle(color, 1f, Offset(x, y))
                    x += spacing
                }
                y += spacing * 0.866f
                row++
            }
        }
        PatternType.DIAGONAL_LEFT -> {
            // Draw lines like \ (top-left to bottom-right)
            var offset = 0f
            while (offset < size.width + size.height) {
                drawLine(color, 
                    Offset(offset - size.height, 0f), 
                    Offset(offset, size.height), 
                    strokeWidth = 0.5f)
                offset += spacing
            }
        }
        PatternType.DIAGONAL_RIGHT -> {
            // Draw lines like / (top-right to bottom-left)
            var offset = -size.height
            while (offset < size.width) {
                drawLine(color, 
                    Offset(offset, size.height), 
                    Offset(offset + size.height, 0f), 
                    strokeWidth = 0.5f)
                offset += spacing
            }
        }
        PatternType.CROSSHATCH -> {
            // First draw \ lines (DIAGONAL_LEFT)
            var offset = 0f
            while (offset < size.width + size.height) {
                drawLine(color, 
                    Offset(offset - size.height, 0f), 
                    Offset(offset, size.height), 
                    strokeWidth = 0.5f)
                offset += spacing
            }
            // Then draw / lines (DIAGONAL_RIGHT)
            offset = -size.height
            while (offset < size.width) {
                drawLine(color, 
                    Offset(offset, size.height), 
                    Offset(offset + size.height, 0f), 
                    strokeWidth = 0.5f)
                offset += spacing
            }
        }
    }
}

// ==================== TAB: Lines ====================

@Composable
private fun LinesTab(
    uiState: com.osnotes.app.ui.viewmodels.TemplateCreatorUiState,
    viewModel: TemplateCreatorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Line Thickness
        Text("Line Thickness: ${String.format("%.1f", uiState.lineThickness)} pt", fontWeight = FontWeight.Medium)
        Slider(
            value = uiState.lineThickness,
            onValueChange = { viewModel.updateLineThickness(it) },
            valueRange = 0.1f..3f
        )
        
        Divider()
        
        // Pattern Opacity
        Text("Pattern Opacity: ${(uiState.patternOpacity * 100).toInt()}%", fontWeight = FontWeight.Medium)
        Slider(
            value = uiState.patternOpacity,
            onValueChange = { viewModel.updatePatternOpacity(it) },
            valueRange = 0.1f..1f
        )
        
        // Dot Size (only for dot patterns)
        if (uiState.patternType == PatternType.DOTS || uiState.patternType == PatternType.ISOMETRIC_DOTS) {
            Divider()
            Text("Dot Size: ${String.format("%.1f", uiState.dotSize)} pt", fontWeight = FontWeight.Medium)
            Slider(
                value = uiState.dotSize,
                onValueChange = { viewModel.updateDotSize(it) },
                valueRange = 0.3f..3f
            )
        }
        
        // Secondary Grid Settings
        if (uiState.hasSecondaryGrid) {
            Divider()
            Text("Major Grid Thickness: ${String.format("%.1f", uiState.secondaryLineThickness)} pt", fontWeight = FontWeight.Medium)
            Slider(
                value = uiState.secondaryLineThickness,
                onValueChange = { viewModel.updateSecondaryLineThickness(it) },
                valueRange = 0.2f..3f
            )
        }
    }
}

// ==================== TAB: Colors ====================

@Composable
private fun ColorsTab(
    uiState: com.osnotes.app.ui.viewmodels.TemplateCreatorUiState,
    viewModel: TemplateCreatorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Background Color
        ColorPickerSection(
            label = "Background",
            currentColor = uiState.backgroundColor,
            onColorSelected = { viewModel.updateBackgroundColor(it) },
            presetColors = backgroundColors
        )
        
        Divider()
        
        // Line Color
        ColorPickerSection(
            label = "Pattern Lines/Dots",
            currentColor = uiState.lineColor,
            onColorSelected = { viewModel.updateLineColor(it) },
            presetColors = lineColors
        )
        
        if (uiState.hasSecondaryGrid) {
            Divider()
            ColorPickerSection(
                label = "Major Grid Lines",
                currentColor = uiState.secondaryLineColor,
                onColorSelected = { viewModel.updateSecondaryLineColor(it) },
                presetColors = lineColors
            )
        }
        
        if (uiState.hasMarginLine) {
            Divider()
            ColorPickerSection(
                label = "Margin Line",
                currentColor = uiState.marginLineColor,
                onColorSelected = { viewModel.updateMarginLineColor(it) },
                presetColors = accentColors
            )
        }
    }
}

@Composable
private fun ColorPickerSection(
    label: String,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    presetColors: List<Color>
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(1.dp, Color.Gray, CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presetColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (currentColor == color) 2.dp else 1.dp,
                            color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}

// Color Presets
private val backgroundColors = listOf(
    Color.White,
    Color(0xFFFFFCF7), // Cream
    Color(0xFFF0FAF0), // Light green
    Color(0xFFF5F5FF), // Light blue
    Color(0xFFFFF5F5), // Light pink
    Color(0xFFFFF8E7), // Light yellow
    Color(0xFFF0F0F5), // Light gray
    Color(0xFF1A1A1A)  // Dark
)

private val lineColors = listOf(
    Color(0xFF99BBD9), // Light blue
    Color(0xFFCCCCCC), // Gray
    Color(0xFFB3D9B3), // Light green
    Color(0xFF262626), // Black
    Color(0xFF8C8C8C), // Medium gray
    Color(0xFFA6A6A6), // Lighter gray
    Color(0xFF73B373), // Green
    Color(0xFFD9B399)  // Tan
)

private val accentColors = listOf(
    Color(0xFFCC3333), // Red
    Color(0xFF3366CC), // Blue
    Color(0xFF33CC33), // Green
    Color(0xFFCC33CC), // Purple
    Color(0xFFFF9900), // Orange
    Color(0xFF009999), // Teal
    Color(0xFF262626), // Black
    Color(0xFF999999)  // Gray
)

// ==================== TAB: Margins ====================

@Composable
private fun MarginsTab(
    uiState: com.osnotes.app.ui.viewmodels.TemplateCreatorUiState,
    viewModel: TemplateCreatorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Page Margins", fontWeight = FontWeight.SemiBold)
        
        // Top margin
        Text("Top: ${uiState.marginTop.toInt()} pt")
        Slider(
            value = uiState.marginTop,
            onValueChange = { viewModel.updateMarginTop(it) },
            valueRange = 0f..150f
        )
        
        // Bottom margin
        Text("Bottom: ${uiState.marginBottom.toInt()} pt")
        Slider(
            value = uiState.marginBottom,
            onValueChange = { viewModel.updateMarginBottom(it) },
            valueRange = 0f..150f
        )
        
        // Left margin
        Text("Left: ${uiState.marginLeft.toInt()} pt")
        Slider(
            value = uiState.marginLeft,
            onValueChange = { viewModel.updateMarginLeft(it) },
            valueRange = 0f..150f
        )
        
        // Right margin
        Text("Right: ${uiState.marginRight.toInt()} pt")
        Slider(
            value = uiState.marginRight,
            onValueChange = { viewModel.updateMarginRight(it) },
            valueRange = 0f..150f
        )
        
        Divider()
        
        // Margin Line
        Text("Margin Line", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show Margin Line")
            Switch(
                checked = uiState.hasMarginLine,
                onCheckedChange = { viewModel.toggleMarginLine() }
            )
        }
        
        if (uiState.hasMarginLine) {
            Text("Position: ${uiState.marginLinePosition.toInt()} pt from left")
            Slider(
                value = uiState.marginLinePosition,
                onValueChange = { viewModel.updateMarginLinePosition(it) },
                valueRange = 20f..200f
            )
        }
    }
}

// ==================== TAB: Sections ====================

@Composable
private fun SectionsTab(
    uiState: com.osnotes.app.ui.viewmodels.TemplateCreatorUiState,
    viewModel: TemplateCreatorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header Section
        Text("Header Section", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Header")
            Switch(
                checked = uiState.hasHeader,
                onCheckedChange = { viewModel.toggleHeader() }
            )
        }
        
        if (uiState.hasHeader) {
            Text("Height: ${uiState.headerHeight.toInt()} pt")
            Slider(
                value = uiState.headerHeight,
                onValueChange = { viewModel.updateHeaderHeight(it) },
                valueRange = 30f..150f
            )
            ColorPickerSection(
                label = "Header Color",
                currentColor = uiState.headerColor,
                onColorSelected = { viewModel.updateHeaderColor(it) },
                presetColors = backgroundColors
            )
        }
        
        Divider()
        
        // Footer Section
        Text("Footer/Summary Section", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Footer")
            Switch(
                checked = uiState.hasFooter,
                onCheckedChange = { viewModel.toggleFooter() }
            )
        }
        
        if (uiState.hasFooter) {
            Text("Height: ${uiState.footerHeight.toInt()} pt")
            Slider(
                value = uiState.footerHeight,
                onValueChange = { viewModel.updateFooterHeight(it) },
                valueRange = 30f..200f
            )
            ColorPickerSection(
                label = "Footer Color",
                currentColor = uiState.footerColor,
                onColorSelected = { viewModel.updateFooterColor(it) },
                presetColors = backgroundColors
            )
        }
        
        Divider()
        
        // Side Column (Cornell style)
        Text("Side Column (Cornell Style)", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Side Column")
            Switch(
                checked = uiState.hasSideColumn,
                onCheckedChange = { viewModel.toggleSideColumn() }
            )
        }
        
        if (uiState.hasSideColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Position")
                Row {
                    FilterChip(
                        selected = uiState.sideColumnOnLeft,
                        onClick = { if (!uiState.sideColumnOnLeft) viewModel.toggleSideColumnPosition() },
                        label = { Text("Left") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !uiState.sideColumnOnLeft,
                        onClick = { if (uiState.sideColumnOnLeft) viewModel.toggleSideColumnPosition() },
                        label = { Text("Right") }
                    )
                }
            }
            
            Text("Width: ${uiState.sideColumnWidth.toInt()} pt")
            Slider(
                value = uiState.sideColumnWidth,
                onValueChange = { viewModel.updateSideColumnWidth(it) },
                valueRange = 80f..250f
            )
            ColorPickerSection(
                label = "Column Color",
                currentColor = uiState.sideColumnColor,
                onColorSelected = { viewModel.updateSideColumnColor(it) },
                presetColors = backgroundColors
            )
        }
    }
}

// ==================== TAB: Presets ====================

@Composable
private fun PresetsTab(viewModel: TemplateCreatorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Quick Start Presets",
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Select a preset to load, then customize as needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        CustomTemplate.presets.forEach { preset ->
            PresetCard(
                preset = preset,
                onClick = { viewModel.loadPreset(preset) }
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: CustomTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini preview
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(preset.backgroundColor))
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    drawPatternPreview(preset.patternType)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, fontWeight = FontWeight.Medium)
                Text(
                    getPresetDescription(preset),
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
}

private fun getPresetDescription(preset: CustomTemplate): String {
    return when (preset.patternType) {
        PatternType.HORIZONTAL_LINES -> "Horizontal lines"
        PatternType.GRID -> "Grid pattern"
        PatternType.DOTS -> "Dot grid"
        PatternType.ISOMETRIC_DOTS -> "Isometric dots"
        PatternType.DIAGONAL_RIGHT -> "Diagonal lines"
        else -> "Custom pattern"
    } + if (preset.hasSideColumn) " with side column" else ""
}

// ==================== Preview Canvas ====================

@Composable
private fun EnhancedTemplatePreview(
    uiState: com.osnotes.app.ui.viewmodels.TemplateCreatorUiState,
    modifier: Modifier = Modifier
) {
    val aspectRatio = 595f / 842f // A4 ratio
    
    Card(
        modifier = modifier.aspectRatio(aspectRatio),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val scale = width / 595f
            
            // Background
            drawRect(uiState.backgroundColor)
            
            // Header section
            if (uiState.hasHeader) {
                val headerH = uiState.headerHeight * scale
                drawRect(
                    uiState.headerColor,
                    topLeft = Offset(0f, height - headerH),
                    size = androidx.compose.ui.geometry.Size(width, headerH)
                )
            }
            
            // Footer section
            if (uiState.hasFooter) {
                val footerH = uiState.footerHeight * scale
                drawRect(
                    uiState.footerColor,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(width, footerH)
                )
            }
            
            // Side column
            if (uiState.hasSideColumn) {
                val colWidth = uiState.sideColumnWidth * scale
                val footerH = if (uiState.hasFooter) uiState.footerHeight * scale else 0f
                val headerH = if (uiState.hasHeader) uiState.headerHeight * scale else 0f
                
                if (uiState.sideColumnOnLeft) {
                    drawRect(
                        uiState.sideColumnColor,
                        topLeft = Offset(0f, footerH),
                        size = androidx.compose.ui.geometry.Size(colWidth, height - footerH - headerH)
                    )
                } else {
                    drawRect(
                        uiState.sideColumnColor,
                        topLeft = Offset(width - colWidth, footerH),
                        size = androidx.compose.ui.geometry.Size(colWidth, height - footerH - headerH)
                    )
                }
            }
            
            // Pattern
            val marginL = uiState.marginLeft * scale
            val marginR = uiState.marginRight * scale
            val marginT = uiState.marginTop * scale
            val marginB = uiState.marginBottom * scale
            val spacing = uiState.lineSpacing * scale
            val patternColor = uiState.lineColor.copy(alpha = uiState.patternOpacity)
            
            when (uiState.patternType) {
                PatternType.HORIZONTAL_LINES -> {
                    var y = height - marginT - spacing
                    while (y > marginB) {
                        drawLine(patternColor, Offset(marginL, y), Offset(width - marginR, y), 
                            strokeWidth = uiState.lineThickness * scale)
                        y -= spacing
                    }
                }
                PatternType.VERTICAL_LINES -> {
                    var x = marginL + spacing
                    while (x < width - marginR) {
                        drawLine(patternColor, Offset(x, marginB), Offset(x, height - marginT),
                            strokeWidth = uiState.lineThickness * scale)
                        x += spacing
                    }
                }
                PatternType.GRID -> {
                    // Minor grid
                    var x = marginL + spacing
                    while (x < width - marginR) {
                        drawLine(patternColor, Offset(x, marginB), Offset(x, height - marginT),
                            strokeWidth = uiState.lineThickness * scale)
                        x += spacing
                    }
                    var y = height - marginT - spacing
                    while (y > marginB) {
                        drawLine(patternColor, Offset(marginL, y), Offset(width - marginR, y),
                            strokeWidth = uiState.lineThickness * scale)
                        y -= spacing
                    }
                    // Major grid
                    if (uiState.hasSecondaryGrid) {
                        val majorSpacing = uiState.secondaryLineSpacing * scale
                        val majorColor = uiState.secondaryLineColor.copy(alpha = uiState.patternOpacity)
                        x = marginL + majorSpacing
                        while (x < width - marginR) {
                            drawLine(majorColor, Offset(x, marginB), Offset(x, height - marginT),
                                strokeWidth = uiState.secondaryLineThickness * scale)
                            x += majorSpacing
                        }
                        y = height - marginT - majorSpacing
                        while (y > marginB) {
                            drawLine(majorColor, Offset(marginL, y), Offset(width - marginR, y),
                                strokeWidth = uiState.secondaryLineThickness * scale)
                            y -= majorSpacing
                        }
                    }
                }
                PatternType.DOTS -> {
                    var x = marginL + spacing
                    while (x < width - marginR) {
                        var y = marginB + spacing
                        while (y < height - marginT) {
                            drawCircle(patternColor, uiState.dotSize * scale, Offset(x, y))
                            y += spacing
                        }
                        x += spacing
                    }
                }
                PatternType.ISOMETRIC_DOTS -> {
                    var row = 0
                    var y = marginB + spacing
                    while (y < height - marginT) {
                        val xOffset = if (row % 2 == 0) 0f else spacing / 2f
                        var x = marginL + spacing + xOffset
                        while (x < width - marginR) {
                            drawCircle(patternColor, uiState.dotSize * scale, Offset(x, y))
                            x += spacing
                        }
                        y += spacing * 0.866f
                        row++
                    }
                }
                PatternType.DIAGONAL_LEFT, PatternType.DIAGONAL_RIGHT, PatternType.CROSSHATCH -> {
                    val topY = marginT
                    val bottomY = height - marginB
                    val patternHeight = bottomY - topY
                    
                    // DIAGONAL_LEFT: Draw \ lines (from top-left to bottom-right)
                    // Line goes from (x, topY) to (x + patternHeight, bottomY)
                    if (uiState.patternType == PatternType.DIAGONAL_LEFT || uiState.patternType == PatternType.CROSSHATCH) {
                        var offset = marginL - patternHeight
                        while (offset < width - marginR) {
                            val x1 = offset.coerceIn(marginL, width - marginR)
                            val x2 = (offset + patternHeight).coerceIn(marginL, width - marginR)
                            val y1 = topY + (x1 - offset)
                            val y2 = topY + (x2 - offset)
                            drawLine(patternColor,
                                Offset(x1, y1.coerceIn(topY, bottomY)),
                                Offset(x2, y2.coerceIn(topY, bottomY)),
                                strokeWidth = uiState.lineThickness * scale)
                            offset += spacing
                        }
                    }
                    
                    // DIAGONAL_RIGHT: Draw / lines (from bottom-left to top-right)
                    // Line goes from (x, bottomY) to (x + patternHeight, topY)
                    if (uiState.patternType == PatternType.DIAGONAL_RIGHT || uiState.patternType == PatternType.CROSSHATCH) {
                        var offset = marginL - patternHeight
                        while (offset < width - marginR) {
                            val x1 = offset.coerceIn(marginL, width - marginR)
                            val x2 = (offset + patternHeight).coerceIn(marginL, width - marginR)
                            val y1 = bottomY - (x1 - offset)
                            val y2 = bottomY - (x2 - offset)
                            drawLine(patternColor,
                                Offset(x1, y1.coerceIn(topY, bottomY)),
                                Offset(x2, y2.coerceIn(topY, bottomY)),
                                strokeWidth = uiState.lineThickness * scale)
                            offset += spacing
                        }
                    }
                }
                PatternType.NONE -> { /* blank */ }
            }
            
            // Margin line
            if (uiState.hasMarginLine) {
                val lineX = uiState.marginLinePosition * scale
                drawLine(
                    uiState.marginLineColor,
                    Offset(lineX, marginB),
                    Offset(lineX, height - marginT),
                    strokeWidth = 1f * scale
                )
            }
        }
    }
}
