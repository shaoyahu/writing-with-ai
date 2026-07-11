# morning-freewrite Design

## 1. 目标架构

新增 1 个 feature(`feature/freewrite/`) + 1 个 notification 子系统(`core/notification/`) + 1 个设置 sub-screen(`feature/settings/freewrite/`)。复用现有:
- `AiActionViewModel.start(WritingOp, sourceText, noteId)`(M3,M4-4)— 走真 AI provider,过 consent / apikey / 模型检测,逐 op 流式返回状态
- `NoteRepository.upsert + noteTagDao.add(NoteTagCrossRef)`(M1)— 落库 + 挂 tag
- `WidgetLaunchRoute` sealed 类(M4-1)— `MainActivity` extra 解析路径的模式参考
- `UserPrefsStore`(M0)— 3 个新 pref key 走既有 `preferencesDataStore` 注入

新增通知路径依赖:
- `AlarmManager` + `PendingIntent`(framework,Android 4.4+)
- `NotificationChannel` + `NotificationManager`(API 26+;本项目 minSdk 已 >= 26,见 CLAUDE.md §架构要点)
- `POST_NOTIFICATIONS` 运行时权限(API 33+)
- `SCHEDULE_EXACT_ALARM` 权限(API 31+,可选,精确触发用)
- `RECEIVE_BOOT_COMPLETED` 用于重启恢复

## 2. 数据流(通知 → 沉浸屏 → AI 链 → 落库)

```
┌────────────────────────────────────────────────────────────────┐
│  AlarmManager fire @ 8:00 (daily)                              │
│      │                                                          │
│      ▼                                                          │
│  PendingIntent → MainActivity (extra: route=freewrite/2026-07-10) │
│      │                                                          │
│      ▼                                                          │
│  MainActivity.onCreate → 解析 extra → write widgetPendingRoute │
│      │                                                          │
│      ▼                                                          │
│  AppNav LaunchedEffect → consent 已通过 →                    │
│  navigate(MorningFreewrite(date=2026-07-10))                   │
│      │                                                          │
│      ▼                                                          │
│  MorningFreewriteScreen (全屏沉浸,5min 倒计时)              │
│      │                                                          │
│      ▼ (用户点"完成" / 倒计时归零)                              │
│  MorningFreewriteViewModel.finish()                            │
│      │                                                          │
│      ├─► aiVm.start(POLISH, text, journalNoteId)              │
│      │   │ await Done                                          │
│      │   │                                                       │
│      │   ├─ Polish Failed → 保存原文 + tag=journal + 提示      │
│      │   │                                                       │
│      │   └─ Polish Done                                        │
│      │       │                                                  │
│      │       ▼                                                  │
│      │   aiVm.start(ORGANIZE, polishedText, journalNoteId)    │
│      │       │ await Done                                      │
│      │       │                                                  │
│      │       ├─ Organize Failed → 保存 polishedText + tag=journal │
│      │       │                                                  │
│      │       └─ Organize Done → 保存 organizedText + tag=journal │
│      │                                                          │
│      ▼                                                          │
│  NoteRepository.createJournalEntry(...) 落库 + Snackbar        │
│      │                                                          │
│      ▼                                                          │
│  popBackStack() 回 AppShell                                    │
└────────────────────────────────────────────────────────────────┘
```

## 3. 通知子系统(`core/notification/`)

### 3.1 `MorningFreewriteNotifier.kt`(单例,Hilt @Singleton)

- 内部 `init` 块调 `createChannel()`,id=`morning_freewrite`,name=`R.string.freewrite_notification_channel_name`,IMPORTANCE_DEFAULT
- `fun showDailyReminder(date: LocalDate)`:建 `NotificationCompat.Builder` → `setSmallIcon(R.drawable.ic_notification)` + `setContentTitle(R.string.freewrite_notification_title)` + `setContentText(R.string.freewrite_notification_body)` + `setContentIntent(pendingIntent)` + `setAutoCancel(true)`
- `pendingIntent`:用 `TaskStackBuilder.create(context).addNextIntentWithParentStack(MainActivity Intent)` 构造(M4-1 widget 既有模式),Intent extra `route=freewrite/{date}` + `FLAG_ACTIVITY_NEW_TASK`;PendingIntent flag `FLAG_IMMUTABLE` + `FLAG_UPDATE_CURRENT`

