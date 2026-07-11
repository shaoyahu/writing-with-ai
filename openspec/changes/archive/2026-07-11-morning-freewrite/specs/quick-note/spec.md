# quick-note Delta Spec

> morning-freewrite change 增量。Append-only,不动现有 `quick-note/spec.md` 的任何 Requirement。

## MODIFIED Requirements

### Requirement: Daily morning-freewrite notification fires at user-configured time

App MUST 在用户开启「每日晨写」开关后,每天在用户配置的时间(默认 08:00,时区走 `ZoneId.systemDefault()`)通过 `AlarmManager` + `NotificationChannel`(`morning_freewrite`)发本地通知;通知点击启动 `MainActivity` 并把 `route=freewrite/{date}`(date 为当日 `yyyy-MM-dd`)作为 Intent extra 透传。`AlarmManager.setExactAndAllowWhileIdle` 在 API 31+ 需 `SCHEDULE_EXACT_ALARM` 权限;本项目**不申请**该权限(Play Store 政策限制),fallback `setAndAllowWhileIdle`(DOZE 时可能晚几分钟,文档写明)。

#### Scenario: 开启 toggle 后注册 alarm
- **WHEN** 用户在 `SettingsFreewriteScreen` 把 toggle 切到 true,系统弹 `POST_NOTIFICATIONS` 权限请求,用户允许
- **THEN** `MorningFreewriteScheduler.schedule(hour=8, minute=0, today)` 调 `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, nextTriggerAt.toEpochMilli(), pendingIntent)`;`nextTriggerAt` 是当日 8:00;若当前已过 8:00 则取次日 8:00

#### Scenario: 通知点击启动 freewrite route
- **WHEN** AlarmManager 触发,`MorningFreewriteNotifier.showDailyReminder(today)` 发出通知;用户点通知
- **THEN** MainActivity 启动 + Intent extra `route=freewrite/2026-07-10`;`AppNav.LaunchedEffect(widgetPendingRoute)` 解析后 navigate `MorningFreewrite(date="2026-07-10")`;沉浸屏渲染

#### Scenario: 用户拒绝 POST_NOTIFICATIONS 权限
- **WHEN** 用户在 toggle=true 弹的 `requestPermission` launcher 选"拒绝"
- **THEN** toggle 自动回弹 false + Snackbar `R.string.freewrite_permission_rationale_body` + 入口"去系统设置"跳 `Settings.ACTION_APP_NOTIFICATION_SETTINGS`;`scheduler.schedule` 不调

#### Scenario: Android 12+ 无 SCHEDULE_EXACT_ALARM 权限
- **WHEN** API 31+ 设备 `AlarmManager.canScheduleExactAlarms() == false`
- **THEN** `MorningFreewriteScheduler.schedule` fallback `alarmManager.setAndAllowWhileIdle(...)`(非精确);不申请权限,不弹系统对话框;通知仍能发,但 ±15 分钟精度不保证

#### Scenario: 修改时间重排 alarm
- **WHEN** 用户在 `SettingsFreewriteScreen` 改时间 picker 从 08:00 到 07:30
- **THEN** `MorningFreewriteScheduler.cancel()` 调 `alarmManager.cancel(pendingIntent)` 取消旧 alarm,再 `schedule(hour=7, minute=30, today)` 注册新 alarm

#### Scenario: 关闭 toggle 取消 alarm
- **WHEN** 用户把 toggle 从 true 切到 false
- **THEN** `MorningFreewriteScheduler.cancel()` 被调;后续不再发通知;AlarmManager 内部 pendingIntent 被 GC

#### Scenario: device reboot 后重排
- **WHEN** 设备重启,`BootReceiver` 收到 `BOOT_COMPLETED`
- **THEN** 读 `UserPrefsStore.morningFreewriteEnabledFlow.first()`;若 true,调 `scheduler.schedule(hour, minute, today)` 重排 alarm

### Requirement: Freewrite screen is full-screen distraction-free

