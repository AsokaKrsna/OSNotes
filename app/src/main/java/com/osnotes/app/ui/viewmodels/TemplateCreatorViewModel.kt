package com.osnotes.app.ui.viewmodels

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osnotes.app.data.repository.CustomTemplateRepository
import com.osnotes.app.domain.model.CustomTemplate
import com.osnotes.app.domain.model.PatternType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Enhanced UI State for Template Creator with all customization options.
 */
data class TemplateCreatorUiState(
    // Basic info
    val templateName: String = "Custom Template",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    
    // Background
    val backgroundColor: Color = Color.White,
    
    // Pattern
    val patternType: PatternType = PatternType.HORIZONTAL_LINES,
    val lineColor: Color = Color(0xFF99BBD9),
    val lineSpacing: Float = 24f,
    val lineThickness: Float = 0.5f,
    val patternOpacity: Float = 1.0f,
    
    // Secondary grid
    val hasSecondaryGrid: Boolean = false,
    val secondaryLineColor: Color = Color(0xFFA6A6A6),
    val secondaryLineSpacing: Float = 48f,
    val secondaryLineThickness: Float = 0.8f,
    
    // Dot settings
    val dotSize: Float = 1.0f,
    
    // Margins
    val marginLeft: Float = 40f,
    val marginRight: Float = 40f,
    val marginTop: Float = 60f,
    val marginBottom: Float = 40f,
    
    // Margin line
    val hasMarginLine: Boolean = false,
    val marginLineColor: Color = Color(0xFFCC3333),
    val marginLinePosition: Float = 72f,
    
    // Header
    val hasHeader: Boolean = false,
    val headerHeight: Float = 60f,
    val headerColor: Color = Color(0xFFF0F0F5),
    
    // Footer
    val hasFooter: Boolean = false,
    val footerHeight: Float = 50f,
    val footerColor: Color = Color(0xFFF0F0F5),
    
    // Side column
    val hasSideColumn: Boolean = false,
    val sideColumnWidth: Float = 150f,
    val sideColumnColor: Color = Color(0xFFFAF5EB),
    val sideColumnOnLeft: Boolean = true,
    
    // Legacy
    val hasVerticalLines: Boolean = false,
    val hasHorizontalLines: Boolean = true,
    val hasDots: Boolean = false
)

