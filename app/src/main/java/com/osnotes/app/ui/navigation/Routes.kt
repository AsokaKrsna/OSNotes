package com.osnotes.app.ui.navigation

/**
 * Navigation routes for OSNotes app.
 */
object Routes {
    const val HOME = "home"
    const val FOLDER = "folder/{path}"
    const val EDITOR = "editor/{filePath}"
    const val SETTINGS = "settings"
    const val PAGE_MANAGER = "page_manager/{filePath}?annotationCount={annotationCount}"
    const val TEMPLATE_CREATOR = "template_creator?templateId={templateId}"
    const val TEMPLATE_MANAGER = "template_manager"
    
    fun folder(path: String) = "folder/${java.net.URLEncoder.encode(path, "UTF-8")}"
    fun editor(filePath: String) = "editor/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    fun pageManager(filePath: String, annotationCount: Int = 0) = 
        "page_manager/${java.net.URLEncoder.encode(filePath, "UTF-8")}?annotationCount=$annotationCount"
    fun templateCreator(templateId: String? = null) = if (templateId != null) {
        "template_creator?templateId=${java.net.URLEncoder.encode(templateId, "UTF-8")}"
    } else {
        "template_creator"
    }
}
