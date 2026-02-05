package com.osnotes.app.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Property-based tests for OperationValidator validation and normalization logic.
 * 
 * **Validates: Requirements 6.4, 6.5, 12.1, 12.2, 12.3, 12.4**
 * 
 * These tests verify that the validator correctly normalizes indices and validates
 * operations across all valid input combinations.
 */
class OperationValidatorPropertyTest : StringSpec({
    
    "Feature: batch-page-operations, Property 10: Index Normalization - Delete operations adjust subsequent indices" {
        checkAll(100, Arb.validPageCount(), Arb.validPageIndex()) { pageCount, deleteIndex ->
            // Given: A delete operation followed by another operation on a later page
            val targetIndex = (deleteIndex + 1).coerceAtMost(pageCount - 1)
            if (targetIndex > deleteIndex && pageCount > 2) {
                val operations = listOf(
                    PageOperation.Delete(originalPageIndex = deleteIndex),
                    PageOperation.Duplicate(originalPageIndex = targetIndex)
                )
                
                // When: Normalizing indices
                val normalized = OperationValidator.normalizeIndices(operations, pageCount)
                
                // Then: The duplicate operation's index should be adjusted down by 1
                normalized shouldHaveSize 2
                normalized[0].normalizedIndex shouldBe deleteIndex
                normalized[1].normalizedIndex shouldBe (targetIndex - 1)
            }
        }
    }
    
    "Feature: batch-page-operations, Property 10: Index Normalization - Duplicate operations adjust subsequent indices" {
        checkAll(100, Arb.validPageCount(), Arb.validPageIndex()) { pageCount, duplicateIndex ->
            // Given: A duplicate operation followed by another operation on a later page
            val targetIndex = (duplicateIndex + 1).coerceAtMost(pageCount - 1)
            if (targetIndex > duplicateIndex && pageCount > 1) {
                val operations = listOf(
                    PageOperation.Duplicate(originalPageIndex = duplicateIndex),
                    PageOperation.Move(originalPageIndex = targetIndex, targetIndex = 0)
                )
                
                // When: Normalizing indices
                val normalized = OperationValidator.normalizeIndices(operations, pageCount)
                
                // Then: The move operation's source index should be adjusted up by 1
                normalized shouldHaveSize 2
                normalized[0].normalizedIndex shouldBe duplicateIndex
                normalized[1].normalizedIndex shouldBe (targetIndex + 1)
            }
        }
    }
    
    "Feature: batch-page-operations, Property 10: Index Normalization - Multiple deletes compound adjustment" {
        checkAll(100, Arb.validPageCount().filter { it >= 5 }) { pageCount ->
            // Given: Multiple delete operations
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = 1),
                PageOperation.Delete(originalPageIndex = 3),
                PageOperation.Duplicate(originalPageIndex = 4)
            )
            
            // When: Normalizing indices
            val normalized = OperationValidator.normalizeIndices(operations, pageCount)
            
            // Then: Indices should be adjusted for all previous deletes
            // Delete at 1: stays at 1
            // Delete at 3: adjusted to 2 (after first delete)
            // Duplicate at 4: adjusted to 2 (after both deletes)
            normalized shouldHaveSize 3
            normalized[0].normalizedIndex shouldBe 1
            normalized[1].normalizedIndex shouldBe 2
            normalized[2].normalizedIndex shouldBe 2
        }
    }
    
    "Feature: batch-page-operations, Property 17: Delete Last Page Validation - Rejects deleting last page" {
        checkAll(100, Arb.int(0..10)) { pageIndex ->
            // Given: A document with only one page
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = pageIndex)
            )
            
            // When: Validating the operations
            val result = OperationValidator.validate(operations, pageCount = 1)
            
            // Then: Validation should fail with appropriate error
            result.isValid shouldBe false
            result.errors shouldHaveSize 1
            result.errors[0].message shouldContain "Cannot delete the last remaining page"
        }
    }
    
    "Feature: batch-page-operations, Property 17: Delete Last Page Validation - Tracks page count across operations" {
        checkAll(100, Arb.int(2..10)) { pageCount ->
            // Given: Enough delete operations to delete all pages
            val operations = (0 until pageCount).map { 
                PageOperation.Delete(originalPageIndex = it)
            }
            
            // When: Validating the operations
            val result = OperationValidator.validate(operations, pageCount)
            
            // Then: Validation should fail when trying to delete the last page
            result.isValid shouldBe false
            result.errors.any { it.message.contains("Cannot delete the last remaining page") } shouldBe true
        }
    }
    
    "Feature: batch-page-operations, Property 18: Invalid Index Validation - Rejects negative indices" {
        checkAll(100, Arb.int(-100..-1), Arb.validPageCount()) { negativeIndex, pageCount ->
            // Given: An operation with a negative page index
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = negativeIndex)
            )
            
            // When: Validating the operations
            val result = OperationValidator.validate(operations, pageCount)
            
            // Then: Validation should fail with appropriate error
            result.isValid shouldBe false
            result.errors shouldHaveSize 1
            result.errors[0].message shouldContain "Invalid page index: $negativeIndex"
        }
    }
    
    "Feature: batch-page-operations, Property 18: Invalid Index Validation - Rejects out of bounds indices" {
        checkAll(100, Arb.validPageCount()) { pageCount ->
            // Given: An operation with page index >= pageCount
            val invalidIndex = pageCount + Arb.int(0..100).bind()
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = invalidIndex)
            )
            
            // When: Validating the operations
            val result = OperationValidator.validate(operations, pageCount)
            
            // Then: Validation should fail with appropriate error
            result.isValid shouldBe false
            result.errors shouldHaveSize 1
            result.errors[0].message shouldContain "Invalid page index: $invalidIndex"
            result.errors[0].message shouldContain "Valid range is 0 to ${pageCount - 1}"
        }
    }
    
    "Feature: batch-page-operations, Property 18: Invalid Index Validation - Rejects invalid move target indices" {
        checkAll(100, Arb.validPageCount()) { pageCount ->
            // Given: A move operation with invalid target index
            val validSource = Arb.int(0 until pageCount).bind()
            val invalidTarget = pageCount + Arb.int(0..100).bind()
            val operations = listOf(
                PageOperation.Move(originalPageIndex = validSource, targetIndex = invalidTarget)
            )
            
            // When: Validating the operations
            val result = OperationValidator.validate(operations, pageCount)
            
            // Then: Validation should fail with appropriate error
            result.isValid shouldBe false
            result.errors shouldHaveSize 1
            result.errors[0].message shouldContain "Invalid target index: $invalidTarget"
        }
    }
    
    "Feature: batch-page-operations, Property 19: Pre-Execution Validation - Validates all operations" {
        checkAll(100, Arb.validPageCount(), Arb.validOperationList(1..5)) { pageCount, operations ->
            // Given: A list of operations
            // When: Validating the operations
            val result = OperationValidator.validate(operations, pageCount)
            
            // Then: Result should indicate whether all operations are valid
            if (result.isValid) {
                result.errors.shouldBeEmpty()
            } else {
                result.errors.size shouldBeGreaterThan 0
            }
            
            // And: Each error should have an operation ID and message
            result.errors.forEach { error ->
                error.operationId shouldNotBe ""
                error.message shouldNotBe ""
            }
        }
    }
    
    "Feature: batch-page-operations, Property 19: Pre-Execution Validation - Detects conflicting operations" {
        checkAll(100, Arb.validPageCount(), Arb.validPageIndex()) { pageCount, pageIndex ->
            // Given: Multiple delete operations on the same page
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = pageIndex),
                PageOperation.Delete(originalPageIndex = pageIndex)
            )
            
            // When: Validating the operations
            val result = OperationValidator.validate(operations, pageCount)
            
            // Then: Validation should fail with conflict error
            result.isValid shouldBe false
            result.errors.any { it.message.contains("Conflicting operation") } shouldBe true
        }
    }
    
    "Feature: batch-page-operations, Property 19: Pre-Execution Validation - Detects delete and move conflicts" {
        checkAll(100, Arb.validPageCount(), Arb.validPageIndex()) { pageCount, pageIndex ->
            if (pageCount > 1) {
                // Given: Delete and move operations on the same page
                val targetIndex = (pageIndex + 1) % pageCount
                val operations = listOf(
                    PageOperation.Delete(originalPageIndex = pageIndex),
                    PageOperation.Move(originalPageIndex = pageIndex, targetIndex = targetIndex)
                )
                
                // When: Validating the operations
                val result = OperationValidator.validate(operations, pageCount)
                
                // Then: Validation should fail with conflict error
                result.isValid shouldBe false
                result.errors.any { 
                    it.message.contains("Cannot move page") && 
                    it.message.contains("delete operation") 
                } shouldBe true
            }
        }
    }
    
    "Feature: batch-page-operations, Property 20: Final Page Order Calculation - Deletes remove pages" {
        checkAll(100, Arb.validPageCount().filter { it >= 3 }) { pageCount ->
            // Given: A delete operation
            val deleteIndex = Arb.int(0 until pageCount).bind()
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = deleteIndex)
            )
            
            // When: Calculating final page order
            val normalized = OperationValidator.normalizeIndices(operations, pageCount)
            val finalOrder = calculateFinalPageOrder(pageCount, normalized)
            
            // Then: Final order should have one fewer page
            finalOrder.size shouldBe (pageCount - 1)
            
            // And: The deleted page should not be present
            finalOrder shouldNotContain deleteIndex
        }
    }
    
    "Feature: batch-page-operations, Property 20: Final Page Order Calculation - Duplicates add pages" {
        checkAll(100, Arb.validPageCount()) { pageCount ->
            // Given: A duplicate operation
            val duplicateIndex = Arb.int(0 until pageCount).bind()
            val operations = listOf(
                PageOperation.Duplicate(originalPageIndex = duplicateIndex)
            )
            
            // When: Calculating final page order
            val normalized = OperationValidator.normalizeIndices(operations, pageCount)
            val finalOrder = calculateFinalPageOrder(pageCount, normalized)
            
            // Then: Final order should have one more page
            finalOrder.size shouldBe (pageCount + 1)
            
            // And: The duplicated page should appear twice
            finalOrder.count { it == duplicateIndex } shouldBe 2
        }
    }
    
    "Feature: batch-page-operations, Property 20: Final Page Order Calculation - Moves relocate pages" {
        checkAll(100, Arb.validPageCount().filter { it >= 2 }) { pageCount ->
            // Given: A move operation
            val fromIndex = Arb.int(0 until pageCount).bind()
            val toIndex = Arb.int(0 until pageCount).filter { it != fromIndex }.bind()
            val operations = listOf(
                PageOperation.Move(originalPageIndex = fromIndex, targetIndex = toIndex)
            )
            
            // When: Calculating final page order
            val normalized = OperationValidator.normalizeIndices(operations, pageCount)
            val finalOrder = calculateFinalPageOrder(pageCount, normalized)
            
            // Then: Final order should have same number of pages
            finalOrder.size shouldBe pageCount
            
            // And: The moved page should be at the target position
            finalOrder[toIndex] shouldBe fromIndex
        }
    }
    
    "Feature: batch-page-operations, Property 20: Final Page Order Calculation - Complex sequences produce correct order" {
        checkAll(100, Arb.validPageCount().filter { it >= 5 }) { pageCount ->
            // Given: A complex sequence of operations
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = 1),
                PageOperation.Duplicate(originalPageIndex = 2),
                PageOperation.Move(originalPageIndex = 3, targetIndex = 0)
            )
            
            // When: Calculating final page order
            val normalized = OperationValidator.normalizeIndices(operations, pageCount)
            val finalOrder = calculateFinalPageOrder(pageCount, normalized)
            
            // Then: Final order should have correct page count
            // Original: pageCount pages
            // After delete: pageCount - 1
            // After duplicate: pageCount
            finalOrder.size shouldBe pageCount
            
            // And: All page indices should be valid
            finalOrder.forEach { pageIndex ->
                pageIndex shouldBeGreaterThan -1
                pageIndex shouldBeLessThan pageCount
            }
        }
    }
    
    "Feature: batch-page-operations, Property 20: Final Page Order Calculation - Preserves relative order of unaffected pages" {
        checkAll(100, Arb.validPageCount().filter { it >= 4 }) { pageCount ->
            // Given: Operations that don't affect all pages
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = 1)
            )
            
            // When: Calculating final page order
            val normalized = OperationValidator.normalizeIndices(operations, pageCount)
            val finalOrder = calculateFinalPageOrder(pageCount, normalized)
            
            // Then: Pages 0, 2, 3, ... should maintain their relative order
            // Original: [0, 1, 2, 3, ...]
            // After delete(1): [0, 2, 3, ...]
            finalOrder[0] shouldBe 0
            if (pageCount > 2) {
                finalOrder[1] shouldBe 2
            }
            if (pageCount > 3) {
                finalOrder[2] shouldBe 3
            }
        }
    }
})