### 3.2 `MorningFreewriteScheduler.kt`(@Singleton)

封装 3 个 API:
- `fun schedule(hour: Int, minute: Int, date: LocalDate)`:计算 `nextTriggerAt = nextLocalDateTimeOf(hour, minute, now)`,调 `alarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, nextTriggerAt.toEpochMilli(), pendingIntent)`
- `fun cancel()`:调 `alarmManager.cancel(pendingIntent)`
- `fun nextTriggerAt(hour: Int, minute: Int, now: ZonedDateTime): ZonedDateTime`:纯函数,返回「下一次 hour:minute 触发」;若 `now.toLocalTime() < LocalTime.of(hour, minute)` 返回今天,否则返回明天(走 `ZoneId.systemDefault()`)

`SCHEDULE_EXACT_ALARM` 处理:
- API 31+:调 `AlarmManager.canScheduleExactAlarms()` 检查;若 false 则 fallback `setAndAllowWhileIdle`(非精确,DOZE 时可能晚几分钟);在文档写明「fallback 时不能保证 ±15 分钟精度」
- API < 31:直接 `setExactAndAllowWhileIdle`,无需权限

> **不在 main code 里申请 SCHEDULE_EXACT_ALARM**(Google Play 政策限制「日历 / 闹钟 / 提醒」以外应用申请会被拒);fallback `setAndAllowWhileIdle` 足够 v1 跑。doc 标注「v2 再考虑」。

### 3.3 `BootReceiver.kt`(`BroadcastReceiver`,Hilt inject 走 `@AndroidEntryPoint`)

- onReceive → 从 `UserPrefsStore` 读 `morningFreewriteEnabledFlow.first()` + `hour` + `minute`
- 若 enabled → 调 `scheduler.schedule(hour, minute, today)` 重排
- AndroidManifest 注册:`<receiver android:name="core.notification.BootReceiver" android:exported="true"><intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter></receiver>`,对应 `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`

### 3.4 触发时机

`MorningFreewriteScheduler.schedule(...)` 在以下时机调:
- App 启动时(`WritingApp.onCreate` 末尾)
- 设置 toggle 开启时(立即排)
- 设置时间改变时(取消旧的,排新的)
- `BootReceiver.onReceive`(reboot 后)

## 4. 沉浸屏(`feature/freewrite/`)

### 4.1 `MorningFreewriteScreen.kt`

全屏沉浸:
- `WindowCompat.setDecorFitsSystemWindows(window, false)` + `WindowInsetsControllerCompat.hide(systemBars)`(API 30+)或 `View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY`(API 26-29)
- 走 `DisposableEffect` 控制生命周期:进入时 hide,退出时 `show(systemBars)` 还原
- **不**渲染 AppShell tab bar / nav bar / FAB / menu / overflow(走独立 Nav destination,不嵌 AppShell)

布局:
```
┌─────────────────────────────────────┐
│                            ⏱ 4:32  │ ← 右上角倒计时(top-end)
│                                     │
│   ████████████████████████████      │
│                                     │
│   [          Borderless TextField ] │
│   [   沉浸编辑区(weight 1f)      ] │
│   [                                ]│
│   [                                ]│
│   [                                ]│
│                                     │
│         [   完成   ]                │ ← 底部按钮(条件显示,前 30 秒隐藏避免误触)
│         [   跳过   ]                │
└─────────────────────────────────────┘
```

