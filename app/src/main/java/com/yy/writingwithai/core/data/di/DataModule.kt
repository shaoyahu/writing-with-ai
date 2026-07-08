package com.yy.writingwithai.core.data.di

import android.content.Context
import androidx.room.Room
import com.yy.writingwithai.core.data.db.AiHistoryDao
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.dao.sync.SyncMetaDao
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
        // review r3 修 H7:DEBUG 模式不再调用 Room 的"版本不匹配时静默删库重建"方法。
        // 该调用在 debug 装旧版时静默抹掉用户全部数据(local notes / attachments / ai_history),
        // 与用户预期(debug 仅是 dev 便利)严重不符。统一走 addMigrations 路径，
        // 若缺 migration → Room 直接抛 IllegalStateException，逼开发者加迁移脚本。
        builder.addMigrations(AppDatabase.MIGRATION_1_2)
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideNoteTagDao(db: AppDatabase): NoteTagDao = db.noteTagDao()

    @Provides
    @Singleton
    fun provideAiHistoryDao(db: AppDatabase): AiHistoryDao = db.aiHistoryDao()

    @Provides
    @Singleton
    fun provideNoteLinkDao(db: AppDatabase): NoteLinkDao = db.noteLinkDao()

    @Provides
    @Singleton
    fun provideFeishuRefDao(db: AppDatabase): FeishuRefDao = db.feishuRefDao()

    @Provides
    @Singleton
    fun provideFeishuSyncEventDao(db: AppDatabase): FeishuSyncEventDao = db.feishuSyncEventDao()

    @Provides
    @Singleton
    fun provideNoteEntityDao(db: AppDatabase): NoteEntityDao = db.noteEntityDao()

    @Provides
    @Singleton
    fun provideEntityAliasDao(db: AppDatabase): EntityAliasDao = db.entityAliasDao()

    @Provides
    @Singleton
    fun provideNoteAttachmentDao(db: AppDatabase): NoteAttachmentDao = db.noteAttachmentDao()

    // fix-full-review:补上 SyncMetaDao provider，否则 @Inject SyncMetaDao 会在 Hilt 运行时报错。
    @Provides
    @Singleton
    fun provideSyncMetaDao(db: AppDatabase): SyncMetaDao = db.syncMetaDao()
}
