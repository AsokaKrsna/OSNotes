# Design Document: Batch Page Operations

## Overview

The batch page operations feature introduces a new editing mode in the Page Manager that allows users to queue multiple page manipulation operations (delete, duplicate, move) and execute them in a single batch. This design eliminates the performance issues caused by repeated PDF reconstructions and document reloads in the current implementation.

### Key Design Decisions

1. **Operation Queue Pattern**: Use a queue-based architecture where operations are stored as data objects and executed together, rather than immediately modifying the PDF
2. **Optimistic UI Updates**: Show visual feedback for pending operations without modifying the actual PDF until batch execution
3. **Single-Pass Execution**: Process all operations in one PDF reconstruction cycle to minimize I/O and improve performance
4. **Index Normalization**: Track logical page indices separately from physical indices to handle operations that change page count
5. **State Machine**: Use a clear state machine for batch mode (Inactive → Active → Executing → Complete/Error) to manage UI and operation flow

### Architecture Principles

- **Separation of Concerns**: Keep operation queueing logic separate from PDF manipulation logic
- **Immutability**: Operations are immutable once queued; modifications create new operation objects
- **Fail-Safe**: Original PDF is never modified until all operations are validated and ready to execute
- **Testability**: All operation logic is pure and testable without Android dependencies

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     PageManagerScreen                        │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Batch Mode UI                                         │ │
│  │  - Edit Mode Indicator                                 │ │
│  │  - Operation Count Badge                               │ │
│  │  - Apply/Cancel Buttons                                │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Page Thumbnail Grid                                   │ │
│  │  - Operation Badges (Delete/Duplicate/Move)            │ │
│  │  - Context Menu (Queue Operations)                     │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  PageManagerViewModel                        │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Batch Mode State                                      │ │
│  │  - isBatchMode: Boolean                                │ │
│  │  - operationQueue: List<PageOperation>                 │ │
│  │  - pendingOperationsByPage: Map<Int, List<Operation>>  │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Operation Management                                  │ │
│  │  - queueOperation()                                    │ │
│  │  - removeOperation()                                   │ │
│  │  - clearAllOperations()                                │ │
│  │  - executeBatch()                                      │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              PdfAnnotationFlattener                          │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Batch Execution                                       │ │
│  │  - executeBatchOperations()                            │ │
│  │  - validateOperations()                                │ │
│  │  - normalizeIndices()                                  │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Single-Pass PDF Reconstruction                        │ │
│  │  - Open source PDF once                                │ │
│  │  - Calculate final page order                          │ │
│  │  - Copy pages in final order                           │ │
│  │  - Replace original file                               │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### State Machine

```
┌──────────┐
│ Inactive │ (Normal page manager mode)
└────┬─────┘
     │ enterBatchMode()
     ▼
┌──────────┐
│  Active  │ (Queueing operations)
└────┬─────┘
     │ executeBatch()
     ▼
┌───────────┐
│ Executing │ (Processing operations)
└────┬──────┘
     │
     ├─ Success ──▶ ┌──────────┐
     │              │ Complete │ ──▶ Back to Inactive
     │              └──────────┘
     │
     └─ Failure ──▶ ┌───────┐
                    │ Error │ ──▶ Back to Active (retry) or Inactive (cancel)
                    └───────┘
```

## Components and Interfaces

### 1. PageOperation (Sealed Class)

Represents a single page operation to be executed.

```kotlin
sealed class PageOperation {
    abstract val id: String
    abstract val originalPageIndex: Int
    
    data class Delete(
        override val id: String = UUID.randomUUID().toString(),
        override val originalPageIndex: Int
    ) : PageOperation()
    
    data class Duplicate(
        override val id: String = UUID.randomUUID().toString(),
        override val originalPageIndex: Int,
        val insertAfter: Boolean = true  // Insert after source page
    ) : PageOperation()
    
    data class Move(
        override val id: String = UUID.randomUUID().toString(),
        override val originalPageIndex: Int,
        val targetIndex: Int
    ) : PageOperation()
}
```

### 2. BatchModeState (Data Class)

Holds the state of batch mode in the ViewModel.

