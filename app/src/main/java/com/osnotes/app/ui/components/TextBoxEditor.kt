package com.osnotes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.ExperimentalComposeUiApi
import com.osnotes.app.domain.model.TextBoxState
import com.osnotes.app.domain.model.TextBoxMode

// Color palette for text
private val TEXT_COLORS = listOf(
    Color.Black,
    Color(0xFF424242), // Dark Gray
    Color(0xFF2563EB), // Blue
    Color(0xFFDC2626), // Red
    Color(0xFF059669), // Green
    Color(0xFFF59E0B), // Amber
    Color(0xFF7C3AED), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFF06B6D4), // Cyan
    Color(0xFF84CC16), // Lime
    Color(0xFFF97316), // Orange
    Color.White
)

// Font sizes
private val FONT_SIZES = listOf(8f, 10f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 36f, 48f)

/**
 * Advanced text box editor with markdown support, formatting options, and color picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TextBoxEditor(
    textBoxState: TextBoxState,
    currentColor: Color,
    currentFontSize: Float,
    onTextChange: (String) -> Unit,
    onColorChange: (Color) -> Unit = {},
    onFontSizeChange: (Float) -> Unit = {},
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (textBoxState.mode != TextBoxMode.EDITING) return
    
    var text by remember(textBoxState) { mutableStateOf(textBoxState.text) }
    var selectedColor by remember(currentColor) { mutableStateOf(currentColor) }
    var selectedFontSize by remember(currentFontSize) { mutableStateOf(currentFontSize) }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showFontSizePicker by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    
    // More aggressive focus handling
    var focusAttempts by remember { mutableStateOf(0) }
    
    // Use multiple strategies to show keyboard
    LaunchedEffect(Unit) {
        android.util.Log.d("TextBoxEditor", "Starting keyboard show attempts")
        
        // Strategy 1: Immediate focus request
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
            focusAttempts++
            android.util.Log.d("TextBoxEditor", "Attempt 1: Immediate focus + keyboard show")
        } catch (e: Exception) {
            android.util.Log.w("TextBoxEditor", "Attempt 1 failed: ${e.message}")
        }
        
        // Strategy 2: Delayed focus request
        kotlinx.coroutines.delay(150)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
            focusAttempts++
            android.util.Log.d("TextBoxEditor", "Attempt 2: Delayed focus + keyboard show")
        } catch (e: Exception) {
            android.util.Log.w("TextBoxEditor", "Attempt 2 failed: ${e.message}")
        }
        
        // Strategy 3: Force show keyboard using InputMethodManager
        kotlinx.coroutines.delay(200)
        try {
            val inputMethodManager = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            inputMethodManager.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0)
            focusRequester.requestFocus()
            focusAttempts++
            android.util.Log.d("TextBoxEditor", "Attempt 3: Force show keyboard + focus")
        } catch (e: Exception) {
            android.util.Log.w("TextBoxEditor", "Attempt 3 failed: ${e.message}")
        }
    }
    
    // Also try when mode changes
    LaunchedEffect(textBoxState.mode) {
        if (textBoxState.mode == TextBoxMode.EDITING) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
                android.util.Log.d("TextBoxEditor", "Mode change: focus + keyboard show")
            } catch (e: Exception) {
                android.util.Log.w("TextBoxEditor", "Mode change failed: ${e.message}")
            }
        }
    }
    
    Dialog(
        onDismissRequest = {
            onCancel()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false, // This helps with keyboard handling
            securePolicy = SecureFlagPolicy.Inherit
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Text Editor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = {
                            onCancel()
                        }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }
                
                // Formatting toolbar
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Font size and color row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Font size selector
                            OutlinedButton(
                                onClick = { showFontSizePicker = !showFontSizePicker },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.FormatSize,
                                    contentDescription = "Font Size",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${selectedFontSize.toInt()}sp")
                            }
                            
                            // Color picker button
                            OutlinedButton(
                                onClick = { showColorPicker = !showColorPicker },
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(selectedColor)
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Color")
                            }
                        }
                        
                        // Font size picker
                        if (showFontSizePicker) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(FONT_SIZES) { size ->
                                    FilterChip(
                                        onClick = {
                                            selectedFontSize = size
                                            onFontSizeChange(size)
                                            showFontSizePicker = false
                                        },
                                        label = { Text("${size.toInt()}") },
                                        selected = selectedFontSize == size
                                    )
                                }
                            }
                        }
                        
                        // Color picker
                        if (showColorPicker) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(TEXT_COLORS) { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (selectedColor == color) 3.dp else 1.dp,
                                                color = if (selectedColor == color) Color(0xFF6366F1) else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedColor = color
                                                onColorChange(color)
                                                showColorPicker = false
                                            }
                                    )
                                }
                            }
                        }
                        
                        // Text formatting buttons (removed for simplicity)
                        // Row(
                        //     horizontalArrangement = Arrangement.spacedBy(8.dp)
                        // ) {
                        //     FilterChip(
                        //         onClick = { isBold = !isBold },
                        //         label = { 
                        //             Text(
                        //                 "B", 
                        //                 fontWeight = FontWeight.Bold,
                        //                 style = MaterialTheme.typography.titleMedium
                        //             ) 
                        //         },
                        //         selected = isBold
                        //     )
                        //     
                        //     FilterChip(
                        //         onClick = { isItalic = !isItalic },
                        //         label = { 
                        //             Text(
                        //                 "I", 
                        //                 fontStyle = FontStyle.Italic,
                        //                 style = MaterialTheme.typography.titleMedium
                        //             ) 
                        //         },
                        //         selected = isItalic
                        //     )
                        // }
                    }
                }
                
                // Text input area with native EditText for better keyboard control
                AndroidView(
                    factory = { context ->
                        android.widget.EditText(context).apply {
                            setText(text)
                            textSize = selectedFontSize
                            setTextColor(selectedColor.toArgb())
                            hint = "Tap here to start typing..."
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                                       android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                       android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            
                            // Set background and padding
                            setPadding(16, 16, 16, 16)
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setStroke(2, Color.Gray.toArgb())
                                cornerRadius = 8f
                            }
                            
                            // Add text change listener
                            addTextChangedListener(object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    val newText = s?.toString() ?: ""
                                    if (newText != text) {
                                        onTextChange(newText)
                                    }
                                }
                            })
                            
                            // Force show keyboard
                            setOnFocusChangeListener { _, hasFocus ->
                                if (hasFocus) {
                                    post {
                                        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                        android.util.Log.d("TextBoxEditor", "Native EditText focused - showing keyboard")
                                    }
                                }
                            }
                            
                            // Request focus immediately
                            post {
                                requestFocus()
                                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
                                android.util.Log.d("TextBoxEditor", "Native EditText created - requesting focus and keyboard")
                            }
                        }
                    },
                    update = { editText ->
                        if (editText.text.toString() != text) {
                            editText.setText(text)
                        }
                        editText.textSize = selectedFontSize
                        editText.setTextColor(selectedColor.toArgb())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                
                // Markdown help (removed)
                // Card(
                //     colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.1f)),
                //     shape = RoundedCornerShape(8.dp)
                // ) {
                //     Column(
                //         modifier = Modifier.padding(12.dp),
                //         verticalArrangement = Arrangement.spacedBy(4.dp)
                //     ) {
                //         Text(
                //             "Markdown Support:",
                //             fontWeight = FontWeight.Bold,
                //             fontSize = 12.sp,
                //             color = Color.Blue
                //         )
                //         Text("**bold text**", fontSize = 11.sp, color = Color.Gray)
                //         Text("*italic text*", fontSize = 11.sp, color = Color.Gray)
                //         Text("# Heading", fontSize = 11.sp, color = Color.Gray)
                //         Text("- List item", fontSize = 11.sp, color = Color.Gray)
                //         Text("~~strikethrough~~", fontSize = 11.sp, color = Color.Gray)
                //     }
                // }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onCancel()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            onDone()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done")
                    }
                }
            }
        }
    }
}

/**
 * Floating action buttons that appear when text box is active.
 */
@Composable
fun TextBoxActionBar(
    textBoxState: TextBoxState,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!textBoxState.isActive) return
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (textBoxState.mode) {
                TextBoxMode.DRAWING -> {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Draw text box area",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextBoxMode.EDITING -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Text(
                            "Edit text",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        IconButton(
                            onClick = onDone,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.Green,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                TextBoxMode.POSITIONING -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.OpenWith,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Drag to move • Drag corners to resize",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        IconButton(
                            onClick = onDone,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.Green,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                else -> {
                    // No action bar for NONE mode
                }
            }
        }
    }
}