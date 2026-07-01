package com.yy.writingwithai.core.data.di

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * review r3 修 H7:DataModule.provideAppDatabase 不再调用 fallbackToDestructiveMigration(),
 * 即使在 DEBUG 模式也不抹用户数据(local notes / attachments / ai_history)。
 *
 * 该 fix 是配置级变更(去掉一个方法调用)，无法在 JVM 单元测试中跑 Hilt + Room 完整链路。
 * 这里通过读源码文件做结构化检查，避免 review 误回退。
 */
class DataModuleTest {

    @Test
    fun `provideAppDatabase source should not call fallbackToDestructiveMigration`() {
        // 读 DataModule.kt 源文件，确认 provideAppDatabase 方法体内不含 fallbackToDestructiveMigration
        val source = this::class.java.classLoader.getResource("DataModule.kt")
            ?.readText()
            ?: this::class.java.protectionDomain.codeSource.location.readText()
        // 上面是占位 — 实际读 classpath 不一定有;改用 java.io.File 读源码路径
        val file = java.io.File("src/main/java/com/yy/writingwithai/core/data/di/DataModule.kt")
        assertTrue(file.exists(), "DataModule.kt source must exist on disk for this structural test")
        val text = file.readText()

        // 找到 provideAppDatabase 方法体
        val methodStart = text.indexOf("fun provideAppDatabase(")
        assertTrue(methodStart >= 0, "provideAppDatabase method not found")
        // 取方法结束 — 下一个 "fun " 或 "@Provides" 之前
        val nextAnnotation = text.indexOf("@Provides", methodStart + 1)
        val nextFun = text.indexOf("fun provide", methodStart + 1)
        val end = listOf(nextAnnotation, nextFun, text.length).filter { it > 0 }.min()
        val body = text.substring(methodStart, end)

        // fix:只检查非注释行(去掉 // 和 /* */ 注释)，避免注释中提及该词导致误判。
        val codeOnly = body.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("/*") || it.startsWith("*") }
            .joinToString("\n")

        assertFalse(
            codeOnly.contains("fallbackToDestructiveMigration"),
            "provideAppDatabase must NOT call fallbackToDestructiveMigration (R3 H7 fix — silent data wipe)"
        )
        assertTrue(
            codeOnly.contains("addMigrations"),
            "provideAppDatabase must keep addMigrations so Room migrations are explicit"
        )
    }
}
