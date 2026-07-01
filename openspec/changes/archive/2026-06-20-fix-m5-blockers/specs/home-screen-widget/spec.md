# home-screen-widget Delta Spec (fix-m5-blockers)

## ADDED Requirements

### Requirement: 1x4 widget uses only Glance 1.1+ supported APIs

`QuickNote1x4Widget` MUST **不** 引用以下不存在于 Glance 1.1+ 的 API(2026-06-20 实地验证 `compileDebugKotlin` 报 unresolved):
- `androidx.glance.unit.RoundedCornerRadius`(数据类不存在)
- `Modifier.fillMaxHeight()`(modifier 不存在)
- `background(color, RoundedCornerRadius)` 多参重载(背景只接 `ColorProvider` 单参，圆角走 `Modifier.cornerRadius(...)` 链式)

`QuickNote1x4Widget` 圆角 MUST 改用 `GlanceModifier.cornerRadius(radius: Dp)`(从 `androidx.glance.layout.cornerRadius` import，具体路径以 `libs.versions.toml` 实际 Glance 版本为准);按钮高度 MUST 改用 `defaultWeight()` + `Modifier.height(Dp)` 组合。

#### Scenario: 1x4 widget 编过 assembleDebug
- **WHEN** 跑 `./gradlew :app:assembleDebug`
- **THEN** `compileDebugKotlin` 阶段 0 个 `Unresolved reference` / `Argument type mismatch` 错误，产物 `app/build/outputs/apk/debug/app-debug.apk` 存在

#### Scenario: 1x4 widget 圆角走 chain modifier
- **WHEN** 读 `QuickNote1x4Widget.kt`
- **THEN** import 不出现 `RoundedCornerRadius`;`background()` 调用形如 `background(cp(cWhite))`(单参)或 `background(cp(cBlue))`;圆角通过 `GlanceModifier.cornerRadius(16.dp)` 链式设置

#### Scenario: 1x4 widget 高度用 defaultWeight + height
- **WHEN** 读 `QuickNote1x4Widget.kt` 按钮 Box
- **THEN** 不出现 `fillMaxHeight()`;改用 `Modifier.defaultWeight().height(48.dp)` 之类组合;外层 `Row` `verticalAlignment = Alignment.CenterVertically`
