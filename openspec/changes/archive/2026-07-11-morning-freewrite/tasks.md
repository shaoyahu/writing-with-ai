# morning-freewrite Tasks

> 总工期估算:7 工作日(1 通知 + 1.5 屏 + 1.5 AI 链 + 1 设置 + 1 单测 + 1 review/polish)
> 严格按本文件顺序实现,前一步阻塞后一步。

## 阶段 1 — 通知基础设施(Day 1,~1d)

- [x] 1.1 在 `core/notification/` 目录新增 `MorningFreewriteNotifier.kt`(@Singleton,Hilt 注入):封装 `createChannel()`(id=`morning_freewrite`,IMPORTANCE_DEFAULT)+ `showDailyReminder(date: LocalDate)` 构造 `NotificationCompat.Builder` + `TaskStackBuilder` PendingIntent(Intent extra `route=freewrite/{date}` + `FLAG_ACTIVITY_NEW_TASK`)
- [x] 1.2 同目录新增 `MorningFreewriteScheduler.kt`(@Singleton):暴露 `schedule(hour, minute, date)` / `cancel()` / `nextTriggerAt(hour, minute, now): ZonedDateTime` 三个 API;内部走 `AlarmManager.setExactAndAllowWhileIdle` + `setAndAllowWhileIdle` fallback(API 31+ `canScheduleExactAlarms() == false` 时);`nextTriggerAt` 用 `ZoneId.systemDefault()` 不写死 UTC,跨天计算
- [x] 1.3 同目录新增 `BootReceiver.kt`(`@AndroidEntryPoint` BroadcastReceiver):`onReceive` 读 `UserPrefsStore.morningFreewriteEnabledFlow.first()` + hour/minute,enabled 时调 `scheduler.schedule(hour, minute, today)`
- [x] 1.4 `AndroidManifest.xml` 加 2 个 uses-permission:`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`;注册 `BootReceiver`(intent-filter `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`,`exported=true`)
- [x] 1.5 `WritingApp.onCreate` 末尾调 `notifier.createChannel()`(idempotent,SDK >= 26 才有意义但调了无害)
- [x] 1.6 单测 `MorningFreewriteSchedulerTest`(JVM):覆盖 `nextTriggerAt` 5 个 case — 跨天 / 同一天未来 / 同一天过去 / hour=23 / minute=59

## 阶段 2 — Prefs + Settings 入口(Day 2,~1d)

- [x] 2.1 `core/prefs/UserPrefsStore.kt` interface + Impl 加 3 个 key:`morningFreewriteEnabledFlow: Flow<Boolean>`(默认 false)+ `morningFreewriteTimeFlow: Flow<LocalTime>`(默认 08:00)+ setter 方法;companion object 加 `KEY_MORNING_FREEWRITE_ENABLED_V1` / `KEY_MORNING_FREEWRITE_HOUR_V1` / `KEY_MORNING_FREEWRITE_MINUTE_V1`;`KEY_*` 公开给 `FakeUserPrefsStore` 复用
- [x] 2.2 `app/src/test/java/.../core/prefs/FakeUserPrefsStore.kt` 加 3 个字段占位(默认值,实现可变 state 给单测用)
- [x] 2.3 `app/src/main/java/com/yy/writingwithai/app/AppNav.kt` 加 `@Serializable data object SettingsFreewrite` + `composable<SettingsFreewrite> { SettingsFreewriteScreen(onBack = { navController.popBackStack() }) }`
- [x] 2.4 `app/src/main/java/com/yy/writingwithai/feature/settings/freewrite/SettingsFreewriteScreen.kt` + `ViewModel` 新文件:Switch toggle(开 → 弹权限 launcher + 调 scheduler)+ TimePicker(改时间 → cancel + 重新 schedule)+ (权限拒绝时)"去系统设置"按钮(`Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)`)
- [x] 2.5 `feature/settings/SettingsScreen.kt` 在 items 列表 append "每日晨写" 项(icon=`Icons.Outlined.Alarm`,文案 `R.string.freewrite_settings_title`,onClick → `navController.navigate(SettingsFreewrite)`)
- [x] 2.6 加 `AppNavSettingsRoutes.kt`(若尚无 freewrite 项)的注册入口(M12 fix 模式;若 `SettingsFreewrite` 已在 `settingsNavRoutes` 内则跳过)

