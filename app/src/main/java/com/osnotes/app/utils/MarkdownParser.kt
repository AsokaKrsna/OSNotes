package com.osnotes.app.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Simple markdown parser for text annotations.
 * Supports basic formatting: **bold**, *italic*, ~~strikethrough~~, # headings, - lists
 */
object MarkdownParser {
    
    fun parseMarkdown(
        text: String,
        baseColor: Color = Color.Black,
        baseFontSize: Float = 16f
    ): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            val lines = text.split('\n')
            
            lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) append('\n')
                
                when {
                    // Heading
                    line.startsWith("# ") -> {
                        val headingText = line.substring(2)
                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = (baseFontSize * 1.5f).sp,
                                color = baseColor
                            )
                        )
                        append(headingText)
                        pop()
                    }
                    
                    // List item
                    line.startsWith("- ") -> {
                        val listText = line.substring(2)
                        append("• ")
                        parseInlineMarkdown(listText, baseColor, baseFontSize)
                    }
                    
                    // Regular text with inline formatting
                    else -> {
                        parseInlineMarkdown(line, baseColor, baseFontSize)
                    }
                }
            }
        }
    }
    
    private fun AnnotatedString.Builder.parseInlineMarkdown(
        text: String,
        baseColor: Color,
        baseFontSize: Float
    ) {
        var currentIndex = 0
        
        while (currentIndex < text.length) {
            when {
                // Bold text **text**
                text.startsWith("**", currentIndex) -> {
                    val endIndex = text.indexOf("**", currentIndex + 2)
                    if (endIndex != -1) {
                        val boldText = text.substring(currentIndex + 2, endIndex)
                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = baseColor
                            )
                        )
                        append(boldText)
                        pop()
                        currentIndex = endIndex + 2
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                
                // Italic text *text*
                text.startsWith("*", currentIndex) && !text.startsWith("**", currentIndex) -> {
                    val endIndex = text.indexOf("*", currentIndex + 1)
                    if (endIndex != -1 && !text.startsWith("**", endIndex - 1)) {
                        val italicText = text.substring(currentIndex + 1, endIndex)
                        pushStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = baseColor
                            )
                        )
                        append(italicText)
                        pop()
                        currentIndex = endIndex + 1
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                
                // Strikethrough text ~~text~~
                text.startsWith("~~", currentIndex) -> {
                    val endIndex = text.indexOf("~~", currentIndex + 2)
                    if (endIndex != -1) {
                        val strikeText = text.substring(currentIndex + 2, endIndex)
                        pushStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.LineThrough,
                                color = baseColor
                            )
                        )
                        append(strikeText)
                        pop()
                        currentIndex = endIndex + 2
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                
                // Regular character
                else -> {
                    pushStyle(SpanStyle(color = baseColor))
                    append(text[currentIndex])
                    pop()
                    currentIndex++
                }
            }
        }
    }
    
    /**
     * Strips markdown formatting from text to get plain text.
     */
    fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // Bold
            .replace(Regex("\\*(.*?)\\*"), "$1") // Italic
            .replace(Regex("~~(.*?)~~"), "$1") // Strikethrough
            .replace(Regex("^# (.*)"), "$1") // Heading
            .replace(Regex("^- (.*)"), "• $1") // List
    }
    
    /**
     * Checks if text contains markdown formatting.
     */
    fun hasMarkdown(text: String): Boolean {
        return text.contains(Regex("\\*\\*.*?\\*\\*|\\*.*?\\*|~~.*?~~|^# |^- ", RegexOption.MULTILINE))
    }
}