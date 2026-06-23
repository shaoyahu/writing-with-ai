## Context

当前分享走 `Intent.ACTION_SEND text/markdown`（纯文本），导出走 `NoteExporter.exportToJsonZip()`（全量 zip）。缺少中间态：单篇文件保存 + 批量部分导出。

## Goals / Non-Goals

**Goals:**
- 单篇笔记导出为 .md / .txt 文件（SAF CreateDocument）
- 列表页多选 + 批量导出选中笔记（复用 NoteExporter）
- 分享 Intent 加 EXTRA_TITLE

**Non-Goals:**
- 不做 FileProvider（v1 纯文本分享足够，文件分享留给 v2）
- 不做导出格式自定义（如 HTML/PDF）
- 不做导出模板

## Decisions

### D1: SAF CreateDocument 写文件

用 `ActivityResultContracts.CreateDocument("text/markdown")` 让用户选保存位置，不需要 WRITE_EXTERNAL_STORAGE 权限。文件名用 `note.title.md`，title 为空用 `note.id.md`。

### D2: 批量导出复用 NoteExporter

`NoteExporter` 新增 `exportSelected(noteIds: List<String>, outputStream)` 方法，复用现有 JSON + Markdown zip 格式，只导出选中笔记。

### D3: 多选模式用 LazyColumn item 长按触发

列表页长按 note 进入多选模式，顶部 AppBar 切换为"已选 N 篇" + 导出/取消按钮。

## Risks / Trade-offs

- [SAF 文件名冲突] → CreateDocument 会自动提示用户覆盖确认，无需额外处理
- [多选模式交互] → 长按触发是 Android 惯例，但需与长按选文本区分 → 只在非搜索态响应长按
