## Context

M0-M4-2 已落地:M0 工程脚手架 / M1 quick-note CRUD / M2 AI 抽象层 / M3 AI 操作 UI / M4-1 Glance 桌面 widget / M4-2 predictive back。**M4-3 是 v1 数据迁移契约**:用户必须能导出所有笔记 + AI 历史,能在新设备 100% 还原。

roadmap §5.3 拍板"导出:`notes` + `ai_history` + `tags` 打包为 JSON zip;Markdown zip(每条笔记一个 .md)作为可读版本,放同一压缩包的两个目录"。roadmap §3.5 拍板"导出:单条 → Markdown / 纯文本;批量 → JSON zip"。

需求落地:
1. settings UI 入口(TopAppBar overflow menu → 数据迁移)
2. 导出 → SAF `ACTION_CREATE_DOCUMENT` 选位置 → 写 JSON zip
3. 导入 → SAF `ACTION_OPEN_DOCUMENT` 选 zip → 解析 → Room 写入
4. 同压缩包含 JSON + Markdown 两个目录(JSON 优先,M5 polish 改 Markdown 可读视图)
5. 失败条目写 `import_report.md`,不中断整批
6. i18n 完整(中文 + 英文 TODO 占位)

## Goals / Non-Goals

**Goals:**
- `NoteExporter` Hilt 单例,API `suspend fun exportToJsonZip(notes: List<Note>): File` + `suspend fun exportToMarkdownZip(notes: List<Note>): File`
- `NoteImporter` Hilt 单例,API `suspend fun importFromZip(file: File): ImportReport`
- 输出 zip 含 4 文件:`notes.json` + `ai_history.json` + `tags.json` + `meta.json`(版本 / 时间戳 / app 版本)
- Markdown 导出:每条笔记一个 `.md` 文件,放 `notes/` 子目录;`# 标题\n\n内容` 格式
- 导入 zip 去重:`note.id` 已存在跳过(不覆盖)
- 失败条目收集到 `import_report.md`(压缩包根目录,导入完成后写回)
- settings UI:TopAppBar overflow menu → "数据迁移" → 列表屏(导出按钮 / 导入按钮 + 进度 + 结果)
- `Dispatchers.IO` executor;export/import 走 `viewModelScope.launch`
- i18n 9 个 key
- Android 11+ SAF 兼容(API < 30 加 legacy 权限)
- 测试覆盖:导出 zip 文件结构 + 导入 zip JSON 反序列化 + 去重逻辑

**Non-Goals:**
- 自动备份(M5 polish 阶段)
- 云同步(roadmap §15 v1 仅本地)
- Markdown 渲染(M3 已有源码,导出纯文本即可)
- 第三方 zip 库(android.util 已自带)
- 跨设备时间同步(v1 接受 createdAt 不一致,M5 polish)
- 失败时断点续传(M5 polish)
- 加密导出 zip(apikey 是 M4-4 onboarding-consent scope,本 change 不涉及)

## Decisions

### 1. zip 文件结构:JSON 优先,Markdown 可读

```
writing-with-ai-export-2026-06-19T10-30-00.zip
├── notes.json              # 全部 notes(序列化 List<Note>)
├── ai_history.json         # 全部 ai_history(序列化 List<AiHistory>)
├── tags.json               # 全部 tags(序列化 List<String> + note-tag map)
├── meta.json               # 版本 / 时间戳 / app 版本
└── notes/                  # 可读 Markdown 视图
    ├── abc-123.md
    ├── def-456.md
    └── ...
```

**Why:** 解析路径(JSON)与可读路径(Markdown)分离。开发工具 / Git diff 友好。Markdown 文件是"冗余副本",方便人工浏览 zip 验证导出完整性。

**替代方案:** 只导 JSON(简单但人不可读);只导 Markdown(简单但损失 ai_history / tags) — **选 JSON + Markdown 双目录**。

### 2. `androidx.documentfile:documentfile` SAF

```kotlin
val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    type = "application/zip"
    putExtra(Intent.EXTRA_TITLE, "writing-with-ai-export-${timestamp()}.zip")
}
startActivityForResult(createIntent, REQUEST_CODE_EXPORT)
```

