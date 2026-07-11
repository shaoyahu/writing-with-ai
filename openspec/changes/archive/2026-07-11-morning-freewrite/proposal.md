# morning-freewrite

## Why

项目当前的核心使用场景是"想到随手记 + AI 扩写/润色/整理"(M1-M6 已落地),但 v1 roadmap §6 把"日常习惯"明确列为下一步:把 AI 写作从「工具」变成「每日 5 分钟的晨写仪式」。具体场景:

- 用户每天早上 8:00 收到本地通知,点开 App 直接进入沉浸式写作屏(无 tab bar / 无 FAB / 无侧边栏,只剩编辑区和倒计时)
- 5 分钟倒计时跑完(或用户主动点"完成") → 系统自动把原文串行跑 Polish → ORGANIZE 两次 AI,产出润色+整理后的版本
- 产出直接落库一条 note,自动挂 `journal` tag,标题用日期(YYYY-MM-DD);用户事后在普通笔记列表里也能翻出来重看

为什么是「串行 Polish + Organize」而不是让用户手动选 1 个 op:这条链路的目标是「让用户每天坚持写 + 拿到一份过得去的整理稿」,不给选择 = 把决策摩擦降到最低,跑 2 次的成本(M5 polish 时实测)大概 10-20 秒,可以接受。

为什么不在编辑器里加 AI 自动跑:会打破"手动触发"这个 CLAUDE.md / spec 里反复强调的边界(AI 操作必须手动 accept / reject),且让用户在沉浸写作时分心。独立 screen + 倒计时 = 把"写"和"整理"清楚切成两段。

## What Changes

- 新增"每日晨写"通知机制:AlarmManager 精确触发 + NotificationChannel(`morning_freewrite`),首启引导权限(POST_NOTIFICATIONS Android 13+)
- 新增 `feature/freewrite/MorningFreewriteScreen.kt`:全屏沉浸编辑器(隐藏 status/nav bar + AppShell tab bar + FAB + menu),5 分钟倒计时,标题"晨写"
- 新增 `feature/freewrite/MorningFreewriteViewModel.kt`:持 `content` StateFlow + 5 分钟倒计时 LaunchedEffect + 倒计时归零 / 用户点完成时串行调 `AiActionViewModel.start(POLISH)` → `start(ORGANIZE, polishedText)`,完成落库
- 新增设置入口:`SettingsScreen` 加"每日晨写" item → `SettingsFreewriteScreen`(新文件)含 toggle + TimePicker + 跳转系统通知设置;`UserPrefsStore` 加 3 个新 key:`morning_freewrite_enabled`(Boolean,默认 false)、`morning_freewrite_hour`(Int,默认 8)、`morning_freewrite_minute`(Int,默认 0)
- 新增 `core/notification/MorningFreewriteScheduler.kt`:封装 `AlarmManager.setExactAndAllowWhileIdle` 注册 + `PendingIntent` 启动 MainActivity 的 extra `route=freewrite/{date}`;App 启动 / `BOOT_COMPLETED` / 设置 toggle 变化 / 时间变化时调用
- 新增 `MainActivity` extra 解析路径:`route=freewrite/{date}` → navigate 到新 `MorningFreewrite` route(在 `app/AppNav.kt` 注册);写入 `WidgetLaunchRoute` 同款 entry-point helper
- 串行 AI 链失败兜底:Polish / Organize 任一 Failed → 保存「当前已拿到的那份」(POLISH 失败 → 保存原始原文;ORGANIZE 失败 → 保存润色后的版本)+ 弹 Snackbar `"AI 整理失败,已保存原始"`(对应 `R.string.freewrite_fallback_toast`),**不**丢用户输入
- 不新增 `WritingOp`(沿用 `POLISH` + `ORGANIZE`);不新增 `DefaultPrompts.forOp` 分支(沿用现有 prompt)
- i18n:中英双语,~14 个 string key(`freewrite_*` namespace)
- `AndroidManifest.xml` 加 `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED` 两个 uses-permission;新增 `BootReceiver`(注册 `BOOT_COMPLETED` → 重排 alarm)
- 单测:`MorningFreewriteSchedulerTest`(JVM 测下次触发时间计算);`MorningFreewriteViewModelTest`(state machine + 倒计时 + 失败兜底)

## Non-Goals (v1)

- ❌ 番茄钟 / 多时段提醒:本 change 只做"每日固定 1 次",多时段是 v2
- ❌ 声音输入:v1 沿用系统 IME(STT 委托),不在晨写屏新增专属录音按钮(走 `voice-input` change 既有原则)
- ❌ 连续打卡 streak / 完成率统计:v1 不存 streak 数据
- ❌ 云端同步 / 备份:晨写产出走普通 Room 落库,云同步后续由 cloud-sync 接管
- ❌ 自定义提醒文案 / 自定义 tag:tag 固定 `journal`,标题固定 `YYYY-MM-DD`,v2 再说
- ❌ 跳过 Polish 直接 ORGANIZE:本 change 是「Polish → ORGANIZE」2 段链,用户不可配置(决策摩擦=0)
- ❌ 在编辑器里加 "Run Polish" 手动按钮:本 change 严格只在晨写屏跑链,不动 `QuickNoteEditorScreen`
- ❌ 新增 capability:沿用现有 `quick-note` capability(增量 spec),不新开 `morning-freewrite` capability(避免仓库 capability 膨胀)

