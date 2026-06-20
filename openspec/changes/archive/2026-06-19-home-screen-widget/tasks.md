## 1. NoteRepository 扩展

- [ ] 1.1 在 `core/data/repo/NoteRepository.kt` 新增 `fun observeRecent(limit: Int): Flow<List<Note>>`,内部走 `noteDao.observeAll().map { it.take(limit).map { e -> e.toModel() } }`
- [ ] 1.2 加 JUnit5 + Turbine 测试 `core/data/repo/NoteRepositoryObserveRecentTest.kt`:mock `noteDao.observeAll()` emit 5 条 → 验 `observeRecent(3)` emit 头 3 条,按 updatedAt desc

## 2. Glance widget 主体

- [ ] 2.1 新建 `core/widget/QuickNoteWidget.kt`:`class QuickNoteWidget : GlanceAppWidget()`,`sizeMode = SizeMode.Single`,`provideGlance(context, id)` 内 `currentSize = context.getGlanceState(...)` + 根据 width 切 2x2 / 4x2 渲染:
  - 2x2:Column(title="随手记",中部 1 条 note(title + relativeTime),底部 "+" 按钮 → `actionRunCallback<OpenNoteAction>(...)`)
  - 4x2:Column(title="随手记",中部 3 条 note(每条 `actionRunCallback<OpenNoteAction>(KEY_NOTE_ID=noteId)`),右上"+"按钮)
- [ ] 2.2 颜色走 `ColorProvider(R.color.xxx)`,**不**直接 `androidx.compose.foundation.layout.*`
- [ ] 2.3 empty 状态走 `R.string.widget_empty`;note 文案 `note.title.ifBlank { note.content.take(Note.TITLE_FALLBACK_LEN) }` + `formatRelativeTime(note.updatedAt)`

## 3. widget receiver + ActionCallback

- [ ] 3.1 新建 `core/widget/QuickNoteWidgetReceiver.kt`:`class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver()` + `override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()`
- [ ] 3.2 新建 `core/widget/OpenNoteAction.kt`:`class OpenNoteAction : ActionCallback` + `companion object { val KEY_NOTE_ID = ActionParameters.Key<String>("noteId") }` + `override suspend fun onAction(context, glanceId, parameters) { val noteId = parameters[KEY_NOTE_ID]; context.startActivity(MainActivity intent + extra route "quicknote/detail/$noteId") }`

## 4. widget 数据源

- [ ] 4.1 新建 `core/widget/QuickNoteWidgetRepository.kt`:`@Singleton class QuickNoteWidgetRepository @Inject constructor(private val noteRepository: NoteRepository) { fun observeRecent(limit: Int): Flow<List<Note>> = noteRepository.observeRecent(limit) }`
- [ ] 4.2 `provideGlance` 内 `val state = currentState<Preferences>(); val notes = noteRepository.observeRecent(3).first()`(每次 updateAll 重拉)

## 5. widget 刷新器(主路径)

- [ ] 5.1 新建 `core/widget/QuickNoteWidgetUpdater.kt`:`@Singleton class QuickNoteWidgetUpdater @Inject constructor() { suspend fun updateAll(context: Context) { withContext(Dispatchers.IO) { QuickNoteWidget().updateAll(context) } } }`
- [ ] 5.2 接入主路径触发点:
  - `core/data/repo/NoteRepository.kt` `upsert` / `delete` 末尾调 `WidgetUpdater.updateAll(context)`(通过 Hilt `Lazy<Context>` 注入 `ApplicationContext`)
  - `feature/quicknote/detail/QuickNoteDetailViewModel.kt` `delete` 后调
  - `feature/aiwriting/streaming/AiActionViewModel.kt` `acceptReplace` 在 `_state.value = Idle` **之前**调
- [ ] 5.3 加 JUnit5 测试 `QuickNoteWidgetUpdaterTest.kt`:Mock `QuickNoteWidget().updateAll(context)` → 调 `WidgetUpdater.updateAll(context)` 不抛异常

## 6. widget 刷新器(WorkManager 兜底)

