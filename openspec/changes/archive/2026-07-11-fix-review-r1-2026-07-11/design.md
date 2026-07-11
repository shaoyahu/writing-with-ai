# Design · fix-review-r1-2026-07-11

## 1. CRITICAL · attachment Phase 2 + 3 接入

**问题**: `attachment-inline-render` tasks.md 标完成但详情屏 `Text(note.content)` 仍用原始 `Text`,lightbox 三文件全缺,nav route 无。

**根因**: tasks 写了 "把 `Text(note.content)` 改 `InlineMarkdownText`" 但实施时只跑完 Phase 1(parser + DAO + InlineMarkdownText 文件),Phase 2 详情屏接线 + Phase 3 lightbox 全跳过。

**方案**:

1. `QuickNoteDetailScreen`:
   - 正文区域 `Text(note.content, ...)` → `InlineMarkdownText(note.content, attachmentDao, onAttachmentClick = { navController.navigate("attachment_lightbox/$it") })`
   - 传 `attachmentDao = hiltViewModel<QuickNoteDetailViewModel>().attachmentDao`,Dao 通过 `EntryPoint` 注入辅助(避免 ViewModel 膨胀)
2. 新建 `feature/quicknote/lightbox/AttachmentLightboxScreen.kt`:
   - `@Composable fun AttachmentLightboxScreen(attachmentId: String, onBack: () -> Unit)`
   - 全屏 `Box(Modifier.fillMaxSize().background(Black).clickable(onClick=onBack))` + `AsyncImage(model=file, contentScale=ContentScale.Fit)`
   - 顶栏 IconButton `Close` 调用 `onBack`
   - 底部 footer 显示 `file.name + sizeFmt(file.length())`
   - 状态: `LaunchedEffect(attachmentId) { loadFile(attachmentId) }`
3. 新建 `AttachmentLightboxViewModel` 提供 `file: StateFlow<File?>`,从 `NoteAttachmentDao` 查 `localPath`,IO dispatcher 校验存在
4. `AppNav.kt` 加 `@Serializable data object AttachmentLightboxRoute` + `composable("attachment_lightbox/{id}")` + `composable<AttachmentLightboxRoute>`
5. 字符串: `quicknote_attachment_lightbox_close` / `quicknote_attachment_lightbox_size_fmt` / `quicknote_attachment_image_load_failed` 双语齐全

## 2. HIGH · MarkdownRenderer dropLast 删 + ProviderCostStore setCostRate 守卫 + UsagePeriod 时区修正 + AiHistoryDaoTest

**2.1 `core/ui/MarkdownRenderer.kt:331`**:

```kotlin
// 删除:
if (s.endsWith(")")) s = s.dropLast(1)
// 替换:parser 已确保合法 ID,删无条件跳过收尾 ')' 让恶意 ID 走 Text 段
```

**2.2 `core/prefs/ProviderCostStore.kt:42 setCostRate`**:

```kotlin
suspend fun setCostRate(providerId: String, input: Double, output: Double) {
    require(input.isFinite() && input >= 0) { "input rate not finite or negative: $input" }
    require(output.isFinite() && output >= 0) { "output rate not finite or negative: $output" }
    prefs.edit { putFloat("${providerId}_input", input.toFloat()).putFloat("${providerId}_output", output.toFloat()) }
}
```

`setCostRate` 入口加 `isFinite()` + 非负校验,Float 溢出 / NaN 进 prefs 直接抛 IllegalArgumentException。

**2.3 `core/data/repo/UsagePeriod.kt:43 startOfDayMillis`**:

```kotlin
// 旧 (本地时区零点):
Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY,0); ... }.timeInMillis

// 新 (UTC epoch day):
private const val DAY_MS = 86_400_000L
fun startOfDayMillis(ms: Long): Long = (ms / DAY_MS) * DAY_MS
```

与 SQL `GROUP BY (createdAt / 86400000)` 对齐,统一 UTC epoch day bucket。

**2.4 新增 `app/src/androidTest/java/.../core/data/db/AiHistoryDaoTest.kt`**:

```kotlin
@RunWith(AndroidJUnit4::class)
class AiHistoryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: AiHistoryDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.aiHistoryDao()
    }

    @Test fun observeByGroupId_returnsOnlyMatchingRows() = runTest {
        // insert 2 rows groupId=A, 1 row groupId=B
        // assert observeByGroupId(A).first().size == 2
    }

    @Test fun observeByNoteId_doesNotLeakAcrossNotes() = runTest {
        // insert row(n1) + row(n2)
        // assert observeByNoteId(n1).first().single().noteId == n1
    }

    @Test fun nullNoteId_historyNotPolluted() = runTest {
        // insert null-noteId row
        // assert observeAll(100).first().size == 1 + null entry excluded from observeByNoteId(n)
    }

    @After fun tearDown() = db.close()
}
```