`MorningFreewriteScreen` MUST 是沉浸式全屏编辑器:隐藏 system bars(status bar + navigation bar)、不渲染 AppShell tab bar、不渲染 FAB、不渲染 overflow menu、不渲染 top app bar;只渲染倒计时(右上角)+ 编辑区(weight=1f,borderless `BasicTextField`,`MaterialTheme.typography.headlineMedium`)+ "完成" / "跳过" 按钮(底部)。进入屏后自动 `focusRequester.requestFocus()` 唤起 IME。

#### Scenario: 沉浸 mode 隐藏 system bars
- **WHEN** 屏渲染
- **THEN** `WindowCompat.setDecorFitsSystemWindows(window, false)` + `WindowInsetsControllerCompat.hide(systemBars)` 被调;status bar + navigation bar 不可见

#### Scenario: 不渲染 AppShell tab bar
- **WHEN** 屏渲染
- **THEN** 该屏 MUST 不在 `AppShell` 子 NavHost 内(走独立 Nav destination `MorningFreewrite`,在根 NavHost 注册);底部 tab bar 不渲染

#### Scenario: 倒计时显示在右上角
- **WHEN** 屏进入,`secondsLeft = 300`(5 分钟)
- **THEN** 右上角 `Text(style = labelLarge)` 显示 `"5:00"`;每秒 `-1`,显示 `"4:59"` → `"4:58"` → ... → `"0:00"`

#### Scenario: 倒计时颜色变化
- **WHEN** `secondsLeft == 30`
- **THEN** 倒计时文字色 = `MaterialTheme.colorScheme.tertiary`(橙)
- **WHEN** `secondsLeft <= 10`
- **THEN** 倒计时文字色 = `MaterialTheme.colorScheme.error`(红)

#### Scenario: 自动 focus 唤起 IME
- **WHEN** 屏渲染
- **THEN** `LaunchedEffect(Unit) { contentFocusRequester.requestFocus() }` 触发,IME 弹出,光标定位在编辑区

#### Scenario: back 弹确认 dialog
- **WHEN** 用户在屏内按 back
- **THEN** `AlertDialog` 弹出,title=`R.string.freewrite_back_dialog_title`,body=`R.string.freewrite_back_dialog_body`,两个按钮"确认"(popBackStack 丢弃内容)/"继续写"(留在屏)

### Requirement: 5-minute countdown triggers finish flow

`MorningFreewriteViewModel` MUST 持 `secondsLeft: MutableStateFlow<Int>`(初值 300),`init` 块跑 `viewModelScope.launch { tick() }` 每秒 `delay(1000)` 后 `-1`;`secondsLeft <= 0` 时自动调 `finish()`(等同用户主动点"完成")。

#### Scenario: 倒计时正常递减
- **WHEN** 屏进入 5 秒后
- **THEN** `secondsLeft` 从 300 → 295;UI 倒计时文字同步更新

#### Scenario: 倒计时归零触发自动 finish
- **WHEN** `secondsLeft` 减到 0
- **THEN** `viewModel.finish()` 自动被调;`_state = Polishing(content)`,进入 AI 链

#### Scenario: 用户点"完成"提前结束
- **WHEN** `secondsLeft == 120`(剩余 2 分钟),用户点"完成"按钮
- **THEN** `viewModel.finish()` 被调;`tick()` 协程取消(检测到 state != Writing 后 return)

#### Scenario: tick 协程在非 Writing 态停止
- **WHEN** `_state` 从 `Writing` 切到 `Polishing`
- **THEN** `tick()` 协程在下一次 `delay(1000)` 醒来后看到 state != Writing,return 退出

### Requirement: AI chain Polish → Organize with fallback to earlier stage on failure

`MorningFreewriteViewModel.finish()` MUST 串行调 2 次 `AiActionViewModel.start(...)`:第一次 `op=POLISH, sourceText=<raw>`,第二次 `op=ORGANIZE, sourceText=<polishedText>`。两次均 `Done` → 用 `Organize` 输出落库;任一 `Failed` → 用「上一阶段产出」落库(Polish Failed 用原文;Organize Failed 用润色后版本)+ Snackbar `R.string.freewrite_fallback_toast`。两条路径都 MUST 落库,**禁止**因 AI 失败把用户输入丢失。

