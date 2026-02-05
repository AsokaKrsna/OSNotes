package com.osnotes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Provides theme-aware colors and gradients for the app.
 */
object AppColors {
    
    /**
     * Background gradient that adapts to the current theme.
     */
    @Composable
    fun backgroundGradient(): Brush {
        val background = MaterialTheme.colorScheme.background
        val surface = MaterialTheme.colorScheme.surface
        
        return Brush.verticalGradient(
            colors = listOf(background, surface)
        )
    }
    
    /**
     * Primary accent color.
     */
    @Composable
    fun primaryAccent(): Color = MaterialTheme.colorScheme.primary
    
    /**
     * Text color for content.
     */
    @Composable
    fun textPrimary(): Color = MaterialTheme.colorScheme.onBackground
    
    /**
     * Secondary/muted text color.
     */
    @Composable
    fun textSecondary(): Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    
    /**
     * Gets the glassmorphic surface colors based on theme.
     */
    @Composable
    fun glassSurfaceColors(): Pair<Color, Color> {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        return if (isDark) {
            Color.White.copy(alpha = 0.1f) to Color.White.copy(alpha = 0.05f)
        } else {
            Color.Black.copy(alpha = 0.05f) to Color.Black.copy(alpha = 0.02f)
        }
    }
    
    /**
     * Gets the glassmorphic border colors based on theme.
     */
    @Composable
    fun glassBorderColors(): Pair<Color, Color> {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        return if (isDark) {
            Color.White.copy(alpha = 0.2f) to Color.White.copy(alpha = 0.05f)
        } else {
            Color.Black.copy(alpha = 0.1f) to Color.Black.copy(alpha = 0.02f)
        }
    }
}

// Extension to get luminance
private fun Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}
