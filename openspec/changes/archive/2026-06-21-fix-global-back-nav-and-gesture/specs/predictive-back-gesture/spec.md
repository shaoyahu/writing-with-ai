# predictive-back-gesture Specification (delta)

## MODIFIED Requirements (fix-global-back-nav-and-gesture)

### Requirement: Predictive back animation MUST falls back gracefully (delta · 措辞放宽)

**原 Requirement 措辞**:`MainActivity` MUST **不**写自定义 `BackHandler { enabled = ... }`,**不**拦截系统 back 事件;让 `NavHost` + `OnBackPressedDispatcher` 自管(roadmap §7.2 明确"不自定义手势拦截，避免和系统手势打架")。

**改写后**:`MainActivity` MUST **不**在 Compose 层挂自定义 `BackHandler { enabled = ... }`(避免和系统手势打架);但 **MUST 允许** Activity 层挂 `OnBackPressedCallback`(Android 12+ 官方 idiom，行为是"back 结果处理"而非"手势拦截")实现防误触 UX(如主页"再按一次退出")。

#### Scenario: Activity 不挂自定义 BackHandler Composable

- **WHEN** `grep -rE "BackHandler" app/src/main/java/com/yy/writingwithai/`
- **THEN** 0 匹配(Compose `BackHandler` 禁用;`OnBackPressedCallback` 是 androidx.activity API，不受此约束)

#### Scenario: 主页 back 走 OnBackPressedCallback + Toast 二次确认

- **WHEN** 用户在 `QuicknoteList`(主页)按系统 back(虚拟键 / 三键导航)或侧滑手势
- **THEN** `MainActivity.onBackPressedDispatcher` 调 `backCallback.handleOnBackPressed()`(`backCallback.isEnabled == true` 当 destination 是 `QuicknoteList`);第一次触发 → `lastBackPressAt = now` + Toast "再按一次退出应用"(4s 自动消失);App **不** finish

## ADDED Requirements (fix-global-back-nav-and-gesture)

### Requirement: Home screen back gesture shows "press again to exit" hint

`MainActivity` MUST 维护 `private val backCallback = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { val now = SystemClock.elapsedRealtime(); if (now - lastBackPressAt > 2000L) { lastBackPressAt = now; Toast.makeText(this@MainActivity, R.string.back_press_exit_hint, Toast.LENGTH_SHORT).show() } else { isEnabled = false; onBackPressedDispatcher.onBackPressed() } } }`;`onCreate` MUST 在 `setContent` 前调 `onBackPressedDispatcher.addCallback(this, backCallback)`。

`App.kt` MUST 暴露 `onNavControllerReady: (NavController) -> Unit = {}` 形参;`MainActivity` MUST 传 `onNavControllerReady = { nc -> nc.addOnDestinationChangedListener { _, dest, _ -> backCallback.isEnabled = dest.hasRoute(QuicknoteList::class) } }`。

#### Scenario: 主页首次 back 触发 Toast + App 保持前台

- **WHEN** 用户在主页首次 back;`lastBackPressAt` 初始 0
- **THEN** `now - lastBackPressAt > 2000L` → `lastBackPressAt = now` + Toast "再按一次退出应用" 显示;`backCallback.isEnabled` 保持 true;App 不 finish，留在主页

#### Scenario: 主页 2s 内再次 back 触发 finish

- **WHEN** 用户在主页 2s 内再次 back;`lastBackPressAt` 已记录
- **THEN** `now - lastBackPressAt <= 2000L` → `backCallback.isEnabled = false` + `onBackPressedDispatcher.onBackPressed()`(递归调用，callback 已 disabled → dispatcher 走默认 → Activity.finish())→ App 退 launcher

#### Scenario: 主页 2s 后 back 重新触发 Toast(窗口重置)

- **WHEN** 用户在主页 back → 等 3s → 再 back
- **THEN** `now - lastBackPressAt > 2000L`(因 3s 间隔)→ 重置 + Toast 再次显示(用户有再 2s 窗口)

#### Scenario: 非主页 back 不走 hint(默认 popBackStack)

- **WHEN** 用户在 `QuicknoteDetail` / `QuicknoteEdit` / `Settings*` / `ModelManagement*` 等非主页屏按 back
- **THEN** `backCallback.isEnabled == false`(destination listener 已切);`onBackPressedDispatcher` 默认行为 → `NavController.popBackStack()` → 返回上一页;无 Toast 显示

#### Scenario: widget 启动的 quicknote/edit 不被当主页

- **WHEN** 用户从 widget 启动进 `quicknote/edit?prefillFocus=true`(主页是 `QuicknoteList`,edit 不是主页)
- **THEN** `backCallback.isEnabled == false`;back 行为默认 `popBackStack()` → 退到 `QuicknoteList`(此时 `destination` listener 触发 `isEnabled = true` → 下次 back 走 Toast hint)