- 编辑区:`BasicTextField`(`headlineMedium` 字号 ≥ 20sp,M3 `MaterialTheme.typography.headlineMedium`),无边框,无 placeholder,启动自动 `focusRequester.requestFocus()` 唤起 IME
- 倒计时:`Text(style = labelLarge)` 显示 `MM:SS`,30 秒内变橙(`MaterialTheme.colorScheme.tertiary`),10 秒内变红(`MaterialTheme.colorScheme.error`)
- 倒计时归零 → 自动调 `viewModel.finish()`(等同用户主动点"完成")
- 用户点"完成" → `viewModel.finish()`;AI 链跑期间 → "完成"按钮变 `CircularProgressIndicator` + `Text("AI 整理中...")`,disable 点击
- 用户按 back → 弹 `AlertDialog` 确认(原文会丢,不可逆)
- AI 跑完 → Snackbar `R.string.freewrite_fallback_toast` 或成功提示 → `popBackStack()`

### 4.2 `MorningFreewriteViewModel.kt`

```kotlin
sealed interface FreewriteUiState {
    data class Writing(val content: String, val secondsLeft: Int) : FreewriteUiState
    data class Polishing(val content: String) : FreewriteUiState      // Polish 进行中
    data class Organizing(val content: String) : FreewriteUiState     // Organize 进行中
    data class Saved(val finalNoteId: String, val usedFallback: Boolean) : FreewriteUiState
    data class Failed(val reason: String) : FreewriteUiState          // 严重失败,文本也丢了
}
```

字段:
- `_state: MutableStateFlow<FreewriteUiState>`(UI 订阅)
- `_secondsLeft: MutableStateFlow<Int>`,`init` 跑 `viewModelScope.launch { tick() }`,每秒 `-1`,归零 → `finish()`
- `journalNoteId: String` = `UUID.randomUUID().toString()`(预生成,Polish/Organize 用同一 id 走 `updateAiMetadata` 写 `lastAiOp`)
- `setContent(s: String)`:更新 `_state.Writing.content`
- `finish()`:`_state = Polishing(content)`,调 aiVm.start(POLISH, content, journalNoteId),等 Done → 切 Organizing → aiVm.start(ORGANIZE, polishedText, journalNoteId),等 Done → `repo.createJournalEntry(...)` → `Saved`;任一 Failed 走 fallback(下面 §6)

倒计时 tick:
```kotlin
private suspend fun tick() {
    while (true) {
        delay(1000)
        val cur = _state.value
        if (cur is FreewriteUiState.Writing) {
            val next = cur.secondsLeft - 1
            if (next <= 0) {
                _state.value = cur.copy(secondsLeft = 0)
                finish()
                return
            } else {
                _state.value = cur.copy(secondsLeft = next)
            }
        } else return  // Writing 之外不再 tick
    }
}
```

## 5. 设置入口(`feature/settings/freewrite/SettingsFreewriteScreen.kt`)

- 在 `SettingsScreen` 的 items 列表里 append 一项:`SettingsFreewrite`(icon=`Icons.Outlined.Alarm`,文案 `R.string.freewrite_settings_title`,onClick → `navController.navigate(SettingsFreewrite)`)
- `SettingsFreewriteScreen` 内容:
  - `Switch`:`R.string.freewrite_settings_toggle_label`,onCheckedChange → 弹权限 launcher(API 33+)+ 调 `scheduler.schedule/cancel`
  - `TimePicker`(M3:`TimePickerDialog` Material3,API 26+ 可用;若用户设备 API 26-30 走 `TimePickerDialog` Material3 也兼容)— 改时间 → 取消旧 alarm + 排新 alarm
  - "权限被拒时"显示 `Text(R.string.freewrite_permission_rationale_body)` + `Button("去系统设置")` → `startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(EXTRA_APP_PACKAGE, packageName))`

AppNav 注册:
```kotlin
@Serializable data object SettingsFreewrite

composable<SettingsFreewrite> {
    SettingsFreewriteScreen(onBack = { navController.popBackStack() })
}
```

## 6. AI 串行链 + 失败兜底(关键)

`MorningFreewriteViewModel.finish()` 实现细节:

```kotlin
fun finish() {
    val text = (_state.value as? Writing)?.content ?: return
    val polished: String?
    try {
        _state.value = Polishing(text)
        polished = runOpOnce(WritingOp.POLISH, text)
    } catch (e: AiError) {
        // Polish 失败 → 用原文 + tag=journal 保存 + Snackbar
        saveJournal(original = text, polished = null, organized = null, fallback = true)
        return
    }
    try {
        _state.value = Organizing(polished)
        val organized = runOpOnce(WritingOp.ORGANIZE, polished)
        saveJournal(original = text, polished = polished, organized = organized, fallback = false)
    } catch (e: AiError) {
        // Organize 失败 → 用润色后 + tag=journal 保存
        saveJournal(original = text, polished = polished, organized = null, fallback = true)
    }
}

private suspend fun runOpOnce(op: WritingOp, source: String): String = suspendCancellableCoroutine { cont ->
    viewModelScope.launch {
        aiActionVm.state.collect { st ->
            when (st) {
                is Done -> { cont.resume(st.finalText); cancel() }
                is Failed -> { cont.resumeWithException(st.error) }
                else -> { /* Streaming 继续等 */ }
            }
        }
    }
    aiActionVm.start(op, source, journalNoteId)
}
```

> 简化版(实际会复用 `AiActionViewModel` 既有 state 收集模式,不在此新写 collect 协议 — 详见 tasks #4)。

`saveJournal(original, polished, organized, fallback)`:
- 决定 `finalContent = organized ?: polished ?: original`
- 调 `repo.upsert(Note(id=journalNoteId, title=date.toString(), content=finalContent, createdAt=now, updatedAt=now, isPinned=false, lastAiOp=if (organized != null) "organize" else if (polished != null) "polish" else null, lastAiAt=if (organized != null || polished != null) now else null), tags=listOf("journal"))`
- `_state = Saved(journalNoteId, usedFallback = fallback)`
- emit `_saveEvents: SharedFlow<SaveEvent>` 让屏弹 Snackbar

## 7. `NoteRepository.createJournalEntry(...)`

为不污染 `NoteRepository.upsert` 公共语义,在 `NoteRepository` 加新方法:

```kotlin
suspend fun createJournalEntry(
    noteId: String,
    title: String,
    content: String,
    lastAiOp: String?,
    lastAiAt: Long?
) {
    val now = System.currentTimeMillis()
    val note = Note(
        id = noteId,
        title = title,
        content = content,
        createdAt = now,
        updatedAt = now,
        isPinned = false,
        lastAiOp = lastAiOp,
        lastAiAt = lastAiAt
    )
    upsert(note, tags = listOf(JOURNAL_TAG))
    if (lastAiOp != null && lastAiAt != null) {
        updateAiMetadata(noteId, lastAiOp, lastAiAt)
    }
}

companion object { const val JOURNAL_TAG = "journal" }
```

- `JOURNAL_TAG = "journal"` 写在 `NoteRepository` companion object,跨 feature 引用走 `NoteRepository.JOURNAL_TAG`(避免散落字面量)
- 走既有 `upsert + noteTagDao.add` 路径,事务 + widget 刷新自动跑

## 8. 路由 + MainActivity 解析

`app/AppNav.kt` 加:
```kotlin
@Serializable
data class MorningFreewrite(val date: String)

composable<MorningFreewrite> { backStackEntry ->
    val args = backStackEntry.toRoute<MorningFreewrite>()
    MorningFreewriteScreen(
        date = args.date,
        onBack = { navController.popBackStack() },
        onFinished = { navController.popBackStack() }
    )
}
```

`MainActivity.onCreate` extra 解析(M4-4 已有 widget 解析路径,本 change 复用同款 helper):
- 读 `intent.getStringExtra("route")`
- 若 `route?.startsWith("freewrite/")` → 解析 `date = route.substringAfter("freewrite/")` → `widgetPendingRoute.value = MorningFreewrite(date)`(或新加 sealed 分支)
- `AppNav.LaunchedEffect(widgetPendingRoute)` 回放 → navigate `MorningFreewrite`

`WidgetLaunchRoute` 扩展(M4-1 既有 sealed 模式):
- 加 `data class Freewrite(val date: String) : WidgetLaunchRoute`
- `navigatePendingRoute` 加 when 分支

## 9. 权限矩阵

| Permission | API | 必须? | 申请时机 |
| --- | --- | --- | --- |
| `POST_NOTIFICATIONS` | 33+ | 是 | 设置 toggle 开启时 launcher 弹 |
| `RECEIVE_BOOT_COMPLETED` | all | 是 | manifest 静态声明,无运行时申请 |
| `SCHEDULE_EXACT_ALARM` | 31+ | 否 | v1 不申请,fallback `setAndAllowWhileIdle` |
| `USE_EXACT_ALARM` | 33+ | 否 | 同上,play store 政策限制,本项目不是「闹钟 / 日历」类应用 |

AndroidManifest 新增:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<application ...>
    <receiver
        android:name="com.yy.writingwithai.core.notification.BootReceiver"
        android:enabled="true"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
            <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        </intent-filter>
    </receiver>
</application>
```

## 10. i18n 落点

`values/strings.xml` + `values-en/strings.xml`(中英双语,英文走 TODO 占位同既有模式):
- `freewrite_notification_channel_name` / `freewrite_notification_title` / `freewrite_notification_body`
- `freewrite_screen_title` / `freewrite_timer_zero` / `freewrite_done_button` / `freewrite_skip_button` / `freewrite_ai_running`
- `freewrite_settings_title` / `freewrite_settings_toggle_label` / `freewrite_settings_time_label` / `freewrite_settings_time_fmt`(占位 `%1$d:%2$d`)
- `freewrite_permission_rationale_title` / `freewrite_permission_rationale_body` / `freewrite_open_notification_settings`
- `freewrite_fallback_toast`(AI 失败兜底提示)
- `freewrite_back_dialog_title` / `freewrite_back_dialog_body` / `freewrite_back_dialog_confirm` / `freewrite_back_dialog_cancel`(back 确认)

Composable 内**禁止**硬编码中文,统一 `stringResource(R.string.freewrite_xxx)`。

## 11. 依赖 + 库

不新增任何第三方依赖。`androidx.core` 已含 `NotificationCompat` + `TaskStackBuilder`,`kotlinx-coroutines` 已在 graph。

## 12. 单测覆盖

| 测试文件 | 覆盖 |
| --- | --- |
| `MorningFreewriteSchedulerTest` | `nextTriggerAt` 纯函数:跨天 / 同一天未来 / 同一天过去 / hour=23 / minute=59 |
| `MorningFreewriteViewModelTest` | state machine:Writing → Polishing → Organizing → Saved;Polish Failed → fallback;Organize Failed → fallback;两个都 Failed → Failed 态 + 日志告警 |
| `NoteRepositoryJournalTest`(Robolectric) | `createJournalEntry` 落库 + tag=journal 写入 + widget 刷新被调 |

## 13. 与既有 spec / change 的边界

- **不动 `AiActionViewModel`**(M3,M4-4 已稳):串行链只是 ViewModel 层的 client,不变 AI 流式协议本身
- **不动 `Note` schema**(M1 已稳):只走 `upsert + addTag` 既有方法
- **不动 `ai-actions` spec**:本 change 不新增 op,只复用 POLISH + ORGANIZE
- **不动 `ai-gateway` spec**:fallback 失败处理在 ViewModel 层,不碰 Gateway
- **新增 `quick-note` capability 的 ~6 个 Requirement**(详见 specs/quick-note/spec.md)

## 14. 未来扩展(v2+ 留白)

- v2:多时段提醒(早 8 + 午 12 + 晚 9) — scheduler API 直接扩展,viewModel 不动
- v2:streak 统计 — 在 `freewrite_history` 表写完成时间,统计连续天数
- v2:跳过 Polish 直接 Organize — 让用户配置 mode
- v2:SCHEDULE_EXACT_ALARM 申请 — 走 Play Store 「闹钟 / 日历」类应用白名单
- v2:语音输入(走既有 `voice-input` 集成原则)