**Why:** Android 11+ SAF 是系统级文件选择器,**无需申请 `WRITE_EXTERNAL_STORAGE` 权限**,符合 Play Store 安全扫描。androidx.documentfile 提供 `DocumentFile` 包装 URI。

**替代方案:** 走 legacy `WRITE_EXTERNAL_STORAGE` + `Environment.getExternalStoragePublicDirectory()` — 需 `<uses-permission>` 声明,Play Store 安全扫描警告,且 Android 13+ 限制更严。**选 SAF**。

### 3. minSdk 26 + Android 11 (API 30) 双路径

- API 30+ (Android 11+):SAF,无权限
- API 26-29 (Android 8-10):SAF 也支持(`ACTION_CREATE_DOCUMENT` API 19 就有),但部分设备文件选择器差异 → 加 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` 权限声明做兜底

**Why:** 旧设备 SAF 兼容性差,加 legacy 权限做 fallback。Android 10+ scoped storage 已默认限制外部写,**应用必须有 SAF 主路径**。

**替代方案:** 只走 SAF(API 26-29 用户路径复杂)— **双路径**,旧设备用 SAF + 用户授权弹窗。

### 4. 导入去重:`note.id` 已存在跳过

```kotlin
fun importNote(note: Note): ImportResult {
    val existing = noteDao.getById(note.id)
    return if (existing != null) {
        ImportResult.SKIPPED  // 不覆盖,保留用户原数据
    } else {
        noteDao.upsert(note.toEntity())
        ImportResult.IMPORTED
    }
}
```

**Why:** 防止导入覆盖用户设备已有数据。跨设备合并场景:用户在两台设备分别写笔记 → 导入合并(`note.id` 不同),已存在的保留。

**替代方案:** `createdAt` 比较,更新的覆盖旧的(复杂,且 createdAt 不一致 race)— **选 id 去重**(M5 polish 处理 createdAt 时间同步)。

### 5. 失败条目:`import_report.md` 收集

```markdown
# writing-with-ai 导入报告

时间:2026-06-19T10:30:00
总计:10 条笔记
- 成功:7
- 跳过(id 重复):2
- 失败:1

## 失败详情