依赖已在 `app/build.gradle.kts` 加 `androidx.room:room-testing`。

## 3. HIGH · a11y 三处: UsageBarChart / NoteGraphCanvas / InlineMarkdownText

**3.1 `feature/aiwriting/usage/UsageBarChart.kt:38`**:

```kotlin
val chartA11y = stringResource(R.string.aiwriting_usage_chart_a11y)
Canvas(modifier = Modifier.semantics {
    contentDescription = chartA11y
    // per-bucket live region
}) { ... }
// bucket.tokens > 0 时每柱加 Modifier.semantics { contentDescription = "${date} ${tokens} tokens" }
```

资源新增:
- `aiwriting_usage_chart_a11y` — "AI 用量日条形图"
- per-bucket a11y 文案走 `aiwriting_usage_chart_bucket_fmt` 格式串

**3.2 `feature/quicknote/graph/NoteGraphCanvas.kt:84-180`**:

```kotlin
Canvas(modifier = Modifier
    .fillMaxSize()
    .semantics(mergeDescendants = true) {
        contentDescription = buildString {
            append(stringResource(R.string.note_graph_a11y_summary))
            appendLine()
            nodes.forEach { appendLine(stringResource(R.string.note_graph_node_fmt, it.title)) }
            edges.forEach { appendLine(stringResource(R.string.note_graph_edge_fmt, ...)) }
        }
    }
) { ... }
```

**3.3 `core/ui/MarkdownText.kt:148`**:

```kotlin
AsyncImage(
    model = file,
    contentDescription = stringResource(R.string.quicknote_attachment_image_a11y, attachmentEntity.originalName),
    ...
)
fallback when failed:
Text(text = stringResource(R.string.quicknote_attachment_image_load_failed), modifier = Modifier.semantics { contentDescription = same })
```

## 4. MEDIUM (13 项)

| # | 位置 | 改 |
| -- | -- | -- |
| 4.1 | `core/note/graph/ForceLayout.kt:32` | `tolerance = 0.5` → `tolerance = 0.05`,与 paper 一致 |
| 4.2 | `core/note/graph/GraphDataLoader.kt:81` | 2-hop 真用 `noteDao.getByIds(ids)`,删 TODO;无 notes 返回空 |
| 4.3 | `feature/quicknote/detail/QuickNoteDetailScreen.kt` | `Log.d(...)` 加 `if (BuildConfig.DEBUG)` 守卫 |
| 4.4 | `core/notification/BootReceiver.kt` | `enabled=false` 分支调 `scheduler.cancel()` |
| 4.5 | `feature/morningfreewrite/MorningFreewriteScreen.kt` `BackHandler` | 三态(dirty / saving / clean)统一走 `showExitDialog`,`enabled = state.isDirty` |
| 4.6 | `core/notification/Scheduler.kt` | `canScheduleExactAlarms()==false` 弹 `AlertDialog` 跳 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| 4.7 | `core/notification/Notifier.kt` | channel description 与 body 分开,description 走 `setDescription(...)` 一次,body 每通知设 |
| 4.8 | `feature/aiwriting/streaming/StreamingPanel.kt` | LaunchedEffect key 用 `position + accumulatedLength` 不用 `position` |
| 4.9 | `AppDatabaseMigrationTest.kt` | 加 `@Test fun freshInstall_v14_matchesLatestSchema()` |
| 4.10 | `core/note/graph/ForceLayout.kt:130` | 排斥力公式 `1 / (dist * dist)` 改 paper 一致 `1.0 / dist` + damping |
| 4.11 | `core/note/graph/CircularLayout.kt` | `angle = hash % 360` 改 `(hash.toLong() and 0xFFFFFFFFL).toDouble() / 4294967295.0 * 2 * PI` 保证 [0, 2π) |
| 4.12 | `MorningFreewriteScreen.kt:NoProvider` 入口 | 跳 `navController.navigate("settings/model_management")` 而不是 toast |
| 4.13 | `ForceLayout` / `CircularLayout` 类 | 加 `@Inject constructor()`,`GraphModule` 用 `@Binds` |

## 5. 验证矩阵

| check | 命令 |
| -- | -- |
| 编译 | `./gradlew :app:assembleDebug` |
| ktlint | `./gradlew :app:ktlintCheck` |
| 单测 | `./gradlew :app:testDebugUnitTest` |
| androidTest(JVM) | `./gradlew :app:testDebug` 含 AiHistoryDaoTest |
| 仪器 | `./gradlew :app:connectedDebugAndroidTest` 含 freshInstall v14 |

全绿后才走 archive。