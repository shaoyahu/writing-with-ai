package com.yy.writingwithai.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.AiHistoryEntity
import com.yy.writingwithai.core.data.db.entity.FtsNoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef
import com.yy.writingwithai.core.data.db.entity.entity.EntityAliasRow
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import com.yy.writingwithai.core.feishu.sync.FeishuRefDao
import com.yy.writingwithai.core.feishu.sync.FeishuRefEntity
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventDao
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventEntity

/**
 * 应用 Room 数据库。
 *
 * - version 1:首版 schema,对应 quick-note-feature
 * - version 2:加 ai_history 表,对应 ai-abstraction-layer
 * - version 3:加 note_links,对应 note-association
 *   - `@AutoMigration(2, 3)` 走 schema diff 自动建新表 + 索引 + 外键 CASCADE
 * - version 4:加 feishu_ref + feishu_sync_event,对应 feishu-bidir-sync
 *   - `@AutoMigration(3, 4)` 走 schema diff
 * - `exportSchema = true` 配合 `app/build.gradle.kts` 的 KSP arg,
 *   schema JSON 输出到 `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/<version>.json`
 */
@Database(
    entities = [
        NoteEntity::class,
        NoteTagCrossRef::class,
        AiHistoryEntity::class,
        NoteLinkEntity::class,
        FeishuRefEntity::class,
        FeishuSyncEventEntity::class,
        NoteEntityRow::class,
        EntityAliasRow::class,
        FtsNoteEntity::class
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun noteTagDao(): NoteTagDao

    abstract fun aiHistoryDao(): AiHistoryDao

    abstract fun noteLinkDao(): NoteLinkDao

    abstract fun feishuRefDao(): FeishuRefDao

    abstract fun feishuSyncEventDao(): FeishuSyncEventDao

    abstract fun noteEntityDao(): NoteEntityDao

    abstract fun entityAliasDao(): EntityAliasDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ai_history (
                            id TEXT NOT NULL PRIMARY KEY,
                            noteId TEXT,
                            providerId TEXT NOT NULL,
                            model TEXT NOT NULL,
                            op TEXT NOT NULL,
                            inputTokens INTEGER NOT NULL DEFAULT 0,
                            outputTokens INTEGER NOT NULL DEFAULT 0,
                            totalTokens INTEGER NOT NULL DEFAULT 0,
                            durationMs INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL,
                            inputSnapshot TEXT NOT NULL DEFAULT '',
                            outputSnapshot TEXT NOT NULL DEFAULT '',
                            truncated INTEGER NOT NULL DEFAULT 0,
                            error TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ai_history_noteId " +
                            "ON ai_history (noteId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ai_history_createdAt " +
                            "ON ai_history (createdAt)"
                    )
                }
            }
    }
}
