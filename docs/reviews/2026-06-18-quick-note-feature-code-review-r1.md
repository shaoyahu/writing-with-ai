# code-review · quick-note-feature · r1

**Date:** 2026-06-18
**Subject:** `quick-note-feature`(M1 随手记闭环)
**Review type:** code-review
**Reviewer:** Claude(自审)— 3 个并行 reviewer(correctness / spec compliance / architecture convention)结果整合
**Change root:** `openspec/changes/archive/2026-06-18-quick-note-feature/`
**Spec:** `openspec/specs/quick-note/spec.md`(11 Requirement × 26 Scenario)

---

## 总结

| 维度 | 结果 |
| --- | --- |
| Build | ✅ `assembleDebug` / `lintDebug` / `testDebugUnitTest` 12 tests 全绿 |
| Spec 符合度 | ⚠️ 1 处明确违规 + 1 处可疑(spec §11 schema 未 git 追踪;§10 "404" 硬编码) |
| 正确性 | ⚠️ 4 个 HIGH bug / 6 个 MEDIUM |
| 架构 / 约定 | ✅ 包结构 / Hilt / R.string 规则全过;1 个 dead code + 3 个 polish |
| 整体 | **建议 r1 修 4 个 HIGH + 2 个 MEDIUM 再 r2;M1 不能裸进 M2** |

---

## 🔴 HIGH(必须修)

### H1. `return@collect` 不停止收集 — 编辑 ViewModel tags 订阅泄漏

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorViewModel.kt:46-51`

**问题:** `repository.observeNoteWithTags(existing.id).collect { item -> ...; return@collect }` — `return@collect` 只从 lambda 返回,**不**取消 Flow 收集。Room 持续推送,后续 `note_tags` 变更会覆盖 `tagsFlow`,把用户当前编辑的 tag 列表抹回数据库值。违反上方注释"tag 从 observeNoteWithTags 取一次即可"。

**修复:** 改 `.first()` 一次性读:
```kotlin
val item = repository.observeNoteWithTags(existing.id).first()
if (item != null) tagsFlow.value = item.tags
loadedFlow.value = true
```
(加 `import kotlinx.coroutines.flow.first`)

### H2. 编辑既有笔记 — init 加载与用户输入的竞态

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorViewModel.kt:37-59`

**问题:** `loadedFlow` 初始 `false` 让保存按钮 disabled,但 `titleFlow` / `contentFlow` 可编辑;用户在加载完成前输入 → init 触发 `titleFlow.value = existing.title` 静默覆盖。与 H1 同源。

**修复方向(任一):**
- (A) `init` 启动时记录 `hadUserInput = titleFlow.value.isNotEmpty() || contentFlow.value.isNotEmpty()`,只有未输入时才回填
- (B) `take(1).first()` + 加载完才允许编辑(进度条 + 灰显输入框)

推荐 (A),UX 更顺。

### H3. `requireNotNull("id")` — process-death / 深链场景 crash

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailViewModel.kt:25-26`

**问题:** 类型安全路由正常情况总能注入 `id`,但 saved state 跨版本升级 / 深链 / 自定义 NavGraph 后置回调等场景可能缺失 → IAE 直接 crash 进程。

**修复:**
```kotlin
private val routeId: String? = savedStateHandle.get<String>("id")
val uiState: StateFlow<NoteDetailUiState> =
    if (routeId.isNullOrBlank()) {
        MutableStateFlow(NoteDetailUiState.NotFound).asStateFlow()
    } else {
        repository.observeNoteWithTags(routeId)...
    }
```

### H4. 搜索 LIKE 通配符未转义 — 用户输入 `%` / `_` 触发通配符

**文件:** `app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt:51-52` + `app/src/main/java/com/yy/writingwithai/core/data/db/NoteDao.kt:54-61`

**问题:** `val q = "%${query.trim()}%"` 后直接传 `LIKE :q`。用户搜 `100%` 命中所有包含 `100` 的行;搜 `a_b` 命中 `axxb`。**不是** SQL 注入(parameterized),但**行为错误**。

**修复:**
```kotlin
// Repository
val escaped = query.trim()
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")
val q = "%$escaped%"

// DAO
@Query("""
    SELECT * FROM notes
    WHERE title LIKE :q ESCAPE '\' OR content LIKE :q ESCAPE '\'
    ORDER BY isPinned DESC, updatedAt DESC
""")
fun search(q: String): Flow<List<NoteEntity>>
```

### H5. 分享 Intent 不处理 `ActivityNotFoundException`

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/share/ShareNote.kt:36`