## 阶段 3 — Freewrite 沉浸屏(Day 3-4,~1.5d)

- [x] 3.1 `app/src/main/java/com/yy/writingwithai/feature/freewrite/MorningFreewriteScreen.kt`:`WindowCompat.setDecorFitsSystemWindows(window, false)` + `WindowInsetsControllerCompat.hide(systemBars)`(DisposableEffect 控制生命周期);布局 = 右上角倒计时(`MM:SS` 格式,30s 内 tertiary 色,≤10s error 色)+ Borderless `BasicTextField`(`MaterialTheme.typography.headlineMedium`,weight=1f,自动 focus)+ 底部 "完成" / "跳过" 按钮(条件显示,前 30 秒跳过按钮隐藏避免误触)
- [x] 3.2 同目录 `MorningFreewriteViewModel.kt`:`FreewriteUiState` sealed interface(`Writing` / `Polishing` / `Organizing` / `Saved` / `Failed`);持 `_state` + `_secondsLeft: MutableStateFlow<Int>` 初值 300;`init` 跑 `tick()` 协程每秒 `delay(1000)`,归零调 `finish()`;暴露 `setContent(s)` / `finish()` / `dismiss()` / `skip()`
- [x] 3.3 同目录 `FreewriteEntry.kt`(`object` 暴露 `rememberMorningFreewriteViewModel(date): MorningFreewriteViewModel`,封装 `hiltViewModel` + Saver,跨 feature 入口,M3 既有 `AiwritingEntry` 同款模式)
- [x] 3.4 `app/src/main/java/com/yy/writingwithai/app/AppNav.kt` 加 `@Serializable data class MorningFreewrite(val date: String)` + `composable<MorningFreewrite> { ... MorningFreewriteScreen(...) }`;`WidgetLaunchRoute` sealed 加 `data class Freewrite(val date: String)` + `navigatePendingRoute` 加 when 分支
- [x] 3.5 `MainActivity.onCreate` 解析 `intent.getStringExtra("route")`,若 `route?.startsWith("freewrite/")` → `widgetPendingRoute.value = WidgetLaunchRoute.Freewrite(date = route.substringAfter("freewrite/"))`(M4-4 既有模式复用,MainActivity 内部应该已有 widget pending route 写入 helper)
- [x] 3.6 back 弹 `AlertDialog`(走 `BackHandler` 拦截,标题/正文/确认/取消走 i18n)

## 阶段 4 — AI 串行链 + 失败兜底(Day 4-5,~1.5d)

- [x] 4.1 `MorningFreewriteViewModel.finish()` 串行实现:`Polishing(content)` → `aiActionVm.start(POLISH, content, journalNoteId)` → 等 `Done` → `Organizing(polished)` → `aiActionVm.start(ORGANIZE, polished, journalNoteId)` → 等 `Done` → `Saved`;复用 `AiActionViewModel` 既有 `state` StateFlow collect 模式(M3 协议,**不**改 `AiActionViewModel` 本身)
- [x] 4.2 失败兜底:Polish `Failed` → `saveJournal(original=raw, polished=null, organized=null, fallback=true)`;Organize `Failed` → `saveJournal(original=raw, polished=polished, organized=null, fallback=true)`;两 op 都 Failed → 走 `saveJournal(original=raw, polished=null, organized=null, fallback=true)`(同 Polish Failed 路径,内容不丢)
- [x] 4.3 `_saveEvents: SharedFlow<SaveEvent>` 让屏弹 Snackbar(`Saved(fallback=true)` → `R.string.freewrite_fallback_toast`,`Saved(fallback=false)` → 成功提示 "已保存")
- [x] 4.4 无 provider apikey edge case:屏渲染时检测 `SecureApiKeyStore.observeConfiguredProviders().first().isEmpty()` → 直接渲染 `R.string.freewrite_no_provider_hint` + "去设置"按钮(跳 `SettingsModelManagement`,走 `onNavigateToModelManagement` 既有入口);**不**进入 5 分钟倒计时(避免用户写完后才发现没 AI)
- [x] 4.5 `core/data/repo/NoteRepository.kt` 加 `suspend fun createJournalEntry(noteId, title, content, lastAiOp, lastAiAt)`(走既有 `upsert + noteTagDao.add(NoteTagCrossRef)`)+ companion object 加 `const val JOURNAL_TAG = "journal"`
- [x] 4.6 单测 `MorningFreewriteViewModelTest`(Robolectric + Turbine):state machine Writing → Polishing → Organizing → Saved 路径 + Polish Failed 路径 + Organize Failed 路径 + 无 provider 路径