- [ ] 6.1 加 `androidx.work:work-runtime-ktx` 到 `gradle/libs.versions.toml`(version 2.9.x 与 BOM 兼容)
- [ ] 6.2 加 `androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx" }` library entry + `testImplementation` 用 `androidx-work-testing`
- [ ] 6.3 新建 `core/widget/QuickNoteWidgetWorker.kt`:`class QuickNoteWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) { override suspend fun doWork(): Result { QuickNoteWidget().updateAll(applicationContext); return Result.success() } }`
- [ ] 6.4 改 `app/WritingApp.kt`:`onCreate()` 末尾注册周期任务:
  ```kotlin
  val periodic = PeriodicWorkRequestBuilder<QuickNoteWidgetWorker>(15, TimeUnit.MINUTES).build()
  WorkManager.getInstance(this).enqueueUniquePeriodicWork("quicknote-widget-refresh", ExistingPeriodicWorkPolicy.KEEP, periodic)
  ```
- [ ] 6.5 加 JUnit5 测试 `QuickNoteWidgetWorkerTest.kt`:`TestListenableWorkerBuilder<QuickNoteWidgetWorker>` 跑 doWork,验 success

## 7. widget 资源

- [ ] 7.1 新建 `app/src/main/res/xml/widget_info.xml`:
  ```xml
  <appwidget-provider xmlns:android="..."
      android:minWidth="160dp" android:minHeight="160dp"
      android:targetCellWidth="2" android:targetCellHeight="2"
      android:resizeMode="horizontal|vertical"
      android:updatePeriodMillis="0"
      android:initialLayout="@layout/widget_initial"
      android:previewLayout="@drawable/widget_preview"
      android:widgetCategory="home_screen" />
  ```
- [ ] 7.2 新建 `app/src/main/res/layout/widget_initial.xml`:Glance 极简占位(纯背景色 + 居中文字"加载中…")
- [ ] 7.3 新建 `app/src/main/res/drawable/widget_preview.xml`:widget 添加预览图(矩形 + "随手记"标题 + 1 条模拟笔记 + "+"按钮)

## 8. AndroidManifest 注册

- [ ] 8.1 改 `app/src/main/AndroidManifest.xml` `<application>` 内加:
  ```xml
  <receiver
      android:name=".core.widget.QuickNoteWidgetReceiver"
      android:exported="false"
      android:label="@string/widget_2x2_title">
      <intent-filter>
          <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
      </intent-filter>
      <meta-data
          android:name="android.appwidget.provider"
          android:resource="@xml/widget_info" />
  </receiver>
  ```
- [ ] 8.2 加权限 `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`(可选,boot 后让 widget WorkManager 自启;WorkManager 已自带,本 change 不强求)

## 9. MainActivity 解析 widget route

- [ ] 9.1 改 `app/MainActivity.kt`(若文件不存在则新建):
  ```kotlin
  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContent {
              WritingAppTheme {
                  AppNav(initialRoute = intent?.getStringExtra("route"))
              }
          }
      }
  }
  ```
- [ ] 9.2 改 `app/AppNav.kt` 加 `initialRoute: String? = null` 参数;若 `initialRoute == "quicknote/edit?prefillFocus=true"` → `navController.navigate(QuicknoteEdit(prefillFocus = true))`;若 `"quicknote/detail/n1"` → `navController.navigate(QuicknoteDetail("n1"))`;否则走默认 `startDestination = QuicknoteList`

## 10. AppNav route + prefillFocus

- [ ] 10.1 改 `app/AppNav.kt` `QuicknoteEdit` data class:
  ```kotlin
  @Serializable data class QuicknoteEdit(val id: String? = "NEW", val prefillFocus: Boolean = false)
  ```
- [ ] 10.2 改 `composable<QuicknoteEdit>` block 解析 `prefillFocus`,透传给 `QuickNoteEditorScreen`

## 11. 编辑器屏 prefillFocus

