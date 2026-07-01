## 1. Build 配置:启用 Room schema export + KSP arg

- [x] 1.1 在 `app/build.gradle.kts` 的 `ksp { arg("room.schemaLocation", "com.yy.writingwithai.core.data.db.AppDatabase/1")` 与 `room { schemaDirectory("$projectDir/schemas") }` 块，把 schema export 目录固定为 `app/schemas/`
- [x] 1.2 在 `app/schemas/.gitignore` 加例外，确认整个 `app/schemas/` 目录被 git 追踪(`git add -f` 强制加 `app/schemas/.gitkeep`)
- [x] 1.3 跑 `./gradlew :app:assembleDebug` 确认 KSP 配置无报错

## 2. 数据层:Room Entity + DAO

- [x] 2.1 新建 `core/data/db/entity/NoteEntity.kt` — Room Entity，字段(id PK String / title / content / createdAt Long / updatedAt Long / isPinned Boolean / lastAiOp String? / lastAiAt Long?);`indices = [Index("updatedAt")]`
- [x] 2.2 新建 `core/data/db/entity/NoteTagCrossRef.kt` — 联合主键 `(noteId, tag)`;`indices = [Index("noteId"), Index("tag")]`
- [x] 2.3 新建 `core/data/db/NoteDao.kt` — 接口，暴露 `upsert / getById(id) / delete(id) / observeAll() / observeByTag(tag) / search(query) / setPinned(id, pinned)`;`observeAll / observeByTag / search` 返回 `Flow<List<NoteEntity>>`,`search` 走 `WHERE title LIKE :q OR content LIKE :q`
- [x] 2.4 新建 `core/data/db/NoteTagDao.kt` — 暴露 `add(noteId, tag) / remove(noteId, tag) / observeTagsFor(noteId) / observeAllTags()`
- [x] 2.5 更新 `core/data/db/AppDatabase.kt` — 替换空 schema,`@Database(entities = [NoteEntity::class, NoteTagCrossRef::class], version = 1, exportSchema = true)`
- [x] 2.6 跑 `./gradlew :app:assembleDebug` 确认 Room 编译通过 + `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/1.json` 自动生成

## 3. Repository 层

- [x] 3.1 新建 `core/data/model/Note.kt` — UI 领域模型(data class，字段名与 Entity 一致，但不暴露 Room 注解)
- [x] 3.2 新建 `core/data/model/Tag.kt` — UI 领域模型(简单 `data class Tag(val name: String)`)
- [x] 3.3 新建 `core/data/repo/NoteRepository.kt` — `@Singleton class`，构造函数注入 `NoteDao` + `NoteTagDao`;提供 `observeNotes(search, tag) / getNote(id) / upsert(Note) / delete(id) / setPinned(id, pinned) / observeTagsForNote(id) / observeAllTags()`
- [x] 3.4 新建 `core/data/repo/TagRepository.kt`(薄封装，转 NoteTagDao → `Flow<List<String>>`，供列表 tag chip 用)
- [x] 3.5 新建 `core/data/mapper/NoteMapper.kt` — `NoteEntity ↔ Note`(M1 是直通，留扩展点)
- [x] 3.6 新建 `core/data/di/DataModule.kt` — `@Module @InstallIn(SingletonComponent::class)`,`@Provides @Singleton fun provideAppDatabase(...) / NoteDao / NoteTagDao`(M0 已建 `DatabaseModule` 占位，替换或扩展)
- [x] 3.7 新建 `core/data/di/RepositoryModule.kt` — `@Binds` 绑定 `NoteRepository` / `TagRepository` 为单例

## 4. i18n:string 资源

