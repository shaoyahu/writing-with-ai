# home-screen-widget Specification (delta)

## ADDED Requirements (widget-rome-compat)

### Requirement: GlanceStateDefinition persists widget state via DataStore

`QuickNoteWidget` 与 `QuickNote1x4Widget` MUST 在 `glanceAppWidget.stateDefinition` 注入自定义 `WidgetStateDefinition`,数据后端走 DataStore(`androidx.datastore.core.DataStore` + `kotlinx.serialization` Serializer);**不** 用 Glance 默认 `PreferencesGlanceStateDefinition`(SharedPreferences)。

`WidgetState` MUST 是 `@Serializable data class`(kotlinx.serialization):
- `cachedNoteIds: List<String>` — 最近 N 条笔记 id 缓存(widget 进程被杀后 stale 兜底显示)
- `lastRefreshAt: Long` — 上次 refresh epoch millis(0 = 未 refresh 过)
- `romVendor: RomVendor` — `enum RomVendor { MIUI / EMUI / COLOROS / ORIGINOS / AOSP }`

DataStore 文件名 MUST 为 `widget_state`,目录与 `consent_store` / `prompt_template_store` 同级;Serializer MUST 是自定义 `WidgetStateSerializer : Serializer<WidgetState>`(非 default JSON,GlanceStateDefinition 内部用)。

Widget receiver 注入方式:`override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition(context.applicationContext)`(在 `QuickNoteWidgetReceiver` 与 `QuickNote1x4WidgetReceiver` 各一份)。

#### Scenario: widget 进程被杀后状态恢复

- **WHEN** widget 进程被国产 ROM(MIUI 等)杀 → 30s 后系统拉起 widget host process
- **THEN** `WidgetStateDefinition.getDataStore(context, fileKey)` 返回原 `widget_state` DataStore 实例;`provideGlance` 内 `currentState<WidgetState>()` 拿到 stale `cachedNoteIds`;若 `lastRefreshAt` 距今 < 15 分钟 → 显示该 id 列表(即便 Room 已更新)作为兜底;若 > 15 分钟 → 显示空状态 + ROM hint + "最后更新于 X 分钟前"占位

#### Scenario: 默认 WidgetState 初始化

- **WHEN** 用户首次添加 widget,`widget_state` DataStore 还不存在
- **THEN** Serializer 默认值生效:`cachedNoteIds = emptyList()` / `lastRefreshAt = 0L` / `romVendor = RomDetector.current()`(此时 AOSP 或 4 国产之一)

#### Scenario: DataStore round-trip 完整性

- **WHEN** `WidgetState(cachedNoteIds = ["n1", "n2"], lastRefreshAt = 1234567890L, romVendor = MIUI)` 写入 DataStore → 读回
- **THEN** 字段值与写入完全一致(无字段丢失 / 类型丢失)

### Requirement: Widget colors derive from GlanceTheme ColorScheme

`QuickNoteWidget` 与 `QuickNote1x4Widget` MUST 删除 6 个硬编码 hex 颜色(`cBlue` / `cWhite` / `cBg` / `cTitle` / `cBody` / `cMeta` 与 `cp(...)` 工具函数);改走 `WidgetTheme.kt` 暴露的 `WidgetColors` token,`@Composable @ReadOnlyComposable fun widgetColors(): WidgetColors`。

`WidgetColors` MUST 含 6 个 token(`ColorProvider` 双套 light/dark):
- `widgetPrimary`(主色 / "+" 按钮背景)
- `widgetBackground`(widget 整体背景)
- `widgetOnBackground`(笔记标题色)
- `widgetOnSurfaceVariant`(正文色)
- `widgetPrimaryContainer`(强调)
- `widgetOutline`(边框)

6 个 token MUST 从 `androidx.compose.material3.MaterialTheme.colorScheme` 派生(系统跟随 + Material You 取色);系统暗色 / 亮色 / 跟随三档自适应。

#### Scenario: 删除硬编码颜色

- **WHEN** `grep "Color(0xFF" app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidget.kt app/src/main/java/com/yy/writingwithai/core/widget/QuickNote1x4Widget.kt`
- **THEN** 0 个匹配(颜色硬编码全部走 token)

#### Scenario: widget 颜色跟系统暗色 / 亮色

- **WHEN** 系统设置切到深色模式 → widget 重新渲染
- **THEN** widget 6 个 token 自动应用 `colorScheme` 的 dark 套色;反之亦然

#### Scenario: Material You 取色生效

