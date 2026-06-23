## Tasks

### FTS4 全文搜索
- [ ] AppDatabase 新增 FtsNoteEntity (@Fts4 contentEntity=NoteEntity)
- [ ] AppDatabase 版本升 v6 + AutoMigration 或手动 Migration
- [ ] NoteDao 新增 searchFts(query: String): List<NoteWithTags>
- [ ] NoteRepository 搜索路径切到 FTS

### 标签+实体联合过滤
- [ ] QuickNoteListViewModel 新增 selectedEntities: StateFlow<Set<String>>
- [ ] QuickNoteListScreen 加实体 filter chip 行
- [ ] NoteEntityDao 新增 observeByEntities(entityKeys: List<String>)

### 搜索排序+历史
- [ ] 搜索排序切换 UI(时间/相关性)
- [ ] 新增 SearchHistoryStore(DataStore, 最近 20 条)
- [ ] QuickNoteListScreen 搜索框下方显示搜索历史

### 验证
- [ ] ./gradlew :app:check 全绿
