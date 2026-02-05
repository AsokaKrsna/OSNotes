package com.osnotes.app.domain.model

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable

/**
 * Page template definitions.
 */
sealed class PageTemplate(
    val id: String,
    val name: String,
    val description: String
) {
    abstract fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean)
    
    // Predefined templates
    object Blank : PageTemplate("blank", "Blank", "Empty white page") {
        override fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean) {
            val paint = Paint().apply {
                color = if (isDarkMode) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }
    
    object Grid : PageTemplate("grid", "Grid", "Square grid pattern") {
        override fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean) {
            // Background
            val bgPaint = Paint().apply {
                color = if (isDarkMode) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            
            // Grid lines
            val linePaint = Paint().apply {
                color = if (isDarkMode) 0xFF3A3A3A.toInt() else 0xFFE0E0E0.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            
            val spacing = 20f // 20pt grid
            
            // Vertical lines
            var x = spacing
            while (x < width) {
                canvas.drawLine(x, 0f, x, height, linePaint)
                x += spacing
            }
            
            // Horizontal lines
            var y = spacing
            while (y < height) {
                canvas.drawLine(0f, y, width, y, linePaint)
                y += spacing
            }
        }
    }
    
    object DotGrid : PageTemplate("dotgrid", "Dot Grid", "Dot grid pattern") {
        override fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean) {
            // Background
            val bgPaint = Paint().apply {
                color = if (isDarkMode) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            
            // Dots
            val dotPaint = Paint().apply {
                color = if (isDarkMode) 0xFF4A4A4A.toInt() else 0xFFCCCCCC.toInt()
                style = Paint.Style.FILL
            }
            
            val spacing = 20f
            val dotRadius = 1f
            
            var x = spacing
            while (x < width) {
                var y = spacing
                while (y < height) {
                    canvas.drawCircle(x, y, dotRadius, dotPaint)
                    y += spacing
                }
                x += spacing
            }
        }
    }
    
    object Lined : PageTemplate("lined", "Lined", "Horizontal lines") {
        override fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean) {
            // Background
            val bgPaint = Paint().apply {
                color = if (isDarkMode) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            
            // Lines
            val linePaint = Paint().apply {
                color = if (isDarkMode) 0xFF3A3A3A.toInt() else 0xFFE0E0E0.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            
            val spacing = 24f // Line spacing
            val marginTop = 60f // Top margin
            
            var y = marginTop
            while (y < height - 40f) {
                canvas.drawLine(40f, y, width - 40f, y, linePaint)
                y += spacing
            }
            
            // Left margin line (red)
            val marginPaint = Paint().apply {
                color = 0x40FF0000
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawLine(60f, marginTop, 60f, height - 40f, marginPaint)
        }
    }
    
    object Cornell : PageTemplate("cornell", "Cornell Notes", "Cornell note-taking format") {
        override fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean) {
            // Background
            val bgPaint = Paint().apply {
                color = if (isDarkMode) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            
            // Section dividers
            val linePaint = Paint().apply {
                color = if (isDarkMode) 0xFF4A4A4A.toInt() else 0xFFCCCCCC.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            
            // Cue column (left, ~2.5 inches = ~180pt)
            val cueWidth = 150f
            canvas.drawLine(cueWidth, 60f, cueWidth, height - 150f, linePaint)
            
            // Summary section (bottom, ~2 inches = ~144pt)
            val summaryTop = height - 120f
            canvas.drawLine(30f, summaryTop, width - 30f, summaryTop, linePaint)
            
            // Labels
            val labelPaint = Paint().apply {
                color = if (isDarkMode) 0xFF6A6A6A.toInt() else 0xFF999999.toInt()
                textSize = 10f
            }
            canvas.drawText("CUES", 50f, 50f, labelPaint)
            canvas.drawText("NOTES", cueWidth + 20f, 50f, labelPaint)
            canvas.drawText("SUMMARY", 50f, summaryTop + 20f, labelPaint)
        }
    }
    
    object MeetingMinutes : PageTemplate("meeting", "Meeting Minutes", "Meeting notes format") {
        override fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean) {
            // Background
            val bgPaint = Paint().apply {
                color = if (isDarkMode) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            
            val linePaint = Paint().apply {
                color = if (isDarkMode) 0xFF4A4A4A.toInt() else 0xFFCCCCCC.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            
            val labelPaint = Paint().apply {
                color = if (isDarkMode) 0xFF6A6A6A.toInt() else 0xFF999999.toInt()
                textSize = 12f
            }
            
            // Header section
            var y = 40f
            canvas.drawText("Date:", 40f, y, labelPaint)
            canvas.drawLine(80f, y + 5f, width / 2 - 20f, y + 5f, linePaint)
            
            canvas.drawText("Attendees:", width / 2, y, labelPaint)
            canvas.drawLine(width / 2 + 70f, y + 5f, width - 40f, y + 5f, linePaint)
            
            y += 30f
            canvas.drawText("Subject:", 40f, y, labelPaint)
            canvas.drawLine(100f, y + 5f, width - 40f, y + 5f, linePaint)
            
            // Divider
            y += 30f
            canvas.drawLine(40f, y, width - 40f, y, linePaint)
            
            // Agenda section
            y += 25f
            canvas.drawText("AGENDA", 40f, y, labelPaint)
            
            // Lined area
            y += 20f
            val spacing = 24f
            while (y < height - 200f) {
                canvas.drawLine(40f, y, width - 40f, y, linePaint)
                y += spacing
            }
            
            // Action items section
            canvas.drawLine(40f, y, width - 40f, y, linePaint)
            y += 25f
            canvas.drawText("ACTION ITEMS", 40f, y, labelPaint)
            
            y += 20f
            while (y < height - 40f) {
                canvas.drawLine(40f, y, width - 40f, y, linePaint)
                y += spacing
            }
        }
    }
    
    /**
     * Custom template with user-defined settings.
     */
    class Custom(
        id: String,
        name: String,
        private val backgroundColor: Int,
        private val lineColor: Int,
        private val lineSpacing: Float,
        private val hasVerticalLines: Boolean,
        private val hasHorizontalLines: Boolean,
        private val hasDots: Boolean,
        private val marginLeft: Float,
        private val marginRight: Float,
        private val marginTop: Float,
        private val marginBottom: Float
    ) : PageTemplate(id, name, "Custom template") {
        override fun draw(canvas: Canvas, width: Float, height: Float, isDarkMode: Boolean) {
            // Background
            val bgPaint = Paint().apply {
                color = backgroundColor
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            
            // Lines/dots
            val linePaint = Paint().apply {
                color = lineColor
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            
            val dotPaint = Paint().apply {
                color = lineColor
                style = Paint.Style.FILL
            }
            
            val startX = marginLeft
            val endX = width - marginRight
            val startY = marginTop
            val endY = height - marginBottom
            
            if (hasVerticalLines || hasDots) {
                var x = startX + lineSpacing
                while (x < endX) {
                    if (hasVerticalLines) {
                        canvas.drawLine(x, startY, x, endY, linePaint)
                    }
                    x += lineSpacing
                }
            }
            
            if (hasHorizontalLines || hasDots) {
                var y = startY + lineSpacing
                while (y < endY) {
                    if (hasHorizontalLines) {
                        canvas.drawLine(startX, y, endX, y, linePaint)
                    } else if (hasDots) {
                        var x = startX + lineSpacing
                        while (x < endX) {
                            canvas.drawCircle(x, y, 1f, dotPaint)
                            x += lineSpacing
                        }
                    }
                    y += lineSpacing
                }
            }
        }
    }
    
    companion object {
        val all = listOf(Blank, Grid, DotGrid, Lined, Cornell, MeetingMinutes)
        
        fun fromId(id: String): PageTemplate {
            return all.find { it.id == id } ?: Blank
        }
    }
}

/**
 * Pattern type for custom templates.
 */
@Serializable
enum class PatternType {
    NONE,
    HORIZONTAL_LINES,
    VERTICAL_LINES,
    GRID,
    DOTS,
    ISOMETRIC_DOTS,
    DIAGONAL_LEFT,
    DIAGONAL_RIGHT,
    CROSSHATCH
}

/**
 * Custom template configuration created by user.
 * Use toPageTemplate() to convert to a PageTemplate.
 */
@Serializable
data class CustomTemplate(
    val id: String,
    val name: String,
    
    // Background
    val backgroundColor: Int,
    
    // Primary pattern
    val patternType: PatternType = PatternType.HORIZONTAL_LINES,
    val lineColor: Int,
    val lineSpacing: Float,
    val lineThickness: Float = 0.5f,
    val patternOpacity: Float = 1.0f,
    
    // Secondary pattern (for grids)
    val hasSecondaryGrid: Boolean = false,
    val secondaryLineColor: Int = lineColor,
    val secondaryLineSpacing: Float = lineSpacing * 2,
    val secondaryLineThickness: Float = 0.8f,
    
    // Dot-specific settings
    val dotSize: Float = 1.0f,
    
    // Margins
    val marginLeft: Float,
    val marginRight: Float,
    val marginTop: Float,
    val marginBottom: Float,
    
    // Margin line (vertical red line like legal pads)
    val hasMarginLine: Boolean = false,
    val marginLineColor: Int = 0xFFCC3333.toInt(),
    val marginLinePosition: Float = 72f,
    
    // Header section
    val hasHeader: Boolean = false,
    val headerHeight: Float = 60f,
    val headerColor: Int = 0xFFF0F0F5.toInt(),
    
    // Footer section
    val hasFooter: Boolean = false,
    val footerHeight: Float = 50f,
    val footerColor: Int = 0xFFF0F0F5.toInt(),
    
    // Side column (Cornell-style)
    val hasSideColumn: Boolean = false,
    val sideColumnWidth: Float = 150f,
    val sideColumnColor: Int = 0xFFFAF5EB.toInt(),
    val sideColumnOnLeft: Boolean = true,
    
    // Legacy compatibility
    val hasVerticalLines: Boolean = false,
    val hasHorizontalLines: Boolean = true,
    val hasDots: Boolean = false
) {
    fun toPageTemplate(): PageTemplate {
        return PageTemplate.Custom(
            id = id,
            name = name,
            backgroundColor = backgroundColor,
            lineColor = lineColor,
            lineSpacing = lineSpacing,
            hasVerticalLines = hasVerticalLines || patternType == PatternType.VERTICAL_LINES || patternType == PatternType.GRID,
            hasHorizontalLines = hasHorizontalLines || patternType == PatternType.HORIZONTAL_LINES || patternType == PatternType.GRID,
            hasDots = hasDots || patternType == PatternType.DOTS || patternType == PatternType.ISOMETRIC_DOTS,
            marginLeft = marginLeft,
            marginRight = marginRight,
            marginTop = marginTop,
            marginBottom = marginBottom
        )
    }
    
    companion object {
        /**
         * Preset templates for quick starting points.
         */
        fun collegeRuled() = CustomTemplate(
            id = "preset_college_ruled",
            name = "College Ruled",
            backgroundColor = 0xFFFFFCF7.toInt(),
            patternType = PatternType.HORIZONTAL_LINES,
            lineColor = 0xFF99BBD9.toInt(),
            lineSpacing = 24f,
            lineThickness = 0.5f,
            marginLeft = 72f,
            marginRight = 40f,
            marginTop = 85f,
            marginBottom = 50f,
            hasMarginLine = true,
            marginLineColor = 0xFFCC3333.toInt(),
            marginLinePosition = 64f
        )
        
        fun engineeringPaper() = CustomTemplate(
            id = "preset_engineering",
            name = "Engineering Paper",
            backgroundColor = 0xFFF0FAF0.toInt(),
            patternType = PatternType.GRID,
            lineColor = 0xFFB3D9B3.toInt(),
            lineSpacing = 14.17f,
            lineThickness = 0.2f,
            hasSecondaryGrid = true,
            secondaryLineColor = 0xFF73B373.toInt(),
            secondaryLineSpacing = 28.35f,
            secondaryLineThickness = 0.5f,
            marginLeft = 28f,
            marginRight = 28f,
            marginTop = 28f,
            marginBottom = 28f
        )
        
        fun graphPaper() = CustomTemplate(
            id = "preset_graph",
            name = "Graph Paper",
            backgroundColor = 0xFFFFFFFF.toInt(),
            patternType = PatternType.GRID,
            lineColor = 0xFFD2D2D2.toInt(),
            lineSpacing = 14.17f,
            lineThickness = 0.25f,
            hasSecondaryGrid = true,
            secondaryLineColor = 0xFFA6A6A6.toInt(),
            secondaryLineSpacing = 28.35f,
            secondaryLineThickness = 0.5f,
            marginLeft = 30f,
            marginRight = 30f,
            marginTop = 30f,
            marginBottom = 30f
        )
        
        fun cornellNotes() = CustomTemplate(
            id = "preset_cornell",
            name = "Cornell Notes",
            backgroundColor = 0xFFFCFAF5.toInt(),
            patternType = PatternType.HORIZONTAL_LINES,
            lineColor = 0xFFBFC7D1.toInt(),
            lineSpacing = 24f,
            lineThickness = 0.4f,
            marginLeft = 25f,
            marginRight = 25f,
            marginTop = 70f,
            marginBottom = 25f,
            hasHeader = true,
            headerHeight = 45f,
            headerColor = 0xFFF2F2F7.toInt(),
            hasFooter = true,
            footerHeight = 130f,
            footerColor = 0xFFEBF2FA.toInt(),
            hasSideColumn = true,
            sideColumnWidth = 150f,
            sideColumnColor = 0xFFFAF5EB.toInt(),
            sideColumnOnLeft = true
        )
        
        fun bulletJournal() = CustomTemplate(
            id = "preset_bullet_journal",
            name = "Bullet Journal",
            backgroundColor = 0xFFFFFFF8.toInt(),
            patternType = PatternType.DOTS,
            lineColor = 0xFF8C8C8C.toInt(),
            lineSpacing = 14.17f,
            dotSize = 0.6f,
            marginLeft = 35f,
            marginRight = 35f,
            marginTop = 35f,
            marginBottom = 35f
        )
        
        fun isometricGrid() = CustomTemplate(
            id = "preset_isometric",
            name = "Isometric Grid",
            backgroundColor = 0xFFFFFFFF.toInt(),
            patternType = PatternType.ISOMETRIC_DOTS,
            lineColor = 0xFF80808C.toInt(),
            lineSpacing = 17f,
            dotSize = 0.55f,
            marginLeft = 30f,
            marginRight = 30f,
            marginTop = 30f,
            marginBottom = 30f
        )
        
        fun musicStaff() = CustomTemplate(
            id = "preset_music",
            name = "Music Staff",
            backgroundColor = 0xFFFEFDF9.toInt(),
            patternType = PatternType.HORIZONTAL_LINES,
            lineColor = 0xFF262626.toInt(),
            lineSpacing = 7f,
            lineThickness = 0.4f,
            marginLeft = 85f,
            marginRight = 55f,
            marginTop = 55f,
            marginBottom = 55f,
            hasMarginLine = true,
            marginLineColor = 0xFF262626.toInt(),
            marginLinePosition = 85f
        )
        
        fun diagonalRuled() = CustomTemplate(
            id = "preset_diagonal",
            name = "Diagonal Ruled",
            backgroundColor = 0xFFFFFFFF.toInt(),
            patternType = PatternType.DIAGONAL_RIGHT,
            lineColor = 0xFFCCCCCC.toInt(),
            lineSpacing = 20f,
            lineThickness = 0.3f,
            marginLeft = 30f,
            marginRight = 30f,
            marginTop = 30f,
            marginBottom = 30f
        )
        
        val presets = listOf(
            collegeRuled(),
            graphPaper(),
            engineeringPaper(),
            cornellNotes(),
            bulletJournal(),
            isometricGrid(),
            musicStaff(),
            diagonalRuled()
        )
    }
}