```kotlin
data class BatchModeState(
    val isActive: Boolean = false,
    val operations: List<PageOperation> = emptyList(),
    val isExecuting: Boolean = false,
    val executionProgress: Float = 0f,
    val currentOperation: String = "",
    val error: String? = null
) {
    val operationCount: Int get() = operations.size
    val hasOperations: Boolean get() = operations.isNotEmpty()
    
    fun operationsForPage(pageIndex: Int): List<PageOperation> {
        return operations.filter { it.originalPageIndex == pageIndex }
    }
}
```

### 3. PageManagerViewModel Extensions

New methods added to the existing ViewModel:

```kotlin
// Batch mode management
fun enterBatchMode()
fun exitBatchMode()
fun isBatchModeActive(): Boolean

// Operation queueing
fun queueDeleteOperation(pageIndex: Int)
fun queueDuplicateOperation(pageIndex: Int)
fun queueMoveOperation(fromIndex: Int, toIndex: Int)

// Operation management
fun removeOperation(operationId: String)
fun clearAllOperations()
fun getOperationsForPage(pageIndex: Int): List<PageOperation>

// Batch execution
suspend fun executeBatch(): Result<Unit>
fun validateOperations(): ValidationResult

// State observation
val batchModeState: StateFlow<BatchModeState>
```

### 4. PdfAnnotationFlattener Extensions

New batch execution method:

```kotlin
suspend fun executeBatchOperations(
    sourceUri: Uri,
    operations: List<PageOperation>,
    strokes: Map<Int, List<InkStroke>> = emptyMap(),
    shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
    textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap(),
    onProgress: (Float, String) -> Unit = { _, _ -> }
): PageOperationResult
```

### 5. OperationValidator (Utility Class)

Validates operations before execution:

```kotlin
object OperationValidator {
    fun validate(
        operations: List<PageOperation>,
        pageCount: Int
    ): ValidationResult
    
    fun normalizeIndices(
        operations: List<PageOperation>,
        pageCount: Int
    ): List<NormalizedOperation>
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>
)

data class ValidationError(
    val operationId: String,
    val message: String
)

data class NormalizedOperation(
    val operation: PageOperation,
    val normalizedIndex: Int,
    val normalizedTargetIndex: Int? = null
)
```

## Data Models

### Operation Queue Structure

The operation queue is stored as a simple list in the ViewModel state. Operations are processed in the order they were added, but during execution they are reordered by type (Delete → Duplicate → Move) to ensure correct index handling.

```kotlin
// In PageManagerUiState
data class PageManagerUiState(
    // ... existing fields ...
    val batchMode: BatchModeState = BatchModeState()
)
```

### Index Tracking

To handle operations that change page count, we track both:
1. **Original Index**: The page index when the operation was queued
2. **Normalized Index**: The actual index after accounting for previous operations

Example:
```
Initial pages: [0, 1, 2, 3, 4]

Queue: Delete(1), Duplicate(3), Move(4→1)

After Delete(1):  [0, 2, 3, 4]      // Page 1 removed
After Duplicate(3): [0, 2, 3, 3', 4] // Page 3 duplicated
After Move(4→1):  [0, 4, 2, 3, 3']  // Page 4 moved to position 1
```

The normalizer calculates the correct indices by simulating the operations:

```kotlin
fun normalizeIndices(operations: List<PageOperation>, pageCount: Int): List<NormalizedOperation> {
    var currentPageCount = pageCount
    val normalized = mutableListOf<NormalizedOperation>()
    
    // Group by type and process in order: Delete, Duplicate, Move
    val deletes = operations.filterIsInstance<PageOperation.Delete>()
    val duplicates = operations.filterIsInstance<PageOperation.Duplicate>()
    val moves = operations.filterIsInstance<PageOperation.Move>()
    
    // Process deletes
    deletes.forEach { delete ->
        val adjustedIndex = calculateAdjustedIndex(delete.originalPageIndex, normalized)
        normalized.add(NormalizedOperation(delete, adjustedIndex))
        currentPageCount--
    }
    
    // Process duplicates
    duplicates.forEach { duplicate ->
        val adjustedIndex = calculateAdjustedIndex(duplicate.originalPageIndex, normalized)
        normalized.add(NormalizedOperation(duplicate, adjustedIndex))
        currentPageCount++
    }
    
    // Process moves
    moves.forEach { move ->
        val adjustedFromIndex = calculateAdjustedIndex(move.originalPageIndex, normalized)
        val adjustedToIndex = calculateAdjustedIndex(move.targetIndex, normalized)
        normalized.add(NormalizedOperation(move, adjustedFromIndex, adjustedToIndex))
    }
    
    return normalized
}
```

