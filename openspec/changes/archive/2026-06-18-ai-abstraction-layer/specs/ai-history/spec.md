# ai-history

## Purpose

M2 AI 调用历史持久化:定义 `AiHistoryEntity` Room 表 + `AiHistoryDao` / `AiHistoryRepository`,每次 AI 调用(成功/失败)自动落库,输入/输出截断到合理长度,支持后续 query"用量统计"和"清理旧记录"。

TBD — synced from OpenSpec change `ai-abstraction-layer`(2026-06-18)。

## ADDED Requirements

### Requirement: AiHistory entity schema

系统 MUST 在 Room `ai_history` 表中持久化 `AiHistoryEntity`,字段如下:

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | String(PK) | UUID |
| noteId | String? | 关联 Note.id(可 null,系统级 ping 无关联笔记) |
| providerId | String | "deepseek" / "minimax" / "mimo" / "fake" / "custom" |
| model | String | 模型名 |
| op | String | "expand" / "polish" / "organize" |
| inputTokens | Int | 入向 token |
| outputTokens | Int | 出向 token |
| totalTokens | Int | 合计 token |
| durationMs | Long | 耗时(ms) |
| createdAt | Long | epoch millis |
| inputSnapshot | String | 入参快照(截断 10k 字符) |
| outputSnapshot | String | 出参快照(截断 10k 字符) |
| truncated | Boolean | input/output 是否被截断 |
| error | String? | Failed 时的错误摘要(null = 成功) |

#### Scenario: Successful call written
- **WHEN** `AiGateway.streamWritingOp(...)` 成功完成(Uusage → Done)
- **THEN** `ai_history` 插入一行,`error=null`,`outputSnapshot` 为非空(包含 AI 返回的完整/截断文本)

#### Scenario: Failed call written
- **WHEN** `AiGateway.streamWritingOp(...)` emit `Failed(AiError.Network(code=500, detail="timeout"))`
- **THEN** `ai_history` 插入一行,`error="Network(500): timeout"`,`outputSnapshot` 存已收到的部分输出(可为空字符串)

#### Scenario: Long output truncated
- **WHEN** AI 返回 15k 字符
- **THEN** `outputSnapshot` 只存前 10k 字符,`truncated=true`

### Requirement: AiHistoryDao provides query and cleanup operations

系统 MUST 提供 `AiHistoryDao`,暴露:
- `insert(AiHistoryEntity)`
- `observeByNoteId(noteId): Flow<List<AiHistoryEntity>>` — 按笔记查历史
- `observeAll(limit): Flow<List<AiHistoryEntity>>` — 全部历史(按 createdAt desc)
- `deleteOlderThan(cutoffMs: Long): Int` — 清理过期记录(保留天数可配,默认 90 天)
- `getTotalTokens(): Flow<Long?>` — 累计 token(供未来"用量统计"查询)

#### Scenario: History for a note
- **WHEN** note `"abc"` 经历了 EXPAND(成功) → POLISH(失败)
- **THEN** `observeByNoteId("abc")` 返回 2 行,按 `createdAt desc` 排序

### Requirement: AiHistoryRepository auto-writes on completion

系统 MUST 提供 `AiHistoryRepository`,由 `CoreAiGateway` 在每次 stream 完成(无论成功/失败)时自动调用 `record(...)` 写入;`inputTokens/outputTokens/totalTokens` 从 `AiStreamEvent.Usage` 中取,若失收(例如网络中断未收到 Usage),inputTokens 取估算值(按字符数),outputTokens 取已收 Delta 的累积字符数。

#### Scenario: Auto-write after Done
- **WHEN** `CoreAiGateway.streamWritingOp(...)` Flow 收到 `Usage` → `Done`
- **THEN** `AiHistoryRepository.record()` 被调用,`truncated` 按 10k 规则

#### Scenario: Auto-write after Failed
- **WHEN** `CoreAiGateway.streamWritingOp(...)` Flow 收到 `Failed`(网络中断,未收到 Usage)
- **THEN** `AiHistoryRepository.record()` 仍被调用,`inputTokens` 取估算值,`outputTokens` 取累积 Delta 数,`error` 存 AiError 摘要

### Requirement: Database migration from v1 to v2 adds ai_history table

系统 MUST 在 `AppDatabase` version 1→2 的 `Migration` 中执行:
```sql
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
);
CREATE INDEX IF NOT EXISTS idx_ai_history_noteId ON ai_history (noteId);
CREATE INDEX IF NOT EXISTS idx_ai_history_createdAt ON ai_history (createdAt);
```

#### Scenario: Migration v1→v2
- **WHEN** App(M1 已有 `notes` + `note_tags` 表)升级到 M2
- **THEN** `ai_history` 表被创建,`notes` / `note_tags` 表完好无损

#### Scenario: Fresh install
- **WHEN** 新安装的 App 首次启动
- **THEN** `AppDatabase` 直接创建 v2 schema(含 `notes` + `note_tags` + `ai_history`)