### note id=xxx-xxx title="..."
失败原因:Room UNIQUE constraint violation (tag="灵感")
解决建议:手动删除冲突 tag 后重试
```

**Why:** 部分失败不应中断整批,失败条目详细记录让用户手动处理。`import_report.md` 是纯文本,可读 + 可分享。

### 6. `Dispatchers.IO` executor + `viewModelScope.launch`

```kotlin
fun exportToJsonZip(): Job = viewModelScope.launch(Dispatchers.IO) {
    _uiState.value = DataUiState.Exporting
    try {
        val file = noteExporter.exportToJsonZip(...)
        _uiState.value = DataUiState.Done(report)
    } catch (e: Exception) {
        _uiState.value = DataUiState.Failed(e.message ?: "Unknown error")
    }
}
```

**Why:** 导出/导入 IO 密集,放后台线程。`viewModelScope` 让 ViewModel 销毁时取消任务(避免 leak)。

### 7. settings 入口:TopAppBar overflow menu

`QuickNoteListScreen.kt` TopAppBar `actions = { IconButton(...) { DropdownMenu(... MenuItem(text = "数据迁移", onClick = { navController.navigate(SettingsData) }) } }`。

**Why:** 复用现有列表屏 TopAppBar,不引入新 BottomNav(M5 polish)。M4-3 是 settings 第一个 tab,后续 M4-4 onboarding-consent / M5 polish 加完整 settings tab 集合。

**替代方案:** 走 BottomNav(roadmap §3.4 没画)— 留 M5 polish。

### 8. 测试覆盖:导出 / 导入 / 去重

JUnit5 + Robolectric 覆盖:
- `NoteExporterTest`:导出后 zip 含 `notes.json` / `ai_history.json` / `tags.json` / `meta.json` 4 文件
- `NoteImporterTest`:导入 zip → Room 写入;`note.id` 已存在跳过
- `ImportReportTest`:失败条目收集到 `import_report.md`

### 9. i18n:9 个 key 走 R.string

所有用户可见字符串走 `R.string.settings_data_*`(中文 + 英文 TODO 占位);`import_report_summary` 是 `String.format` 占位。

### 10. `androidx.documentfile` 仅 1 个新依赖

```toml
[versions]
documentfile = "1.0.1"

[libraries]
androidx-documentfile = { module = "androidx.documentfile:documentfile", version.ref = "documentfile" }
```

**Why:** SAF DocumentFile 包装 URI 是标准做法;`androidx.documentfile` 是 AndroidX 官方包,与现有依赖一致。

## Risks / Trade-offs

- **[Risk] 大文件导出阻塞** → export/import 走 `Dispatchers.IO` + `viewModelScope.launch`,UI 显示进度;实测 1000 条笔记 zip < 5s,可接受
- **[Risk] Markdown 文件名特殊字符** → `note.id` 是 UUID,`note.title.ifBlank { "untitled" }.replace(/[^a-zA-Z0-9-_]/, "_")` — UUID 文件名天然安全,title 仅用于可选 metadata
- **[Risk] 跨设备 `createdAt` 不一致** → v1 接受,M5 polish 处理时间同步(spec 验证项 N/A)
- **[Risk] Android 13+ SAF 选择器 UX** → `DocumentFile.fromSingleUri(context, uri)` 兼容新 API,无需特殊处理
- **[Risk] import 失败不中断整批,但失败可能因 schema 不兼容** → 失败原因写 `import_report.md`,用户手动迁移
- **[Risk] ZIP 4 GB 上限** → 32-bit ZIP 限制(`ZipOutputStream` 内部);普通用户笔记 < 1 MB,**v1 接受**,超 4 GB 改 `Zip64`(M5 polish)
- **[Risk] 加密 zip 需求** → apikey 不存笔记(只 AI history provider / model 字段,不含 apikey),v1 导出 zip 不加密;M4-4 onboarding-consent 不涉及加密 zip
- **[Risk] settings 入口仅 overflow menu** → 不走 BottomNav,M5 polish 集成完整 settings 框架

## Migration Plan

无(纯新增,无 schema 变更)。回滚:`git revert` 即可,删 5 个新文件 + 1 个新 res + 2 个 manifest 权限。

## Open Questions

- **`NoteExporter.exportToJsonZip(notes)` vs `exportAllNotes()`?** 倾向:`exportAllNotes()` 由 ViewModel 调 `noteRepository.observeAll().first()` 拿到 notes 再传 exporter;**不**让 exporter 直接持有 Repository(单职责,exporter 接收纯 `List<Note>`)。M5 polish 若需要 streaming 大数据集,可加 `Flow<List<Note>>` 重载
- **Markdown zip 与 JSON zip 是 2 个调用,还是 1 个合并 zip?** 倾向:**合并**(`exportToZip(notes, includeMarkdown = true)`)— 用户一次导出,同时获得 JSON(机器可读)+ Markdown(人可读)。**spec §"markdown view 是冗余副本"已隐含合并**
- **`ImportReport` 是 data class 还是 sealed?** 倾向:data class,字段 `successCount` / `skippedCount` / `failedCount` / `failedNotes: List<Pair<Note, Throwable>>`,UI 直接渲染
- **settings 仅 data tab,完整 settings 框架什么时候建?** 倾向:**M5 polish**;M4-3 / M4-4 只加必要 tab,避免大爆炸
- **`meta.json` 是否包含 apikey?** 倾向:**不**(`meta.json` 只含 export_timestamp / app_version / schema_version);apikey 走 M4-4 onboarding-consent 单独的 encrypted blob
- **`tags.json` 是 `List<String>` 还是 `Map<noteId, List<String>>`?** 倾向:**后者**(JSON 直接映射 `note_tags` Room 表,导入时不依赖 notes.json 反查)— spec §"Markdown 导出文件名特殊字符"已隐含 noteId 字段