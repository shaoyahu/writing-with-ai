# Tasks: animation-switch-redesign-followup

> 参考:
> - [proposal.md](./proposal.md)
> - [design.md](./design.md)
> - [specs/animation-system/spec.md](./specs/animation-system/spec.md)
> - 既有 `openspec/specs/animation-system/spec.md`(archive 时合并 delta)
> - 已归档 `openspec/changes/archive/2026-07-02-animation-switch-redesign/`(只读历史,本次不动)

## 1. 共享组件提取(无依赖,可并行)

- [x] 1.1 新增 `feature/settings/animation/AnimationToggleRow.kt` 顶层 public Composable
  - 把 `AnimationStylePreviewScreen.kt` 的 `private fun AnimationToggleRow(...)` 整段移到顶层文件
  - 移除 `private` 修饰,使函数 `public` 默认可见性
  - 文件顶部 import 同步更新(`import com.yy.writingwithai.core.ui.AnimatedSwitch`)
  - 加 KDoc 说明"供 `AnimationDetailScreen` 复用,且未来其他动画页面可复用"

## 2. 修复双重标志(依赖 §1)

- [x] 2.1 修 `feature/settings/animation/AnimationStylePreviewScreen.kt` `AnimationStyleCard`
  - 删除选中态 `if (selected) Icon(...)` 整段(原 L242-L248)
  - 移除 `import androidx.compose.material.icons.filled.Check`(若不再用)
  - 仅保留 `RadioButton(selected = selected)` 作为 4 选 1 选中态视觉锚点

## 3. 拆 VM(依赖 §1)

- [x] 3.1 `AnimationStylePreviewViewModel.kt` 删 nav/tab 字段
  - 删除 `navAnimationsEnabled: StateFlow<Boolean>` + `tabAnimationsEnabled: StateFlow<Boolean>` + 2 个 setter 方法
  - 删除相关 import (`UserPrefsStore.navAnimationsEnabledFlow` 等)
  - 保留 `animationStyle` + `reduceMotionEnabled` + `onStyleSelected`
- [x] 3.2 新增 `feature/settings/animation/AnimationDetailViewModel.kt`
  - 构造:`@HiltViewModel` + `@Inject constructor(@ApplicationContext context, userPrefsStore)`
  - 暴露 `navAnimationsEnabled: StateFlow<Boolean>`(collect 自 PrefsStore)
  - 暴露 `tabAnimationsEnabled: StateFlow<Boolean>`(collect 自 PrefsStore)
  - 暴露 `reduceMotionEnabled: StateFlow<Boolean>`(沿用旧 VM 的反射读法)
  - 方法 `onNavAnimationsToggled(enabled: Boolean)` → `setNavAnimationsEnabled`
  - 方法 `onTabAnimationsToggled(enabled: Boolean)` → `setTabAnimationsEnabled`
  - 删掉 `AnimationStyle` 相关 import
- [ ] 3.3 拆 `AnimationStylePreviewViewModelTest.kt`
  - 删除现有 `onNavAnimationsToggled false ...` / `onTabAnimationsToggled false ...` 2 个 case
  - 新增 `AnimationDetailViewModelTest.kt`,沿用 MockK Context + FakeUserPrefsStore 模式,2 case 覆盖 nav/tab toggle
  - `onStyleSelected` / `reduceMotion` 2 case 仍留 `AnimationStylePreviewViewModelTest`

## 4. 简化主屏(依赖 §1-§3)

- [x] 4.1 `AnimationStylePreviewScreen.kt` 删 toggle 段
  - 删除 `item(key = "toggle_nav")` + `item(key = "toggle_tab")` 两个 LazyColumn item
  - 删除 collect `navEnabled` / `tabEnabled` 两个 StateFlow
  - 删除 `AnimationToggleRow(...)` 调用与本文件 private 定义(已在 §1 提到顶层)
  - 保留 `AnimationStyleCard` + `ReduceMotionBanner`
  - viewModel 改为只注入 `AnimationStylePreviewViewModel`(只读 style)

