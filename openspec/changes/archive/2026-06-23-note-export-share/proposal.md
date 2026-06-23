## Why

当前笔记只有全量 JSON zip 导出（设置页）和 ACTION_SEND text/markdown 分享（详情页），缺少高频场景：单篇笔记导出为 MD/TXT 文件保存到本地、批量导出选中笔记。用户日常需要把单篇笔记存为文件或分享给其他 App 打开。

## What Changes

- **单篇导出为 Markdown 文件**：详情屏 overflow 菜单加"导出为 Markdown"，SAF CreateDocument 写 .md
- **单篇导出为 TXT 文件**：详情屏 overflow 菜单加"导出为文本"，SAF CreateDocument 写 .txt
- **分享增强**：分享 Intent 加 EXTRA_TITLE + 可选 FileProvider 传文件
- **批量导出**：列表页加多选模式 + "导出选中"按钮

## Capabilities

### New Capabilities

### Modified Capabilities
- `quick-note`: 详情屏 overflow 新增导出菜单项；列表页新增多选模式
- `data-export-import`: NoteExporter 新增 exportSelected() 支持 partial export

## Impact

- QuickNoteDetailScreen / QuickNoteListScreen / NoteExporter / ShareNote / strings.xml / AndroidManifest(FileProvider)