- **WHEN** Android 12+ 设备用户在系统设置选 Material You 壁纸取色
- **THEN** widget `widgetPrimary` 跟随 wallpaper 派生色调(`colorScheme.primary`);非 hex 硬编码

### Requirement: Relative time formatting uses DateUtils.getRelativeTimeSpanString

`core/widget/QuickNoteWidget.kt` 与 `QuickNote1x4Widget.kt` 内 `formatRelativeTime` / `formatRelativeTimeCompact` MUST 改 `android.text.format.DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS, flags)`,`flags` 含 `FORMAT_ABBREV_RELATIVE`;**删除** 30 行手写 `when (diff < m / h / d / 7d)` 分支。

输出 MUST locale-aware:中文系统 → "1 小时前" / "刚刚";英文系统 → "1h ago" / "just now";日文 / 其他 locale → framework 默认。

#### Scenario: locale-aware 时间格式

- **WHEN** `formatRelativeTime(context, epochMs = now - 60_000)` 在中文系统调用
- **THEN** 输出 "1 分钟前" 或 framework 等价 locale 文案(非固定英文 "1 minute ago")

#### Scenario: 短时间 < 1 分钟

- **WHEN** `epochMs = now - 10_000`(10 秒前)
- **THEN** 输出 "刚刚"(中文)/ "just now"(英文)/ locale 等价(非 "0 分钟前")

#### Scenario: 删除手写 when 分支

- **WHEN** `grep "when (diff" app/src/main/java/com/yy/writingwithai/core/widget/`
- **THEN** 0 个匹配(手写 when 已替换 framework)

### Requirement: Domestic ROM optimization hints displayed on empty widget

`QuickNoteWidget` 与 `QuickNote1x4Widget` 在 `notes.isEmpty()` 状态 MUST 走 `RomDetector.current()` 命中分支显示 hint 文案;`RomDetector` MUST 是 `object`,内部 `current(): RomVendor` 用 `Build.MANUFACTURER` + `Build.BRAND` 判 4 国产 ROM。

ROM 命中映射:
- `MIUI`:`Build.MANUFACTURER == "Xiaomi"` || `Build.BRAND.contains("Redmi", ignoreCase=true)` → 显示 `R.string.widget_rom_miui_hint`
- `EMUI`:`Build.MANUFACTURER == "HUAWEI"` || `Build.BRAND.contains("Honor", ignoreCase=true)` → 显示 `R.string.widget_rom_emui_hint`
- `COLOROS`:`Build.MANUFACTURER == "OPPO"` || `Build.BRAND.contains("realme", ignoreCase=true)` → 显示 `R.string.widget_rom_coloros_hint`
- `ORIGINOS`:`Build.MANUFACTURER == "vivo"` || `Build.BRAND.contains("iQOO", ignoreCase=true)` → 显示 `R.string.widget_rom_originos_hint`
- `AOSP`(默认 / Pixel / 其他):**不** 显示 hint,只显示既有 `widget_empty` 文案

4 个 hint 文案 MUST 在 `values/strings.xml`(中文权威)+ `values-en/strings.xml`(TODO 占位)。

#### Scenario: 小米设备显示 MIUI hint

- **WHEN** `Build.MANUFACTURER = "Xiaomi"` + `RomDetector.current() == MIUI` + widget 空状态
- **THEN** widget 显示 "还没有笔记\n点 + 创建第一条"(既有)+ 下行 "小米设备请到设置 → 电池 → 自启动管理开启本应用"(`R.string.widget_rom_miui_hint`)

#### Scenario: AOSP / Pixel 设备不显示 ROM hint

- **WHEN** `Build.MANUFACTURER = "Google"` + `RomDetector.current() == AOSP` + widget 空状态
- **THEN** widget 仅显示 "还没有笔记\n点 + 创建第一条"(既有),**不** 显示额外 ROM hint

#### Scenario: ROM 检测覆盖子品牌

- **WHEN** `Build.BRAND = "Redmi"`(红米子品牌)+ `Build.MANUFACTURER = "Xiaomi"`
- **THEN** `RomDetector.current()` 返回 `MIUI`,显示 `R.string.widget_rom_miui_hint`

#### Scenario: 已知 ROM 全部命中

- **WHEN** `grep -E "Build.MANUFACTURER|Build.BRAND" app/src/main/java/com/yy/writingwithai/core/widget/RomDetector.kt`
- **THEN** 至少含 8 个命中(`Xiaomi` + `Redmi` + `HUAWEI` + `Honor` + `OPPO` + `realme` + `vivo` + `iQOO`)