## 5. 新二级页(依赖 §1-§4)

- [x] 5.1 新增 `feature/settings/animation/AnimationDetailScreen.kt`
  - 入参:`onBack: () -> Unit, viewModel: AnimationDetailViewModel = hiltViewModel()`
  - 渲染:TopAppBar("动画详细") + `LazyColumn` 包含 reduce-motion banner + 2 个 `AnimationToggleRow`(Navigation / Tab)
  - 复用顶部 `AnimationToggleRow`(从 §1)
  - banner 内容复用 `ReduceMotionBanner` —— **提取到包顶层**(`animation/ReduceMotionBanner.kt`),供 2 个 Screen 共用
- [x] 5.2 提取 `feature/settings/animation/ReduceMotionBanner.kt` 顶层
  - 把 `AnimationStylePreviewScreen` 里的 private `ReduceMotionBanner` 移到顶层
  - 默认 visible(参数里 bool 用法不引入此处)

## 6. 路由注册(依赖 §5)

- [x] 6.1 `app/AppNav.kt` 新增 `data object SettingsAnimationDetail`
  - 紧挨 `SettingsAnimationStyle` 注册(L407 附近)
  - 在 NavHost(`composable<T>` block,L295-L300 附近)注册 `composable<SettingsAnimationDetail>` → `AnimationDetailScreen(onBack = ...)`
- [x] 6.2 `feature/my/MeTabTarget.kt` 加 `SettingsAnimationDetail` 枚举项
- [x] 6.3 `app/AppShell.kt` Me 路由表 `when` exhaustive 新分支
  - `MeTabTarget.SettingsAnimationDetail -> navController.navigate(...)`

## 7. 入口(依赖 §6)

- [x] 7.1 `feature/my/MyScreen.kt` Section 2 「显示」加 ListItem
  - 紧挨「动画风格」「语言」之间或底部插入一条 MyListItem
  - `title = stringResource(R.string.anim_detail_title)`
  - leading icon:`Icons.Filled.Tune`(Material Icons 已有)
  - `onNavigate = MeTabTarget.SettingsAnimationDetail`
  - 用现成 `MyListItem` Composable,不新加组件

## 8. i18n(依赖 §7,内容独立)

- [x] 8.1 `app/src/main/res/values/strings.xml` 加 1 key
  - `anim_detail_title` = "动画详细"
- [x] 8.2 `app/src/main/res/values-en/strings.xml` 同步加同名 1 key
  - `anim_detail_title` = "Animation Details"
- [x] 8.3 parity 验证
  - `grep -oE 'name="anim_(style|toggle|detail)_[a-z_]+"' values/strings.xml | sort -u` 与 `values-en/strings.xml` 输出 diff 为空

## 9. 验证与归档(依赖 §1-§8)

- [x] 9.1 编译 + ktlint + 单测全跑
  - `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:ktlintCheck`
  - `./gradlew :app:testDebugUnitTest`
- [ ] 9.2 装到模拟器目视 review
  - `./gradlew :app:installDebug`
  - 打开「我的 → 显示 → 动画详细」:看到 2 开关,且 reduce-motion 时 disabled 显示
  - 打开「我的 → 显示 → 动画风格」:只剩 4 张风格卡,选中态只有 RadioButton,无右侧 Check 重叠
  - 验证:在「动画详细」关掉 nav 开关 → 回到「随手记」 tab 切换 → 仍有动画;但页面间 push 仍无过度(因为 nav = false)
  - 验证:杀进程重启,2 开关状态保留
- [ ] 9.3 走 `openspec archive` 归档本 change
  - delta 合并回 `openspec/specs/animation-system/spec.md`
  - change 目录移入 `openspec/changes/archive/`
  - 在 `docs/progress.md` 追加一条记录
  - git commit(由用户手动执行)
- [ ] 9.4 Open Questions 拍板(均在 design §Decisions 拍板,本次 followup 无新增 open question)
