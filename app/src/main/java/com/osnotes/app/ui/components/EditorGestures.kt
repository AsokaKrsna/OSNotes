package com.osnotes.app.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gesture configuration for the note editor.
 */
data class EditorGestureConfig(
    val stylusDoubleTapEnabled: Boolean = true,
    val twoFingerUndoEnabled: Boolean = true,
    val palmRejectionEnabled: Boolean = true,
    val doubleTapTimeoutMs: Long = 300L,
    val twoFingerTapTimeoutMs: Long = 200L
)

/**
 * Gesture callbacks for editor actions.
 */
interface EditorGestureCallbacks {
    fun onStylusDoubleTap()
    fun onTwoFingerTap()
    fun onStylusDown()
    fun onStylusUp()
}

/**
 * Extension modifier to add editor-specific gestures.
 * - Stylus double-tap: Switch between last two tools
 * - Two-finger tap: Undo
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.editorGestures(
    config: EditorGestureConfig = EditorGestureConfig(),
    callbacks: EditorGestureCallbacks
): Modifier = this.pointerInput(config) {
    var lastStylusTapTime = 0L
    var lastStylusTapPosition: androidx.compose.ui.geometry.Offset? = null
    
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        
        when (down.type) {
            PointerType.Stylus, PointerType.Eraser -> {
                callbacks.onStylusDown()
                
                // Check for double-tap
                if (config.stylusDoubleTapEnabled) {
                    val currentTime = System.currentTimeMillis()
                    val lastPos = lastStylusTapPosition
                    val timeDiff = currentTime - lastStylusTapTime
                    
                    if (timeDiff < config.doubleTapTimeoutMs && lastPos != null) {
                        // Check if taps are close enough (within 50dp)
                        val distance = (down.position - lastPos).getDistance()
                        if (distance < 50f) {
                            callbacks.onStylusDoubleTap()
                            // Reset to prevent triple-tap triggering
                            lastStylusTapTime = 0L
                            lastStylusTapPosition = null
                            return@awaitEachGesture
                        }
                    }
                    
                    lastStylusTapTime = currentTime
                    lastStylusTapPosition = down.position
                }
                
                // Wait for up
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        callbacks.onStylusUp()
                        break
                    }
                }
            }
            
            PointerType.Touch -> {
                // Check for two-finger tap
                if (config.twoFingerUndoEnabled) {
                    // Wait briefly for second finger
                    var secondFingerDetected = false
                    val startTime = System.currentTimeMillis()
                    
                    while (System.currentTimeMillis() - startTime < config.twoFingerTapTimeoutMs) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        
                        // Count active touch pointers
                        val touchPointers = event.changes.filter { 
                            it.type == PointerType.Touch && it.pressed 
                        }
                        
                        if (touchPointers.size >= 2) {
                            secondFingerDetected = true
                            break
                        }
                        
                        // If original finger lifted, break
                        if (event.changes.none { it.id == down.id && it.pressed }) {
                            break
                        }
                    }
                    
                    if (secondFingerDetected) {
                        // Wait for both fingers to lift (quick tap)
                        val twoFingerStart = System.currentTimeMillis()
                        var bothLifted = false
                        
                        while (System.currentTimeMillis() - twoFingerStart < 300L) {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            
                            if (activePointers.isEmpty()) {
                                bothLifted = true
                                break
                            }
                        }
                        
                        if (bothLifted) {
                            callbacks.onTwoFingerTap()
                        }
                    }
                }
            }
            
            else -> {
                // Ignore other pointer types (mouse, unknown)
            }
        }
    }
}

/**
 * State holder for gesture detection.
 */
@Composable
fun rememberEditorGestureState(
    onStylusDoubleTap: () -> Unit = {},
    onTwoFingerTap: () -> Unit = {},
    onStylusActiveChange: (Boolean) -> Unit = {}
): EditorGestureCallbacks {
    return remember(onStylusDoubleTap, onTwoFingerTap, onStylusActiveChange) {
        object : EditorGestureCallbacks {
            override fun onStylusDoubleTap() = onStylusDoubleTap()
            override fun onTwoFingerTap() = onTwoFingerTap()
            override fun onStylusDown() = onStylusActiveChange(true)
            override fun onStylusUp() = onStylusActiveChange(false)
        }
    }
}

/**
 * Palm rejection helper.
 * Identifies palm touches based on contact size and pressure patterns.
 */
object PalmRejection {
    
    /**
     * Determines if a pointer event is likely a palm touch.
     * Returns true if the touch should be rejected.
     */
    fun isPalmTouch(
        pointerType: PointerType,
        pressure: Float,
        size: Float? = null
    ): Boolean {
        // Only reject touch inputs, not stylus
        if (pointerType != PointerType.Touch) {
            return false
        }
        
        // High pressure often indicates palm
        if (pressure > 0.8f) {
            return true
        }
        
        // Large touch area indicates palm
        size?.let {
            if (it > 0.3f) { // Normalized size
                return true
            }
        }
        
        return false
    }
    
    /**
     * Extension function to check if a PointerInputChange is a palm touch.
     */
    fun PointerInputChange.isPalm(): Boolean {
        return isPalmTouch(
            pointerType = this.type,
            pressure = this.pressure
        )
    }
}
