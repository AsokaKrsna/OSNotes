package com.osnotes.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osnotes.app.data.repository.CustomTemplateRepository
import com.osnotes.app.domain.model.CustomTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplateManagerUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TemplateManagerViewModel @Inject constructor(
    private val customTemplateRepository: CustomTemplateRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TemplateManagerUiState())
    val uiState: StateFlow<TemplateManagerUiState> = _uiState.asStateFlow()
    
    val templates: StateFlow<List<CustomTemplate>> = customTemplateRepository.customTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                customTemplateRepository.deleteTemplate(templateId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun duplicateTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                customTemplateRepository.duplicateTemplate(templateId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
