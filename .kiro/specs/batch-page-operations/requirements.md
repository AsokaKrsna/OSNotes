# Requirements Document

## Introduction

This document specifies the requirements for a batch mode page manipulation feature for the OSNotes Android PDF note-taking application. The feature addresses critical performance and usability issues in the current page management system by allowing users to queue multiple page operations (delete, duplicate, move) and execute them in a single batch, eliminating the need for repeated PDF reconstructions and document reloads that cause infinite loading issues.

## Glossary

- **Page_Manager**: The screen component that displays PDF page thumbnails and provides page manipulation controls
- **Batch_Mode**: An editing state where page operations are queued without immediate execution
- **Operation_Queue**: A data structure storing pending page operations to be executed together
- **Page_Operation**: An action that modifies the PDF document structure (delete, duplicate, or move a page)
- **PDF_Reconstruction**: The process of creating a new PDF file with modified page structure
- **Flattener**: The PdfAnnotationFlattener service that handles PDF page manipulation operations
- **Annotation_Count**: The number of non-flattened annotations in the document (must be 0 for page operations)
- **Batch_Execution**: The process of applying all queued operations in a single pass

## Requirements

### Requirement 1: Batch Mode Activation

**User Story:** As a user, I want to enter a batch editing mode for page operations, so that I can queue multiple changes without triggering immediate PDF modifications.

#### Acceptance Criteria

1. WHEN the user has a document with annotation count equal to 0, THE Page_Manager SHALL display a "Batch Edit" button
2. WHEN the user taps the "Batch Edit" button, THE Page_Manager SHALL enter Batch_Mode and display batch mode UI controls
3. WHILE in Batch_Mode, THE Page_Manager SHALL disable navigation back to the editor until changes are applied or cancelled
4. WHEN annotation count is greater than 0, THE Page_Manager SHALL disable the "Batch Edit" button and display a warning message

### Requirement 2: Operation Queueing

**User Story:** As a user, I want to queue multiple page operations without immediate execution, so that I can plan my changes before applying them.

#### Acceptance Criteria

1. WHILE in Batch_Mode, WHEN the user selects delete on a page, THE Operation_Queue SHALL add a delete operation for that page index
2. WHILE in Batch_Mode, WHEN the user selects duplicate on a page, THE Operation_Queue SHALL add a duplicate operation for that page index
3. WHILE in Batch_Mode, WHEN the user selects reorder on a page, THE Operation_Queue SHALL add a move operation with source and target indices
4. WHEN an operation is queued, THE Page_Manager SHALL NOT trigger PDF_Reconstruction
5. WHEN an operation is queued, THE Page_Manager SHALL NOT reload the document

### Requirement 3: Visual Feedback for Pending Operations

**User Story:** As a user, I want to see which pages have pending operations, so that I can understand what changes will be applied.

#### Acceptance Criteria

1. WHEN a page has a pending delete operation, THE Page_Manager SHALL display a red "Delete" badge on that page thumbnail
2. WHEN a page has a pending duplicate operation, THE Page_Manager SHALL display a blue "Duplicate" badge on that page thumbnail
3. WHEN a page has a pending move operation, THE Page_Manager SHALL display a purple "Move to X" badge on that page thumbnail
4. WHEN multiple operations affect the same page, THE Page_Manager SHALL display all relevant badges
5. WHILE in Batch_Mode, THE Page_Manager SHALL display a header showing the total count of pending operations

### Requirement 4: Operation Management

**User Story:** As a user, I want to review and modify my queued operations, so that I can correct mistakes before applying changes.

#### Acceptance Criteria

1. WHILE in Batch_Mode, WHEN the user taps on a page with pending operations, THE Page_Manager SHALL display a dialog listing all operations for that page
2. WHEN viewing the operations dialog, THE Page_Manager SHALL provide a "Remove" button for each operation
3. WHEN the user removes an operation from the queue, THE Operation_Queue SHALL delete that operation and update visual feedback
4. WHILE in Batch_Mode, THE Page_Manager SHALL provide a "Clear All" button that removes all queued operations
5. WHEN the user taps "Clear All", THE Page_Manager SHALL display a confirmation dialog before clearing the Operation_Queue

### Requirement 5: Batch Execution

**User Story:** As a user, I want to apply all queued operations in a single action, so that I can efficiently modify my document without repeated reloads.

#### Acceptance Criteria

1. WHILE in Batch_Mode with pending operations, THE Page_Manager SHALL display an "Apply Changes" button
2. WHEN the user taps "Apply Changes", THE Page_Manager SHALL display a confirmation dialog with a summary of all operations
3. WHEN the user confirms batch execution, THE Flattener SHALL execute all operations in the Operation_Queue in a single PDF_Reconstruction
4. WHEN batch execution completes successfully, THE Page_Manager SHALL reload the document once and exit Batch_Mode
5. WHEN batch execution completes successfully, THE Page_Manager SHALL clear the Operation_Queue