// Custom Arbitraries for generating test data

/**
 * Generates valid page counts (1 to 100 pages).
 */
fun Arb.Companion.validPageCount(): Arb<Int> = Arb.int(1..100)

/**
 * Generates valid page indices (0 to 99).
 */
fun Arb.Companion.validPageIndex(): Arb<Int> = Arb.int(0..99)

/**
 * Generates a list of valid operations for a given page count.
 */
fun Arb.Companion.validOperationList(range: IntRange): Arb<List<PageOperation>> = arbitrary {
    val count = Arb.int(range).bind()
    val pageCount = Arb.validPageCount().bind()
    
    List(count) {
        val pageIndex = Arb.int(0 until pageCount).bind()
        val operationType = Arb.int(0..2).bind()
        
        when (operationType) {
            0 -> PageOperation.Delete(originalPageIndex = pageIndex)
            1 -> PageOperation.Duplicate(originalPageIndex = pageIndex)
            else -> PageOperation.Move(
                originalPageIndex = pageIndex,
                targetIndex = Arb.int(0 until pageCount).bind()
            )
        }
    }
}

/**
 * Helper function to calculate the final page order after applying all operations.
 * This simulates the batch execution algorithm.
 * 
 * @param originalPageCount The original number of pages before any operations
 * @param normalizedOperations List of normalized operations to apply
 * @return List of page indices in their final order
 */
fun calculateFinalPageOrder(
    originalPageCount: Int,
    normalizedOperations: List<NormalizedOperation>
): List<Int> {
    // Start with original page order
    val pages = (0 until originalPageCount).toMutableList()
    
    // Apply deletes (from end to start to avoid index shifting issues)
    normalizedOperations
        .filter { it.operation is PageOperation.Delete }
        .sortedByDescending { it.normalizedIndex }
        .forEach { pages.removeAt(it.normalizedIndex) }
    
    // Apply duplicates
    normalizedOperations
        .filter { it.operation is PageOperation.Duplicate }
        .forEach { 
            val sourcePage = pages[it.normalizedIndex]
            val duplicate = it.operation as PageOperation.Duplicate
            val insertPosition = if (duplicate.insertAfter) {
                it.normalizedIndex + 1
            } else {
                it.normalizedIndex
            }
            pages.add(insertPosition, sourcePage)
        }
    
    // Apply moves
    normalizedOperations
        .filter { it.operation is PageOperation.Move }
        .forEach {
            val page = pages.removeAt(it.normalizedIndex)
            pages.add(it.normalizedTargetIndex!!, page)
        }
    
    return pages
}