- [ ] 11.1 改 `feature/quicknote/edit/QuickNoteEditorScreen.kt`:加 `prefillFocus: Boolean = false` 参数 + `FocusRequester` + `LaunchedEffect(prefillFocus) { if (prefillFocus) focusRequester.requestFocus() }`
- [ ] 11.2 `OutlinedTextField` 绑 `Modifier.focusRequester(focusRequester)`
- [ ] 11.3 `QuickNoteEditorViewModel` 不需要改(参数是 UI 一过性)

## 12. PendingIntent + TaskStackBuilder

- [ ] 12.1 新建 `core/widget/WidgetIntents.kt`:
  ```kotlin
  fun createNotePendingIntent(context: Context): PendingIntent {
      val intent = Intent(context, MainActivity::class.java)
          .putExtra("route", "quicknote/edit?prefillFocus=true")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      return TaskStackBuilder.create(context)
          .addNextIntentWithParentStack(intent)
          .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!
  }
  fun openNotePendingIntent(context: Context, noteId: String): PendingIntent {
      val intent = Intent(context, MainActivity::class.java)
          .putExtra("route", "quicknote/detail/$noteId")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      return TaskStackBuilder.create(context)
          .addNextIntentWithParentStack(intent)
          .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!
  }
  ```
- [ ] 12.2 `QuickNoteWidget` 内 "+" 按钮调 `createNotePendingIntent(context)`;笔记项 `actionRunCallback<OpenNoteAction>(parametersOf(OpenNoteAction.KEY_NOTE_ID to noteId))`

## 13. i18n

- [ ] 13.1 改 `app/src/main/res/values/strings.xml` 加 5 个 `widget_*` 中文 key:
  - `widget_2x2_title = "随手记"`
  - `widget_4x2_title = "随手记"`
  - `widget_empty = "还没有笔记\n点 + 创建第一条"`
  - `widget_create_cd = "新建笔记"`
  - `widget_open_note_cd = "打开笔记 %1$s"`
- [ ] 13.2 改 `app/src/main/res/values-en/strings.xml` 加 5 个对应英文 TODO 占位

## 14. ktlint + Compose PascalCase

- [ ] 14.1 跑 `./gradlew :app:ktlintCheck` → 已知 PascalCase follow-up 之外,0 新增;新 Composable 函数 PascalCase(`QuickNoteWidget` / `OpenNoteAction` 等)

## 15. 整体验收

- [ ] 15.1 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] 15.2 `./gradlew :app:testDebugUnitTest` → M1+M2+M3+M4-1 测试全绿
- [ ] 15.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [ ] 15.4 `./gradlew :app:ktlintCheck` → 0 新增违规
- [ ] 15.5 手工冒烟(Pixel Launcher / AOSP 模拟器):
  - 长按桌面 → Widgets → 找到"随手记" → 拖到桌面 → 显示"还没有笔记"
  - 编辑器屏(从 App 内)新建一条笔记 → 保存 → back → widget 显示该笔记
  - 长按 widget resize 到 4x2 → 显示 1 条笔记(只有 1 条)
  - 编辑器再创建 3 条笔记 → resize 到 4x2 → 显示 3 条
  - 点 widget 笔记项 → 启动 MainActivity 到该笔记详情 → back → launcher
  - 点 widget "+" → 启动 MainActivity 到编辑器 → 输入框自动 focus → 键入文字 → 保存 → back → launcher + widget 更新
- [ ] 15.6 手工冒烟(WorkManager):App kill → 桌面 widget 显示笔记 → 等待 15min(用 `./gradlew :app:installDebug` + `adb shell am broadcast` 模拟太繁琐,M5 polish 阶段验)→ widget 仍显示最新数据
- [ ] 15.7 README 更新:在 README 顶部"国产 ROM 适配状态"标注"widget 已在 AOSP / Pixel Launcher 验证,小米 / 华为 / OPPO / vivo 待 M5 polish"

## 16. OpenSpec 收尾(apply 通过 review 后做)

- [ ] 16.1 review 通过后,跑 `openspec archive home-screen-widget -y`
- [ ] 16.2 更新 `docs/progress.md`:M4-1 完成
- [ ] 16.3 在 `docs/plans/writing-with-ai-mobile-roadmap.md` §13 标注 M4-1 完成;§15.2 标 `home-screen-widget` done