**问题:** `startActivity(chooser)` 在没有 app 能处理 `ACTION_SEND text/markdown` 时(Android TV、极简 ROM)抛 ANFE,UI crash。

**修复:**
```kotlin
try {
    startActivity(chooser)
} catch (e: android.content.ActivityNotFoundException) {
    android.widget.Toast.makeText(
        this, getString(R.string.quicknote_share_no_app),
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}
```
(toast 文案走 R.string,新增 `quicknote_share_no_app`)

### H6. Spec §11 schema JSON 未 git 追踪

**问题:** `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/1.json` 在磁盘上生成,但 `git status` 视其为 untracked。spec §11 Scenario 1 明确要求"存在并被 git 追踪"。

**修复:** 用户 commit 前手动 `git add app/schemas/`。本 change 不自动 commit(CLAUDE.md),仅提醒。

---

## 🟡 MEDIUM(应该修)

### M1. 详情页硬编码 `"404"`

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt:138`

**修复:**
- `values/strings.xml`: `<string name="quicknote_detail_not_found">笔记不存在</string>`
- `values-en/strings.xml`: 真实英文翻译(不是 TODO 占位,这是用户可能撞到的提示)
- Composable: `Text(text = stringResource(R.string.quicknote_detail_not_found))`

### M2. `fallbackToDestructiveMigration()` 装到 release

**文件:** `app/src/main/java/com/yy/writingwithai/core/data/di/DataModule.kt:34`

**问题:** 注释写"正式发版前必须移除并改走 Migration",但 release flavor 也走了这条路径。一旦 v1.0.0 发版后再加字段,用户笔记被静默 wipe。

**修复:** `BuildConfig.DEBUG` gate(需 `buildFeatures.buildConfig = true`):
```kotlin
val builder = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
if (BuildConfig.DEBUG) builder.fallbackToDestructiveMigration()
builder.build()
```

### M3. `observeAllTags()` 在 inner combine — 切 tag 时列表闪烁

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/list/QuickNoteListViewModel.kt:31-58`

**问题:** `flatMapLatest` 内部把 `observeAllTags()` 和 `observeNotesWithTags()` 一起 `combine`,每次 query 或 selectedTag 变化 cancel 旧 inner Flow,Room 重新订阅 `observeAllTags()` 首次 emit `[]` → 列表瞬间空。

**修复:** 把 `observeAllTags()` 提升到外层,只订阅一次:
```kotlin
val uiState: StateFlow<NoteListUiState> =
    combine(
        combine(query, selectedTag) { q, tag -> q to tag }
            .flatMapLatest { (q, tag) -> repository.observeNotesWithTags(q, tag) },
        repository.observeAllTags(),
    ) { notes, allTags ->
        // 合并映射
    }.stateIn(...)
```

### M4. `TITLE_FALLBACK_LEN` 重复

**文件:** `feature/quicknote/list/NoteRow.kt:114` 定义 `private const val TITLE_FALLBACK_LEN = 30`,但 `feature/quicknote/detail/QuickNoteDetailScreen.kt:155` 用裸 `30` literal。

**修复:** 提升到 `core/data/model/Note.kt` companion:
```kotlin
data class Note(...) {
    companion object {
        const val TITLE_FALLBACK_LEN = 30
    }
}
```

### M5. `TagRepository` 是 dead code(YAGNI)

**文件:** `app/src/main/java/com/yy/writingwithai/core/data/repo/TagRepository.kt`

**问题:** 4 行 facade,只 delegate `NoteRepository.observeAllTags()`。`QuickNoteListViewModel` 注入 `NoteRepository`,无消费者。

**修复:** 删除文件,`observeAllTags()` 已在 `NoteRepository`。M2+ 标签加 metadata 时再独立建 `TagRepository`。

### M6. 删除时 back 退出 → 删除被取消

**文件:** `feature/quicknote/detail/QuickNoteDetailViewModel.kt:47-52` + `feature/quicknote/detail/QuickNoteDetailScreen.kt:191-207`

**问题:** 用户点确认 → `viewModelScope.launch { delete(); onDeleted() }` 启动 → 立刻按 back → ViewModel cleared → `viewModelScope.cancel()` → 删除协程被取消 → 笔记没被删(dialog 已 dismiss,UI 看不出来)。

