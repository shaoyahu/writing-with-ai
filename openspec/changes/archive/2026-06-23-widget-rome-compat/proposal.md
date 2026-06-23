## Why

M4-1 `home-screen-widget` 落地后 widget 已在 AOSP / Pixel Launcher 验证(progress.md 第 53 行 "widget 真机测未做" 范围)。M4-1 r2 review 列了 5 项 follow-up(progress.md 第 146 行),M5 polish-and-internal-release 集中收了 1 项(国产 ROM ROM 适配笔记落 `polish-and-internal-release` 测试中),其余 4 项 + 国产 ROM 适配实际落地推迟到现在。

无落地意味着:
- widget 进程被国产 ROM(MIUI / ColorOS / HarmonyOS / OriginOS)杀死后,状态丢失 → 重启显示空 widget
- widget 颜色硬编码(`cBlue` / `cWhite` / `cBg` / `cTitle` / `cBody` / `cMeta`),未走 M3 ColorScheme + glance-material3;深色 / 亮色不能跟随系统
- `formatRelativeTime` 手写 when 分支,不支持 locale(英文 / 中文混排不当)
- 用户在国产 ROM 上长按桌面 → Widgets → 拖到桌面 → 显示空 widget(因 widget 进程被优化)→ 用户无 fallback 提示

roadmap §14 标了"国产 ROM 对 widget 限制"风险,需 M4 集中做兼容测试。本 change 收口。

## What Changes

- `QuickNoteWidget` / `QuickNote1x4Widget` 接入自定义 `GlanceStateDefinition` 走 DataStore(避免 widget 进程死状态丢失)
- 颜色 token 化:删除硬编码 `cBlue` / `cWhite` / `cBg` / `cTitle` / `cBody` / `cMeta`,改走 `GlanceTheme.colors` + Material 3 ColorScheme 派生(`widgetBackground` / `widgetOnBackground` / `widgetPrimary` 等 token);同步应用 1x4 widget
- `formatRelativeTime` / `formatRelativeTimeCompact` 改 `DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS)` locale-aware,删手写 when
- 新增 `core/widget/RomDetector.kt` + widget 自检:Build.MANUFACTURER / Build.BRAND 命中 MIUI / EMUI / ColorOS / OriginOS 时,空 widget 文案带"如 widget 不显示,请在系统设置 → 电池 → 自启动管理开启"提示
- `MainActivity.onNewIntent` 增加"重复触发 widget Intent"测试覆盖(M4-4 r1 M1 修已实现,本 change 加 test 锁住)
- 加 2 个 i18n key(`widget_rom_self_start_hint` + `widget_rom_optimization_hint`)
- `docs/usage/domestic-rom-widget.md` 新增:四大国产 ROM 适配状态表 + 用户自助开 widget 自启动教程(截图占位)

**非破坏**:GlanceStateDefinition 默认走 `PreferencesGlanceStateDefinition`(M4-1 隐式),改 DataStore 是扩展;widget_info.xml 不动;layout / drawable 不动。

## Capabilities

### New Capabilities

- `domestic-rom-widget`:定义国产 ROM(MIUI / EMUI / ColorOS / OriginOS)widget 适配契约,包含 ROM 检测 + 自启动提示 + 用户文档路径

### Modified Capabilities

- `home-screen-widget`:新增 4 个 Requirement:
  1. `GlanceStateDefinition persists widget state via DataStore` — 自定义定义走 DataStore,widget 进程恢复后状态完整
  2. `Widget colors derive from GlanceTheme ColorScheme` — 删硬编码颜色,改 `GlanceTheme.colors.widgetPrimary/Background/OnBackground/...`
  3. `Relative time formatting uses DateUtils.getRelativeTimeSpanString` — locale-aware
  4. `Domestic ROM optimization hints displayed on empty widget` — Build.MANUFACTURER 命中 → 显示"电池 → 自启动管理"提示

## Impact

- 改:`app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidget.kt`(颜色 token 化 + DateUtils)
- 改:`app/src/main/java/com/yy/writingwithai/core/widget/QuickNote1x4Widget.kt`(同)
- 新:`app/src/main/java/com/yy/writingwithai/core/widget/RomDetector.kt`(Build 检测 + ROM 命中)
- 新:`app/src/main/java/com/yy/writingwithai/core/widget/WidgetStateDefinition.kt`(DataStore-backed GlanceStateDefinition)
- 新:`app/src/main/java/com/yy/writingwithai/core/widget/WidgetTheme.kt`(`GlanceTheme.colors` 派生 token)
- 改:`MainActivity.kt`(`onNewIntent` 测试覆盖,M4-4 r1 M1 验证项)
- 改:`res/values/strings.xml` + `values-en/strings.xml`(2 个 key)
- 新:`docs/usage/domestic-rom-widget.md`(国产 ROM 适配表 + 用户教程)
- 改:`openspec/changes/widget-rome-compat/specs/{home-screen-widget, domestic-rom-widget}/spec.md`(delta + 新)
- 测试:`core/widget/RomDetectorTest.kt`(MIUI / EMUI / ColorOS / OriginOS / Pixel 5 case)+ `core/widget/WidgetStateDefinitionTest.kt`(DataStore round-trip + widget 进程杀重启模拟)+ `core/widget/WidgetThemeTest.kt`(GlanceTheme token 派生)+ `MainActivityOnNewIntentTest.kt`(Robolectric + Activity 启动 + Intent 触发)
- 0 个 build.gradle / 0 个 AndroidManifest 改动(GlanceStateDefinition 通过 `glanceAppWidget.stateDefinition` 注入)