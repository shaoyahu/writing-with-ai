package com.yy.writingwithai.core.data.di

import android.content.Context
import androidx.room.Room
import com.yy.writingwithai.core.data.db.AiHistoryDao
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.feishu.sync.FeishuRefDao
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module:提供 Room 数据库及其 DAO。
 *
 * 见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Note database schema is exportable"。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val DB_NAME = "writing_with_ai.db"

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
        if (com.yy.writingwithai.BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        } else {
            builder.addMigrations(AppDatabase.MIGRATION_1_2)
        }
        return builder.build()
    }

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideNoteTagDao(db: AppDatabase): NoteTagDao = db.noteTagDao()

    @Provides
    fun provideAiHistoryDao(db: AppDatabase): AiHistoryDao = db.aiHistoryDao()

    @Provides
    fun provideNoteLinkDao(db: AppDatabase): NoteLinkDao = db.noteLinkDao()

    @Provides
    fun provideFeishuRefDao(db: AppDatabase): FeishuRefDao = db.feishuRefDao()

    @Provides
    fun provideFeishuSyncEventDao(db: AppDatabase): FeishuSyncEventDao = db.feishuSyncEventDao()

    @Provides
    fun provideNoteEntityDao(db: AppDatabase): NoteEntityDao = db.noteEntityDao()

    @Provides
    fun provideEntityAliasDao(db: AppDatabase): EntityAliasDao = db.entityAliasDao()
}
