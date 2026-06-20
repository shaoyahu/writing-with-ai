package com.yy.writingwithai.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yy.writingwithai.core.data.db.entity.AiHistoryEntity
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef

/**
 * 应用 Room 数据库。
 *
 * - version 1:首版 schema,对应 quick-note-feature
 * - version 2:加 ai_history 表,对应 ai-abstraction-layer
 * - `exportSchema = true` 配合 `app/build.gradle.kts` 的 KSP arg,
 *   schema JSON 输出到 `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/<version>.json`
 */
@Database(
    entities = [
        NoteEntity::class,
        NoteTagCrossRef::class,
        AiHistoryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun noteTagDao(): NoteTagDao

    abstract fun aiHistoryDao(): AiHistoryDao

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