## Capabilities

### Modified Capabilities

- `quick-note`:新增 ~6 个 Requirement 覆盖「通知 + 权限」「全屏沉浸屏」「5 分钟倒计时」「AI 串行链 + 失败兜底」「journal tag 自动归档」「设置入口 + 时间 picker」

## Impact

### 受影响文件 / 新增文件

**新增**(7 个):
- `app/src/main/java/com/yy/writingwithai/feature/freewrite/MorningFreewriteScreen.kt`
- `app/src/main/java/com/yy/writingwithai/feature/freewrite/MorningFreewriteViewModel.kt`
- `app/src/main/java/com/yy/writingwithai/feature/freewrite/FreewriteEntry.kt`(跨 feature 入口,`object`)
- `app/src/main/java/com/yy/writingwithai/core/notification/MorningFreewriteScheduler.kt`
- `app/src/main/java/com/yy/writingwithai/core/notification/MorningFreewriteNotifier.kt`(建 channel + 发通知)
- `app/src/main/java/com/yy/writingwithai/core/notification/BootReceiver.kt`
- `app/src/main/java/com/yy/writingwithai/feature/settings/freewrite/SettingsFreewriteScreen.kt` + ViewModel

**修改**(5 个):
- `app/src/main/java/com/yy/writingwithai/core/prefs/UserPrefsStore.kt` — 加 3 个 key + setter/getter
- `app/src/main/AndroidManifest.xml` — 加 2 个 permission + BootReceiver 注册 + NotificationChannel 在 `WritingApp.onCreate` 创建
- `app/src/main/java/com/yy/writingwithai/app/AppNav.kt` — 加 `MorningFreewrite` route(`@Serializable data class MorningFreewrite(val date: String)`) + `composable<MorningFreewrite>` block + widget pending route 分支(M3 fix `WidgetLaunchRoute` sealed 扩展)
- `app/src/main/java/com/yy/writingwithai/app/MainActivity.kt` — `onCreate` 解析 `route=freewrite/YYYY-MM-DD` extra + 写入 widgetPendingRoute(M4-4 既有模式复用)
- `app/src/main/java/com/yy/writingwithai/feature/settings/SettingsScreen.kt` — 加"每日晨写" item,跳 `SettingsFreewrite`
- `app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt` — 加 `fun createJournalEntry(content: String, polishedContent: String?, finalContent: String, date: LocalDate)` helper,内部 upsert + 加 `journal` tag(走既有的 `noteTagDao.add`)+ write `lastAiOp="organize"` + `lastAiAt=now`(沿用 `updateAiMetadata` 既有方法)

**单测**(3 个):
- `app/src/test/java/com/yy/writingwithai/core/notification/MorningFreewriteSchedulerTest.kt`
- `app/src/test/java/com/yy/writingwithai/feature/freewrite/MorningFreewriteViewModelTest.kt`
- `app/src/test/java/com/yy/writingwithai/core/data/repo/NoteRepositoryJournalTest.kt`

### i18n

~14 个 string key 全部进 `values/strings.xml` + `values-en/strings.xml`,namespace `freewrite_*`:
- `freewrite_notification_title` / `freewrite_notification_body`(通知)
- `freewrite_screen_title` / `freewrite_timer_fmt` / `freewrite_done_button` / `freewrite_skip_button`(屏)
- `freewrite_settings_title` / `freewrite_settings_toggle_label` / `freewrite_settings_time_label` / `freewrite_settings_time_fmt`(设置)
- `freewrite_permission_rationale_title` / `freewrite_permission_rationale_body` / `freewrite_open_notification_settings`(权限引导)
- `freewrite_fallback_toast`(AI 失败兜底)

### 依赖

不新增第三方依赖。`AlarmManager` / `NotificationManager` / `NotificationChannel` / `PendingIntent` 全部来自 framework + `androidx.core:core-ktx` 已依赖。

## Risks

