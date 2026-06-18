package com.yy.writingwithai.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef

/**
 * 应用 Room 数据库。
 *
 * - version 1:首版 schema,对应 quick-note-feature
 * - `exportSchema = true` 配合 `app/build.gradle.kts` 的 KSP arg,
 *   schema JSON 输出到 `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/1.json`
 * - 新增表 / 改字段 → version 递增 + `Migration` 对象(见 spec §"Note database schema is exportable")
 */
@Database(
    entities = [
        NoteEntity::class,
        NoteTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun noteTagDao(): NoteTagDao
}
