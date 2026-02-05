package com.osnotes.app.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * Unit tests for OperationValidator.
 * 
 * **Validates: Requirements 12.1, 12.2, 12.3, 12.4**
 * 
 * These tests verify that the validator correctly identifies invalid operations
 * and validates operation sequences before batch execution.
 */
class OperationValidatorTest : StringSpec({
    
    "validate accepts valid delete operation" {
        // Given: A valid delete operation on a document with multiple pages
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 1)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should pass
        result.isValid shouldBe true
        result.errors.shouldBeEmpty()
    }
    
    "validate rejects delete operation on last page" {
        // Given: A delete operation on a document with only one page
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 0)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 1)
        
        // Then: Validation should fail with appropriate error
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Cannot delete the last remaining page. A document must have at least one page."
    }
    
    "validate rejects operation with invalid page index" {
        // Given: An operation with page index out of bounds
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 10)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should fail with appropriate error
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Invalid page index: 10. Valid range is 0 to 4."
    }
    
    "validate rejects operation with negative page index" {
        // Given: An operation with negative page index
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = -1)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should fail with appropriate error
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Invalid page index: -1. Valid range is 0 to 4."
    }
    
    "validate rejects move operation with invalid target index" {
        // Given: A move operation with invalid target index
        val operations = listOf(
            PageOperation.Move(originalPageIndex = 1, targetIndex = 10)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should fail with appropriate error
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Invalid target index: 10. Valid range is 0 to 4."
    }
    
    "validate rejects multiple delete operations on same page" {
        // Given: Multiple delete operations on the same page
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 1),
            PageOperation.Delete(originalPageIndex = 1)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should fail with conflict error
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Conflicting operation: Page 1 already has a delete operation."
    }
    
    "validate rejects delete and move on same page" {
        // Given: Delete and move operations on the same page
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 1),
            PageOperation.Move(originalPageIndex = 1, targetIndex = 3)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should fail with conflict error
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Conflicting operation: Cannot move page 1 because it has a pending delete operation."
    }
    
    "validate rejects multiple move operations on same page" {
        // Given: Multiple move operations on the same page
        val operations = listOf(
            PageOperation.Move(originalPageIndex = 1, targetIndex = 3),
            PageOperation.Move(originalPageIndex = 1, targetIndex = 4)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should fail with conflict error
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Conflicting operation: Page 1 already has a move operation."
    }
    
    "validate accepts multiple operations on different pages" {
        // Given: Multiple operations on different pages
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 1),
            PageOperation.Duplicate(originalPageIndex = 2),
            PageOperation.Move(originalPageIndex = 3, targetIndex = 0)
        )
        
        // When: Validating the operations
        val result = OperationValidator.validate(operations, pageCount = 5)
        
        // Then: Validation should pass
        result.isValid shouldBe true
        result.errors.shouldBeEmpty()
    }
    
    "validate tracks page count changes across operations" {
        // Given: Multiple delete operations that would eventually delete all pages
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 0),
            PageOperation.Delete(originalPageIndex = 1)
        )
        
        // When: Validating on a 2-page document
        val result = OperationValidator.validate(operations, pageCount = 2)
        
        // Then: Second delete should be rejected
        result.isValid shouldBe false
        result.errors shouldHaveSize 1
        result.errors[0].message shouldBe "Cannot delete the last remaining page. A document must have at least one page."
    }
    
    "normalizeIndices returns empty list for invalid operations" {
        // Given: Invalid operations
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 10)
        )
        
        // When: Normalizing indices
        val normalized = OperationValidator.normalizeIndices(operations, pageCount = 5)
        
        // Then: Should return empty list
        normalized.shouldBeEmpty()
    }
    
    "normalizeIndices groups operations by type" {
        // Given: Mixed operation types
        val operations = listOf(
            PageOperation.Move(originalPageIndex = 2, targetIndex = 0),
            PageOperation.Delete(originalPageIndex = 1),
            PageOperation.Duplicate(originalPageIndex = 3),
            PageOperation.Delete(originalPageIndex = 4)
        )
        
        // When: Normalizing indices
        val normalized = OperationValidator.normalizeIndices(operations, pageCount = 10)
        
        // Then: Operations should be grouped: Delete, Duplicate, Move
        normalized[0].operation shouldBe operations[1] // Delete
        normalized[1].operation shouldBe operations[3] // Delete
        normalized[2].operation shouldBe operations[2] // Duplicate
        normalized[3].operation shouldBe operations[0] // Move
    }
    
    "normalizeIndices adjusts indices for delete operations" {
        // Given: Delete operation followed by another operation
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 1),
            PageOperation.Duplicate(originalPageIndex = 3)
        )
        
        // When: Normalizing indices
        val normalized = OperationValidator.normalizeIndices(operations, pageCount = 5)
        
        // Then: Duplicate index should be adjusted down by 1
        normalized[0].normalizedIndex shouldBe 1 // Delete at 1
        normalized[1].normalizedIndex shouldBe 2 // Duplicate at 3, adjusted to 2
    }
    
    "normalizeIndices adjusts indices for duplicate operations" {
        // Given: Duplicate operation followed by another operation
        val operations = listOf(
            PageOperation.Duplicate(originalPageIndex = 1),
            PageOperation.Move(originalPageIndex = 3, targetIndex = 0)
        )
        
        // When: Normalizing indices
        val normalized = OperationValidator.normalizeIndices(operations, pageCount = 5)
        
        // Then: Move index should be adjusted up by 1
        normalized[0].normalizedIndex shouldBe 1 // Duplicate at 1
        normalized[1].normalizedIndex shouldBe 4 // Move from 3, adjusted to 4
        normalized[1].normalizedTargetIndex shouldBe 0 // Target not affected
    }
    
    "normalizeIndices handles complex operation sequences" {
        // Given: Complex sequence of operations
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 1),
            PageOperation.Delete(originalPageIndex = 3),
            PageOperation.Duplicate(originalPageIndex = 2),
            PageOperation.Move(originalPageIndex = 4, targetIndex = 0)
        )
        
        // When: Normalizing indices
        val normalized = OperationValidator.normalizeIndices(operations, pageCount = 6)
        
        // Then: All indices should be correctly adjusted
        // Original: [0, 1, 2, 3, 4, 5]
        // After delete(1): [0, 2, 3, 4, 5]
        // After delete(3): [0, 2, 4, 5] (3 becomes 2 after first delete)
        // After duplicate(2): [0, 2, 2', 4, 5] (2 becomes 1 after deletes)
        // After move(4→0): [4, 0, 2, 2', 5] (4 becomes 3 after operations)
        
        normalized[0].normalizedIndex shouldBe 1 // Delete at 1
        normalized[1].normalizedIndex shouldBe 2 // Delete at 3, adjusted to 2
        normalized[2].normalizedIndex shouldBe 1 // Duplicate at 2, adjusted to 1
        normalized[3].normalizedIndex shouldBe 3 // Move from 4, adjusted to 3
        normalized[3].normalizedTargetIndex shouldBe 0 // Target at 0
    }
})
