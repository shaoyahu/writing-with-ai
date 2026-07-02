# Tasks: animation-switch-redesign

> 参考:
> - [proposal.md](./proposal.md)
> - [design.md](./design.md)
> - [specs/animation-system/spec.md](./specs/animation-system/spec.md)
> - 既有 `openspec/specs/animation-system/spec.md`(archive 时合并 delta)

## 1. 基础设施(无依赖,可并行)

- [x] 1.1 修 `core/ui/AnimatedSwitch.kt` 的 thumb padding 对称性(Decision 1)
  - 把 `offsetX` 改为 `(trackWidth - thumbSize) / 2 + (trackWidth - thumbSize) * checkedProgress`
  - 与 `androidx.compose.material3.Switch` 内部算法对齐
  - 验证:thumb 在关闭态/打开态到 track 边缘距离相等(1dp 误差内)
- [x] 1.2 `core/ui/animation/AnimationTokens.kt` 新增 `tokensFor(style, navEnabled, tabEnabled)`(Decision 3)
  - 保留 `AnimationStyle.tokens()`(实现委托给 `tokensFor(this, true, true)`,行为完全不变)
  - 5 个被覆盖字段:`navEnter / navExit / navPopEnter / navPopExit / tabContentSpec`
  - 其它字段(switchSpec / expandEnter / collapseExit / ...)透传
- [x] 1.3 `core/prefs/UserPrefsStore.kt` 新增 2 个 Boolean key + Flow + setter(Decision 2)
  - `booleanPreferencesKey("nav_animations_enabled_v1")` + `navAnimationsEnabledFlow` + `setNavAnimationsEnabled(Boolean)`
  - `booleanPreferencesKey("tab_animations_enabled_v1")` + `tabAnimationsEnabledFlow` + `setTabAnimationsEnabled(Boolean)`
  - 默认 `true`,Flow 用 `.map { it ?: true }` 兜底,corrupt 走 `true` + 单行 logcat 警告

## 2. Theme 接线(依赖 §1)

- [x] 2.1 `app/ui/theme/Theme.kt` 改 `WritingAppTheme` 3 路 Flow collect(Decision 4)
  - 新增 `navAnimationsEnabledFlow` + `tabAnimationsEnabledFlow` collect
  - `tokens = remember(effectiveStyle, navEnabled, tabEnabled, reduceMotion) { ... }`
  - reduce-motion 仍走 `AnimationStyle.NONE.tokens()` 强制,且不写盘 2 个 Boolean

## 3. 设置页(依赖 §1 + §2)

- [x] 3.1 `AnimationStylePreviewViewModel.kt` 新增 2 个 StateFlow + 2 个 toggle 方法(Decision 5)
  - `navAnimationsEnabled: StateFlow<Boolean>`(collect 自 PrefsStore)
  - `tabAnimationsEnabled: StateFlow<Boolean>`(collect 自 PrefsStore)
  - `onNavAnimationsToggled(enabled: Boolean)` 调 `setNavAnimationsEnabled`
  - `onTabAnimationsToggled(enabled: Boolean)` 调 `setTabAnimationsEnabled`
- [x] 3.2 `AnimationStylePreviewScreen.kt` 重构布局(Decision 5)
  - **第一段**:2 个独立开关行(导航动画 / 标签动画),每行左侧标题+描述,右侧 `AnimatedSwitch(checked = ..., onCheckedChange = vm.onNavAnimationsToggled/onTabAnimationsToggled)`
  - reduce-motion 时 2 个开关显示为 disabled(只读 OFF),banner 提示"系统无障碍设置已强制关闭"
  - **第二段**:保留 4 张"风格库"卡片(MINIMAL / FLUID / IMMERSIVE / NONE),点选仍调 `vm.onStyleSelected(style)`,但**只改 style 枚举**,不再影响 nav/tab 布尔
  - 移除 4 张卡片里**内嵌的** `AnimatedSwitch` 演示器(原来每张卡片底部的"导航+switch+标签"那行,因为现在开关已经独立出来了)
- [ ] 3.3 (可选) `SettingsScreen.kt` / `NoteAssociationSettingsScreen.kt` 用 `AnimatedSwitch` 的地方,**目视 review** padding 修改后的视觉,如有破坏性 regression,调整调用点的 size / 容器

