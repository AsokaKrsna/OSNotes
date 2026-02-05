package com.osnotes.app.ui.input

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manages palm rejection for stylus input.
 * Detects and filters out palm touches when using a stylus.
 */
class PalmRejectionManager {
    
    companion object {
        private const val PALM_REJECTION_RADIUS = 100f // pixels
        private const val PALM_PRESSURE_THRESHOLD = 0.3f
        private const val PALM_SIZE_THRESHOLD = 0.5f
        private const val STYLUS_TIMEOUT_MS = 500L // Time to keep rejecting after stylus lift
    }
    
    private var activeStylusPointer: Int? = null
    private var stylusPosition: Offset = Offset.Zero
    private var lastStylusTime: Long = 0L
    private val rejectedPointers = mutableSetOf<Int>()
    
    /**
     * Processes a motion event and determines if it should be rejected due to palm detection.
     * 
     * @param event The motion event to process
     * @return true if the event should be processed, false if it should be rejected
     */
    fun shouldProcessEvent(event: MotionEvent): Boolean {
        val currentTime = System.currentTimeMillis()
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = if (event.actionMasked == MotionEvent.ACTION_DOWN) 0 else event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val toolType = event.getToolType(pointerIndex)
                
                when (toolType) {
                    MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> {
                        // Stylus input - always accept and update tracking
                        activeStylusPointer = pointerId
                        stylusPosition = Offset(event.getX(pointerIndex), event.getY(pointerIndex))
                        lastStylusTime = currentTime
                        
                        // Remove from rejected list if it was there
                        rejectedPointers.remove(pointerId)
                        
                        return true
                    }
                    
                    MotionEvent.TOOL_TYPE_FINGER -> {
                        // Finger input - check for palm rejection
                        val fingerPosition = Offset(event.getX(pointerIndex), event.getY(pointerIndex))
                        
                        if (shouldRejectFingerInput(fingerPosition, event, pointerIndex, currentTime)) {
                            rejectedPointers.add(pointerId)
                            return false
                        }
                        
                        return true
                    }
                    
                    else -> {
                        // Unknown tool type - accept by default
                        return true
                    }
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                // Check each pointer in the move event
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val toolType = event.getToolType(i)
                    
                    when (toolType) {
                        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> {
                            // Update stylus position
                            if (activeStylusPointer == pointerId) {
                                stylusPosition = Offset(event.getX(i), event.getY(i))
                                lastStylusTime = currentTime
                            }
                        }
                        
                        MotionEvent.TOOL_TYPE_FINGER -> {
                            // Check if this finger should be rejected
                            if (rejectedPointers.contains(pointerId)) {
                                continue // Skip rejected pointers
                            }
                            
                            val fingerPosition = Offset(event.getX(i), event.getY(i))
                            if (shouldRejectFingerInput(fingerPosition, event, i, currentTime)) {
                                rejectedPointers.add(pointerId)
                            }
                        }
                    }
                }
                
                // Return true if at least one pointer is not rejected
                return event.pointerCount > rejectedPointers.size
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = if (event.actionMasked == MotionEvent.ACTION_UP) 0 else event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val toolType = event.getToolType(pointerIndex)
                
                when (toolType) {
                    MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> {
                        if (activeStylusPointer == pointerId) {
                            activeStylusPointer = null
                            lastStylusTime = currentTime
                        }
                    }
                }
                
                // Remove from rejected list
                rejectedPointers.remove(pointerId)
                
                // Don't process if this pointer was rejected
                return !rejectedPointers.contains(pointerId)
            }
            
            MotionEvent.ACTION_CANCEL -> {
                // Clear all tracking on cancel
                activeStylusPointer = null
                rejectedPointers.clear()
                return true
            }
        }
        
        return true
    }
    
    /**
     * Determines if finger input should be rejected based on palm detection heuristics.
     */
    private fun shouldRejectFingerInput(
        fingerPosition: Offset,
        event: MotionEvent,
        pointerIndex: Int,
        currentTime: Long
    ): Boolean {
        // Heuristic 1: Reject finger input near active stylus
        if (activeStylusPointer != null) {
            val distance = distance(fingerPosition, stylusPosition)
            if (distance < PALM_REJECTION_RADIUS) {
                return true
            }
        }
        
        // Heuristic 2: Reject finger input shortly after stylus activity
        if (currentTime - lastStylusTime < STYLUS_TIMEOUT_MS) {
            val distance = distance(fingerPosition, stylusPosition)
            if (distance < PALM_REJECTION_RADIUS * 1.5f) {
                return true
            }
        }
        
        // Heuristic 3: Reject based on touch characteristics (pressure, size)
        val pressure = event.getPressure(pointerIndex)
        val size = event.getSize(pointerIndex)
        
        if (pressure > PALM_PRESSURE_THRESHOLD && size > PALM_SIZE_THRESHOLD) {
            return true
        }
        
        // Heuristic 4: Reject multiple simultaneous finger touches (likely palm)
        val fingerCount = (0 until event.pointerCount).count { i ->
            event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER && 
            !rejectedPointers.contains(event.getPointerId(i))
        }
        
        if (fingerCount > 2) {
            return true
        }
        
        return false
    }
    
    /**
     * Calculates distance between two points.
     */
    private fun distance(p1: Offset, p2: Offset): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Checks if a pointer is currently rejected.
     */
    fun isPointerRejected(pointerId: Int): Boolean {
        return rejectedPointers.contains(pointerId)
    }
    
    /**
     * Gets the current stylus position if active.
     */
    fun getStylusPosition(): Offset? {
        return if (activeStylusPointer != null) stylusPosition else null
    }
    
    /**
     * Checks if stylus is currently active.
     */
    fun isStylusActive(): Boolean {
        return activeStylusPointer != null
    }
    
    /**
     * Resets all palm rejection state.
     */
    fun reset() {
        activeStylusPointer = null
        rejectedPointers.clear()
        stylusPosition = Offset.Zero
        lastStylusTime = 0L
    }
}