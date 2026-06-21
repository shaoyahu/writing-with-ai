# fix-global-back-nav-and-gesture

## Why

真机 walkthrough(2026-06-20)暴露 2 个跨多 feature 的导航/手势 UX bug,均影响 v1 内测前的"导航可达性":

- **bug 8**:除主页外其他页面无"左上角返回按钮"。现状排查:
  - `QuickNoteDetailScreen`:TopAppBar.navigationIcon = ArrowBack(line 137-141)✅
  - `QuickNoteEditorScreen`:TopAppBar.navigationIcon = ArrowBack(line 61-66)✅
  - `SettingsDataScreen`:TopAppBar.navigationIcon = ArrowBack(line 60-63)✅
  - `ModelManagementScreen`:TopAppBar.navigationIcon = ArrowBack(line 59-63)✅
  - `ModelProviderDetailScreen`:TopAppBar.navigationIcon = ArrowBack(line 95-99)✅
  - `PromptTemplateScreen`:TopAppBar.navigationIcon = ArrowBack(line 51-54)✅
  - **`SettingsScreen`:TopAppBar.navigationIcon = 缺失**(line 32-37,只设 title,无 navigationIcon)❌
  - `OnboardingRoute`:无 TopAppBar(全屏滚动)✅ 无返回需求

**用户痛点**:设置主屏 → 误点 ListItem 后(无 chip)→ 没返回按钮 → 只能系统 back / 杀进程重进 / 侧滑(some 国产 ROM 手势不一致)

- **bug 9**:主页侧滑**第一次直接退到桌面**(无防误触)。现状:`MainActivity` 无 `OnBackPressedCallback` / `BackHandler`,Compose 用 `OnBackPressedDispatcher`(经 NavHost 自动集成);用户在 `QuicknoteList` 主页侧滑 → dispatcher 无 callback → 系统走 Activity.finish() → 退 launcher → 容易误触(尤其国产 ROM 侧滑敏感度高)。

两 bug 性质不同(bug 8 单屏 + bug 9 全局),但都在导航域,合并同一 OpenSpec change。

## What changes

### bug 8 修复

- `feature/settings/SettingsScreen.kt` TopAppBar 加 `navigationIcon = IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack) }`
- `SettingsEntry.kt` SettingsRoute 签名加 `onBack: () -> Unit` 形参;`AppNav.kt` composable<Settings> 传 `{ navController.popBackStack() }`
- 同步检查所有 TopAppBar 是否接 navigationIcon(纯加,不改逻辑)

### bug 9 修复 · Android 12+ 原生 OnBackPressedCallback + Toast"再按一次退出"

按用户拍板"直接用原生",不写自定义 `BackHandler enabled = false` 风格(roadmap §7.2 拍板"不自定义手势拦截,避免和系统手势打架")。

实现路径(原生 API · Android 12+ 官方推荐 idiom):

1. `MainActivity.onCreate` / `onResume` 写一个 `OnBackPressedCallback(enabled = isOnHomeRoute)` 绑到 `onBackPressedDispatcher`:
   - `isOnHomeRoute` = `navController.currentDestination?.route == "com.yy.writingwithai.app.QuicknoteList"`(Compose Nav 类型安全 route 检查)
   - callback `enabled = true` 时拦截 back → 显示 Toast "再按一次退出应用"(传统 Android 防误触 idiom,非系统级手势改动)→ 启动 2s 计时器 → 期间第二次 back 走 `isEnabled = false` → dispatcher 默认 finish()
   - 计时器结束 → callback `enabled = true` 重新拦截

   **不**改 `enableOnBackInvokedCallback`(M4-2 已开);**不**改 predictive back 动画(M4-2 已实现);**只**在主页 back 路径插一个 callback → 系统 predictive back 动画仍走(用户在侧滑时看到系统级返回 launcher 预览),但侧滑结束后回调里再 Toast 二次确认 → 真退。

2. `MainActivity.kt`:
   - 加 `private var lastBackPressAt: Long = 0L`
   - 加 `private val backCallback = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { val now = SystemClock.elapsedRealtime(); if (now - lastBackPressAt > 2000L) { lastBackPressAt = now; Toast.makeText(this@MainActivity, R.string.back_press_exit_hint, Toast.LENGTH_SHORT).show() } else { isEnabled = false; onBackPressedDispatcher.onBackPressed() } } }`
   - `onCreate { onBackPressedDispatcher.addCallback(this, backCallback) }`
   - **导航变化时**:`navController.addOnDestinationChangedListener { _, destination, _ -> backCallback.isEnabled = destination.hasRoute(QuicknoteList::class) }`

   理由:这是 Android 12+ **官方推荐**的"再按一次退出" idiom。**不**与系统手势冲突(predictive back 动画走完后调用 callback `handleOnBackPressed`,用户感知是"看到 launcher 预览 + Toast 提示 + 再滑才真退")。

3. `res/values/strings.xml` + `values-en/strings.xml`:
   - `back_press_exit_hint`(中文 "再按一次退出应用" / 英文 TODO)

### 不改

- `enableOnBackInvokedCallback` 声明(已在 AndroidManifest,M4-2)
- NavHost 路由结构 / Nav type-safe route
- `OnBackPressedDispatcher` 默认行为(除主页外所有 destination 让 callback `isEnabled = false` → 默认 popBackStack / finish)
- `Settings` / `ModelManagement` / `ModelProviderDetail` / `PromptTemplate` 等已有 ArrowBack 的屏幕(已 OK,bug 8 真因只在 SettingsScreen 单点)

## Impact

- 影响的 spec:
  - MODIFIED `openspec/specs/app-shell/spec.md` `Requirement: AppNav defines an empty NavHost` → 加 Scenario "所有非主页 destination TopAppBar 含 navigationIcon = ArrowBack"
  - MODIFIED `openspec/specs/predictive-back-gesture/spec.md` `Requirement: AndroidManifest enables predictive back on app + activity` → 加 Scenario "主页侧滑触发二次确认 Toast"
  - MODIFIED `openspec/specs/predictive-back-gesture/spec.md` 新增 `Requirement: Home screen back gesture shows "press again to exit" hint`
- 不影响其他 spec
- 不引入新依赖(`OnBackPressedCallback` + `OnBackPressedDispatcher` 已在 androidx.activity)
- feature 自包含
