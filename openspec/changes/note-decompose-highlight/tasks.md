# note-decompose-highlight · Tasks

- [x] T1: 字符串资源 — 在 `strings.xml`(中/英) 中添加拆解相关文案：`note_decompose`/`note_redecompose`/`note_decomposing`/`note_decompose_no_entities`/`note_decompose_found_fmt`/`note_decompose_entity_sheet_title`/`note_decompose_no_related`
- [x] T2: ViewModel 状态扩展 — 在 `QuickNoteDetailViewModel` 中新增 `DecomposeState` 密封接口（Idle/Loading/Decomposed(entityCount)/Error(message)）、`decomposeState: StateFlow<DecomposeState>`、`entityRows: StateFlow<List<NoteEntityRow>>`、`decompose()` 方法（调用 EntityExtractor + CompositeNoteLinker + 刷新 entityRows）
- [x] T3: 菜单项添加 — 在 `QuickNoteDetailScreen` 的 `AppActionDropdown` buildList 中添加"拆解"菜单项，条件：AI 已配置 + 非加载中；文案根据 entityRows 是否为空区分"拆解"/"重新拆解"
- [x] T4: 实体下划线渲染 — 将详情页正文从 `BasicTextField(readOnly=true)` 改为 `ClickableText`，构建 `AnnotatedString`：对 entityRows 中 spanStart >= title.length+1 的实体添加下划线 SpanStyle + StringAnnotation(tag="entity", annotation=entityKey)；重叠实体按 span 长度降序处理
- [x] T5: 实体点击 → BottomSheet — 点击实体时根据 annotation 查出 NoteEntityRow，弹出 `ModalBottomSheet` 展示该实体的关联笔记（过滤 ENTITY_HIT + sharedEntities 含当前 entityKey）；点击关联笔记导航到详情页；空态提示
- [x] T6: 缓存加载 — 进入详情页时，`LaunchedEffect(noteId)` 查询 `entityDao.getByNoteId(noteId)` 填充 `entityRows`，有记录则直接展示下划线
- [x] T7: 编译 + ktlint + 单测验证 — `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全部通过
