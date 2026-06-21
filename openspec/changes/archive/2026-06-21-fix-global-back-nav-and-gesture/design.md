# fix-global-back-nav-and-gesture · design

## 真因再确认

### bug 8 · SettingsScreen 无 ArrowBack

代码对照:`SettingsScreen.kt:32-37` TopAppBar 只 `title = { Text(...) }`,**没** `navigationIcon = ...`。`SettingsEntry.kt` SettingsRoute 也没接 `onBack: () -> Unit`。其他 6 个屏均有。

修复:SettingsScreen TopAppBar 加 `navigationIcon = IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack) }` + SettingsRoute 加 `onBack: () -> Unit` + AppNav 传 `navController.popBackStack()`。

### bug 9 · 主页侧滑无防误触

代码对照:`MainActivity` 无 `OnBackPressedCallback`;`AppNav` 主页 `QuicknoteList` 用 `NavHost` 自动 back 集成;NavHost 在 destination 是 startDestination 时 back → `NavController` 默认走 Activity.finish()(因 backstack 空)→ 退 launcher。

**不**写自定义 `BackHandler { enabled = ... }`(roadmap §7.2 拍板:不拦截手势,避免和系统打架)。

修复:用 **Android 官方推荐 idiom** —— `OnBackPressedCallback` + 2s 计时器 + Toast"再按一次退出":

```kotlin
private var lastBackPressAt: Long = 0L
private val backCallback = object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressAt > 2000L) {
            lastBackPressAt = now
            Toast.makeText(this@MainActivity, R.string.back_press_exit_hint, Toast.LENGTH_SHORT).show()
        } else {
            isEnabled = false  // 第二次 back 不再拦截,让 dispatcher 走默认 finish()
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
```

关键:
- `isEnabled` 随 destination 变化:**只在 QuicknoteList 主页启用**;其他 destination(详情 / 编辑 / 设置)→ `isEnabled = false` → 默认 popBackStack
- `addOnDestinationChangedListener { _, dest, _ -> backCallback.isEnabled = dest.hasRoute(QuicknoteList::class) }`
- `onCreate { onBackPressedDispatcher.addCallback(this, backCallback) }`

### 与 predictive-back-gesture spec 的关系

原 `predictive-back-gesture.spec.md` §"Predictive back animation MUST falls back gracefully" Scenario "不自定义 BackHandler" 措辞过严。bug 9 引入的是 **`OnBackPressedCallback`** 而非 **`BackHandler` Composable** —— 前者是 Activity-level,后者是 Compose-level。两者机制不同:OnBackPressedCallback 是 Android 12+ 官方防误触 idiom,BackHandler 是 Compose 早期 API。

修复:改写原 Scenario "不自定义 BackHandler" → "Activity 不挂自定义 BackHandler Composable" + 新 Scenario "主页 back 走 OnBackPressedCallback + Toast 二次确认 idiom"。

## 设计决策

### 决策 1 · bug 8 修复范围

候选:
- (a) 只修 SettingsScreen 单点
- (b) **修 SettingsScreen + 全部 7 个屏加 `onBack` 形参 + AppNav 传** → 一致性最强

选 (a)。理由:其他 6 个屏已 OK,bug 8 真因只在 SettingsScreen 1 个文件;过度工程不必要。

### 决策 2 · bug 9 二次确认机制:Toast vs Snackbar vs Dialog

候选:
- (a) Toast(传统 idiom)
- (b) Snackbar 底部
- (c) AlertDialog "确认退出?"

选 (a)。理由:
- 用户拍板"原生"
- Android 12+ 官方推荐 idiom(避免 Snackbar 阻塞底栏;避免 Dialog 强制交互)
- 实现简单,3 行代码

### 决策 3 · bug 9 拦截范围:全 destination vs 仅主页

候选:
- (a) **仅主页(QuicknoteList)启用 callback**
- (b) 所有 destination 都拦截 → 主页以外 back 也走 Toast → 破坏正常 popBackStack

