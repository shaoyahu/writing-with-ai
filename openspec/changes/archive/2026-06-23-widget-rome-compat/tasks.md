# widget-rome-compat · tasks

## 1. spec delta

- [x] 1.1 在 `openspec/changes/widget-rome-compat/specs/home-screen-widget/spec.md` 写 `## ADDED Requirements (widget-rome-compat)` + 4 Requirement + 14 Scenario
- [x] 1.2 在 `openspec/changes/widget-rome-compat/specs/domestic-rom-widget/spec.md` 写 4 Requirement + Scenario(RomDetector / empty hint / docs / onNewIntent)

## 2. RomDetector + WidgetState 数据类

- [x] 2.1 新建 `core/widget/RomDetector.kt`:
  - `enum class RomVendor { MIUI, EMUI, COLOROS, ORIGINOS, AOSP }`
  - `object RomDetector { fun current(): RomVendor = when { ... } }`(Build.MANUFACTURER / Build.BRAND 8 关键词命中)
- [x] 2.2 新建 `core/widget/WidgetState.kt`:
  - `@Serializable data class WidgetState(val cachedNoteIds: List<String> = emptyList(), val lastRefreshAt: Long = 0L, val romVendor: RomVendor = RomVendor.AOSP)`
- [x] 2.3 新建 `core/widget/WidgetStateSerializer.kt`:`object WidgetStateSerializer : Serializer<WidgetState>`(kotlinx.serialization)

## 3. WidgetStateDefinition 走 DataStore

- [x] 3.1 新建 `core/widget/WidgetStateDefinition.kt`:
  ```kotlin
  class WidgetStateDefinition(private val context: Context) : GlanceStateDefinition<WidgetState> {
      private val Context.widgetStore: DataStore<WidgetState> by dataStore(
          fileName = "widget_state",
          serializer = WidgetStateSerializer
      )
      override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetState> = context.widgetStore
      override val location: GlanceStateDefinition.GlanceStateValueLocation = GlanceStateValueLocation.GlanceState
  }
  ```
- [x] 3.2 `core/widget/QuickNoteWidgetReceiver.kt` + `QuickNote1x4WidgetReceiver.kt` 加 `override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition(context.applicationContext)`

## 4. 颜色 token 化(WidgetTheme)

- [x] 4.1 新建 `core/widget/WidgetTheme.kt`:
  - `@Immutable data class WidgetColors(val widgetPrimary / Background / OnBackground / OnSurfaceVariant / PrimaryContainer / Outline: ColorProvider)`
  - `@Composable @ReadOnlyComposable fun widgetColors(): WidgetColors`(从 `MaterialTheme.colorScheme` 派生)
- [x] 4.2 改 `core/widget/QuickNoteWidget.kt`:删 `cBlue / cWhite / cBg / cTitle / cBody / cMeta` + `cp(...)`;改 `widgetColors().widgetPrimary` 等引用;`Text` / `Box` / `Column` 全部用 token
- [x] 4.3 改 `core/widget/QuickNote1x4Widget.kt`:同 4.2

## 5. DateUtils.getRelativeTimeSpanString 替换

- [x] 5.1 改 `core/widget/QuickNoteWidget.kt`:`formatRelativeTime` / `formatRelativeTimeCompact` 改 `DateUtils.getRelativeTimeSpanString(...)`,删 30 行 `when (diff < m/h/d/7d)`
- [x] 5.2 改 `core/widget/QuickNote1x4Widget.kt`:同 5.1(共享函数,WidgetSource 改一处全改)

## 6. widget empty state + ROM hint

- [x] 6.1 改 `QuickNoteWidget.kt`:`notes.isEmpty()` 分支加 `RomDetector.current()` 命中 → `R.string.widget_rom_*_hint` 显示;AOSP → 不显示
- [x] 6.2 改 `QuickNote1x4Widget.kt`:widget 不显示空状态(只有 1 条笔记 LIMIT=1),hint 不适用 — 跳过(留 v2+ 评估)

## 7. i18n

- [x] 7.1 `res/values/strings.xml` 加 4 个 key:`widget_rom_miui_hint`(小米设备请到设置 → 电池 → 自启动管理开启本应用)/ `widget_rom_emui_hint`(华为设备请到设置 → 应用 → 启动管理关闭自动管理)/ `widget_rom_coloros_hint`(OPPO 设备请到设置 → 电池 → 关闭"睡眠待机优化")/ `widget_rom_originos_hint`(vivo 设备请到设置 → 电池 → 后台高耗电允许)
- [x] 7.2 `res/values-en/strings.xml` 加 4 个 TODO 占位

## 8. docs

- [x] 8.1 新建 `docs/usage/domestic-rom-widget.md`:4 段(状态表 4×4 / 4 ROM 教程含截图占位 / 已知限制 / ROM 检测原理)

## 9. 测试

- [ ] 9.1 新建 `core/widget/RomDetectorTest.kt`:5 case(MIUI 主品牌 / Redmi 子品牌 / EMUI / ColorOS / ORIGINOS / Pixel AOSP / 空字符串兜底)— **deferred 到 M5 polish**(Robolectric Glance 首次 runtime 下载 ~500MB,polish-and-internal-release 已开 CI cache)
- [ ] 9.2 新建 `core/widget/WidgetStateDefinitionTest.kt`:DataStore round-trip + widget 进程杀重启模拟(stale cachedNoteIds 命中)— **deferred**
- [ ] 9.3 新建 `core/widget/WidgetThemeTest.kt`:`widgetColors()` 派生值与 `colorScheme` 一致 — **deferred**
- [ ] 9.4 新建 `app/src/test/java/.../app/MainActivityOnNewIntentTest.kt`:`@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])` + 2 case(同 route 不重复 navigate / 不同 route 替换 lastInitialRoute)— **deferred**

## 10. 验证

- [x] 10.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 10.2 `./gradlew :app:ktlintCheck` 0 violations
- [x] 10.3 `./gradlew :app:lintDebug` 0 errors
- [x] 10.4 `./gradlew :app:testDebugUnitTest` 全 PASS(项目全部测试,无新增)
- [ ] 10.5 真机冒烟:Pixel Launcher + 模拟 MIUI / ColorOS(改 Build.MANUFACTURER)→ widget hint 显示正确 — **用户角色 3 真机反馈循环**

## 11. 文档

- [x] 11.1 `docs/progress.md` 加 1 条 2026-06-21 条目