1. **POST_NOTIFICATIONS 权限拒绝**:Android 13+(API 33+)用户拒绝后通知不响,5 分钟倒计时场景永远到不了。**对策**:首次开启 toggle 时弹 rationale + `requestPermission` launcher;拒绝则回退 toggle off + Snackbar 提示「去系统设置打开通知权限」+ 给 `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)` 跳转入口。spec 必须覆盖这条路径。
2. **AI 串行链中途失败(Polish 失败 / Organize 失败)**:用户已经写了 5 分钟,内容必须保住。**对策**:链设计为「当前阶段产出的内容」立即落临时 note,任一 op Failed → 用「上一阶段产出」(Polish 失败用原始原文,Organize 失败用润色后版本)替换 `finalContent` → 走 `repository.upsert` + `journal` tag + Snackbar 提示「AI 整理失败,已保存原始」。**禁止**把整个流程视为「全成功才算完」(spec 必须明确这条 fallback)。
3. **AlarmManager 精确触发在 doze 模式下被推迟**:`setExactAndAllowWhileIdle` 在 API 31+ 需要 `SCHEDULE_EXACT_ALARM` 权限(普通应用拿不到,只有「日历 / 闹钟 / 提醒」类应用能)。**对策**:Android 12+(API 31+)走 `setExactAndAllowWhileIdle` + 引导用户授予 `SCHEDULE_EXACT_ALARM`(系统会弹);如果拿不到则 fallback `setAndAllowWhileIdle`(非精确,但 DOZE 时仍可能晚几分钟);在 spec 里把两条路径都写出来,设计文档里明确「晨写 8:00」是「尽量 8:00 触发」而不是「必须 8:00 触发」。
4. **AI 链耗时比预期长(双 op 串行)**:Polish + ORGANIZE 串行在网络抖动 / 余额不足 / 模型限流时可能跑 30-60 秒,远超 5 分钟写作窗口。**对策**:`MorningFreewriteScreen` 在用户点"完成"后弹 `CircularProgressIndicator` + 文案"AI 整理中..."(沿用现有 `CircularProgressIndicator` 设计 token),不让用户以为 App 卡死;UI 不显示中间过程 streaming 文本(M3 `StreamingPanel` 那种 UI 太重,晨写屏只要 spinner)。
5. **device boot 后 alarm 丢失**:用户每天重启手机后 AlarmManager 注册会被清掉。**对策**:`BootReceiver` 接收 `BOOT_COMPLETED` 后从 `UserPrefsStore` 读 enabled + 时间,调 `MorningFreewriteScheduler.schedule()` 重排。receiver 注册走 manifest `intent-filter android:name="android.intent.action.BOOT_COMPLETED"` + `uses-permission RECEIVE_BOOT_COMPLETED`(spec 必含)。
6. **运行时切时间(夏令时 / 用户改时间)**:alarm 是按 wall-clock 算下次触发的,系统时钟改了会重算。**对策**:`MorningFreewriteScheduler.nextTriggerAt(hour, minute, now)` 每次重算都用 `LocalDateTime` / `LocalTime` + 系统 zone(走 `java.time` API 不调 `Calendar`),`ZoneId.systemDefault()` 拿当前时区;夏令时切换那天触发时间可能 ±1 小时,文档里写明「按本地时间触发,不保证 ±1 小时精度」。

## Acceptance Criteria

- [ ] 通知通道在 `WritingApp.onCreate` 首次启动时创建,id=`morning_freewrite`,IMPORTANCE_DEFAULT,带 notification icon
- [ ] 设置 toggle 开启 → 弹 `POST_NOTIFICATIONS` 权限请求(Android 13+)→ 允许后调 `AlarmManager.setExactAndAllowWhileIdle` 注册;拒绝 → toggle 自动回弹 + Snackbar + 跳转入口
- [ ] 通知点击 → MainActivity 启动并 parse `route=freewrite/2026-07-10` extra → navigate `MorningFreewrite(date="2026-07-10")` → 屏渲染沉浸编辑器(隐藏 status/nav bar + tab bar + FAB)
- [ ] 5 分钟倒计时正常显示并归零;归零时自动触发"完成"流程(等同用户主动点完成);倒计时期间用户可手动点"完成"提前结束
- [ ] "完成"流程:Polish → ORGANIZE 串行 → 两者都成功 → 落 note(title=日期, content=Organize 输出, tags=[journal], lastAiOp="organize", lastAiAt=now)
- [ ] Polish 失败 → 落原始原文 + tag=journal + Snackbar "AI 整理失败,已保存原始"
- [ ] Organize 失败 → 落润色后内容 + tag=journal + Snackbar "AI 整理失败,已保存原始"
- [ ] 用户未配 AI provider apikey → 通知照常响,屏渲染时检测无 apikey → 直接显示"请先配置 AI 模型"+ 跳转设置按钮(走 `onNavigateToModelManagement` 已有入口);不静默失败
- [ ] 用户在晨写屏按 back → 弹 `AlertDialog` 确认(直接 back 丢失 5 分钟输入不可逆);走"确认" → discard 退出;走"继续写" → 留在屏
- [ ] 设备 reboot → `BootReceiver` 触发 → 从 prefs 读 enabled + 时间 → 重新注册 alarm(若 enabled)
- [ ] 系统时间改为凌晨 3 点测"未来 8 点"路径,alarm 注册时间正确(用 `ZoneId.systemDefault()` 不写死 UTC)
- [ ] 所有单测通过(`./gradlew :app:testDebugUnitTest`)+ ktlint 通过(`./gradlew :app:ktlintCheck`)+ Debug APK 编译通过
- [ ] i18n:14 个 string key 中英文都有;Composable 内 grep 不到中文字面量