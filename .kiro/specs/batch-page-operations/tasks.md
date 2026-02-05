# Implementation Plan: Batch Page Operations

## Overview

This implementation plan breaks down the batch page operations feature into discrete, incremental coding tasks. Each task builds on previous work and includes validation through tests. The implementation follows a bottom-up approach: data models → business logic → UI integration → testing.

## Tasks

- [x] 1. Create data models and sealed classes for page operations
  - Create `PageOperation` sealed class with `Delete`, `Duplicate`, and `Move` subclasses
  - Create `BatchModeState` data class to hold batch mode state
  - Create `ValidationResult` and `ValidationError` data classes
  - Create `NormalizedOperation` data class for index-adjusted operations
  - Add `PageBadge` and `BadgeType` for visual feedback
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

- [x] 1.1 Write property test for operation data models
  - **Property 1: Operation Queueing Correctness**
  - **Validates: Requirements 2.1, 2.2, 2.3**

- [ ] 2. Implement operation validator and index normalizer
  - [x] 2.1 Create `OperationValidator` object with validation logic
    - Implement `validate()` method to check operation validity
    - Check for invalid page indices
    - Check for deleting last page
    - Check for conflicting operations
    - _Requirements: 12.1, 12.2, 12.3, 12.4_
  
  - [x] 2.2 Implement index normalization logic
    - Implement `normalizeIndices()` method
    - Group operations by type (Delete, Duplicate, Move)
    - Calculate adjusted indices for each operation
    - Handle page count changes from deletes and duplicates
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_
  
  - [x] 2.3 Implement final page order calculation
    - Implement `calculateFinalPageOrder()` helper method
    - Apply deletes to page list
    - Apply duplicates to page list
    - Apply moves to page list
    - Return final page order
    - _Requirements: 5.3, 6.4, 6.5_

- [x] 2.4 Write property tests for validation and normalization
  - **Property 10: Index Normalization**
  - **Property 17: Delete Last Page Validation**
  - **Property 18: Invalid Index Validation**
  - **Property 19: Pre-Execution Validation**
  - **Property 20: Final Page Order Calculation**
  - **Validates: Requirements 6.4, 6.5, 12.1, 12.2, 12.3, 12.4**

- [ ] 3. Extend PageManagerViewModel with batch mode state
  - [x] 3.1 Add batch mode state to PageManagerUiState
    - Add `batchMode: BatchModeState` field to `PageManagerUiState`
    - Update state initialization
    - _Requirements: 1.2, 2.1, 2.2, 2.3_
  
  - [ ] 3.2 Implement batch mode activation and deactivation
    - Implement `enterBatchMode()` method
    - Implement `exitBatchMode()` method
    - Implement `isBatchModeActive()` method
    - Update UI state when entering/exiting batch mode
    - _Requirements: 1.2, 1.3, 7.1, 7.3_
  
  - [ ] 3.3 Implement operation queueing methods
    - Implement `queueDeleteOperation(pageIndex: Int)`
    - Implement `queueDuplicateOperation(pageIndex: Int)`
    - Implement `queueMoveOperation(fromIndex: Int, toIndex: Int)`
    - Validate operations before queueing
    - Update batch mode state with new operations
    - _Requirements: 2.1, 2.2, 2.3, 12.1, 12.2_

- [ ] 3.4 Write property tests for operation queueing
  - **Property 1: Operation Queueing Correctness**
  - **Property 2: Queueing Has No Side Effects**
  - **Property 8: Operation Ordering Preservation**
  - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 6.1**

- [ ] 4. Implement operation management in ViewModel
  - [ ] 4.1 Implement operation removal
    - Implement `removeOperation(operationId: String)`
    - Update batch mode state
    - Update visual feedback
    - _Requirements: 4.3_
  
  - [ ] 4.2 Implement clear all operations
    - Implement `clearAllOperations()`
    - Clear operation queue
    - Update batch mode state
    - _Requirements: 4.4_
  
  - [ ] 4.3 Implement operation query methods
    - Implement `getOperationsForPage(pageIndex: Int)`
    - Return list of operations affecting a specific page
    - _Requirements: 3.4, 4.1_

