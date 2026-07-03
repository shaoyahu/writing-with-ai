## Why

项目 `app/src/main/res/values/strings.xml`(中文) + `app/src/main/res/values-en/strings.xml`(英文) 资源都已就绪，但**没有 locale 选择器**:用户不能在 APP 内手动切中文 / English，只能跟系统语言走。当前系统语言是中文(用户截图 UI 全部中文)，切换系统语言麻烦。需要在「我的」→「设置」加「语言」选项(跟随系统 / 中文 / English)，用户选完即时生效。

## What Changes

- 新增 `core/i18n/LocaleHelper.kt` — DataStore 存用户 locale 选择(`system` / `zh` / `en`),`WritingApp.attachBaseContext` 注入 `Configuration` locale
- 新增 `feature/settings/i18n/SettingsLanguageScreen.kt` + `SettingsLanguageViewModel.kt` — 列表选 3 选 1(跟随系统 / 中文 / English)，点选立即写 DataStore + `recreate()` Activity 让 UI 刷新
- `AndroidManifest.xml` 单 `MainActivity` 加 `android:configChanges="locale|layoutDirection|orientation|screenSize|smallestScreenSize|screenLayout"` — locale 变化不让系统重建 Activity(我们自己 recreate)
- 补全 `values-en/strings.xml` 缺失项 — `app_name` / 关键 UI 字符串双语;新加的"语言设置"相关字符串(values + values-en 都加)
- 不影响其他 feature:`AppNav` 加新路由 `SettingsLanguage`，「我的 → 设置」菜单加入口

**BREAKING**:无。资源新增(都是可选，无现有 key 改名)。Activity configChanges 不影响行为(只对 locale 切换这一种情况优化)。

## Capabilities

### New Capabilities

- `language-switcher`:用户手动选择 APP 显示语言(跟随系统 / 中文 / English),DataStore 持久化，即时生效

### Modified Capabilities

- (无):没有现成 capability 涉及语言切换。本次 change 是新 capability，不开 delta。

## Impact

- 新增文件:
  - `core/i18n/LocaleHelper.kt`(`core/prefs` 包下也合理，跟 `ConsentStore` 等同类)
  - `core/i18n/LocaleStore.kt`(DataStore 包装)
  - `feature/settings/i18n/SettingsLanguageScreen.kt` + `SettingsLanguageViewModel.kt`
  - `app/src/main/res/values/strings.xml` + `values-en/strings.xml` 加语言相关 key
- 改动文件:
  - `app/src/main/java/com/yy/writingwithai/app/WritingApp.kt` — `attachBaseContext` 用 `LocaleHelper.wrap`
  - `app/src/main/AndroidManifest.xml` — MainActivity 加 `configChanges`
  - `app/src/main/java/com/yy/writingwithai/app/AppNav.kt` — 加 `SettingsLanguage` route
  - `app/src/main/java/com/yy/writingwithai/feature/my/MyScreen.kt` — 设置菜单加"语言"入口
- 测试:
  - `LocaleHelperTest`(locale 转换 + DataStore 读写)
  - `SettingsLanguageViewModelTest`(选 → DataStore 写)
  - 现有 i18n 资源覆盖度 audit(列出 values 有但 values-en 没有的 key)
- Roadmap:§15.1 不需更新(此为 UI 完备化，非新方向)
- spec:新加 `language-switcher` capability