- [x] 4.1 `app/src/main/res/values/strings.xml` 新增 quicknote 系列 key:`quicknote_list_title / quicknote_list_empty / quicknote_list_empty_cta / quicknote_list_search_hint / quicknote_list_fab_new / quicknote_detail_title / quicknote_detail_word_count_fmt / quicknote_detail_read_time_fmt / quicknote_detail_edit / quicknote_detail_share / quicknote_detail_delete / quicknote_detail_pin / quicknote_detail_unpin / quicknote_editor_title_new / quicknote_editor_title_edit / quicknote_editor_title_hint / quicknote_editor_content_hint / quicknote_editor_save / quicknote_editor_cancel / quicknote_editor_delete_confirm / quicknote_editor_delete_confirm_ok / quicknote_editor_delete_confirm_cancel / quicknote_tag_input_hint / quicknote_share_chooser_title`(权威中文值)
- [x] 4.2 `app/src/main/res/values-en/strings.xml` 同 key 占位:`TODO(en): <chinese value>`，确保 build 不报错
- [x] 4.3 跑 `./gradlew :app:assembleDebug` 确认无 missing string 引用

## 5. Nav 路由接入

- [x] 5.1 `app/AppNav.kt` 新增 `@Serializable object QuicknoteList / data class QuicknoteDetail(val id: String) / data class QuicknoteEdit(val id: String? = "NEW")`
- [x] 5.2 把 NavHost `startDestination` 由当前 placeholder 改为 `QuicknoteList`
- [x] 5.3 在 NavHost 加 `composable<QuicknoteList> { QuickNoteListScreen(...) }` / `composable<QuicknoteDetail> { ... }` / `composable<QuicknoteEdit> { ... }` 三个目的地，先以 `Text("TODO")` 占位
- [x] 5.4 跑 `./gradlew :app:assembleDebug` 确认 Nav 编译通过

## 6. 列表屏(QuickNoteListScreen + ViewModel)

- [x] 6.1 新建 `feature/quicknote/model/NoteListUiState.kt` — sealed interface(`Loading / Empty / Content(notes, query, selectedTag)`)
- [x] 6.2 新建 `feature/quicknote/list/QuickNoteListViewModel.kt` — `@HiltViewModel`，注入 `NoteRepository` + `TagRepository`;暴露 `StateFlow<NoteListUiState>`(搜索 / tag 筛选合并到 state);操作方法 `setQuery / selectTag / clearTag / refresh`
- [x] 6.3 新建 `feature/quicknote/list/QuickNoteListScreen.kt` — Compose:`Scaffold` + `TopAppBar`(含搜索框)+ `FilterChip` 行(全部 + 各 tag)+ `LazyColumn`(`NoteRow` Composable，左侧 pin 图标、标题、内容预览、tag chip 行、时间、`wordCount` 小字);空状态走 `R.string.quicknote_list_empty`;FAB 跳 `QuicknoteEdit(id="NEW")`
- [x] 6.4 新建 `feature/quicknote/list/NoteRow.kt` — 单行 Composable，接受 `Note` + `tags`，渲染单条;点击触发 `onClick(noteId)` → NavController 跳详情

## 7. 详情屏(QuickNoteDetailScreen + ViewModel)

- [x] 7.1 新建 `feature/quicknote/detail/QuickNoteDetailViewModel.kt` — `@HiltViewModel`，接受 `SavedStateHandle["id"]`;注入 `NoteRepository`;暴露 `StateFlow<NoteDetailUiState>`(`Loading / NotFound / Content(note, tags, wordCount, readMinutes)`)
- [x] 7.2 新建 `feature/quicknote/detail/WordCount.kt`(纯函数，中文按字符 + 英文按空格分词)+ `ReadingTime.kt`(300 字/分 vs 200 词/分取上限)放 `feature/quicknote/model/`
- [x] 7.3 新建 `feature/quicknote/detail/QuickNoteDetailScreen.kt` — `Scaffold` + `TopAppBar`(返回 + 编辑 + 更多菜单:分享 / 固定 / 删除);正文 `SelectionContainer` 包 `Text`(M1 暂不渲染 Markdown);底部一行 `Text(wordCount · readMinutes)`
- [x] 7.4 删除走确认 dialog(`AlertDialog`,`quicknote_editor_delete_confirm` 系列字符串);删除后 `popBackStack`

## 8. 编辑屏(QuickNoteEditorScreen + ViewModel)

