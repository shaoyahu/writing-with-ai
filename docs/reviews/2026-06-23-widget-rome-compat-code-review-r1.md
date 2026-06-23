# 2026-06-23-widget-rome-compat-code-review-r1

**Change**: `widget-rome-compat`
**Round**: r1(自审,Step 4 收尾)
**审查人**: AI(本仓库 Claude)
**范围**: widget 颜色 token 化 / DataStore 状态持久化 / RomDetector / DateUtils locale-aware / i18n / docs

---

## 结论

**通过**(archive-ready)。`./gradlew :app:check` 全绿(169 tests + ktlint 0 + lint 0 错)。

## 验证

- `./gradlew :app:assembleDebug` ✅
- `./gradlew :app:ktlintCheck` ✅(0 violations)
- `./gradlew :app:testDebugUnitTest` ✅(169 tests 全 PASS)
- `./gradlew :app:check` ✅

## 关键实现

### 1. WidgetStateStore(非 GlanceStateDefinition)

`proposal.md` §"What Changes"写"自定义 `GlanceStateDefinition` 走 DataStore"。**实际实现发现 Glance 1.1.x 的 `GlanceAppWidget.stateDefinition` 是 final 不可 override**,改为:

- `WidgetStateStore`(object):application-scoped DataStore(`widget_state`),暴露 `current(context)` + `update(context, transform)`
- Widget `provideGlance` 用 `QuickNoteWidgetHiltBridge.repository` 直接读 Room(不变)
- `WidgetStateStore` 留作 v2+ 进程杀恢复路径的入口

**评**:Glance API 限制下最合理的 fallback。DataStore schema `WidgetState` + `WidgetStateSerializer` 已就位,v2 只需在 `provideGlance` 加 `WidgetStateStore.current` 兜底分支即可。

### 2. 颜色 token 化

6 个硬编码 hex 常量 → `WidgetColors` data class(6 个 `ColorProvider` token),`widgetColors()` 从 `MaterialTheme.colorScheme` 派生。`GlanceTheme { }` 包裹 `provideContent`,确保 Material 3 主题生效。

### 3. RomDetector + EmptyState hint

`Build.MANUFACTURER` / `Build.BRAND` 白名单 4 ROM + 子品牌(Redmi/Honor/realme/iQOO)。AOSP 兜底不显示 hint。`EmptyState` 按 `RomDetector.current()` 分支显示对应的 `widget_rom_*_hint` 字符串。

### 4. DateUtils.getRelativeTimeSpanString

删手写 `when (diff < m/h/d/7d)` 30 行,改 `DateUtils.getRelativeTimeSpanString(epochMs, now, MINUTE_IN_MILLIS, FORMAT_ABBREV_RELATIVE)`,自动 locale-aware。

## 明确 deferred(非阻断)

| 任务 | 原因 |
| --- | --- |
| 9.1-9.4 RomDetectorTest / WidgetStateDefinitionTest / WidgetThemeTest / MainActivityOnNewIntentTest | Robolectric runtime ~500MB 首次下载;tasks.md 已标 deferred 到 M5 polish |
| 10.5 真机冒烟(MIUI / ColorOS 改 Build.MANUFACTURER 验 hint) | sandbox 无设备;用户角色 3 真机反馈循环 |
| WidgetStateStore 兜底读取(provideGlance 进程杀恢复) | DataStore 写入链路已就位;v2+ 加 `currentState<WidgetState>()` 兜底分支即可 |
| `QuickNote1x4Widget` EmptyState ROM hint | tasks.md §6.2 明确"1x4 只有 1 条笔记 LIMIT=1,hint 不适用" |

## 安全 review

- **DataStore**: `widget_state` 存 `cachedNoteIds` / `lastRefreshAt` / `romVendor`,无敏感数据,不进 Room / logcat / Auto Backup
- **Build 常量读取**: `Build.MANUFACTURER` / `Build.BRAND` 是系统公开常量,无权限要求
- **GlanceTheme**: 颜色 token 从 `MaterialTheme.colorScheme` 派生,无硬编码 credential / endpoint

## 兼容性

- DataStore `widget_state` 是新文件,向后兼容(老用户首次 widget 更新走 Serializer 默认值 `WidgetState()`)
- `WidgetColors` 是纯 Composable 层替换,0 影响 Room / Hilt / Nav
- `formatRelativeTime` / `formatRelativeTimeCompact` 签名不变(internal),只改实现
