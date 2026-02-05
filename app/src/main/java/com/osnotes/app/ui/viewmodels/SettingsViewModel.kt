package com.osnotes.app.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.osnotes.app.data.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SettingsUiState(
    val trackedFoldersCount: Int = 0,
    val toolbarPosition: String = "right", // "right", "left", "top"
    val palmRejection: Boolean = true,
    val autoSaveInterval: Int = 10, // seconds
    val theme: String = "dark", // "dark", "light", "amoled"
    val dynamicColors: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager
) : ViewModel() {
    
    companion object {
        private const val PREFS_NAME = "osnotes_settings"
        private const val KEY_PALM_REJECTION = "palm_rejection"
        private const val KEY_AUTO_SAVE_INTERVAL = "auto_save_interval"
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC_COLORS = "dynamic_colors"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            trackedFoldersCount = storageManager.getTrackedFolders().size,
            toolbarPosition = storageManager.getToolbarPosition(),
            palmRejection = prefs.getBoolean(KEY_PALM_REJECTION, true),
            autoSaveInterval = prefs.getInt(KEY_AUTO_SAVE_INTERVAL, 10),
            theme = prefs.getString(KEY_THEME, "dark") ?: "dark",
            dynamicColors = prefs.getBoolean(KEY_DYNAMIC_COLORS, false)
        )
    }
    
    fun cycleToolbarPosition() {
        val positions = listOf("right", "left", "top")
        val currentIndex = positions.indexOf(_uiState.value.toolbarPosition)
        val nextIndex = (currentIndex + 1) % positions.size
        val newPosition = positions[nextIndex]
        
        storageManager.setToolbarPosition(newPosition)
        _uiState.update { it.copy(toolbarPosition = newPosition) }
    }
    
    fun setPalmRejection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PALM_REJECTION, enabled).apply()
        _uiState.update { it.copy(palmRejection = enabled) }
    }
    
    fun setAutoSaveInterval(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_SAVE_INTERVAL, seconds).apply()
        _uiState.update { it.copy(autoSaveInterval = seconds) }
    }
    
    fun cycleTheme() {
        val themes = listOf("dark", "light", "amoled")
        val currentIndex = themes.indexOf(_uiState.value.theme)
        val nextIndex = (currentIndex + 1) % themes.size
        val newTheme = themes[nextIndex]
        
        prefs.edit().putString(KEY_THEME, newTheme).apply()
        _uiState.update { it.copy(theme = newTheme) }
    }
    
    fun setDynamicColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply()
        _uiState.update { it.copy(dynamicColors = enabled) }
    }
}
