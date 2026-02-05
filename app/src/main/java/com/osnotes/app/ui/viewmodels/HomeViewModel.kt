package com.osnotes.app.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osnotes.app.data.storage.StorageManager
import com.osnotes.app.data.models.NoteInfo
import com.osnotes.app.data.models.FolderInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val favorites: List<NoteInfo> = emptyList(),
    val recentNotes: List<NoteInfo> = emptyList(),
    val trackedFolders: List<FolderInfo> = emptyList(),
    val hasAllFilesAccess: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
        
        // Observe folder changes and auto-refresh
        viewModelScope.launch {
            storageManager.foldersChanged.collect {
                loadData()
            }
        }
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val hasAccess = storageManager.hasAllFilesAccess()
            
            if (hasAccess) {
                val trackedFolders = storageManager.getTrackedFoldersInfo()
                val recentNotes = storageManager.getRecentNotes()
                val favorites = storageManager.getFavorites()
                
                _uiState.update {
                    it.copy(
                        trackedFolders = trackedFolders,
                        recentNotes = recentNotes,
                        favorites = favorites,
                        hasAllFilesAccess = true,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        hasAllFilesAccess = false,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * Request to add a new folder to tracking
     */
    fun addFolder() {
        storageManager.requestFolderSelection()
    }
    
    /**
     * Remove a folder from tracking (long press action)
     */
    fun removeFolder(folderPath: String) {
        viewModelScope.launch {
            storageManager.removeTrackedFolder(folderPath)
        }
    }
    
    /**
     * Create a new note in a specific folder
     */
    fun createNote(name: String, folderPath: String) {
        viewModelScope.launch {
            try {
                storageManager.createNote(name, folderPath)
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun refresh() {
        loadData()
    }
    
    /**
     * Toggle favorite status for a note
     */
    fun toggleFavorite(notePath: String) {
        storageManager.toggleFavorite(notePath)
        loadData() // Refresh to update UI
    }
}
