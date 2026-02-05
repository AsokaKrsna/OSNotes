package com.osnotes.app.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.osnotes.app.domain.model.CustomTemplate
import com.osnotes.app.domain.model.PageTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing custom page templates.
 */
@Singleton
class CustomTemplateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TEMPLATES_FILE = "custom_templates.json"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    
    private val _customTemplates = MutableStateFlow<List<CustomTemplate>>(emptyList())
    val customTemplates: Flow<List<CustomTemplate>> = _customTemplates.asStateFlow()
    
    init {
        loadTemplates()
    }
    
    /**
     * Gets all available templates (predefined + custom).
     */
    fun getAllTemplates(): List<PageTemplate> {
        val predefined = PageTemplate.all
        val custom = _customTemplates.value.map { it.toPageTemplate() }
        return predefined + custom
    }
    
    /**
     * Saves a new custom template.
     */
    suspend fun saveTemplate(template: CustomTemplate) = withContext(Dispatchers.IO) {
        val currentTemplates = _customTemplates.value.toMutableList()
        
        // Remove existing template with same ID
        currentTemplates.removeAll { it.id == template.id }
        
        // Add new template
        currentTemplates.add(template)
        
        // Update state
        _customTemplates.value = currentTemplates
        
        // Persist to file
        saveTemplatesToFile(currentTemplates)
    }
    
    /**
     * Deletes a custom template.
     */
    suspend fun deleteTemplate(templateId: String) = withContext(Dispatchers.IO) {
        val currentTemplates = _customTemplates.value.toMutableList()
        currentTemplates.removeAll { it.id == templateId }
        
        _customTemplates.value = currentTemplates
        saveTemplatesToFile(currentTemplates)
    }
    
    /**
     * Gets a template by ID (searches both predefined and custom).
     */
    fun getTemplateById(templateId: String): PageTemplate? {
        // Check predefined templates first
        PageTemplate.all.find { it.id == templateId }?.let { return it }
        
        // Check custom templates
        return _customTemplates.value.find { it.id == templateId }?.toPageTemplate()
    }
    
    /**
     * Gets a custom template by its ID (for direct template data access).
     */
    fun getCustomTemplateById(templateId: String): CustomTemplate? {
        return _customTemplates.value.find { it.id == templateId }
    }
    
    /**
     * Creates a default custom template configuration.
     */
    fun createDefaultTemplate(): CustomTemplate {
        return CustomTemplate(
            id = "custom_${System.currentTimeMillis()}",
            name = "Custom Template",
            backgroundColor = Color.White.toArgb(),
            patternType = com.osnotes.app.domain.model.PatternType.HORIZONTAL_LINES,
            lineColor = Color(0xFF99BBD9).toArgb(),
            lineSpacing = 24f,
            lineThickness = 0.5f,
            marginLeft = 40f,
            marginRight = 40f,
            marginTop = 60f,
            marginBottom = 40f
        )
    }
    
    /**
     * Loads templates from persistent storage.
     */
    private fun loadTemplates() {
        try {
            val file = File(context.filesDir, TEMPLATES_FILE)
            if (file.exists()) {
                val jsonString = file.readText()
                val templateData = json.decodeFromString<TemplateStorage>(jsonString)
                _customTemplates.value = templateData.templates
            }
        } catch (e: Exception) {
            // If loading fails, start with empty list
            _customTemplates.value = emptyList()
        }
    }
    
    /**
     * Saves templates to persistent storage.
     */
    private suspend fun saveTemplatesToFile(templates: List<CustomTemplate>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, TEMPLATES_FILE)
            val templateData = TemplateStorage(templates)
            val jsonString = json.encodeToString(templateData)
            file.writeText(jsonString)
        } catch (e: Exception) {
            // Handle save error - could log or show user message
        }
    }
    
    /**
     * Exports templates to a shareable format.
     */
    suspend fun exportTemplates(): String = withContext(Dispatchers.IO) {
        val templateData = TemplateStorage(_customTemplates.value)
        json.encodeToString(templateData)
    }
    
    /**
     * Imports templates from a JSON string.
     */
    suspend fun importTemplates(jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val templateData = json.decodeFromString<TemplateStorage>(jsonString)
            val currentTemplates = _customTemplates.value.toMutableList()
            
            var importedCount = 0
            for (template in templateData.templates) {
                // Generate new ID if template already exists
                val finalTemplate = if (currentTemplates.any { it.id == template.id }) {
                    template.copy(
                        id = "imported_${template.id}_${System.currentTimeMillis()}",
                        name = "${template.name} (Imported)"
                    )
                } else {
                    template
                }
                
                currentTemplates.add(finalTemplate)
                importedCount++
            }
            
            _customTemplates.value = currentTemplates
            saveTemplatesToFile(currentTemplates)
            
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Duplicates an existing template.
     */
    suspend fun duplicateTemplate(templateId: String): CustomTemplate? = withContext(Dispatchers.IO) {
        val original = _customTemplates.value.find { it.id == templateId } ?: return@withContext null
        
        val duplicate = original.copy(
            id = "copy_${original.id}_${System.currentTimeMillis()}",
            name = "${original.name} (Copy)"
        )
        
        saveTemplate(duplicate)
        duplicate
    }
}

/**
 * Storage wrapper for JSON serialization.
 */
@Serializable
private data class TemplateStorage(
    val templates: List<CustomTemplate>,
    val version: Int = 1
)