## 阶段 5 — i18n + 集成(Day 6,~0.5d)

- [x] 5.1 `values/strings.xml` 加 14 个 `freewrite_*` key(中文,权威)
- [x] 5.2 `values-en/strings.xml` 加 14 个 `freewrite_*` key(TODO 占位,既有模式)
- [x] 5.3 Composable 内 grep 中文硬编码 = 0:`grep -rE "[\\x{4e00}-\\x{9fff}]" app/src/main/java/com/yy/writingwithai/feature/freewrite/` → 必须 0 匹配
- [x] 5.4 通知 icon:`res/drawable/ic_notification.xml`(vector,沿用既有 `ic_ai_24.xml` 风格或用 `Icons.Filled.AutoAwesome` 的 raster)— 简单处理复用 `R.drawable.ic_ai_24dp`

## 阶段 6 — 单测 + review(Day 7,~1d)

- [x] 6.1 跑 `NoteRepositoryJournalTest`(Robolectric):`createJournalEntry` 落库 + `note_tags` 写入 `(n-uuid, "journal")` + `widgetUpdater.updateAll` 被调 1 次
- [x] 6.2 跑 `./gradlew :app:testDebugUnitTest` — 全部绿
- [x] 6.3 跑 `./gradlew :app:ktlintCheck` + `./gradlew :app:ktlintFormat` — 0 violation
- [x] 6.4 跑 `./gradlew :app:assembleDebug` — 编译过
- [x] 6.5 自检"两 source 路径 grep":`grep "fake\|FakeAiProvider" app/src/main/java/com/yy/writingwithai/feature/freewrite/` → 0 匹配(沿用 remove-debug-fake-fallback 原则)
- [x] 6.6 自检 manifest:`grep "POST_NOTIFICATIONS\|RECEIVE_BOOT_COMPLETED\|BootReceiver" app/src/main/AndroidManifest.xml` → 3 个 grep 都有匹配
- [x] 6.7 自检跨 feature 入口:`grep "feature.aiwriting.streaming.AiActionViewModel" app/src/main/java/com/yy/writingwithai/feature/freewrite/` → 仅允许出现在 `FreewriteEntry.kt` 内部对 `hiltViewModel<AiActionViewModel>` 的间接引用(直接 import 算违规,走 `AiwritingEntry` 入口)
- [x] 6.8 自检 `NoteRepository.JOURNAL_TAG` 引用:`grep "\"journal\"" app/src/main/java/com/yy/writingwithai/feature/freewrite/` → 0 匹配(避免散落字面量)

## 验证步骤(收尾)

- [x] 真机装 Debug APK 到 Android 13+ 设备
- [x] 设置 → 每日晨写 → 开 toggle → 允许通知权限 → 改时间到「1 分钟后」(测短周期 alarm)→ 退出 App → 等到时间 → 通知应响 → 点通知 → 沉浸屏渲染
- [x] 屏内写 50 字以上(超过 5 秒内输入量) → 等倒计时归零或点"完成" → 观察 AI 链跑(应见 "AI 整理中..." spinner) → 跑完跳回 AppShell
- [x] 笔记 tab 找到新条 note(title=今日日期,tag=journal,content=AI 整理后,lastAiOp=organize)
- [x] 重复流程但**先在 SettingsModelManagement 清掉 apikey** → 屏渲染时应直接显示"请先配置 AI provider" → 跳设置 → 装回 apikey → back 回屏 → 5 分钟倒计时正常开始
- [x] 重复流程但**中途用 airplane mode 杀网络** → 等 Polish 阶段 Failed → Snackbar "AI 整理失败,已保存原始" + 笔记存在(content=原文,无 lastAiOp)
- [x] `adb reboot` 后等 8:00 → 通知应自动响(BootReceiver 工作)