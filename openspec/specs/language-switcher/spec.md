# language-switcher Specification

## Purpose

应用内「我的 → 设置 → 语言」可手动切换 UI 语言:跟随系统 / 中文 / English。DataStore 持久化用户选择，重启 APP 后保持。

Synced from OpenSpec change `language-switcher`(2026-07-03)。

## Requirements

### Requirement: LocaleStore persists user language selection
`core/i18n/LocaleStore.kt` MUST 暴露 DataStore Preferences singleton，key = `"language_selection"`，值枚举 `LocaleSelection { SYSTEM, ZH, EN }`（serialName = "system" / "zh" / "en"）。

#### Scenario: Save selection persists across process restart
- **WHEN** 用户在「语言」设置页选 `EN`
- **THEN** `LocaleStore.set(LocaleSelection.EN)` 落 DataStore Preference
- **AND** 杀进程冷启动 APP，`LocaleStore.first()` 返回 `LocaleSelection.EN`

#### Scenario: First install defaults to SYSTEM
- **WHEN** APP 首次安装启动，且 DataStore 未写入过 key
- **THEN** `LocaleStore.first()` 返回 `LocaleSelection.SYSTEM`

### Requirement: LocaleHelper resolves selection to actual Locale
`core/i18n/LocaleHelper.kt` MUST 暴露纯 object:
- `enum class LocaleSelection { SYSTEM, ZH, EN }`(serialName = "system" / "zh" / "en")
- `fun resolveLocale(selection: LocaleSelection, systemLocale: Locale): Locale`(SYSTEM → systemLocale,ZH → Locale("zh"),EN → Locale("en"))
- `fun wrap(base: Context, locale: Locale): Context`(`Configuration.setLocale + createConfigurationContext`)

#### Scenario: SYSTEM resolves to current system locale
- **WHEN** `resolveLocale(LocaleSelection.SYSTEM, systemLocale = Locale.SIMPLIFIED_CHINESE)` 调
- **THEN** 返回 `Locale.SIMPLIFIED_CHINESE`

#### Scenario: ZH and EN return fixed Locales
- **WHEN** `resolveLocale(LocaleSelection.ZH, ...)` 调
- **THEN** 返回 `Locale("zh")`
- **WHEN** `resolveLocale(LocaleSelection.EN, ...)` 调
- **THEN** 返回 `Locale("en")`

### Requirement: Application attachBaseContext wraps with user-selected locale
`WritingApp` MUST 覆盖 `attachBaseContext(base: Context)`，读 `LocaleStore.first()` 拿 selection（IO 调度 + `runBlocking`，冷启动一次调用），resolveLocale,wrap base 后返回。

#### Scenario: Cold start applies persisted language
- **WHEN** APP 冷启动，且 DataStore 持久化 `LocaleSelection.EN`
- **THEN** `attachBaseContext` 调 `LocaleHelper.wrap(base, Locale("en"))` 返回 wrapped context
- **AND** Activity 拿到 wrapped context 后，所有 `stringResource(R.string.*)` 走 `values-en/strings.xml`

### Requirement: MainActivity declares configChanges for locale
`AndroidManifest.xml` MainActivity MUST 加 `android:configChanges="locale|layoutDirection|orientation|screenSize|smallestScreenSize|screenLayout"` 阻止系统 locale 切换时销毁 Activity，由 App 自身 `recreate()` 接管。

#### Scenario: Manual language switch uses recreate not system restart
- **WHEN** 用户在「语言」页选 `EN`
- **THEN** `SettingsLanguageViewModel` 调 `LocaleStore.set(EN)` + `activity.recreate()`
- **AND** Activity 走 `onConfigurationChanged` 而非销毁重建（保留其它 ViewModel / screen state）

### Requirement: SettingsLanguageScreen exposes 3-option language picker
`feature/settings/i18n/SettingsLanguageScreen.kt` 渲染 CenterAlignedTopAppBar + LazyColumn 3 行（SYSTEM / ZH / EN），每行 icon + label + check 标记当前选中。

#### Scenario: Selecting option recreates Activity
- **WHEN** 用户点击某行 language option
- **THEN** `SettingsLanguageViewModel.onSelected(selection)` 触发
- **AND** `LocaleStore.set(selection)` 落盘 + `activity.recreate()`
- **AND** recreate 后 UI 立即显示新 locale 文案

#### Scenario: First-install and unselected shows SYSTEM checked
- **WHEN** DataStore 未写入过 language key
- **THEN** SettingsLanguageScreen 首屏渲染时 SYSTEM 行带 check 标记

### Requirement: MyTab exposes language settings entry
`MyScreen` 设置菜单 MUST 包含"语言"入口（导航到 `SettingsLanguage` route）。

#### Scenario: Tap language entry navigates to SettingsLanguage
- **WHEN** 用户在「我的」tab 点"语言"ListItem
- **THEN** `navController.navigate(SettingsLanguage)` 渲染 SettingsLanguageScreen