#### Scenario: 双 op 成功路径
- **WHEN** 用户点"完成",`raw="今早去晨跑,跑了 5 公里"`
- **THEN** `aiActionVm.start(POLISH, "今早去晨跑,跑了 5 公里", journalNoteId)` 调 1 次;`Done` 后 `aiActionVm.start(ORGANIZE, polishedText, journalNoteId)` 调 1 次;`Note.content = organizedText`;`Note.lastAiOp="organize"` + `lastAiAt=<now>`;`Note.tags=["journal"]`;`Note.title="2026-07-10"`(date.toString())

#### Scenario: Polish 失败 → 保存原文
- **WHEN** `aiActionVm.start(POLISH, ...)` 返回 `Failed(AiError.Network(500))`
- **THEN** `Note.content = raw`(原文,不润色);`Note.lastAiOp=null` + `lastAiAt=null`;`Note.tags=["journal"]`;Snackbar `R.string.freewrite_fallback_toast` 弹出;`_state = Saved(journalNoteId, usedFallback = true)`

#### Scenario: Organize 失败 → 保存润色后
- **WHEN** `Polish` Done,但 `Organize` Failed(AiError.Timeout)
- **THEN** `Note.content = polishedText`;`Note.lastAiOp="polish"` + `lastAiAt=<now>`;`Note.tags=["journal"]`;Snackbar `R.string.freewrite_fallback_toast` 弹出;`_state = Saved(journalNoteId, usedFallback = true)`

#### Scenario: 用户未配 AI provider apikey
- **WHEN** `SecureApiKeyStore.observeConfiguredProviders().first().isEmpty()`
- **THEN** 屏渲染时检测 → 不进入 5 分钟倒计时,直接显示 `R.string.freewrite_no_provider_hint` 文案 + "去设置"按钮(跳 `SettingsModelManagement`);**不**静默失败,不通知;Spec 解释:此 edge case 下通知已响但 AI 链无法跑,屏引导用户先去配 apikey

### Requirement: Auto-archive freewrite note with journal tag and date title

`NoteRepository.createJournalEntry(noteId, title, content, lastAiOp, lastAiAt)` MUST 在事务内 upsert note + 写入 `NoteTagCrossRef(noteId, tag=NoteRepository.JOURNAL_TAG)`,返回落库的 `noteId`。`JOURNAL_TAG = "journal"` 常量定义在 `NoteRepository` companion object 供跨 feature 引用。**禁止**在晨写 feature 内写死 `"journal"` 字面量。

#### Scenario: createJournalEntry 落库 + 挂 tag
- **WHEN** 调 `createJournalEntry("n-uuid", "2026-07-10", "润色+整理后文本", "organize", now)`
- **THEN** `notes` 表 upsert 行 `(id=n-uuid, title="2026-07-10", content="润色+整理后文本", lastAiOp="organize", lastAiAt=now)`;`note_tags` 新增 `(noteId=n-uuid, tag="journal")`

#### Scenario: 引用 NoteRepository.JOURNAL_TAG 常量
- **WHEN** grep `feature/freewrite/**/*.kt`
- **THEN** 0 个 `"journal"` 字面量;唯一引用是 `NoteRepository.JOURNAL_TAG`

#### Scenario: widget 自动刷新
- **WHEN** `createJournalEntry` 落库完成
- **THEN** `NoteRepository.upsert` 末尾的 `widgetUpdater.updateAll(context)` 跑(M1 既有逻辑),widget 重渲染含新 journal 条目

#### Scenario: ai_history 自动落库
- **WHEN** Polish + Organize 双 op 跑完
- **THEN** `ai_history` 表新增 2 条记录(op=polish, op=organize, providerId, tokens)(M2 既有逻辑,AiGateway 内部自动写)

### Requirement: Settings entry for freewrite toggle and time picker

`SettingsScreen` MUST 在 items 列表 append 一项 "每日晨写"(icon=`Icons.Outlined.Alarm`,文案 `R.string.freewrite_settings_title`);点击 → navigate `SettingsFreewrite`。`SettingsFreewriteScreen` MUST 渲染:`Switch` toggle + `TimePicker`(Material3) + (权限拒绝时)"去系统设置"按钮。`UserPrefsStore` MUST 加 3 个 pref key:`morning_freewrite_enabled_v1`(Boolean,默认 false)、`morning_freewrite_hour_v1`(Int,默认 8)、`morning_freewrite_minute_v1`(Int,默认 0)。

