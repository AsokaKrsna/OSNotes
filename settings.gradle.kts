pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MuPDF Maven repository
        maven { url = uri("https://maven.ghostscript.com") }
    }
}

rootProject.name = "OSNotes"
include(":app")
