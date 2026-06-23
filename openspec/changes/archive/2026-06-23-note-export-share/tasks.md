## Tasks

### 单篇导出 MD/TXT
- [ ] QuickNoteDetailScreen overflow 菜单新增"导出为 Markdown"和"导出为文本"项
- [ ] QuickNoteDetailViewModel 新增 exportMarkdown() / exportText() 方法
- [ ] 用 SAF CreateDocument launcher 写文件
- [ ] strings.xml 新增导出相关 i18n key

### 分享增强
- [ ] ShareNote.kt 分享 Intent 加 EXTRA_TITLE

### 批量导出
- [ ] QuickNoteListScreen 新增多选模式(长按进入 + checkbox)
- [ ] QuickNoteListViewModel 新增 selectedIds / toggleSelect / clearSelection
- [ ] 多选态 AppBar 显示"已选 N 篇" + 导出/取消按钮
- [ ] NoteExporter 新增 exportSelected() 方法
- [ ] SettingsDataViewModel/Screen 调用批量导出流程

### 验证
- [ ] ./gradlew :app:check 全绿