### Visual Feedback Model

Each page thumbnail displays badges for pending operations:

```kotlin
data class PageBadge(
    val type: BadgeType,
    val text: String,
    val color: Color
)

enum class BadgeType {
    DELETE,      // Red badge: "Delete"
    DUPLICATE,   // Blue badge: "Duplicate"
    MOVE         // Purple badge: "Move to X"
}

fun getPageBadges(pageIndex: Int, operations: List<PageOperation>): List<PageBadge> {
    return operations
        .filter { it.originalPageIndex == pageIndex }
        .map { operation ->
            when (operation) {
                is PageOperation.Delete -> PageBadge(
                    BadgeType.DELETE,
                    "Delete",
                    Color.Red
                )
                is PageOperation.Duplicate -> PageBadge(
                    BadgeType.DUPLICATE,
                    "Duplicate",
                    Color.Blue
                )
                is PageOperation.Move -> PageBadge(
                    BadgeType.MOVE,
                    "Move to ${operation.targetIndex + 1}",
                    Color(0xFF9C27B0) // Purple
                )
            }
        }
}
```

### Batch Execution Algorithm

The batch execution follows this algorithm:

```
1. Validate all operations
   - Check page indices are in bounds
   - Check not deleting last page
   - Check no conflicting operations

2. Normalize indices
   - Group operations by type
   - Calculate adjusted indices accounting for previous operations

3. Calculate final page order
   - Start with original page list [0, 1, 2, ..., n-1]
   - Apply deletes: remove pages from list
   - Apply duplicates: insert duplicate pages
   - Apply moves: reorder pages

4. Execute single PDF reconstruction
   - Open source PDF once
   - Create output PDF
   - Copy pages in final order
   - Replace original file

5. Update UI
   - Reload document once
   - Clear operation queue
   - Exit batch mode
```

Example execution:

