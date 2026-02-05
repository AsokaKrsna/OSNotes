package com.osnotes.app.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import androidx.compose.ui.graphics.Color

/**
 * Property-based tests for batch operation data models.
 * 
 * **Validates: Requirements 2.1, 2.2, 2.3**
 * 
 * These tests verify that the data models correctly represent page operations
 * and maintain their properties across all valid inputs.
 */
class BatchOperationModelsPropertyTest : StringSpec({
    
    "Feature: batch-page-operations, Property 1: Operation Queueing Correctness - Delete operations" {
        checkAll(100, Arb.int(0..100)) { pageIndex ->
            // Given: A delete operation is created
            val operation = PageOperation.Delete(originalPageIndex = pageIndex)
            
            // Then: The operation should have correct type and page index
            operation.shouldBeInstanceOf<PageOperation.Delete>()
            operation.originalPageIndex shouldBe pageIndex
            operation.id shouldNotBe ""
        }
    }
    
    "Feature: batch-page-operations, Property 1: Operation Queueing Correctness - Duplicate operations" {
        checkAll(100, Arb.int(0..100)) { pageIndex ->
            // Given: A duplicate operation is created
            val operation = PageOperation.Duplicate(originalPageIndex = pageIndex)
            
            // Then: The operation should have correct type and page index
            operation.shouldBeInstanceOf<PageOperation.Duplicate>()
            operation.originalPageIndex shouldBe pageIndex
            operation.insertAfter shouldBe true // Default value
            operation.id shouldNotBe ""
        }
    }
    
    "Feature: batch-page-operations, Property 1: Operation Queueing Correctness - Move operations" {
        checkAll(100, Arb.int(0..100), Arb.int(0..100)) { fromIndex, toIndex ->
            // Given: A move operation is created
            val operation = PageOperation.Move(
                originalPageIndex = fromIndex,
                targetIndex = toIndex
            )
            
            // Then: The operation should have correct type and indices
            operation.shouldBeInstanceOf<PageOperation.Move>()
            operation.originalPageIndex shouldBe fromIndex
            operation.targetIndex shouldBe toIndex
            operation.id shouldNotBe ""
        }
    }
    
    "BatchModeState correctly tracks operations for a page" {
        checkAll(100, Arb.operationList(1..10), Arb.int(0..50)) { operations, queryPageIndex ->
            // Given: A batch mode state with multiple operations
            val state = BatchModeState(
                isActive = true,
                operations = operations
            )
            
            // When: Querying operations for a specific page
            val pageOperations = state.operationsForPage(queryPageIndex)
            
            // Then: Only operations for that page should be returned
            pageOperations.all { it.originalPageIndex == queryPageIndex } shouldBe true
            
            // And: The count should match the filtered operations
            val expectedCount = operations.count { it.originalPageIndex == queryPageIndex }
            pageOperations.size shouldBe expectedCount
        }
    }
    
    "BatchModeState correctly reports operation count" {
        checkAll(100, Arb.operationList(0..20)) { operations ->
            // Given: A batch mode state with operations
            val state = BatchModeState(
                isActive = true,
                operations = operations
            )
            
            // Then: Operation count should match the list size
            state.operationCount shouldBe operations.size
            state.hasOperations shouldBe operations.isNotEmpty()
        }
    }
    
    "getPageBadges generates correct badges for Delete operations" {
        checkAll(100, Arb.int(0..50)) { pageIndex ->
            // Given: A delete operation for a page
            val operations = listOf(PageOperation.Delete(originalPageIndex = pageIndex))
            
            // When: Getting badges for that page
            val badges = getPageBadges(pageIndex, operations)
            
            // Then: Should have one delete badge
            badges shouldHaveSize 1
            badges[0].type shouldBe BadgeType.DELETE
            badges[0].text shouldBe "Delete"
            badges[0].color shouldBe Color.Red
        }
    }
    
    "getPageBadges generates correct badges for Duplicate operations" {
        checkAll(100, Arb.int(0..50)) { pageIndex ->
            // Given: A duplicate operation for a page
            val operations = listOf(PageOperation.Duplicate(originalPageIndex = pageIndex))
            
            // When: Getting badges for that page
            val badges = getPageBadges(pageIndex, operations)
            
            // Then: Should have one duplicate badge
            badges shouldHaveSize 1
            badges[0].type shouldBe BadgeType.DUPLICATE
            badges[0].text shouldBe "Duplicate"
            badges[0].color shouldBe Color.Blue
        }
    }
    
    "getPageBadges generates correct badges for Move operations" {
        checkAll(100, Arb.int(0..50), Arb.int(0..50)) { pageIndex, targetIndex ->
            // Given: A move operation for a page
            val operations = listOf(
                PageOperation.Move(
                    originalPageIndex = pageIndex,
                    targetIndex = targetIndex
                )
            )
            
            // When: Getting badges for that page
            val badges = getPageBadges(pageIndex, operations)
            
            // Then: Should have one move badge with correct target
            badges shouldHaveSize 1
            badges[0].type shouldBe BadgeType.MOVE
            badges[0].text shouldBe "Move to ${targetIndex + 1}"
            badges[0].color shouldBe Color(0xFF9C27B0)
        }
    }
    
    "getPageBadges generates multiple badges for multiple operations on same page" {
        checkAll(100, Arb.int(0..50)) { pageIndex ->
            // Given: Multiple operations for the same page
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = pageIndex),
                PageOperation.Duplicate(originalPageIndex = pageIndex),
                PageOperation.Move(originalPageIndex = pageIndex, targetIndex = pageIndex + 1)
            )
            
            // When: Getting badges for that page
            val badges = getPageBadges(pageIndex, operations)
            
            // Then: Should have three badges
            badges shouldHaveSize 3
            badges.map { it.type } shouldContain BadgeType.DELETE
            badges.map { it.type } shouldContain BadgeType.DUPLICATE
            badges.map { it.type } shouldContain BadgeType.MOVE
        }
    }
    
    "getPageBadges returns empty list for pages with no operations" {
        checkAll(100, Arb.int(0..50), Arb.operationList(1..10)) { queryPageIndex, operations ->
            // Given: Operations that don't affect the query page
            val filteredOps = operations.filter { it.originalPageIndex != queryPageIndex }
            
            // When: Getting badges for a page with no operations
            val badges = getPageBadges(queryPageIndex, filteredOps)
            
            // Then: Should return empty list
            badges shouldHaveSize 0
        }
    }
    
    "ValidationResult correctly represents validation state" {
        checkAll(100, Arb.list(Arb.validationError(), 0..10)) { errors ->
            // Given: A validation result with errors
            val result = ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors
            )
            
            // Then: isValid should match whether there are errors
            result.isValid shouldBe errors.isEmpty()
            result.errors shouldBe errors
        }
    }
    
    "NormalizedOperation preserves original operation" {
        checkAll(100, Arb.pageOperation(), Arb.int(0..100)) { operation, normalizedIndex ->
            // Given: A normalized operation
            val normalized = NormalizedOperation(
                operation = operation,
                normalizedIndex = normalizedIndex
            )
            
            // Then: Original operation should be preserved
            normalized.operation shouldBe operation
            normalized.normalizedIndex shouldBe normalizedIndex
        }
    }
    
    "Each operation has a unique ID" {
        checkAll(100, Arb.int(0..50)) { pageIndex ->
            // Given: Multiple operations created for the same page
            val operations = listOf(
                PageOperation.Delete(originalPageIndex = pageIndex),
                PageOperation.Delete(originalPageIndex = pageIndex),
                PageOperation.Duplicate(originalPageIndex = pageIndex),
                PageOperation.Duplicate(originalPageIndex = pageIndex)
            )
            
            // Then: All operations should have unique IDs
            val ids = operations.map { it.id }
            ids.toSet().size shouldBe ids.size
        }
    }
})

// Custom Arbitraries for generating test data

/**
 * Generates arbitrary PageOperation instances.
 */
fun Arb.Companion.pageOperation(): Arb<PageOperation> = arbitrary {
    val pageIndex = Arb.int(0..100).bind()
    val operationType = Arb.int(0..2).bind()
    
    when (operationType) {
        0 -> PageOperation.Delete(originalPageIndex = pageIndex)
        1 -> PageOperation.Duplicate(originalPageIndex = pageIndex)
        else -> PageOperation.Move(
            originalPageIndex = pageIndex,
            targetIndex = Arb.int(0..100).bind()
        )
    }
}

/**
 * Generates arbitrary lists of PageOperation instances.
 */
fun Arb.Companion.operationList(range: IntRange): Arb<List<PageOperation>> =
    Arb.list(Arb.pageOperation(), range)

/**
 * Generates arbitrary ValidationError instances.
 */
fun Arb.Companion.validationError(): Arb<ValidationError> = arbitrary {
    ValidationError(
        operationId = Arb.string(8..16).bind(),
        message = Arb.string(10..50).bind()
    )
}
