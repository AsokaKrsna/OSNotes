package com.osnotes.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.osnotes.app.ui.screens.HomeScreen
import com.osnotes.app.ui.screens.FolderScreen
import com.osnotes.app.ui.screens.EditorScreen
import com.osnotes.app.ui.screens.SettingsScreen
import com.osnotes.app.ui.screens.PageManagerScreen
import com.osnotes.app.ui.screens.TemplateCreatorScreen
import com.osnotes.app.ui.screens.TemplateManagerScreen
import java.net.URLDecoder

/**
 * Main navigation graph for OSNotes.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = Routes.HOME
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Home Screen
        composable(Routes.HOME) {
            HomeScreen(
                onFolderClick = { path ->
                    navController.navigate(Routes.folder(path))
                },
                onNoteClick = { filePath ->
                    navController.navigate(Routes.editor(filePath))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        
        // Folder View
        composable(
            route = Routes.FOLDER,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            
            FolderScreen(
                folderPath = path,
                onBack = { navController.popBackStack() },
                onNoteClick = { filePath ->
                    navController.navigate(Routes.editor(filePath))
                },
                onSubfolderClick = { subPath ->
                    navController.navigate(Routes.folder(subPath))
                }
            )
        }
        
        // Note Editor
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
            val filePath = URLDecoder.decode(encodedPath, "UTF-8")
            
            // Track reload trigger
            var reloadTrigger by remember { mutableStateOf(0) }
            
            // Reload when returning from page manager
            LaunchedEffect(navController.currentBackStackEntry) {
                val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
                val shouldReload = savedStateHandle?.get<Boolean>("reload_document") ?: false
                if (shouldReload) {
                    reloadTrigger++
                    savedStateHandle?.remove<Boolean>("reload_document")
                }
            }
            
            // Use reloadTrigger as key to force recomposition
            key(reloadTrigger) {
                EditorScreen(
                    filePath = filePath,
                    onBack = { navController.popBackStack() },
                    onOpenPageManager = { annotationCount ->
                        navController.navigate(Routes.pageManager(filePath, annotationCount))
                    }
                )
            }
        }
        
        // Settings
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onTemplateBuilderClick = {
                    navController.navigate(Routes.templateCreator())
                },
                onTemplateManagerClick = {
                    navController.navigate(Routes.TEMPLATE_MANAGER)
                }
            )
        }
        
        composable(
            route = Routes.PAGE_MANAGER,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType },
                navArgument("annotationCount") { 
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
            val filePath = URLDecoder.decode(encodedPath, "UTF-8")
            val annotationCount = backStackEntry.arguments?.getInt("annotationCount") ?: 0
            
            PageManagerScreen(
                documentPath = filePath,
                annotationCount = annotationCount,
                onBack = { navController.popBackStack() },
                onPageSelected = { pageIndex ->
                    // Navigate back to editor and scroll to page
                    navController.popBackStack()
                    // TODO: Pass page index back to editor
                },
                onDocumentChanged = {
                    // Set flag to reload document when returning to editor
                    navController.previousBackStackEntry?.savedStateHandle?.set("reload_document", true)
                }
            )
        }
        
        // Template Creator
        composable(
            route = Routes.TEMPLATE_CREATOR,
            arguments = listOf(
                navArgument("templateId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString("templateId")?.let {
                if (it == "{templateId}") null else URLDecoder.decode(it, "UTF-8")
            }
            
            TemplateCreatorScreen(
                templateId = templateId,
                onBack = { navController.popBackStack() }
            )
        }
        
        // Template Manager
        composable(Routes.TEMPLATE_MANAGER) {
            TemplateManagerScreen(
                onBack = { navController.popBackStack() },
                onEditTemplate = { templateId ->
                    navController.navigate(Routes.templateCreator(templateId))
                },
                onCreateNew = {
                    navController.navigate(Routes.templateCreator())
                }
            )
        }
    }
}