- [ ] 4.4 Write property tests for operation management
  - **Property 5: Operation Removal**
  - **Validates: Requirements 4.3, 4.4**

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement batch execution in PdfAnnotationFlattener
  - [ ] 6.1 Create batch execution method signature
    - Add `executeBatchOperations()` method to `PdfAnnotationFlattener`
    - Accept list of operations, source URI, annotations, progress callback
    - Return `PageOperationResult`
    - _Requirements: 11.1_
  
  - [ ] 6.2 Implement batch execution algorithm
    - Validate operations using `OperationValidator`
    - Normalize indices using `OperationValidator.normalizeIndices()`
    - Calculate final page order using `calculateFinalPageOrder()`
    - Create temp input and output files
    - Open source PDF document once
    - Copy pages in final order to output PDF
    - Replace original file only if all operations succeed
    - Clean up temp files
    - Report progress via callback
    - _Requirements: 5.3, 11.2, 11.3, 11.4, 11.5_
  
  - [ ] 6.3 Implement error handling and rollback
    - Wrap execution in try-catch
    - Preserve original file on error
    - Clean up temp files on error
    - Return error result with message
    - _Requirements: 8.1, 8.2_

- [ ] 6.4 Write property tests for batch execution
  - **Property 6: Batch Execution Processes All Operations**
  - **Property 9: Execution Type Ordering**
  - **Property 12: Error Preservation**
  - **Property 14: Temporary File Management**
  - **Property 15: Single Document Open**
  - **Property 16: Atomic File Replacement**
  - **Validates: Requirements 5.3, 6.2, 6.3, 8.1, 8.2, 11.2, 11.3, 11.5**

- [ ] 7. Implement batch execution in ViewModel
  - [ ] 7.1 Implement executeBatch method
    - Validate operations before execution
    - Update state to show execution in progress
    - Call `pdfAnnotationFlattener.executeBatchOperations()`
    - Handle progress updates
    - Handle success: reload document, clear queue, exit batch mode
    - Handle failure: preserve state, display error
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 8.2, 8.3, 9.3, 9.4_
  
  - [ ] 7.2 Implement cancellation logic
    - Implement `cancelBatchMode()`
    - Clear operation queue
    - Exit batch mode
    - Ensure no PDF reconstruction occurs
    - _Requirements: 7.3, 7.4, 7.5_

- [ ] 7.3 Write property tests for ViewModel batch execution
  - **Property 7: Post-Execution State**
  - **Property 11: Cancellation Behavior**
  - **Validates: Requirements 5.4, 5.5, 7.3, 7.4, 7.5, 9.5**

- [ ] 8. Implement state persistence for configuration changes
  - [ ] 8.1 Add SavedStateHandle support to ViewModel
    - Save batch mode state to SavedStateHandle
    - Save operation queue to SavedStateHandle
    - Restore state in ViewModel init
    - _Requirements: 10.1, 10.2_
  
  - [ ] 8.2 Implement state serialization
    - Make `PageOperation` and `BatchModeState` Parcelable
    - Serialize/deserialize operation queue
    - Handle process death (discard state)
    - _Requirements: 10.1, 10.2, 10.4, 10.5_

- [ ] 8.3 Write property tests for state persistence
  - **Property 13: Configuration Change Persistence**
  - **Validates: Requirements 10.1, 10.2, 10.3**

- [ ] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Implement batch mode UI in PageManagerScreen
  - [ ] 10.1 Add batch mode activation UI
    - Add "Batch Edit" button to top bar
    - Enable button only when annotation count is 0
    - Show warning when annotation count > 0
    - Handle button click to enter batch mode
    - _Requirements: 1.1, 1.2, 1.4_
  
  - [ ] 10.2 Add batch mode indicator and controls
    - Add batch mode header with operation count
    - Add "Apply Changes" button (enabled when operations exist)
    - Add "Cancel" button
    - Add "Clear All" button
    - Disable back navigation while in batch mode
    - _Requirements: 1.3, 3.5, 4.4, 5.1, 7.1_
  
  - [ ] 10.3 Implement operation badges on page thumbnails
    - Create `PageBadge` composable
    - Display delete badge (red) for delete operations
    - Display duplicate badge (blue) for duplicate operations
    - Display move badge (purple) for move operations
    - Support multiple badges per page
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [ ] 10.4 Update page context menu for batch mode
    - Modify context menu to queue operations instead of executing immediately
    - Show operation queueing feedback
    - Handle validation errors (display toast/snackbar)
    - _Requirements: 2.1, 2.2, 2.3, 12.1, 12.2_