```kotlin
fun calculateFinalPageOrder(
    originalPageCount: Int,
    normalizedOperations: List<NormalizedOperation>
): List<Int> {
    // Start with original order
    val pages = (0 until originalPageCount).toMutableList()
    
    // Apply deletes
    normalizedOperations
        .filter { it.operation is PageOperation.Delete }
        .sortedByDescending { it.normalizedIndex }  // Delete from end to start
        .forEach { pages.removeAt(it.normalizedIndex) }
    
    // Apply duplicates
    normalizedOperations
        .filter { it.operation is PageOperation.Duplicate }
        .forEach { 
            val sourcePage = pages[it.normalizedIndex]
            pages.add(it.normalizedIndex + 1, sourcePage)
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
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Operation Queueing Correctness

*For any* operation type (Delete, Duplicate, Move) and valid page index, when the operation is queued in batch mode, the operation queue should contain that operation with the correct page index and operation type.

**Validates: Requirements 2.1, 2.2, 2.3**

### Property 2: Queueing Has No Side Effects

*For any* operation queued in batch mode, queueing the operation should not trigger PDF reconstruction, document reload, or any file I/O operations.

**Validates: Requirements 2.4, 2.5**

### Property 3: Badge Display Correctness

*For any* page with pending operations, the badge list for that page should contain exactly one badge for each operation, with the correct badge type (Delete/Duplicate/Move) and color.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

### Property 4: Operation Count Display

*For any* operation queue, the displayed operation count should equal the number of operations in the queue.

**Validates: Requirements 3.5**

### Property 5: Operation Removal

*For any* operation or set of operations in the queue, removing them should result in those operations no longer being present in the queue, and the operation count should decrease accordingly.

**Validates: Requirements 4.3, 4.4**

### Property 6: Batch Execution Processes All Operations

*For any* non-empty operation queue, executing the batch should process all operations in the queue, resulting in the correct final page order.

**Validates: Requirements 5.3**

### Property 7: Post-Execution State

*For any* successful batch execution, the system should exit batch mode, clear the operation queue, and reload the document exactly once.

**Validates: Requirements 5.4, 5.5, 9.5**

### Property 8: Operation Ordering Preservation

*For any* sequence of operations queued, the operations should be stored in the queue in the same order they were added.

**Validates: Requirements 6.1**

### Property 9: Execution Type Ordering

*For any* operation queue containing multiple operation types, when normalized for execution, delete operations should appear before duplicate operations, and duplicate operations should appear before move operations.

**Validates: Requirements 6.2, 6.3**

### Property 10: Index Normalization

*For any* operation queue containing operations that change page count (delete or duplicate), when normalized, subsequent operations should have their indices adjusted to account for the page count changes.

**Validates: Requirements 6.4, 6.5**

### Property 11: Cancellation Behavior

*For any* batch mode state with or without pending operations, cancelling batch mode should clear the operation queue, exit batch mode, and not trigger any PDF reconstruction or file I/O.

**Validates: Requirements 7.3, 7.4, 7.5**

### Property 12: Error Preservation

*For any* batch execution that fails, the original PDF file should remain unmodified, an error message should be displayed, and batch mode should remain active with the operation queue intact.

**Validates: Requirements 8.1, 8.2, 8.3**

### Property 13: Configuration Change Persistence

*For any* batch mode state with pending operations, after a configuration change (screen rotation), the batch mode state, operation queue, and visual feedback should all be restored correctly.

**Validates: Requirements 10.1, 10.2, 10.3**

### Property 14: Temporary File Management

*For any* batch execution, temporary files should be created for input and output, and should be deleted after execution completes (whether successful or failed).

**Validates: Requirements 11.2**

### Property 15: Single Document Open

*For any* batch execution, the source PDF document should be opened exactly once, regardless of the number of operations in the queue.

**Validates: Requirements 11.3**

### Property 16: Atomic File Replacement

*For any* batch execution, the original PDF file should only be replaced if all operations succeed; if any operation fails, the original file should remain unchanged.

**Validates: Requirements 11.5**

### Property 17: Delete Last Page Validation

*For any* document with exactly one page, attempting to queue a delete operation should be rejected with an error message.

**Validates: Requirements 12.1**

### Property 18: Invalid Index Validation

*For any* operation with a page index outside the valid range [0, pageCount), the operation should be rejected with an error message indicating the invalid index.

**Validates: Requirements 12.2**

### Property 19: Pre-Execution Validation

*For any* operation queue, validation before execution should check all page indices are valid and return a list of validation errors for any invalid operations.

**Validates: Requirements 12.3, 12.4**

### Property 20: Final Page Order Calculation

*For any* operation queue and initial page count, calculating the final page order should produce a list where:
- Pages deleted are not present
- Pages duplicated appear twice (original and copy)
- Pages moved appear at their target positions
- All other pages maintain their relative order

**Validates: Requirements 5.3, 6.4, 6.5**

## Error Handling

### Error Categories

1. **Validation Errors**: Detected before execution
   - Invalid page indices
   - Deleting last page
   - Conflicting operations
   - Response: Display error, allow user to fix or cancel

2. **Execution Errors**: Detected during batch execution
   - File I/O errors
   - PDF corruption
   - Out of memory
   - Response: Preserve original file, display error, offer retry or cancel

3. **State Errors**: Detected during state management
   - Configuration change during execution
   - Process death during execution
   - Response: Discard incomplete operations, return to safe state

### Error Recovery Strategy

```kotlin
suspend fun executeBatch(): Result<Unit> {
    return try {
        // Validate operations
        val validation = validateOperations()
        if (!validation.isValid) {
            return Result.failure(ValidationException(validation.errors))
        }
        
        // Create backup reference (original file is preserved by Flattener)
        val backupUri = _uiState.value.documentPath
        
        // Execute batch
        _uiState.update { it.copy(batchMode = it.batchMode.copy(isExecuting = true)) }
        
        val result = pdfAnnotationFlattener.executeBatchOperations(
            sourceUri = Uri.parse(backupUri),
            operations = _uiState.value.batchMode.operations,
            onProgress = { progress, operation ->
                _uiState.update {
                    it.copy(
                        batchMode = it.batchMode.copy(
                            executionProgress = progress,
                            currentOperation = operation
                        )
                    )
                }
            }
        )
        
        when (result) {
            is PageOperationResult.Success -> {
                // Reload document once
                loadDocument(backupUri)
                
                // Clear batch mode
                _uiState.update {
                    it.copy(batchMode = BatchModeState())
                }
                
                Result.success(Unit)
            }
            is PageOperationResult.Error -> {
                // Original file is preserved by Flattener
                _uiState.update {
                    it.copy(
                        batchMode = it.batchMode.copy(
                            isExecuting = false,
                            error = result.message
                        )
                    )
                }
                Result.failure(ExecutionException(result.message))
            }
        }
    } catch (e: Exception) {
        _uiState.update {
            it.copy(
                batchMode = it.batchMode.copy(
                    isExecuting = false,
                    error = e.message ?: "Unknown error"
                )
            )
        }
        Result.failure(e)
    }
}
```

### Rollback Mechanism

The Flattener's batch execution method uses a fail-safe approach:

1. **Copy source to temp file**: Original file is never modified directly
2. **Process operations on temp file**: All modifications happen on the copy
3. **Validate output**: Ensure the output PDF is valid
4. **Replace original**: Only if all steps succeed, replace the original file
5. **Cleanup**: Delete temp files regardless of success or failure

This ensures that if any step fails, the original PDF remains unchanged.

## Testing Strategy

### Dual Testing Approach

This feature requires both unit tests and property-based tests for comprehensive coverage:

**Unit Tests** focus on:
- Specific UI interactions (button clicks, dialog displays)
- Edge cases (empty queue, single page document)
- Error conditions (invalid indices, file I/O errors)
- Integration points (ViewModel ↔ Flattener communication)

**Property-Based Tests** focus on:
- Universal properties across all inputs (operation queueing, index normalization)
- Comprehensive input coverage through randomization (various operation sequences)
- Invariants that must hold (queue ordering, state consistency)

### Property-Based Testing Configuration

**Library**: Use [Kotest Property Testing](https://kotest.io/docs/proptest/property-based-testing.html) for Kotlin

**Configuration**:
- Minimum 100 iterations per property test
- Each test tagged with: `Feature: batch-page-operations, Property N: [property text]`
- Custom generators for PageOperation types
- Shrinking enabled to find minimal failing cases

**Example Property Test**:

```kotlin
class BatchOperationsPropertyTest : StringSpec({
    "Feature: batch-page-operations, Property 1: Operation Queueing Correctness" {
        checkAll(100, Arb.pageOperation(), Arb.int(0..50)) { operation, pageIndex ->
            // Given: Empty operation queue
            val viewModel = createTestViewModel()
            viewModel.enterBatchMode()
            
            // When: Queue operation
            when (operation) {
                is Delete -> viewModel.queueDeleteOperation(pageIndex)
                is Duplicate -> viewModel.queueDuplicateOperation(pageIndex)
                is Move -> viewModel.queueMoveOperation(pageIndex, Arb.int(0..50).next())
            }
            
            // Then: Operation is in queue with correct details
            val queue = viewModel.batchModeState.value.operations
            queue.size shouldBe 1
            queue.first().originalPageIndex shouldBe pageIndex
            queue.first()::class shouldBe operation::class
        }
    }
    
    "Feature: batch-page-operations, Property 10: Index Normalization" {
        checkAll(100, Arb.operationList(1..10), Arb.int(5..20)) { operations, pageCount ->
            // Given: Operation queue with mixed types
            val normalized = OperationValidator.normalizeIndices(operations, pageCount)
            
            // When: Operations include deletes or duplicates
            val hasDeletes = operations.any { it is PageOperation.Delete }
            val hasDuplicates = operations.any { it is PageOperation.Duplicate }
            
            // Then: Subsequent operations have adjusted indices
            if (hasDeletes || hasDuplicates) {
                // Verify that normalized indices differ from original indices
                // for operations after the first delete/duplicate
                val firstChangeIndex = operations.indexOfFirst { 
                    it is PageOperation.Delete || it is PageOperation.Duplicate 
                }
                
                if (firstChangeIndex < operations.size - 1) {
                    // At least one operation after the change
                    val laterOps = normalized.drop(firstChangeIndex + 1)
                    laterOps.any { 
                        it.normalizedIndex != operations[operations.indexOf(it.operation)].originalPageIndex 
                    } shouldBe true
                }
            }
        }
    }
})