选 (a)。理由:主页以外用户期望"返回上一页",不应该 Toast"再按一次退出";只有"无上一页" 才是退出场景。

### 决策 4 · bug 9 计时窗口

候选:
- (a) 2s(传统 Android idiom)
- (b) 3s / 1.5s

选 (a)。理由:业界标准(微信 / QQ / 大部分国产 App 都用 2s);用户已习惯。

### 决策 5 · bug 9 与 predictive back 动画共存

机制:predictive back 动画(M4-2)在用户**侧滑中**显示 launcher 预览 → 松手后系统调 `dispatchOnBackPressed` → 进 `OnBackPressedCallback.handleOnBackPressed`(若 enabled)→ 我们的 Toast + 二次确认逻辑。

**关键**:`isEnabled = false` 时 dispatcher 走默认 → Activity.finish()(M4-2 行为不变)。所以:
- 第一次侧滑:看到 launcher 预览 + 松手 → Toast "再按一次退出" + App 保持前台
- 第二次侧滑(2s 内):看到 launcher 预览 + 松手 → 走默认 finish() → 真退

**不**与系统动画打架(动画是 OS 渲染,我们只是拦截结果)。

## 实现路径

1. `feature/settings/SettingsScreen.kt`
   - TopAppBar 加 `navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }`
   - `SettingsScreen` 函数签名加 `onBack: () -> Unit` 形参

2. `feature/settings/SettingsEntry.kt`
   - `SettingsRoute` 形参加 `onBack: () -> Unit = {}`

3. `app/AppNav.kt`
   - `composable<Settings> { SettingsEntry.SettingsRoute(onBack = { navController.popBackStack() }, ...) }`

4. `app/MainActivity.kt`
   - 加 imports(SystemClock / Toast / OnBackPressedCallback)
   - 加 `private var lastBackPressAt: Long = 0L`
   - 加 `private val backCallback = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { ... } }`
   - `onCreate { ... onBackPressedDispatcher.addCallback(this, backCallback) }` (在 `setContent` 之前)
   - 在 `AppNav` composable 内 `navController.addOnDestinationChangedListener { _, dest, _ -> backCallback.isEnabled = dest.hasRoute(QuicknoteList::class) }`(通过 `App(onNavControllerReady)` 传 navController)

5. `app/App.kt`(M0 已建)
   - 签名加 `onNavControllerReady: (NavController) -> Unit = {}`
   - 内部 `NavHost(navController = navController, ...)` 后 `SideEffect { onNavControllerReady(navController) }`

6. `app/MainActivity.kt`
   - `App(onNavControllerReady = { nc -> nc.addOnDestinationChangedListener { _, dest, _ -> backCallback.isEnabled = dest.hasRoute(QuicknoteList::class) } })`

7. `res/values/strings.xml` + `values-en/strings.xml`
   - `back_press_exit_hint`(中文 "再按一次退出应用" / 英文 TODO)

## 风险与回退

- 风险 1:`OnBackPressedCallback.handleOnBackPressed` 在 Android 13+ `enableOnBackInvokedCallback="true"` 下行为略变(走 OnBackInvokedCallback 而非 onBackPressed);实测在新 API 上 callback 仍生效,验证走真机
- 风险 2:`addOnDestinationChangedListener` 在 AppNav 第一次 compose 时 navController 已 ready,但 `LaunchedEffect(navController)` 可能不触发(因 navController 引用稳定);缓解:用 `SideEffect { onNavControllerReady(navController) }`
- 风险 3:`hasRoute(QuicknoteList::class)` API 在 navigation-compose 2.8+ 才稳定;当前项目 libs.versions.toml 已用 2.8+ (M4-2 引入),验证 0 兼容问题

回退:`git revert <commit>` 即可,AndroidManifest 与 NavHost 结构 0 改动。