## 4. i18n(依赖 §3,内容独立)

- [ ] 4.1 `app/src/main/res/values/strings.xml` 加 4 个 key
  - `anim_toggle_nav_title` = "导航动画"
  - `anim_toggle_nav_description` = "页面之间切换的过渡动画"
  - `anim_toggle_tab_title` = "标签动画"
  - `anim_toggle_tab_description` = "Tab 切换时的内容过渡"
- [ ] 4.2 `app/src/main/res/values-en/strings.xml` 同步加同名 4 个 key
  - `anim_toggle_nav_title` = "Navigation animations"
  - `anim_toggle_nav_description` = "Transitions when navigating between screens"
  - `anim_toggle_tab_title` = "Tab animations"
  - `anim_toggle_tab_description` = "Content transitions when switching tabs"
- [ ] 4.3 parity 验证
  - `grep -oE 'name="anim_(style|toggle)_[a-z_]+"' values/strings.xml | sort -u` 与 `values-en/strings.xml` 输出 diff 为空

## 5. 测试(依赖 §1-§4)

- [ ] 5.1 `core/prefs/UserPrefsStoreTest.kt` 加 2 个 key 的测试
  - 缺 key 时返回 `true`
  - corrupt value 时 fallback `true` + logcat warning
  - set → read round-trip
- [ ] 5.2 `core/ui/animation/AnimationTokensTest.kt` 新建(若不存在)+ `tokensFor` 覆盖
  - `tokensFor(IMMERSIVE, false, false)` 的 4 个 nav field 都是 `EnterTransition.None / ExitTransition.None`
  - `tabContentSpec` 是 `snap()`
  - 其它字段等于 IMMERSIVE 基线
  - `tokensFor(IMMERSIVE, true, true) == IMMERSIVE.tokens()`
- [ ] 5.3 `feature/settings/animation/AnimationStylePreviewViewModelTest.kt` 加 case
  - 调 `onNavAnimationsToggled(false)` 后 `navAnimationsEnabled.value == false`
  - 调 `onTabAnimationsToggled(false)` 后 `tabAnimationsEnabled.value == false`
  - 调 `onStyleSelected(IMMERSIVE)` 不影响 2 个 toggle
  - reduce-motion 时 2 个 toggle 强制为 false(不持久化,只在 VM 内部判定)

## 6. 验证与归档(依赖 §1-§5)

- [ ] 6.1 编译 + ktlint + 单测全跑
  - `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:ktlintCheck`
  - `./gradlew :app:testDebugUnitTest`
- [ ] 6.2 装到模拟器目视 review
  - `./gradlew :app:installDebug`
  - 打开「设置 → 动画风格」
  - 验证:2 个开关的 thumb padding 对称(关闭/打开两端距离相等)
  - 验证:关掉"导航动画"开关 → 实际页面切换立即变成无动画
  - 验证:关掉"标签动画"开关 → Tab 切换变成即时切换(snap)
  - 验证:4 张风格卡仍可点选,只改 transition 曲线,不影响 2 个开关
  - 验证:杀进程重启,2 个开关状态保留
- [ ] 6.3 走 `openspec archive` 归档本 change
  - delta 合并回 `openspec/specs/animation-system/spec.md`
  - change 目录移入 `openspec/changes/archive/`
  - 在 `docs/progress.md` 追加一条记录(本 change 关键节点)
  - git commit(由用户手动执行,本仓库会话规则)

## 7. Review check(在 apply 前由用户对 design.md 的 Open Questions 拍板)

- [x] 7.1 Q1:`nav_animations_enabled_v1` / `tab_animations_enabled_v1` 默认 `true` 还是 `false`?→ **`true`**(尊重现状)
- [x] 7.2 Q2:reduce-motion 开启时,2 个开关 disabled 显示还是直接隐藏?→ **disabled 显示**(`AnimationToggleRow` 传 `enabled = !reduceMotion`)
- [x] 7.3 Q3:reduce-motion 是否影响 4 张风格卡片的"点选 style"?→ **不影响**(Theme 仍会强切 NONE,但卡片可正常点选)
- [x] 7.4 Q4:`tokensFor` 命名是否保留?→ **保留**(top-level `fun tokensFor(style, navEnabled, tabEnabled): AnimationTokens`,`AnimationStyle.toTokens()` 等价委托)