// Custom generators
fun Arb.Companion.pageOperation(): Arb<PageOperation> = arbitrary {
    val type = listOf("delete", "duplicate", "move").random()
    val index = Arb.int(0..50).bind()
    when (type) {
        "delete" -> PageOperation.Delete(originalPageIndex = index)
        "duplicate" -> PageOperation.Duplicate(originalPageIndex = index)
        else -> PageOperation.Move(originalPageIndex = index, targetIndex = Arb.int(0..50).bind())
    }
}

fun Arb.Companion.operationList(range: IntRange): Arb<List<PageOperation>> = 
    Arb.list(Arb.pageOperation(), range)
```

### Unit Test Examples

```kotlin
class BatchOperationsUnitTest {
    @Test
    fun `batch edit button displayed when annotation count is zero`() {
        // Given: Document with no annotations
        val viewModel = createTestViewModel(annotationCount = 0)
        
        // When: Observing UI state
        val state = viewModel.uiState.value
        
        // Then: Batch edit button should be enabled
        state.canEnterBatchMode shouldBe true
    }
    
    @Test
    fun `deleting last page is rejected`() {
        // Given: Document with one page
        val viewModel = createTestViewModel(pageCount = 1)
        viewModel.enterBatchMode()
        
        // When: Attempt to delete the only page
        viewModel.queueDeleteOperation(0)
        
        // Then: Operation is rejected with error
        val state = viewModel.batchModeState.value
        state.operations.size shouldBe 0
        state.error shouldContain "Cannot delete the only page"
    }
    
