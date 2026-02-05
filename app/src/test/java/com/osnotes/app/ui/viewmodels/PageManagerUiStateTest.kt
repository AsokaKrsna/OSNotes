package com.osnotes.app.ui.viewmodels

import com.osnotes.app.domain.model.BatchModeState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PageManagerUiState initialization and batch mode state.
 * Tests Requirements 1.2, 2.1, 2.2, 2.3
 */
class PageManagerUiStateTest {
    
    @Test
    fun `PageManagerUiState initializes with default BatchModeState`() {
        // Given: Create a new PageManagerUiState with default values
        val uiState = PageManagerUiState()
        
        // Then: batchMode should be initialized with default BatchModeState
        assertNotNull("batchMode should not be null", uiState.batchMode)
        assertFalse("batchMode.isActive should be false by default", uiState.batchMode.isActive)
        assertTrue("batchMode.operations should be empty by default", uiState.batchMode.operations.isEmpty())
        assertFalse("batchMode.isExecuting should be false by default", uiState.batchMode.isExecuting)
        assertEquals("batchMode.executionProgress should be 0f by default", 0f, uiState.batchMode.executionProgress, 0.001f)
        assertEquals("batchMode.currentOperation should be empty by default", "", uiState.batchMode.currentOperation)
        assertNull("batchMode.error should be null by default", uiState.batchMode.error)
    }
    
    @Test
    fun `PageManagerUiState can be created with custom BatchModeState`() {
        // Given: Create a custom BatchModeState
        val customBatchMode = BatchModeState(
            isActive = true,
            operations = emptyList(),
            isExecuting = false,
            executionProgress = 0.5f,
            currentOperation = "Test operation",
            error = "Test error"
        )
        
        // When: Create PageManagerUiState with custom batchMode
        val uiState = PageManagerUiState(batchMode = customBatchMode)
        
        // Then: batchMode should have the custom values
        assertTrue("batchMode.isActive should be true", uiState.batchMode.isActive)
        assertEquals("batchMode.executionProgress should be 0.5f", 0.5f, uiState.batchMode.executionProgress, 0.001f)
        assertEquals("batchMode.currentOperation should be 'Test operation'", "Test operation", uiState.batchMode.currentOperation)
        assertEquals("batchMode.error should be 'Test error'", "Test error", uiState.batchMode.error)
    }
    
    @Test
    fun `PageManagerUiState copy preserves batchMode`() {
        // Given: Create a PageManagerUiState with custom batchMode
        val originalBatchMode = BatchModeState(isActive = true)
        val originalState = PageManagerUiState(batchMode = originalBatchMode)
        
        // When: Copy the state with a different field
        val copiedState = originalState.copy(documentPath = "/test/path")
        
        // Then: batchMode should be preserved
        assertTrue("Copied state should preserve batchMode.isActive", copiedState.batchMode.isActive)
        assertEquals("Copied state should have updated documentPath", "/test/path", copiedState.documentPath)
    }
    
    @Test
    fun `PageManagerUiState copy can update batchMode`() {
        // Given: Create a PageManagerUiState with default batchMode
        val originalState = PageManagerUiState()
        
        // When: Copy the state with a new batchMode
        val newBatchMode = BatchModeState(isActive = true, operations = emptyList())
        val copiedState = originalState.copy(batchMode = newBatchMode)
        
        // Then: batchMode should be updated
        assertTrue("Copied state should have updated batchMode.isActive", copiedState.batchMode.isActive)
        assertFalse("Original state should still have default batchMode.isActive", originalState.batchMode.isActive)
    }
}
