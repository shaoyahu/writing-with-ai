## 1. i18n 资源 audit + 补齐

- [ ] 1.1 写 audit 脚本(临时):用 bash/jq 列出 `values/strings.xml` 有但 `values-en/strings.xml` 没有的 key
- [ ] 1.2 在 `values-en/strings.xml` 补齐缺失 key(允许"故意不加"用注释标注)
- [ ] 1.3 新加"语言设置"相关 key:`settings_language_title` / `settings_language_option_system` / `settings_language_option_zh` / `settings_language_option_en` / `settings_language_section`(values + values-en 都加)

## 2. LocaleHelper + LocaleStore 基础设施

- [ ] 2.1 `core/i18n/LocaleStore.kt` 新增，`@Singleton` + DataStore Preferences,key = `"language_selection"`,enum "system" / "zh" / "en"
- [ ] 2.2 `core/i18n/LocaleHelper.kt` 新增，纯 object:
  - `enum class LocaleSelection { SYSTEM, ZH, EN }`(serialName = "system" / "zh" / "en")
  - `fun resolveLocale(selection: LocaleSelection, systemLocale: Locale): Locale`(SYSTEM → systemLocale,ZH → Locale("zh"),EN → Locale("en"))
  - `fun wrap(base: Context, locale: Locale): Context`(`Configuration.setLocale + createConfigurationContext`)
- [ ] 2.3 `app/src/main/java/com/yy/writingwithai/app/WritingApp.kt`:`attachBaseContext(base: Context)` 覆盖，读 `LocaleStore.first()` 拿 selection,resolve locale,wrap base
  - 注意:`attachBaseContext` 同步调，`LocaleStore.first()` 是 suspend — 用 `runBlocking` 在 IO 调度取一次(冷启动只走一次，OK)
  - Hilt:在 Application 里 `@Inject lateinit var localeStore: LocaleStore`，构造完成后再 attachBaseContext(实际顺序:super.onCreate 在 attachBaseContext 后)
- [ ] 2.4 `app/src/main/AndroidManifest.xml` MainActivity 加 `android:configChanges="locale|layoutDirection|orientation|screenSize|smallestScreenSize|screenLayout"`

## 3. 「设置 → 语言」UI

- [ ] 3.1 `app/src/main/java/com/yy/writingwithai/feature/settings/i18n/SettingsLanguageScreen.kt` + `SettingsLanguageViewModel.kt`
  - VM 读 `LocaleStore.observe()` 显示当前选中，点击选项调 `LocaleStore.set(selection)` 写 + `recreate()`
  - 屏布局:CenterAlignedTopAppBar + LazyColumn 3 行(每行 icon + label + check)
- [ ] 3.2 `AppNav.kt` 加 `data object SettingsLanguage : ...` route + `composable<SettingsLanguage>` block
- [ ] 3.3 `MyScreen.kt` 设置菜单加"语言"入口(`MeTabTarget.SettingsLanguage`)

## 4. 单测

- [ ] 4.1 `LocaleHelperTest`:`resolveLocale(SYSTEM, systemLocale) == systemLocale` / `resolveLocale(ZH, ...) == Locale("zh")` / `resolveLocale(EN, ...) == Locale("en")`
- [ ] 4.2 `LocaleStoreTest`:save / observe 往返一致
- [ ] 4.3 `SettingsLanguageViewModelTest`:mock LocaleStore + activity-recreate trigger

## 5. 验证

- [ ] 5.1 `./gradlew :app:testDebugUnitTest` 全绿 + ktlint 0 violations
- [ ] 5.2 真机/模拟器验证(APK install 后):
  - 启动 APP 默认跟系统(中文)
  - 「我的」→「设置」→「语言」→ 选「English」→ 整个 UI 立即切英文
  - 杀进程冷启动，仍英文
  - 切回「跟随系统」→ 跟随系统语言

## 6. 文档

- [ ] 6.1 `docs/progress.md` 追加:`2026-07-01 language-switcher · 「我的 → 设置 → 语言」可手动切跟随系统 / 中文 / English,DataStore 持久化 + recreate 即时生效`
- [ ] 6.2 `openspec/specs/ai-gateway` / 其他 spec 不需新增 delta(本次 change 是新 capability，不交叉)