#### Scenario: 设置项渲染
- **WHEN** 用户进入 `SettingsScreen`
- **THEN** 列表含 "每日晨写" 一项,onClick → `navController.navigate(SettingsFreewrite)`

#### Scenario: toggle 开启 + 弹权限
- **WHEN** `SettingsFreewriteScreen` Switch 从 false → true,Android 13+ 设备
- **THEN** `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` 启动 `Manifest.permission.POST_NOTIFICATIONS` 请求;允许 → `scheduler.schedule(...)`;拒绝 → toggle 回弹 + Snackbar + "去系统设置"按钮显示

#### Scenario: TimePicker 改时间
- **WHEN** 用户在 TimePicker 选 07:30(从 08:00)
- **THEN** `UserPrefsStore.setMorningFreewriteTime(7, 30)` 持久化;`scheduler.cancel()` + `scheduler.schedule(7, 30, today)` 重排

#### Scenario: UserPrefsStore 默认值
- **WHEN** 首次安装 + DataStore 无 key
- **THEN** `morningFreewriteEnabledFlow.first() == false`;`morningFreewriteTimeFlow.first() == LocalTime.of(8, 0)`

#### Scenario: UserPrefsStore 持久化
- **WHEN** `setMorningFreewriteEnabled(true)` 调用,进程 kill,重启
- **THEN** `morningFreewriteEnabledFlow.first() == true`(DataStore 持久化生效)

### Requirement: i18n for freewrite UI

所有 `morning-freewrite` 引入的 UI 文案 MUST 出现在 `values/strings.xml`(中文,权威)与 `values-en/strings.xml`(英文 TODO 占位),namespace `freewrite_*`,14 个 key:

| key | 中文 | 用途 |
| --- | --- | --- |
| `freewrite_notification_channel_name` | 每日晨写 | NotificationChannel name |
| `freewrite_notification_title` | 晨写时间到 | 通知标题 |
| `freewrite_notification_body` | 5 分钟,写下今天的想法 | 通知正文 |
| `freewrite_screen_title` | 晨写 | 沉浸屏标题(隐式渲染,留 a11y) |
| `freewrite_timer_zero` | 0:00 | 倒计时归零(理论不该显示,但 spec 兜底) |
| `freewrite_done_button` | 完成 | 底部按钮 |
| `freewrite_skip_button` | 跳过 | 底部按钮 |
| `freewrite_ai_running` | AI 整理中... | AI 链跑时按钮变文案 |
| `freewrite_settings_title` | 每日晨写 | 设置列表 + 设置屏标题 |
| `freewrite_settings_toggle_label` | 开启每日提醒 | Switch label |
| `freewrite_settings_time_label` | 提醒时间 | TimePicker label |
| `freewrite_permission_rationale_body` | 通知权限被拒,无法在指定时间提醒你 | 拒绝后 Snackbar |
| `freewrite_open_notification_settings` | 去系统设置 | 跳转按钮 |
| `freewrite_fallback_toast` | AI 整理失败,已保存原始 | AI 失败兜底提示 |
| `freewrite_no_provider_hint` | 请先在「设置 → 模型管理」配置 AI provider | 未配 apikey 时屏提示 |

Composable 内 MUST 通过 `stringResource(R.string.freewrite_xxx)` 引用;**禁止**硬编码中文 / 英文。

#### Scenario: 中英文双 string 都存在
- **WHEN** grep `values/strings.xml` `freewrite_`
- **THEN** 14 个 key 都在
- **WHEN** grep `values-en/strings.xml` `freewrite_`
- **THEN** 14 个 key 都在(英文值以 `TODO(en):` 前缀占位,既有模式)

#### Scenario: 源码无中文硬编码
- **WHEN** `grep -rE "[\\x{4e00}-\\x{9fff}]" app/src/main/java/com/yy/writingwithai/feature/freewrite/`
- **THEN** 0 匹配(只有 i18n 字符串引用,不允许硬编码)