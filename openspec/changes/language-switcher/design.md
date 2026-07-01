## Context

项目 `app/src/main/res/values/strings.xml`(中文，默认) + `values-en/strings.xml`(英文) 资源已就绪。但**没有 locale 选择器**:

- 没有任何 `LocaleHelper` / `LocaleStore` / `Configuration` 注入
- `AndroidManifest.xml` 单 `MainActivity` 没声明 `configChanges="locale"`(系统切语言会重建 Activity)
- `WritingApp : Application` 没 `attachBaseContext` 注入 locale
- 「我的」→「设置」菜单没"语言"入口

当前系统语言是中文(用户截图 UI 全部中文)，切英文要改系统设置，体验差。需要 APP 内可切。

## Goals / Non-Goals

**Goals:**
- 「我的」→「设置」→「语言」3 选 1(跟随系统 / 中文 / English),DataStore 持久化
- 选完即生效(`recreate()` Activity)
- 跟系统语言(`Configuration.getLocales().get(0)`)对齐
- 不破坏现有 i18n 资源结构(只新增，不改)

**Non-Goals:**
- 不支持多 locale 同时显示(只单一显式选)
- 不支持自定义 locale(只 system / zh / en 三档)
- 不重新设计 i18n 资源架构(只补缺失)
- 不在 onboarding 流程加语言选择(可后续加)
- 不做 RTL 自动布局(本就支持 layoutDirection)

## Decisions

### 1. locale 存 DataStore,key = 单字符串 "system" / "zh" / "en"

- **Why**:用户选择范围固定 3 档，字符串 enum 够用，不需要复杂 JSON。`LocaleStore.observe()` 暴露 Flow 给 VM + Application 用。
- **考虑过**:用 `androidx.core:core` 的 `AppCompatDelegate.setApplicationLocales()` — 这个是 Android 13+ 官方 Per-App Language API，但需要 minSdk 检查 + 13 以下需要做 backward compat。综合考虑，自管 DataStore + attachBaseContext 更稳(本项目 minSdk 还不确定，要查)。

### 2. `WritingApp.attachBaseContext` 注入 locale

- **Why**:`Application.attachBaseContext(base: Context)` 是所有 Activity / Composable 拿 Context 的入口，在这里 wrap 一个 new base 即可让所有资源 `getString(R.string.xxx)` 自动走正确 locale 资源。
- 实现:`Configuration` 拷贝 + `setLocale(...)` + `createConfigurationContext(...)` + `super.attachBaseContext(...)`。
- LocaleHelper 提供 `wrap(context: Context, selection: LocaleSelection): Context` 静态方法。

### 3. `MainActivity` 加 `configChanges="locale|layoutDirection|orientation|screenSize|smallestScreenSize|screenLayout"`

- **Why**:`configChanges` 列出 locale 后，系统切语言不会重建 Activity(我们自己 recreate);列出 orientation 等是减少横竖屏切换时的重建。本项目可能已有部分 configChanges，合并不重复。
- **为什么不用 `AppCompatDelegate` 的 per-app locale**:本项目 minSdk 未知，可能低于 33(per-app API 引入)，自管更稳。

### 4. UI 切换 3 选 1 + `recreate()`

- **Why**:`AppCompatActivity.recreate()` 让 Activity 重新走 `attachBaseContext` → 拉新 locale → 整 UI 刷新。`LocalConfiguration` 改后，Compose `stringResource(...)` 自动走新 locale 资源。
- 缺点:Activity 重建 → 当前页 state 丢失。要让用户从「语言」屏 navigate 到 root + recreate(`navController.popBackStack(root, inclusive=false); recreate()`)或直接在 root activity finish + start new。

### 5. 资源 audit 一次性补齐

- **Why**:`values-en/strings.xml` 可能漏一些 key，导致英文界面 fallback 到中文(开发期发现)。本次 change 跑 audit:对比 `values/strings.xml` 和 `values-en/strings.xml` 列出缺失 key，统一补;新增"语言设置"相关 key(values + values-en 都加)。
- 不重写所有中文 → 英文，只确保不漏。

## Risks / Trade-offs

- **R1 — recreate() 导致导航栈丢失** → 用户在「语言」屏选完，recreate 后 NavController 重新初始化，丢失所有 navigate 路径。
  - **Mitigation**:选完**先** navigate 回 root(`popBackStack(Notes, inclusive = false)`)再 recreate，这样重建后从 root 开始。或者提示用户"切换后应用重启"。
  - **接受方案**:recreate 整个 app 行为(简单、稳)。

- **R2 — locale 切换对正在运行的协程有副作用** → ViewModel 持有 DataStore Flow，如果 Flow 在切换瞬间 emit 老 value，可能覆盖新选。
  - **Mitigation**:VM 用 `map { it }` 只读不写，切换是 user 主动 select，不会竞争。

- **R3 — values-en 漏 key 致 fallback 中文** → 用户切 English 后发现某些字还是中文。
  - **Mitigation**:audit 一次性补齐;`build` 时加 lint 检查(可选，本次不做)。

- **R4 — 跟系统 `AppCompatDelegate` per-app locale API 冲突** → 如果未来升 minSdk 33+，想用系统 API 时，本自管实现要拆。
  - **Mitigation**:封装在 `LocaleHelper` / `LocaleStore` 里，未来重构影响面小。

## Migration Plan

- 不需要 migration:DataStore 首次启动读 default = "system"，旧用户行为不变。
- 部署:rebuild APK → 用户覆盖安装 → 默认跟系统(无变化)。用户在「我的」→「设置」首次切语言后才落 DataStore。
- 回滚:无破坏性，删掉 feature/settings/i18n 目录 + LocaleHelper + MainActivity configChanges 行即可。

## Open Questions

- Q1:本项目 minSdk 是多少?(未查)— 如果 ≥33 可用 `AppCompatDelegate.setApplicationLocales(LocaleListCompat)`，省事。需 `git grep` / build.gradle.kts 确认。
- Q2:onboarding 流程要不要加语言选择?(本次不做，留 follow-up)
- Q3:recreate 后导航栈丢失问题，UX 上是否要"应用重启" 提示?还是静默 recreate?