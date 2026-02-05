package com.osnotes.app.ui.viewmodels

import android.content.Context
import android.net.Uri
import com.osnotes.app.data.pdf.MuPdfRenderer
import com.osnotes.app.data.pdf.PdfAnnotationFlattener
import com.osnotes.app.data.repository.CustomTemplateRepository
import com.osnotes.app.domain.model.BatchModeState
import com.osnotes.app.domain.model.PageOperation
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PageManagerViewModel batch mode activation and deactivation.
 * Tests Requirements 1.2, 1.3, 7.1, 7.3
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PageManagerViewModelBatchModeTest {
    
    private lateinit var viewModel: PageManagerViewModel
    private lateinit var mockContext: Context
    private lateinit var mockPdfRenderer: MuPdfRenderer
    private lateinit var mockFlattener: PdfAnnotationFlattener
    private lateinit var mockTemplateRepository: CustomTemplateRepository
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Create mocks
        mockContext = mockk(relaxed = true)
        mockPdfRenderer = mockk(relaxed = true)
        mockFlattener = mockk(relaxed = true)
        mockTemplateRepository = mockk(relaxed = true)
        
        // Setup template repository to return empty flow
        every { mockTemplateRepository.customTemplates } returns flowOf(emptyList())
        
        // Create ViewModel
        viewModel = PageManagerViewModel(
            context = mockContext,
            pdfRenderer = mockPdfRenderer,
            pdfAnnotationFlattener = mockFlattener,
            customTemplateRepository = mockTemplateRepository
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `enterBatchMode activates batch mode`() {
        // Given: ViewModel with batch mode inactive
        assertFalse("Batch mode should be inactive initially", viewModel.isBatchModeActive())
        
        // When: Enter batch mode
        viewModel.enterBatchMode()
        
        // Then: Batch mode should be active
        assertTrue("Batch mode should be active after enterBatchMode()", viewModel.isBatchModeActive())
        assertTrue("UI state batchMode.isActive should be true", viewModel.uiState.value.batchMode.isActive)
    }
    
    @Test
    fun `exitBatchMode deactivates batch mode`() {
        // Given: ViewModel in batch mode
        viewModel.enterBatchMode()
        assertTrue("Batch mode should be active", viewModel.isBatchModeActive())
        
        // When: Exit batch mode
        viewModel.exitBatchMode()
        
        // Then: Batch mode should be inactive
        assertFalse("Batch mode should be inactive after exitBatchMode()", viewModel.isBatchModeActive())
        assertFalse("UI state batchMode.isActive should be false", viewModel.uiState.value.batchMode.isActive)
    }
    
    @Test
    fun `exitBatchMode clears all operations`() {
        // Given: ViewModel in batch mode with operations
        viewModel.enterBatchMode()
        
        // Manually add operations to the state (simulating queued operations)
        val operations = listOf(
            PageOperation.Delete(originalPageIndex = 0),
            PageOperation.Duplicate(originalPageIndex = 1)
        )
        viewModel.uiState.value.batchMode.copy(operations = operations)
        
        // When: Exit batch mode
        viewModel.exitBatchMode()
        
        // Then: Operations should be cleared
        assertTrue("Operations should be empty after exitBatchMode()", viewModel.uiState.value.batchMode.operations.isEmpty())
        assertEquals("Operation count should be 0", 0, viewModel.uiState.value.batchMode.operationCount)
    }
    
    @Test
    fun `exitBatchMode resets batch mode state to default`() {
        // Given: ViewModel in batch mode with custom state
        viewModel.enterBatchMode()
        
        // When: Exit batch mode
        viewModel.exitBatchMode()
        
        // Then: Batch mode state should be reset to default
        val batchMode = viewModel.uiState.value.batchMode
        assertFalse("isActive should be false", batchMode.isActive)
        assertTrue("operations should be empty", batchMode.operations.isEmpty())
        assertFalse("isExecuting should be false", batchMode.isExecuting)
        assertEquals("executionProgress should be 0f", 0f, batchMode.executionProgress, 0.001f)
        assertEquals("currentOperation should be empty", "", batchMode.currentOperation)
        assertNull("error should be null", batchMode.error)
    }
    
    @Test
    fun `isBatchModeActive returns false initially`() {
        // Given: Newly created ViewModel
        // (setup already creates the ViewModel)
        
        // Then: Batch mode should be inactive
        assertFalse("Batch mode should be inactive initially", viewModel.isBatchModeActive())
    }
    
    @Test
    fun `isBatchModeActive returns true after entering batch mode`() {
        // Given: ViewModel with batch mode inactive
        assertFalse("Batch mode should be inactive initially", viewModel.isBatchModeActive())
        
        // When: Enter batch mode
        viewModel.enterBatchMode()
        
        // Then: isBatchModeActive should return true
        assertTrue("isBatchModeActive() should return true", viewModel.isBatchModeActive())
    }
    
    @Test
    fun `isBatchModeActive returns false after exiting batch mode`() {
        // Given: ViewModel in batch mode
        viewModel.enterBatchMode()
        assertTrue("Batch mode should be active", viewModel.isBatchModeActive())
        
        // When: Exit batch mode
        viewModel.exitBatchMode()
        
        // Then: isBatchModeActive should return false
        assertFalse("isBatchModeActive() should return false", viewModel.isBatchModeActive())
    }
    
    @Test
    fun `enterBatchMode can be called multiple times`() {
        // Given: ViewModel with batch mode inactive
        assertFalse("Batch mode should be inactive initially", viewModel.isBatchModeActive())
        
        // When: Enter batch mode multiple times
        viewModel.enterBatchMode()
        viewModel.enterBatchMode()
        viewModel.enterBatchMode()
        
        // Then: Batch mode should still be active
        assertTrue("Batch mode should be active", viewModel.isBatchModeActive())
    }
    
    @Test
    fun `exitBatchMode can be called multiple times`() {
        // Given: ViewModel in batch mode
        viewModel.enterBatchMode()
        assertTrue("Batch mode should be active", viewModel.isBatchModeActive())
        
        // When: Exit batch mode multiple times
        viewModel.exitBatchMode()
        viewModel.exitBatchMode()
        viewModel.exitBatchMode()
        
        // Then: Batch mode should still be inactive
        assertFalse("Batch mode should be inactive", viewModel.isBatchModeActive())
    }
    
    @Test
    fun `enterBatchMode preserves other UI state fields`() {
        // Given: ViewModel with custom UI state
        // Note: We can't directly set the state, but we can verify it doesn't change
        val initialDocumentPath = viewModel.uiState.value.documentPath
        val initialPages = viewModel.uiState.value.pages
        val initialSelectedPages = viewModel.uiState.value.selectedPages
        
        // When: Enter batch mode
        viewModel.enterBatchMode()
        
        // Then: Other UI state fields should be preserved
        assertEquals("documentPath should be preserved", initialDocumentPath, viewModel.uiState.value.documentPath)
        assertEquals("pages should be preserved", initialPages, viewModel.uiState.value.pages)
        assertEquals("selectedPages should be preserved", initialSelectedPages, viewModel.uiState.value.selectedPages)
    }
    
    @Test
    fun `exitBatchMode preserves other UI state fields`() {
        // Given: ViewModel in batch mode with custom UI state
        viewModel.enterBatchMode()
        val initialDocumentPath = viewModel.uiState.value.documentPath
        val initialPages = viewModel.uiState.value.pages
        val initialSelectedPages = viewModel.uiState.value.selectedPages
        
        // When: Exit batch mode
        viewModel.exitBatchMode()
        
        // Then: Other UI state fields should be preserved
        assertEquals("documentPath should be preserved", initialDocumentPath, viewModel.uiState.value.documentPath)
        assertEquals("pages should be preserved", initialPages, viewModel.uiState.value.pages)
        assertEquals("selectedPages should be preserved", initialSelectedPages, viewModel.uiState.value.selectedPages)
    }
}
