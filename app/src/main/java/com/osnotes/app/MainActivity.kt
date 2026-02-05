package com.osnotes.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.osnotes.app.data.storage.StorageManager
import com.osnotes.app.ui.navigation.AppNavigation
import com.osnotes.app.ui.theme.OSNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemeSettings(
    val theme: String = "dark",
    val dynamicColors: Boolean = false
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var storageManager: StorageManager
    
    private val _themeSettings = MutableStateFlow(ThemeSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()
    
    private val prefs by lazy { 
        getSharedPreferences("osnotes_settings", Context.MODE_PRIVATE) 
    }
    
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme" || key == "dynamic_colors") {
            loadThemeSettings()
        }
    }
    
    // Folder picker launcher
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            lifecycleScope.launch {
                storageManager.addTrackedFolderFromUri(it)
            }
        }
    }
    
    // Manage external storage permission launcher (Android 11+)
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if permission was granted, then open folder picker
        if (storageManager.hasAllFilesAccess()) {
            folderPickerLauncher.launch(null)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up folder selection callback
        storageManager.onRequestFolderSelection = {
            requestStorageAccessAndPickFolder()
        }
        
        // Load theme settings
        loadThemeSettings()
        
        // Listen for preference changes
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
        setContent {
            val settings by themeSettings.collectAsStateWithLifecycle()
            
            // Determine dark mode based on theme setting
            val isDarkTheme = when (settings.theme) {
                "light" -> false
                "dark", "amoled" -> true
                else -> true // Default to dark
            }
            val isAmoled = settings.theme == "amoled"
            
            OSNotesTheme(
                darkTheme = isDarkTheme,
                dynamicColor = settings.dynamicColors,
                amoled = isAmoled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
    
    private fun requestStorageAccessAndPickFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } else {
                folderPickerLauncher.launch(null)
            }
        } else {
            folderPickerLauncher.launch(null)
        }
    }
    
    private fun loadThemeSettings() {
        _themeSettings.value = ThemeSettings(
            theme = prefs.getString("theme", "dark") ?: "dark",
            dynamicColors = prefs.getBoolean("dynamic_colors", false)
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}