### Requirement 6: Operation Ordering and Index Management

**User Story:** As a developer, I want the system to correctly handle page indices as operations are queued, so that operations execute in the correct order with correct target pages.

#### Acceptance Criteria

1. WHEN operations are queued, THE Operation_Queue SHALL store operations in the order they were added
2. WHEN executing the batch, THE Flattener SHALL process delete operations before duplicate operations
3. WHEN executing the batch, THE Flattener SHALL process duplicate operations before move operations
4. WHEN a delete operation is queued for page N, THE Operation_Queue SHALL adjust indices for subsequent operations that reference pages after N
5. WHEN a duplicate operation is queued for page N, THE Operation_Queue SHALL adjust indices for subsequent operations that reference pages after N

### Requirement 7: Batch Cancellation

**User Story:** As a user, I want to cancel batch mode and discard all queued operations, so that I can exit without applying changes.

#### Acceptance Criteria

1. WHILE in Batch_Mode, THE Page_Manager SHALL display a "Cancel" button
2. WHEN the user taps "Cancel" with pending operations, THE Page_Manager SHALL display a confirmation dialog
3. WHEN the user confirms cancellation, THE Page_Manager SHALL clear the Operation_Queue and exit Batch_Mode
4. WHEN the user confirms cancellation, THE Page_Manager SHALL NOT trigger any PDF_Reconstruction
5. WHEN the user taps "Cancel" with no pending operations, THE Page_Manager SHALL exit Batch_Mode immediately without confirmation

### Requirement 8: Error Handling and Rollback

**User Story:** As a user, I want the system to handle errors gracefully during batch execution, so that my document is not corrupted if something goes wrong.

#### Acceptance Criteria

1. IF batch execution fails, THEN THE Flattener SHALL preserve the original PDF file without modifications
2. IF batch execution fails, THEN THE Page_Manager SHALL display an error message with details
3. IF batch execution fails, THEN THE Page_Manager SHALL remain in Batch_Mode with the Operation_Queue intact
4. WHEN batch execution fails, THE Page_Manager SHALL provide a "Retry" option to attempt execution again
5. WHEN batch execution fails, THE Page_Manager SHALL provide a "Cancel" option to discard operations and exit Batch_Mode

### Requirement 9: Performance Requirements

**User Story:** As a user, I want batch operations to complete quickly, so that I can efficiently manage my document pages.

#### Acceptance Criteria

1. WHEN executing a batch with 10 or fewer operations, THE Flattener SHALL complete within 5 seconds
2. WHEN executing a batch with 20 or fewer operations, THE Flattener SHALL complete within 10 seconds
3. WHILE batch execution is in progress, THE Page_Manager SHALL display a progress indicator
4. WHILE batch execution is in progress, THE Page_Manager SHALL display the current operation being processed
5. WHEN batch execution completes, THE Page_Manager SHALL reload the document only once

### Requirement 10: State Persistence

**User Story:** As a user, I want my queued operations to survive configuration changes, so that I don't lose my work when the screen rotates.

#### Acceptance Criteria

1. WHEN a configuration change occurs while in Batch_Mode, THE Operation_Queue SHALL persist all queued operations
2. WHEN the Page_Manager is recreated after configuration change, THE Page_Manager SHALL restore Batch_Mode state
3. WHEN the Page_Manager is recreated after configuration change, THE Page_Manager SHALL restore all visual feedback for pending operations
4. IF the process is killed while in Batch_Mode, THEN THE Page_Manager SHALL discard the Operation_Queue on restart
5. WHEN the Page_Manager restarts after process death, THE Page_Manager SHALL NOT be in Batch_Mode

### Requirement 11: Batch Operation Execution

**User Story:** As a developer, I want a single method to execute all queued operations efficiently, so that PDF reconstruction happens only once.

#### Acceptance Criteria

1. THE Flattener SHALL provide a batch execution method that accepts a list of operations
2. WHEN executing a batch, THE Flattener SHALL create temporary files for input and output
3. WHEN executing a batch, THE Flattener SHALL open the source PDF document once
4. WHEN executing a batch, THE Flattener SHALL process all operations in a single pass through the document
5. WHEN executing a batch, THE Flattener SHALL replace the original file only after all operations succeed

### Requirement 12: Operation Validation

**User Story:** As a user, I want the system to validate operations before execution, so that I receive clear feedback about invalid operations.

#### Acceptance Criteria

1. WHEN the user attempts to queue a delete operation on the last remaining page, THE Page_Manager SHALL reject the operation and display an error
2. WHEN the user attempts to queue an operation with an invalid page index, THE Page_Manager SHALL reject the operation and display an error
3. WHEN validating the Operation_Queue before execution, THE Page_Manager SHALL check that all page indices are valid
4. WHEN validation fails, THE Page_Manager SHALL display which operations are invalid and why
5. WHEN validation fails, THE Page_Manager SHALL allow the user to remove invalid operations or cancel batch mode