    @Test
    fun `batch execution with file error preserves original`() = runTest {
        // Given: Operation queue with valid operations
        val viewModel = createTestViewModel()
        viewModel.enterBatchMode()
        viewModel.queueDeleteOperation(1)
        
        // Mock flattener to return error
        val mockFlattener = mockk<PdfAnnotationFlattener>()
        coEvery { 
            mockFlattener.executeBatchOperations(any(), any(), any(), any(), any(), any()) 
        } returns PageOperationResult.Error("File I/O error")
        
        // When: Execute batch
        val result = viewModel.executeBatch()
        
        // Then: Original file unchanged, error displayed, batch mode active
        result.isFailure shouldBe true
        viewModel.batchModeState.value.isActive shouldBe true
        viewModel.batchModeState.value.error shouldNotBe null
    }
}
```

### Integration Testing

Integration tests verify the complete flow from UI interaction to PDF modification:

```kotlin
@Test
fun `complete batch workflow - queue, execute, verify`() = runTest {
    // Given: Document with 5 pages
    val testPdf = createTestPdf(pageCount = 5)
    val viewModel = PageManagerViewModel(context, pdfRenderer, flattener, templateRepo)
    viewModel.loadDocument(testPdf.toString())
    
    // When: Enter batch mode and queue operations
    viewModel.enterBatchMode()
    viewModel.queueDeleteOperation(1)      // Delete page 2
    viewModel.queueDuplicateOperation(3)   // Duplicate page 4
    viewModel.queueMoveOperation(4, 0)     // Move page 5 to start
    
    // Execute batch
    val result = viewModel.executeBatch()
    
    // Then: Operations applied correctly
    result.isSuccess shouldBe true
    viewModel.uiState.value.pages.size shouldBe 5  // 5 - 1 + 1 = 5 pages
    viewModel.batchModeState.value.isActive shouldBe false
    viewModel.batchModeState.value.operations.isEmpty() shouldBe true
    
    // Verify final page order
    // Original: [0, 1, 2, 3, 4]
    // After delete(1): [0, 2, 3, 4]
    // After duplicate(3): [0, 2, 3, 3', 4]
    // After move(4→0): [4, 0, 2, 3, 3']
    val finalOrder = viewModel.uiState.value.pages.map { it.index }
    finalOrder shouldBe listOf(4, 0, 2, 3, 3)
}
```

### Test Coverage Goals

- **Unit Tests**: 80% code coverage minimum
- **Property Tests**: All 20 correctness properties implemented
- **Integration Tests**: All major workflows covered
- **UI Tests**: Critical user interactions verified

### Testing Tools

- **Unit Testing**: JUnit 5, MockK for mocking
- **Property Testing**: Kotest Property Testing
- **UI Testing**: Compose Testing, Espresso
- **Integration Testing**: AndroidX Test, Robolectric
