package com.osnotes.app.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osnotes.app.data.storage.StorageManager
import com.osnotes.app.data.models.NoteInfo
import com.osnotes.app.data.models.FolderInfo
import com.osnotes.app.data.repository.CustomTemplateRepository
import com.osnotes.app.domain.model.CustomTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderUiState(
    val folderName: String = "",
    val folderPath: String = "",
    val notes: List<NoteInfo> = emptyList(),
    val subfolders: List<FolderInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager,
    private val customTemplateRepository: CustomTemplateRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FolderUiState())
    val uiState: StateFlow<FolderUiState> = _uiState.asStateFlow()
    
    // Custom templates exposed for UI
    val customTemplates: StateFlow<List<CustomTemplate>> = customTemplateRepository.customTemplates
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private var currentFolderPath: String = ""
    
    fun loadFolder(folderPath: String) {
        currentFolderPath = folderPath
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val folder = java.io.File(folderPath)
                val folderName = folder.name
                
                val notes = storageManager.listNotes(folderPath)
                val subfolders = storageManager.listFolders(folderPath)
                
                _uiState.update {
                    it.copy(
                        folderName = folderName,
                        folderPath = folderPath,
                        notes = notes,
                        subfolders = subfolders,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    fun createNote(name: String, template: String = "BLANK") {
        viewModelScope.launch {
            try {
                storageManager.createNote(name, currentFolderPath, template)
                loadFolder(currentFolderPath) // Refresh
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                storageManager.createFolder(name, currentFolderPath)
                loadFolder(currentFolderPath) // Refresh
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun toggleFavorite(notePath: String) {
        storageManager.toggleFavorite(notePath)
        loadFolder(currentFolderPath) // Refresh
    }
    
    fun deleteNote(notePath: String) {
        viewModelScope.launch {
            try {
                val success = storageManager.deleteNote(notePath)
                if (success) {
                    loadFolder(currentFolderPath) // Refresh
                } else {
                    _uiState.update { it.copy(error = "Failed to delete note") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteFolder(folderPath: String) {
        viewModelScope.launch {
            try {
                val success = storageManager.deleteFolder(folderPath)
                if (success) {
                    loadFolder(currentFolderPath) // Refresh
                } else {
                    _uiState.update { it.copy(error = "Failed to delete folder (must be empty)") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun renameNote(oldPath: String, newName: String) {
        viewModelScope.launch {
            try {
                // Check if note has unsaved annotations
                val annotationCount = storageManager.getAnnotationCount(oldPath)
                if (annotationCount > 0) {
                    _uiState.update { 
                        it.copy(error = "Cannot rename note with unsaved annotations. Please make permanent first.") 
                    }
                    return@launch
                }
                
                val success = storageManager.renameNote(oldPath, newName)
                if (success) {
                    loadFolder(currentFolderPath) // Refresh
                } else {
                    _uiState.update { it.copy(error = "Failed to rename note (name may already exist)") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun renameFolder(oldPath: String, newName: String) {
        viewModelScope.launch {
            try {
                val success = storageManager.renameFolder(oldPath, newName)
                if (success) {
                    loadFolder(currentFolderPath) // Refresh
                } else {
                    _uiState.update { it.copy(error = "Failed to rename folder (name may already exist)") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
