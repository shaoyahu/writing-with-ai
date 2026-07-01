# fix-global-back-nav-and-gesture · tasks

## 1. spec delta

- [x] 1.1 在 `openspec/changes/fix-global-back-nav-and-gesture/specs/app-shell/spec.md` 写 `## MODIFIED Requirements`:加 Scenario "SettingsScreen TopAppBar 含 ArrowBack"
- [x] 1.2 在 `openspec/changes/fix-global-back-nav-and-gesture/specs/predictive-back-gesture/spec.md` 写 `## MODIFIED Requirements`:
  - MODIFIED "Predictive back animation MUST falls back gracefully":改写 "Activity 不挂自定义 BackHandler Composable" + 加 Scenario "主页 back 走 OnBackPressedCallback"
  - ADDED "Home screen back gesture shows press-again-to-exit hint":3 Scenario

## 2. bug 8 修复

- [x] 2.1 `feature/settings/SettingsScreen.kt`
  - TopAppBar 加 `navigationIcon = IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack) }`
  - 签名加 `onBack: () -> Unit`
- [x] 2.2 `feature/settings/SettingsEntry.kt`
  - `SettingsRoute` 形参加 `onBack: () -> Unit = {}`
- [x] 2.3 `app/AppNav.kt`
  - `composable<Settings> { SettingsEntry.SettingsRoute(onBack = { navController.popBackStack() }, ...) }`

## 3. bug 9 修复 · OnBackPressedCallback

- [x] 3.1 `app/MainActivity.kt`
  - 加 imports(SystemClock / Toast / OnBackPressedCallback / NavController / R)
  - 加 `private var lastBackPressAt: Long = 0L`
  - 加 `private val backCallback = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { val now = SystemClock.elapsedRealtime(); if (now - lastBackPressAt > 2000L) { lastBackPressAt = now; Toast.makeText(this@MainActivity, R.string.back_press_exit_hint, Toast.LENGTH_SHORT).show() } else { isEnabled = false; onBackPressedDispatcher.onBackPressed() } } }`
  - `onCreate` 在 `setContent` 前 `onBackPressedDispatcher.addCallback(this, backCallback)`
- [x] 3.2 `app/App.kt`
  - 签名加 `onNavControllerReady: (NavController) -> Unit = {}`
  - `AppNav` 传 `onNavControllerReady`
- [x] 3.3 `app/AppNav.kt` 加 `SideEffect { onNavControllerReady(navController) }`
- [x] 3.4 `app/MainActivity.kt` `App(onNavControllerReady = { nc -> nc.addOnDestinationChangedListener { _, dest, _ -> backCallback.isEnabled = dest.route == "com.yy.writingwithai.app.QuicknoteList" } })`

## 4. i18n

- [x] 4.1 `res/values/strings.xml` + `values-en/strings.xml` 加 `back_press_exit_hint`

## 5. 测试

- [x] 5.1 跳过 — `AppNavConsentGateTest` 覆盖 consent gate，无既有 OnBackPressedCallback 测试框架
- [x] 5.2 跑 `./gradlew :app:testDebugUnitTest` 全 PASS(22 tests 0 fail)

## 6. 验证

- [x] 6.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 6.2 `./gradlew :app:ktlintCheck` 0 violations
- [x] 6.3 `./gradlew :app:lintDebug` 0 errors
- [ ] 6.4 真机走 4 旅程(Settings 返回 / 主页防误触 / 2s 重置 / 其他屏 back 正常)

## 7. 文档

- [x] 7.1 `docs/progress.md` 加 1 条 2026-06-20 条目
