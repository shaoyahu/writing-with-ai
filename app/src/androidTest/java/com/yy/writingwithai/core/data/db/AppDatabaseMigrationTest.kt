package com.yy.writingwithai.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * fix-2026-06-25-review-r1 M9 · AppDatabase 1→9 AutoMigration 验证。
 *
 * 之前 r1:AppDatabase 累计 7 个 `@AutoMigration` 但没有 `MigrationTestHelper` 覆盖，
 * 如果 `app/schemas/.../<n>.json` 漏提交某个版本，生产 v8 升级 v9 时 AutoMigration
 * 会抛 IllegalStateException 启动崩溃。
 *
 * 现版:在 androidTest 走真实 SQLite 跑全链路 AutoMigration。本地
 * `./gradlew :app:testDebugUnitTest` **不会**跑此 test(androidTest 必须连接设备
 * / 模拟器),CI 跑 `:app:connectedDebugAndroidTest` 时执行。schema 路径
 * `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/<n>.json` 由
 * `app/build.gradle.kts` 的 KSP arg `room.schemaLocation` 生成，
 * `MigrationTestHelper` 默认从 instrumentation context 读 `schemas/` 资源。
 *
 * spec:openspec/changes/quick-note-feature/specs/quick-note/spec.md
 * "Note database schema is exportable"(同 design D1)。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val DB_NAME = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList<androidx.room.migration.AutoMigrationSpec>(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To9_allAutoMigrations() {
        // 从干净的 v1 schema 开始
        helper.createDatabase(DB_NAME, 1).close()
        // 1→2 走手写 Migration(ai_history 加表);2→9 AutoMigration 由
        // MigrationTestHelper 根据 `AppDatabase::class.java` 注解自动发现 + 应用。
        helper.runMigrationsAndValidate(
            DB_NAME,
            9,
            true,
            AppDatabase.MIGRATION_1_2
        )
    }
}
