package com.osnotes.app.domain.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Represents a single page operation to be executed in batch mode.
 * Operations are queued and executed together to avoid repeated PDF reconstructions.
 */
sealed class PageOperation {
    abstract val id: String
    abstract val originalPageIndex: Int
    
    /**
     * Delete operation removes a page from the document.
     * @param id Unique identifier for this operation
     * @param originalPageIndex The page index when the operation was queued
     */
    data class Delete(
        override val id: String = UUID.randomUUID().toString(),
        override val originalPageIndex: Int
    ) : PageOperation()
    
    /**
     * Duplicate operation creates a copy of a page.
     * @param id Unique identifier for this operation
     * @param originalPageIndex The page index when the operation was queued
     * @param insertAfter If true, insert duplicate after source page; if false, insert before
     */
    data class Duplicate(
        override val id: String = UUID.randomUUID().toString(),
        override val originalPageIndex: Int,
        val insertAfter: Boolean = true
    ) : PageOperation()
    
    /**
     * Move operation relocates a page to a different position.
     * @param id Unique identifier for this operation
     * @param originalPageIndex The page index when the operation was queued
     * @param targetIndex The destination index for the page
     */
    data class Move(
        override val id: String = UUID.randomUUID().toString(),
        override val originalPageIndex: Int,
        val targetIndex: Int
    ) : PageOperation()
}

/**
 * Holds the state of batch mode in the Page Manager.
 * @param isActive Whether batch mode is currently active
 * @param operations List of queued operations to be executed
 * @param isExecuting Whether batch execution is currently in progress
 * @param executionProgress Progress of batch execution (0.0 to 1.0)
 * @param currentOperation Description of the operation currently being processed
 * @param error Error message if batch execution failed
 */
data class BatchModeState(
    val isActive: Boolean = false,
    val operations: List<PageOperation> = emptyList(),
    val isExecuting: Boolean = false,
    val executionProgress: Float = 0f,
    val currentOperation: String = "",
    val error: String? = null
) {
    /**
     * Total number of operations in the queue.
     */
    val operationCount: Int get() = operations.size
    
    /**
     * Whether there are any operations queued.
     */
    val hasOperations: Boolean get() = operations.isNotEmpty()
    
    /**
     * Returns all operations that affect a specific page.
     * @param pageIndex The page index to query
     * @return List of operations affecting this page
     */
    fun operationsForPage(pageIndex: Int): List<PageOperation> {
        return operations.filter { it.originalPageIndex == pageIndex }
    }
}

/**
 * Result of validating a list of operations before execution.
 * @param isValid Whether all operations are valid
 * @param errors List of validation errors found
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>
)

/**
 * Represents a validation error for a specific operation.
 * @param operationId The ID of the operation that failed validation
 * @param message Human-readable error message
 */
data class ValidationError(
    val operationId: String,
    val message: String
)

/**
 * Represents an operation with normalized indices after accounting for previous operations.
 * Used during batch execution to calculate correct page indices.
 * @param operation The original operation
 * @param normalizedIndex The adjusted page index after accounting for previous operations
 * @param normalizedTargetIndex The adjusted target index for Move operations (null for other types)
 */
data class NormalizedOperation(
    val operation: PageOperation,
    val normalizedIndex: Int,
    val normalizedTargetIndex: Int? = null
)

/**
 * Visual badge displayed on page thumbnails to indicate pending operations.
 * @param type The type of badge (determines icon/style)
 * @param text The text to display on the badge
 * @param color The color of the badge
 */
data class PageBadge(
    val type: BadgeType,
    val text: String,
    val color: Color
)

/**
 * Types of badges that can be displayed on page thumbnails.
 */
enum class BadgeType {
    /**
     * Red badge indicating a delete operation is pending.
     */
    DELETE,
    
    /**
     * Blue badge indicating a duplicate operation is pending.
     */
    DUPLICATE,
    
    /**
     * Purple badge indicating a move operation is pending.
     */
    MOVE
}

/**
 * Helper function to generate badges for a page based on pending operations.
 * @param pageIndex The page index to generate badges for
 * @param operations List of all pending operations
 * @return List of badges to display on the page thumbnail
 */
fun getPageBadges(pageIndex: Int, operations: List<PageOperation>): List<PageBadge> {
    return operations
        .filter { it.originalPageIndex == pageIndex }
        .map { operation ->
            when (operation) {
                is PageOperation.Delete -> PageBadge(
                    type = BadgeType.DELETE,
                    text = "Delete",
                    color = Color.Red
                )
                is PageOperation.Duplicate -> PageBadge(
                    type = BadgeType.DUPLICATE,
                    text = "Duplicate",
                    color = Color.Blue
                )
                is PageOperation.Move -> PageBadge(
                    type = BadgeType.MOVE,
                    text = "Move to ${operation.targetIndex + 1}",
                    color = Color(0xFF9C27B0) // Purple
                )
            }
        }
}