**修复:** 用 `withContext(NonCancellable)` 包裹删除:
```kotlin
fun delete(onDeleted: () -> Unit) {
    viewModelScope.launch {
        withContext(kotlinx.coroutines.NonCancellable) {
            repository.delete(noteId)
        }
        onDeleted()
    }
}
```

---

## 🟢 LOW(polish / 后续)

| # | 项 | 文件 |
| --- | --- | --- |
| L1 | `"#$tag"` 前缀分散在 4 处;考虑抽 helper | NoteRow / QuickNoteListScreen / QuickNoteDetailScreen / TagInputRow |
| L2 | `LAST_AI_OP` / `LAST_AI_AT` 字段在 M1 全 null(spec 预留给 M2,合规) | Note.kt / NoteEntity.kt |
| L3 | `DateFormat.getDateTimeInstance` 用默认 locale,未走 `LocalConfiguration` | NoteRow.kt |
| L4 | `togglePinned` 双击无 guard | QuickNoteDetailViewModel.kt |
| L5 | `NoteListUiState.Loading` 是 `data object` 但显式 override `query/selectedTag`,与 interface 默认值重复 | NoteListUiState.kt |
| L6 | `RepositoryModule.kt` 空 placeholder 类,无 `@Binds`,纯注释 | core/data/di/RepositoryModule.kt |
| L7 | `5_000L`(SharingStarted.WhileSubscribed timeout)在 3 个 VM 重复;提升到 `core/common/SharingConstants.kt` | List/Detail/Editor VMs |
| L8 | `androidx.appcompat` 引入但 M1 不直接用,留 M5 清理 | app/build.gradle.kts |
| L9 | WordCount 用 `一-鿿`(基本平面),扩展 A/B 漏算;M1 笔记量预期小,可接受 | WordCount.kt |
| L10 | Editor 允许保存 title/content 都空的"占位笔记";spec 未禁止 | QuickNoteEditorViewModel.kt |
| L11 | `data object` Loading 与 `Empty(query="", selectedTag=null)` 行为重复 | NoteListUiState.kt |

---

## 🟢 ✅ 验证通过项(共 10 项)

1. **包结构** — 与 CLAUDE.md §"包结构" 完全一致
2. **Composable PascalCase** — 所有 `@Composable fun Foo(...)` PascalCase;`internal fun Context.shareNoteMarkdown` 正确用 camelCase(非 Composable)
3. **硬编码中文扫描** — Composable 内 0 个用户可见中文字符串(除 M1 待修的 `"404"`)
4. **R.string key parity** — 27 unique `quicknote_*` key 在代码、zh、en 三处 1:1:1
5. **UI ↔ DB 边界** — `feature/` 下零 `core.data.db.entity` 直接 import,全部走 `core.data.model.NoteWithTags`
6. **Hilt 模式** — `DataModule` 走 `@Provides @Singleton`,Repository 走 `@Inject constructor @Singleton`
7. **Compose state hoisting** — `NoteRow` / `TagInputRow` 无状态;`*Screen` 持有 ViewModel
8. **Coroutine scope** — 全 main 源集零 `GlobalScope`,只 `viewModelScope.launch`
9. **未使用 import** — 重构后无残留
10. **`internal` 修饰合理性** — `NoteMapper`、`WordCount.cjkCount/englishWordCount`、`Context.shareNoteMarkdown` 全部合理 scope

---

## 修复优先级建议

按"代码改动小 / 影响大"排序:

1. **H1 + H2**:`return@collect` → `.first()` + 阻止用户输入被覆盖 — 一个文件,15 行
2. **H3**:`requireNotNull` 改 `routeId?.let { ... } ?: NotFound` — 一个文件,5 行
3. **H4**:`NoteRepository.search` 加 ESCAPE — 两个文件,各 3 行
4. **H5**:try/catch `ActivityNotFoundException` — 一个文件,5 行
5. **M1**:`"404"` 走 R.string — 一个文件 + 两个 strings.xml 条目
6. **M3**:`observeAllTags` 提升外层 — 一个文件,重组 20 行

---

## 后续

- 本 review 是 r1;HIGH 修完后再开 **r2**(重点验 H1-H5 是否清干净、M1/M3 是否落地)
- M2 `ai-abstraction-layer` 起 change 前,确认 HIGH 全清、MEDIUM 至少 M1/M3 清
- 已识别的 spec 改动建议:任何 agent 提出但 spec 没改的(H3 涉及 spec §"Navigation routes" 可考虑补 `NotFound` 路由行为)