@HiltViewModel
class TemplateCreatorViewModel @Inject constructor(
    private val customTemplateRepository: CustomTemplateRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TemplateCreatorUiState())
    val uiState: StateFlow<TemplateCreatorUiState> = _uiState.asStateFlow()
    
    private var editingTemplateId: String? = null
    
    val isEditMode: Boolean
        get() = editingTemplateId != null
    
    // ==================== Load / Save ====================
    
    fun loadTemplate(templateId: String) {
        viewModelScope.launch {
            customTemplateRepository.customTemplates.first().find { it.id == templateId }?.let { template ->
                editingTemplateId = templateId
                _uiState.update { stateFromTemplate(template) }
            }
        }
    }
    
    fun loadPreset(preset: CustomTemplate) {
        editingTemplateId = null // Presets create new templates
        _uiState.update { stateFromTemplate(preset).copy(templateName = preset.name + " (Custom)") }
    }
    
    private fun stateFromTemplate(template: CustomTemplate): TemplateCreatorUiState {
        return TemplateCreatorUiState(
            templateName = template.name,
            backgroundColor = Color(template.backgroundColor),
            patternType = template.patternType,
            lineColor = Color(template.lineColor),
            lineSpacing = template.lineSpacing,
            lineThickness = template.lineThickness,
            patternOpacity = template.patternOpacity,
            hasSecondaryGrid = template.hasSecondaryGrid,
            secondaryLineColor = Color(template.secondaryLineColor),
            secondaryLineSpacing = template.secondaryLineSpacing,
            secondaryLineThickness = template.secondaryLineThickness,
            dotSize = template.dotSize,
            marginLeft = template.marginLeft,
            marginRight = template.marginRight,
            marginTop = template.marginTop,
            marginBottom = template.marginBottom,
            hasMarginLine = template.hasMarginLine,
            marginLineColor = Color(template.marginLineColor),
            marginLinePosition = template.marginLinePosition,
            hasHeader = template.hasHeader,
            headerHeight = template.headerHeight,
            headerColor = Color(template.headerColor),
            hasFooter = template.hasFooter,
            footerHeight = template.footerHeight,
            footerColor = Color(template.footerColor),
            hasSideColumn = template.hasSideColumn,
            sideColumnWidth = template.sideColumnWidth,
            sideColumnColor = Color(template.sideColumnColor),
            sideColumnOnLeft = template.sideColumnOnLeft,
            hasVerticalLines = template.hasVerticalLines,
            hasHorizontalLines = template.hasHorizontalLines,
            hasDots = template.hasDots
        )
    }
    
    fun saveTemplate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val state = _uiState.value
                val template = CustomTemplate(
                    id = editingTemplateId ?: "custom_${System.currentTimeMillis()}",
                    name = state.templateName.ifBlank { "Custom Template" },
                    backgroundColor = state.backgroundColor.toArgb(),
                    patternType = state.patternType,
                    lineColor = state.lineColor.toArgb(),
                    lineSpacing = state.lineSpacing,
                    lineThickness = state.lineThickness,
                    patternOpacity = state.patternOpacity,
                    hasSecondaryGrid = state.hasSecondaryGrid,
                    secondaryLineColor = state.secondaryLineColor.toArgb(),
                    secondaryLineSpacing = state.secondaryLineSpacing,
                    secondaryLineThickness = state.secondaryLineThickness,
                    dotSize = state.dotSize,
                    marginLeft = state.marginLeft,
                    marginRight = state.marginRight,
                    marginTop = state.marginTop,
                    marginBottom = state.marginBottom,
                    hasMarginLine = state.hasMarginLine,
                    marginLineColor = state.marginLineColor.toArgb(),
                    marginLinePosition = state.marginLinePosition,
                    hasHeader = state.hasHeader,
                    headerHeight = state.headerHeight,
                    headerColor = state.headerColor.toArgb(),
                    hasFooter = state.hasFooter,
                    footerHeight = state.footerHeight,
                    footerColor = state.footerColor.toArgb(),
                    hasSideColumn = state.hasSideColumn,
                    sideColumnWidth = state.sideColumnWidth,
                    sideColumnColor = state.sideColumnColor.toArgb(),
                    sideColumnOnLeft = state.sideColumnOnLeft,
                    hasVerticalLines = state.hasVerticalLines,
                    hasHorizontalLines = state.hasHorizontalLines,
                    hasDots = state.hasDots
                )
                
                customTemplateRepository.saveTemplate(template)
                
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(isLoading = false, error = "Failed to save: ${e.message}") 
                }
            }
        }
    }
    
    fun resetToDefault() {
        editingTemplateId = null
        _uiState.update { TemplateCreatorUiState() }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    // ==================== Basic Updates ====================
    
    fun updateTemplateName(name: String) {
        _uiState.update { it.copy(templateName = name, isSaved = false) }
    }
    
    fun updateBackgroundColor(color: Color) {
        _uiState.update { it.copy(backgroundColor = color, isSaved = false) }
    }
    
    // ==================== Pattern Updates ====================
    
    fun updatePatternType(patternType: PatternType) {
        _uiState.update { it.copy(patternType = patternType, isSaved = false) }
    }
    
    fun updateLineColor(color: Color) {
        _uiState.update { it.copy(lineColor = color, isSaved = false) }
    }
    
    fun updateLineSpacing(spacing: Float) {
        _uiState.update { it.copy(lineSpacing = spacing.coerceIn(8f, 60f), isSaved = false) }
    }
    
    fun updateLineThickness(thickness: Float) {
        _uiState.update { it.copy(lineThickness = thickness.coerceIn(0.1f, 3f), isSaved = false) }
    }
    
    fun updatePatternOpacity(opacity: Float) {
        _uiState.update { it.copy(patternOpacity = opacity.coerceIn(0.1f, 1f), isSaved = false) }
    }
    
    // ==================== Secondary Grid ====================
    
    fun toggleSecondaryGrid() {
        _uiState.update { it.copy(hasSecondaryGrid = !it.hasSecondaryGrid, isSaved = false) }
    }
    
    fun updateSecondaryLineColor(color: Color) {
        _uiState.update { it.copy(secondaryLineColor = color, isSaved = false) }
    }
    
    fun updateSecondaryLineSpacing(spacing: Float) {
        _uiState.update { it.copy(secondaryLineSpacing = spacing.coerceIn(16f, 120f), isSaved = false) }
    }
    
    fun updateSecondaryLineThickness(thickness: Float) {
        _uiState.update { it.copy(secondaryLineThickness = thickness.coerceIn(0.2f, 3f), isSaved = false) }
    }
    
    // ==================== Dot Settings ====================
    
    fun updateDotSize(size: Float) {
        _uiState.update { it.copy(dotSize = size.coerceIn(0.3f, 3f), isSaved = false) }
    }
    
    // ==================== Margins ====================
    
    fun updateMargins(left: Float, right: Float, top: Float, bottom: Float) {
        _uiState.update {
            it.copy(
                marginLeft = left.coerceIn(0f, 150f),
                marginRight = right.coerceIn(0f, 150f),
                marginTop = top.coerceIn(0f, 150f),
                marginBottom = bottom.coerceIn(0f, 150f),
                isSaved = false
            )
        }
    }
    
    fun updateMarginLeft(value: Float) {
        _uiState.update { it.copy(marginLeft = value.coerceIn(0f, 150f), isSaved = false) }
    }
    
    fun updateMarginRight(value: Float) {
        _uiState.update { it.copy(marginRight = value.coerceIn(0f, 150f), isSaved = false) }
    }
    
    fun updateMarginTop(value: Float) {
        _uiState.update { it.copy(marginTop = value.coerceIn(0f, 150f), isSaved = false) }
    }
    
    fun updateMarginBottom(value: Float) {
        _uiState.update { it.copy(marginBottom = value.coerceIn(0f, 150f), isSaved = false) }
    }
    
    // ==================== Margin Line ====================
    
    fun toggleMarginLine() {
        _uiState.update { it.copy(hasMarginLine = !it.hasMarginLine, isSaved = false) }
    }
    
    fun updateMarginLineColor(color: Color) {
        _uiState.update { it.copy(marginLineColor = color, isSaved = false) }
    }
    
    fun updateMarginLinePosition(position: Float) {
        _uiState.update { it.copy(marginLinePosition = position.coerceIn(20f, 200f), isSaved = false) }
    }
    
    // ==================== Header ====================
    
    fun toggleHeader() {
        _uiState.update { it.copy(hasHeader = !it.hasHeader, isSaved = false) }
    }
    
    fun updateHeaderHeight(height: Float) {
        _uiState.update { it.copy(headerHeight = height.coerceIn(30f, 150f), isSaved = false) }
    }
    
    fun updateHeaderColor(color: Color) {
        _uiState.update { it.copy(headerColor = color, isSaved = false) }
    }
    
    // ==================== Footer ====================
    
    fun toggleFooter() {
        _uiState.update { it.copy(hasFooter = !it.hasFooter, isSaved = false) }
    }
    
    fun updateFooterHeight(height: Float) {
        _uiState.update { it.copy(footerHeight = height.coerceIn(30f, 200f), isSaved = false) }
    }
    
    fun updateFooterColor(color: Color) {
        _uiState.update { it.copy(footerColor = color, isSaved = false) }
    }
    
    // ==================== Side Column ====================
    
    fun toggleSideColumn() {
        _uiState.update { it.copy(hasSideColumn = !it.hasSideColumn, isSaved = false) }
    }
    
    fun updateSideColumnWidth(width: Float) {
        _uiState.update { it.copy(sideColumnWidth = width.coerceIn(80f, 250f), isSaved = false) }
    }
    
    fun updateSideColumnColor(color: Color) {
        _uiState.update { it.copy(sideColumnColor = color, isSaved = false) }
    }
    
    fun toggleSideColumnPosition() {
        _uiState.update { it.copy(sideColumnOnLeft = !it.sideColumnOnLeft, isSaved = false) }
    }
    
    // ==================== Legacy Toggles ====================
    
    fun toggleVerticalLines() {
        _uiState.update { 
            it.copy(hasVerticalLines = !it.hasVerticalLines, isSaved = false) 
        }
    }
    
    fun toggleHorizontalLines() {
        _uiState.update { 
            it.copy(hasHorizontalLines = !it.hasHorizontalLines, isSaved = false)
        }
    }
    
    fun toggleDots() {
        _uiState.update { 
            it.copy(hasDots = !it.hasDots, isSaved = false)
        }
    }
    
    // ==================== Helpers ====================
    
    fun getCurrentTemplate(): CustomTemplate {
        val state = _uiState.value
        return CustomTemplate(
            id = "preview",
            name = state.templateName,
            backgroundColor = state.backgroundColor.toArgb(),
            patternType = state.patternType,
            lineColor = state.lineColor.toArgb(),
            lineSpacing = state.lineSpacing,
            lineThickness = state.lineThickness,
            patternOpacity = state.patternOpacity,
            hasSecondaryGrid = state.hasSecondaryGrid,
            secondaryLineColor = state.secondaryLineColor.toArgb(),
            secondaryLineSpacing = state.secondaryLineSpacing,
            secondaryLineThickness = state.secondaryLineThickness,
            dotSize = state.dotSize,
            marginLeft = state.marginLeft,
            marginRight = state.marginRight,
            marginTop = state.marginTop,
            marginBottom = state.marginBottom,
            hasMarginLine = state.hasMarginLine,
            marginLineColor = state.marginLineColor.toArgb(),
            marginLinePosition = state.marginLinePosition,
            hasHeader = state.hasHeader,
            headerHeight = state.headerHeight,
            headerColor = state.headerColor.toArgb(),
            hasFooter = state.hasFooter,
            footerHeight = state.footerHeight,
            footerColor = state.footerColor.toArgb(),
            hasSideColumn = state.hasSideColumn,
            sideColumnWidth = state.sideColumnWidth,
            sideColumnColor = state.sideColumnColor.toArgb(),
            sideColumnOnLeft = state.sideColumnOnLeft,
            hasVerticalLines = state.hasVerticalLines,
            hasHorizontalLines = state.hasHorizontalLines,
            hasDots = state.hasDots
        )
    }
}