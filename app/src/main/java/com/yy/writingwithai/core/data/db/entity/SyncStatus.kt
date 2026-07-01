package com.yy.writingwithai.core.data.db.entity

/**
 * 笔记与飞书同步状态。
 *
 * F3 fix L1:review r1 syncStatus string → enum —— 之前 `NoteEntity.syncStatus` 是 `String = "local"`,
 * 业务上等同于枚举(LOCAL / SYNCED / DIRTY / CONFLICT)，但类型不安全:
 * 调用方可能传 `"locall"`,Room 写入合法但语义错位;grep 也搜不到所有不合法用法。
 *
 * 状态语义:
 * - [LOCAL] — 仅本地，从未推过飞书 / 飞书 ref 不存在
 * - [SYNCED] — 与飞书 doc 一致
 * - [DIRTY] — 本地有改动未推(待 push)
 * - [CONFLICT] — 本地与飞书 diff，用户未选 keep local / keep remote
 *
 * 持久化为 lowercase string("local"/"synced"/"dirty"/"conflict")，通过 [SyncStatusConverter]
 * 做 enum ↔ String 转换，沿用旧 schema 默认值("local"),AutoMigration 不破坏现有数据。
 *
 * spec: openspec/changes/quick-note-feature/specs/quick-note/spec.md §"Note entity schema"
 */
enum class SyncStatus {
    LOCAL,
    SYNCED,
    DIRTY,
    CONFLICT
}
