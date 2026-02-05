package com.osnotes.app.di

import android.content.Context
import androidx.room.Room
import com.osnotes.app.data.database.AnnotationDao
import com.osnotes.app.data.database.DocumentDao
import com.osnotes.app.data.database.OSNotesDatabase
import com.osnotes.app.data.database.PageDao
import com.osnotes.app.data.pdf.AnnotationManager
import com.osnotes.app.data.pdf.MuPdfRenderer
import com.osnotes.app.data.pdf.PdfAnnotationFlattener
import com.osnotes.app.data.repository.AnnotationRepository
import com.osnotes.app.data.storage.StorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for app-wide dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // ==================== Database ====================
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): OSNotesDatabase {
        return Room.databaseBuilder(
            context,
            OSNotesDatabase::class.java,
            "osnotes_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideAnnotationDao(database: OSNotesDatabase): AnnotationDao {
        return database.annotationDao()
    }
    
    @Provides
    fun providePageDao(database: OSNotesDatabase): PageDao {
        return database.pageDao()
    }
    
    @Provides
    fun provideDocumentDao(database: OSNotesDatabase): DocumentDao {
        return database.documentDao()
    }
    
    // ==================== Repositories ====================
    
    @Provides
    @Singleton
    fun provideAnnotationRepository(
        annotationDao: AnnotationDao,
        pageDao: PageDao,
        documentDao: DocumentDao
    ): AnnotationRepository {
        return AnnotationRepository(annotationDao, pageDao, documentDao)
    }
    
    // ==================== PDF & Storage ====================
    
    @Provides
    @Singleton
    fun provideMuPdfRenderer(
        @ApplicationContext context: Context
    ): MuPdfRenderer {
        return MuPdfRenderer(context)
    }
    
    @Provides
    @Singleton
    fun provideAnnotationManager(
        @ApplicationContext context: Context
    ): AnnotationManager {
        return AnnotationManager(context)
    }
    
    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context,
        pdfRenderer: MuPdfRenderer,
        customTemplateRepository: com.osnotes.app.data.repository.CustomTemplateRepository,
        annotationRepository: com.osnotes.app.data.repository.AnnotationRepository
    ): StorageManager {
        return StorageManager(context, pdfRenderer, customTemplateRepository, annotationRepository)
    }
    
    @Provides
    @Singleton
    fun providePdfAnnotationFlattener(
        @ApplicationContext context: Context,
        customTemplateRepository: com.osnotes.app.data.repository.CustomTemplateRepository
    ): PdfAnnotationFlattener {
        return PdfAnnotationFlattener(context, customTemplateRepository)
    }
    
    @Provides
    @Singleton
    fun provideExportManager(
        @ApplicationContext context: Context,
        pdfRenderer: MuPdfRenderer,
        annotationRepository: AnnotationRepository
    ): com.osnotes.app.data.export.ExportManager {
        return com.osnotes.app.data.export.ExportManager(context, pdfRenderer, annotationRepository)
    }
    
    @Provides
    @Singleton
    fun provideCustomTemplateRepository(
        @ApplicationContext context: Context
    ): com.osnotes.app.data.repository.CustomTemplateRepository {
        return com.osnotes.app.data.repository.CustomTemplateRepository(context)
    }
}

