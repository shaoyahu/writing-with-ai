## ADDED Requirements

### Requirement: APP 必须支持用户在「设置 → 语言」手动选择显示语言

APP MUST 在「我的 → 设置」菜单提供「语言」入口，点击进入语言选择屏后，展示 3 选 1(跟随系统 / 中文 / English)。用户选完后，APP MUST 立即写 DataStore 持久化，并 `recreate()` 当前 Activity 让整 UI 走新 locale 资源。

#### Scenario: 用户打开语言选择屏
- **WHEN** 用户在「我的」→「设置」点击「语言」
- **THEN** 系统 navigate 到 `SettingsLanguage` 路由，展示 3 选 1 列表(当前选中项带 primary 颜色高亮)

#### Scenario: 用户选「中文」并立即生效
- **WHEN** 用户在语言列表选「中文」
- **THEN** DataStore 写 `"zh"`,Activity `recreate()`，新资源走 `values/strings.xml`(中文)，所有 `stringResource(...)` 重新解析

#### Scenario: 用户选「English」并立即生效
- **WHEN** 用户在语言列表选「English」
- **THEN** DataStore 写 `"en"`,Activity `recreate()`，新资源走 `values-en/strings.xml`(英文)

#### Scenario: 用户选「跟随系统」
- **WHEN** 用户选「跟随系统」
- **THEN** DataStore 写 `"system"`,Activity `recreate()`,locale 取 `Configuration.getLocales().get(0)`(系统当前 locale);若系统是中文则走中文资源，英文则走英文

### Requirement: locale 选择必须跨进程持久化

`LocaleStore` MUST 用 DataStore Preferences 持久化用户选择(单字符串 enum:"system" / "zh" / "en")。APP 冷启动时必须读 DataStore 恢复上次选择，不可丢失。

#### Scenario: APP 冷启动恢复上次选择
- **WHEN** 用户上次选了「English」并退出 APP，再次冷启动
- **THEN** `LocaleStore` 读 DataStore 返回 `"en"`,Application.attachBaseContext 注入英文 locale，所有界面走 `values-en/strings.xml`

#### Scenario: 首次启动默认「跟随系统」
- **WHEN** 用户首次启动 APP(DataStore 无 key)
- **THEN** `LocaleStore` 返回默认值 `"system"`,locale 取系统语言

### Requirement: Activity configChanges 必须包含 locale

`AndroidManifest.xml` 单 `MainActivity` MUST 加 `android:configChanges="locale|layoutDirection|orientation|screenSize|smallestScreenSize|screenLayout"`，确保系统切语言时 Activity 不会由系统重建(由我们自己 `recreate()` 接管)。

#### Scenario: configChanges 阻止系统重建
- **WHEN** 用户在 APP 内选语言触发 `recreate()`
- **THEN** Activity 走 `onDestroy() → onCreate()` 单次重建，不走 `configChanges` 命中的情况(我们自己 recreate 时不需要 configChanges);但当系统侧(Notification / AccessibilityService 等)触发 locale change，系统走 configChanges 不重建，我们用 `onConfigurationChanged` 处理或下次 recreate 才生效

### Requirement: 语言设置相关字符串必须在 values + values-en 都加

新增"语言设置"相关的 string resource(屏标题、3 个选项 label、hint 文案)MUST 同时在 `values/strings.xml` 和 `values-en/strings.xml` 定义，缺一会导致某语言 fallback 到默认 locale。

#### Scenario: 中文界面看到中文
- **WHEN** 选「中文」，打开「语言」屏
- **THEN** 屏标题 / 3 个选项 label 都是中文

#### Scenario: 英文界面看到英文
- **WHEN** 选「English」，打开「语言」屏
- **THEN** 屏标题 / 3 个选项 label 都是英文

### Requirement: i18n 资源覆盖度 audit

本次 change MUST 跑一次 values vs values-en 字符串对比 audit，列出 `values/strings.xml` 有但 `values-en/strings.xml` 没有的 key，统一补齐。允许"故意不加英文"标注(lint suppression 注释)，但**没有标记的 key 必须在 values-en 提供**。

#### Scenario: audit 列出缺失 key
- **WHEN** 跑 `diff values/strings.xml values-en/strings.xml`(或自定义脚本)
- **THEN** 输出 missing key 列表(如有)，开发在本次 change 内补齐

#### Scenario: 缺失 key fallback 行为
- **WHEN** 用户选「English」，某 string 在 values-en 没定义
- **THEN** Android framework 自动 fallback 到 values 默认 locale(中文)，该 UI 元素显示中文
- **Mitigation**:本次 change 跑 audit 把缺失补齐，行为消失