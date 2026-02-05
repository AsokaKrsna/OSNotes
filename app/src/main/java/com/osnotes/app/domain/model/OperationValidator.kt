package com.osnotes.app.domain.model

/**
 * Validates batch page operations before execution.
 * 
 * This validator checks for:
 * - Invalid page indices (out of bounds)
 * - Deleting the last remaining page
 * - Conflicting operations
 * 
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */
object OperationValidator {
    
    /**
     * Validates a list of operations against the current document state.
     * 
     * Checks performed:
     * 1. All page indices are within valid range [0, pageCount)
     * 2. No attempt to delete the last remaining page
     * 3. No conflicting operations
     * 
     * @param operations List of operations to validate
     * @param pageCount Current number of pages in the document
     * @return ValidationResult indicating whether operations are valid and any errors found
     * 
     * **Validates: Requirements 12.1, 12.2, 12.3, 12.4**
     */
    fun validate(
        operations: List<PageOperation>,
        pageCount: Int
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        // Track page count changes as we validate operations
        var currentPageCount = pageCount
        
        // Validate each operation
        operations.forEach { operation ->
            // Check for invalid page indices (Requirement 12.2)
            if (operation.originalPageIndex < 0 || operation.originalPageIndex >= pageCount) {
                errors.add(
                    ValidationError(
                        operationId = operation.id,
                        message = "Invalid page index: ${operation.originalPageIndex}. " +
                                "Valid range is 0 to ${pageCount - 1}."
                    )
                )
            }
            
            // Check for Move operation with invalid target index (Requirement 12.2)
            if (operation is PageOperation.Move) {
                if (operation.targetIndex < 0 || operation.targetIndex >= pageCount) {
                    errors.add(
                        ValidationError(
                            operationId = operation.id,
                            message = "Invalid target index: ${operation.targetIndex}. " +
                                    "Valid range is 0 to ${pageCount - 1}."
                        )
                    )
                }
            }
            
            // Check for deleting last page (Requirement 12.1)
            if (operation is PageOperation.Delete) {
                if (currentPageCount <= 1) {
                    errors.add(
                        ValidationError(
                            operationId = operation.id,
                            message = "Cannot delete the last remaining page. " +
                                    "A document must have at least one page."
                        )
                    )
                } else {
                    // Update page count for subsequent validations
                    currentPageCount--
                }
            }
            
            // Update page count for duplicate operations
            if (operation is PageOperation.Duplicate) {
                currentPageCount++
            }
        }
        
        // Check for conflicting operations (Requirement 12.3, 12.4)
        val conflictErrors = checkForConflicts(operations)
        errors.addAll(conflictErrors)
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Checks for conflicting operations in the queue.
     * 
     * Conflicts include:
     * - Multiple delete operations on the same page
     * - Delete and move operations on the same page
     * - Multiple move operations with the same source page
     * 
     * @param operations List of operations to check
     * @return List of validation errors for conflicting operations
     */
    private fun checkForConflicts(operations: List<PageOperation>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        // Group operations by page index
        val operationsByPage = operations.groupBy { it.originalPageIndex }
        
        operationsByPage.forEach { (pageIndex, pageOperations) ->
            // Check for multiple deletes on the same page
            val deleteOps = pageOperations.filterIsInstance<PageOperation.Delete>()
            if (deleteOps.size > 1) {
                deleteOps.drop(1).forEach { op ->
                    errors.add(
                        ValidationError(
                            operationId = op.id,
                            message = "Conflicting operation: Page $pageIndex already has a delete operation."
                        )
                    )
                }
            }
            
            // Check for delete + move conflict
            val hasDelete = pageOperations.any { it is PageOperation.Delete }
            val moveOps = pageOperations.filterIsInstance<PageOperation.Move>()
            if (hasDelete && moveOps.isNotEmpty()) {
                moveOps.forEach { op ->
                    errors.add(
                        ValidationError(
                            operationId = op.id,
                            message = "Conflicting operation: Cannot move page $pageIndex " +
                                    "because it has a pending delete operation."
                        )
                    )
                }
            }
            
            // Check for multiple move operations on the same page
            if (moveOps.size > 1) {
                moveOps.drop(1).forEach { op ->
                    errors.add(
                        ValidationError(
                            operationId = op.id,
                            message = "Conflicting operation: Page $pageIndex already has a move operation."
                        )
                    )
                }
            }
        }
        
        return errors
    }
    
    /**
     * Normalizes operation indices by accounting for page count changes from previous operations.
     * 
     * Operations are reordered by type (Delete → Duplicate → Move) to ensure correct execution.
     * Indices are adjusted to account for:
     * - Pages removed by delete operations
     * - Pages added by duplicate operations
     * 
     * @param operations List of operations to normalize
     * @param pageCount Current number of pages in the document
     * @return List of normalized operations with adjusted indices
     * 
     * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**
     */
    fun normalizeIndices(
        operations: List<PageOperation>,
        pageCount: Int
    ): List<NormalizedOperation> {
        // First validate operations
        val validation = validate(operations, pageCount)
        if (!validation.isValid) {
            // Return empty list if validation fails
            // Caller should check validation before calling normalizeIndices
            return emptyList()
        }
        
        val normalized = mutableListOf<NormalizedOperation>()
        var currentPageCount = pageCount
        
        // Group operations by type (Requirement 6.2, 6.3)
        val deletes = operations.filterIsInstance<PageOperation.Delete>()
        val duplicates = operations.filterIsInstance<PageOperation.Duplicate>()
        val moves = operations.filterIsInstance<PageOperation.Move>()
        
        // Process deletes first (Requirement 6.2)
        deletes.forEach { delete ->
            val adjustedIndex = calculateAdjustedIndex(
                originalIndex = delete.originalPageIndex,
                previousOperations = normalized,
                originalPageCount = pageCount
            )
            normalized.add(NormalizedOperation(delete, adjustedIndex))
            currentPageCount--
        }
        
        // Process duplicates second (Requirement 6.3)
        duplicates.forEach { duplicate ->
            val adjustedIndex = calculateAdjustedIndex(
                originalIndex = duplicate.originalPageIndex,
                previousOperations = normalized,
                originalPageCount = pageCount
            )
            normalized.add(NormalizedOperation(duplicate, adjustedIndex))
            currentPageCount++
        }
        
        // Process moves last (Requirement 6.3)
        moves.forEach { move ->
            val adjustedFromIndex = calculateAdjustedIndex(
                originalIndex = move.originalPageIndex,
                previousOperations = normalized,
                originalPageCount = pageCount
            )
            val adjustedToIndex = calculateAdjustedIndex(
                originalIndex = move.targetIndex,
                previousOperations = normalized,
                originalPageCount = pageCount
            )
            normalized.add(NormalizedOperation(move, adjustedFromIndex, adjustedToIndex))
        }
        
        return normalized
    }
    
    /**
     * Calculates the adjusted index for an operation based on previous operations.
     * 
     * This accounts for:
     * - Pages removed by previous delete operations
     * - Pages added by previous duplicate operations
     * 
     * @param originalIndex The original page index when the operation was queued
     * @param previousOperations List of operations that have been processed before this one
     * @param originalPageCount The original page count before any operations
     * @return The adjusted index accounting for previous operations
     */
    private fun calculateAdjustedIndex(
        originalIndex: Int,
        previousOperations: List<NormalizedOperation>,
        originalPageCount: Int
    ): Int {
        var adjustedIndex = originalIndex
        
        // Adjust for previous delete operations (Requirement 6.4)
        previousOperations
            .filter { it.operation is PageOperation.Delete }
            .forEach { normalizedOp ->
                // If a page before this one was deleted, shift index down
                if (normalizedOp.normalizedIndex < adjustedIndex) {
                    adjustedIndex--
                }
            }
        
        // Adjust for previous duplicate operations (Requirement 6.5)
        previousOperations
            .filter { it.operation is PageOperation.Duplicate }
            .forEach { normalizedOp ->
                val duplicate = normalizedOp.operation as PageOperation.Duplicate
                // If a page was duplicated before this one, shift index up
                val insertPosition = if (duplicate.insertAfter) {
                    normalizedOp.normalizedIndex + 1
                } else {
                    normalizedOp.normalizedIndex
                }
                
                if (insertPosition <= adjustedIndex) {
                    adjustedIndex++
                }
            }
        
        return adjustedIndex
    }
}