- [x] 8.1 新建 `feature/quicknote/edit/QuickNoteEditorViewModel.kt` — `@HiltViewModel`，接受 `SavedStateHandle["id"]`(缺省或 `"NEW"` 视为新建);暴露 `StateFlow<EditorUiState>`(`Loading / Content(title, content, tags, isPinned, isSaving)`);`save(title, content, tags)` 走 Repository upsert
- [x] 8.2 新建 `feature/quicknote/edit/TagInputRow.kt` — `FlowRow`，现有 tag 用 `InputChip`(可删除);末尾 `OutlinedTextField` 输入新 tag，逗号 / 回车提交;chip 删完不显示空 FlowRow
- [x] 8.3 新建 `feature/quicknote/edit/QuickNoteEditorScreen.kt` — `Scaffold` + `TopAppBar`(返回 + 保存);两个 `OutlinedTextField`(title / content,content 走 `BasicTextField` + 多行 + 等宽可选);底部 `TagInputRow`
- [x] 8.4 保存逻辑:id 为 `"NEW"` → 生成 UUID + 写 `createdAt = now`;已有 id → 只更新 `updatedAt = now`;保存后 `popBackStack`(带回 id 给详情屏刷新)

## 9. 单条分享(Intent.ACTION_SEND)

- [x] 9.1 新建 `feature/quicknote/share/ShareNote.kt`(`internal fun Context.shareNoteMarkdown(note: Note)`)放 `feature/quicknote/share/`
- [x] 9.2 逻辑:title 为空则 `EXTRA_TEXT = content`;否则 `EXTRA_TEXT = "$title\n\n$content"`;`Intent.ACTION_SEND` + `type = "text/markdown"`;`Intent.createChooser` 包一层
- [x] 9.3 详情屏"分享"菜单项调 `context.shareNoteMarkdown(note)`

## 10. 测试

- [ ] 10.1 新建 `app/src/test/java/.../core/data/NoteDaoTest.kt` — `@RunWith(JUnitPlatform)` + `Room.inMemoryDatabaseBuilder`;覆盖 upsert / delete(级联) / setPinned / search(LIKE)/ observeAll 排序
- [ ] 10.2 新建 `app/src/test/java/.../core/data/NoteRepositoryTest.kt` — MockK mock DAO + Turbine 验 `Flow` 行为
- [ ] 10.3 新建 `app/src/test/java/.../feature/quicknote/QuickNoteListViewModelTest.kt` — MainDispatcherRule + MockK repo;覆盖 `Loading → Empty / Content` 切换、`setQuery` 触发搜索、`selectTag` 触发筛选
- [ ] 10.4 新建 `app/src/test/java/.../feature/quicknote/detail/WordCountTest.kt` + `ReadingTimeTest.kt` — 纯函数:中文 / 英文 / 混合 / 空 / 大数
- [ ] 10.5 跑 `./gradlew :app:testDebugUnitTest` 全绿

## 11. 整体验收

- [ ] 11.1 `./gradlew :app:assembleDebug` → `app-debug.apk` 生成;`app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/1.json` 存在
- [ ] 11.2 `./gradlew :app:testDebugUnitTest` → 全绿(包括 DAO / Repository / ViewModel / WordCount)
- [ ] 11.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [ ] 11.4 `./gradlew :app:check` → 全绿(已知 ktlint Compose PascalCase follow-up 不计入本 change，见 memory `ktlint-compose-pascalcase-1.0`)
- [ ] 11.5 手工 smoke(Android Studio 装好之后，或者 `adb install` 到设备):新建 → 编辑保存 → 列表出现 → 详情展示字数 + 阅读时间 → tag 添加 → 列表按 tag 筛选 → pin → 分享 → 删除;每条都过

## 12. OpenSpec 收尾

- [ ] 12.1 在 review 通过后，跑 `openspec sync-specs quick-note-feature` 把 delta spec 合并到 `openspec/specs/quick-note/spec.md`
- [ ] 12.2 跑 `openspec archive quick-note-feature` 归档到 `openspec/changes/archive/2026-06-18-quick-note-feature/`
- [ ] 12.3 更新 `docs/progress.md`:M1 完成(对应 init-android-project 的 §"维护规则"，记一条 1-3 行)
- [ ] 12.4 在 `docs/plans/writing-with-ai-mobile-roadmap.md` §13 标注 M1 完成;§15.2 把 `quick-note-feature` 标 done