- [ ] 10.5 Write UI tests for batch mode interface
  - Test batch edit button visibility and enablement
  - Test entering and exiting batch mode
  - Test operation badges display correctly
  - Test context menu queues operations

- [ ] 11. Implement confirmation dialogs
  - [ ] 11.1 Create apply changes confirmation dialog
    - Show dialog when "Apply Changes" is tapped
    - Display summary of all operations
    - Show operation count by type
    - Handle confirm: execute batch
    - Handle cancel: return to batch mode
    - _Requirements: 5.2_
  
  - [ ] 11.2 Create cancel confirmation dialog
    - Show dialog when "Cancel" is tapped with pending operations
    - Warn about discarding changes
    - Handle confirm: clear queue and exit batch mode
    - Handle cancel: return to batch mode
    - Skip dialog if no pending operations
    - _Requirements: 7.2, 7.5_
  
  - [ ] 11.3 Create clear all confirmation dialog
    - Show dialog when "Clear All" is tapped
    - Warn about removing all operations
    - Handle confirm: clear operation queue
    - Handle cancel: return to batch mode
    - _Requirements: 4.5_
  
  - [ ] 11.4 Create operations detail dialog
    - Show dialog when tapping page with pending operations
    - List all operations for that page
    - Provide "Remove" button for each operation
    - Handle operation removal
    - _Requirements: 4.1, 4.2_

- [ ] 11.5 Write UI tests for dialogs
  - Test confirmation dialogs appear correctly
  - Test dialog actions work as expected
  - Test operations detail dialog shows correct operations

- [ ] 12. Implement execution progress UI
  - [ ] 12.1 Create progress dialog/overlay
    - Show progress indicator during batch execution
    - Display current operation being processed
    - Display progress percentage
    - Prevent user interaction during execution
    - _Requirements: 9.3, 9.4_
  
  - [ ] 12.2 Implement error display
    - Show error dialog when batch execution fails
    - Display error message with details
    - Provide "Retry" button
    - Provide "Cancel" button to exit batch mode
    - _Requirements: 8.2, 8.4, 8.5_

- [ ] 12.3 Write UI tests for progress and error states
  - Test progress dialog displays during execution
  - Test error dialog displays on failure
  - Test retry and cancel actions work correctly

- [ ] 13. Integration and final testing
  - [ ] 13.1 Test complete batch workflow
    - Test entering batch mode
    - Test queueing multiple operations
    - Test visual feedback for all operation types
    - Test executing batch successfully
    - Test document reload after execution
    - Test exiting batch mode
    - _Requirements: All_
  
  - [ ] 13.2 Test error scenarios
    - Test validation errors (invalid indices, last page delete)
    - Test execution errors (file I/O errors)
    - Test error recovery (retry, cancel)
    - Test original file preservation on error
    - _Requirements: 8.1, 8.2, 8.3, 12.1, 12.2, 12.3, 12.4_
  
  - [ ] 13.3 Test edge cases
    - Test batch mode with single page document
    - Test batch mode with large documents (50+ pages)
    - Test batch mode with many operations (20+ operations)
    - Test configuration changes during batch mode
    - Test process death during batch mode
    - _Requirements: 9.1, 9.2, 10.1, 10.2, 10.4, 10.5_

- [ ] 13.4 Write integration tests
  - Test complete workflow from UI to PDF modification
  - Test all operation types in combination
  - Test error handling end-to-end

- [ ] 14. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end workflows
- The implementation follows a bottom-up approach: data models → business logic → UI
- Batch execution is implemented in the existing `PdfAnnotationFlattener` class
- State management uses existing `PageManagerViewModel` patterns
- UI components extend existing `PageManagerScreen` composables
