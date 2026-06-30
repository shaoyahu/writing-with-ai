package com.yy.writingwithai.core.data.export

import com.yy.writingwithai.core.data.db.entity.SyncStatus
import kotlinx.serialization.Serializable

/**
 * M4-3 · 导出/导入 Serializable 数据类(字段集 = M1 `Note` / M2 `AiHistory`,无缺无多)。
 *
 * `@SerialName` 兼容旧版本:字段缺失时用默认值(`lastAiOp` / `lastAiAt` / `error` 可空)。
 *
 * fix-2026-06-30-full-review-r1 HIGH H4:补 syncRevision / syncStatus / lastSyncedAt 三个
 * 同步字段,导出-导入 round-trip 不再丢失同步状态(已同步笔记导入后默认 LOCAL,违反
 * "导出字段集 = DB 字段集" 不变量)。
 */
@Serializable
data class ExportNote(
    val id: String,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isPinned: Boolean = false,
    val lastAiOp: String? = null,
    val lastAiAt: Long? = null,
    val syncRevision: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
    val lastSyncedAt: Long? = null
)

@Serializable
data class ExportAiHistory(
    val id: String,
    val noteId: String? = null,
    val providerId: String,
    val model: String = "",
    val op: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val durationMs: Long = 0L,
    val createdAt: Long = 0L,
    val inputSnapshot: String = "",
    val outputSnapshot: String = "",
    val truncated: Boolean = false,
    val error: String? = null
)

/** noteId → List<tag> 映射(M1 `note_tags` 表反查投影)。 */
@Serializable
data class ExportTags(
    val noteIdToTags: Map<String, List<String>> = emptyMap()
)

@Serializable
data class ExportMeta(
    val exportTimestamp: String = "",
    val appVersion: String = "",
    val schemaVersion: String = "1"
)

/** 导入结果报告(UI 直接渲染 + 写 `import_report.md`)。 */
@Serializable
data class ImportReport(
    val successCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val failedNotes: List<FailedNote> = emptyList()
)

@Serializable
data class FailedNote(
    val noteId: String,
    val title: String,
    val error: String
)

// M2:r1 review 抽出 ListSerializer / Json 单例,避免每次 inline new。
val ExportNoteListSerializer =
    kotlinx.serialization.builtins.ListSerializer(ExportNote.serializer())

val ExportAiHistoryListSerializer =
    kotlinx.serialization.builtins.ListSerializer(ExportAiHistory.serializer())

/** 通用 JSON 序列化格式(ignoreUnknownKeys 兼容旧版本 zip + prettyPrint 输出可读);产物跨 exporter / importer / test 共享。 */
val ExportJsonFormat =
    kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
