## 1. 动画基础设施

- [x] 1.1 新增 `app/src/main/java/com/yy/writingwithai/app/ui/theme/AnimationStyle.kt`(枚举 MINIMAL/FLUID/IMMERSIVE/NONE + `tokens()` 映射)
- [x] 1.2 新增 `app/src/main/java/com/yy/writingwithai/app/ui/theme/AnimationTokens.kt`(数据类 4 套工厂 + `LocalAnimationTokens = compositionLocalOf`)
- [x] 1.3 新增单元测试 `AnimationTokensTest` 覆盖 4 风格 spring/tween 参数非空,ref `AnimationStyle.tokens()`

## 2. UserPrefsStore 扩展

- [x] 2.1 `core/prefs/UserPrefsStore.kt` 接口新增 `animationStyleFlow: Flow<AnimationStyle>` + `suspend fun setAnimationStyle(style: AnimationStyle)`
- [x] 2.2 `UserPrefsStoreImpl` 实现 DataStore `stringPreferencesKey("animation_style_v1")`,`enumValueOf` 解析,未知值回退 MINIMAL + LOG warn
- [x] 2.3 `core/prefs/FakeUserPrefsStore.kt` 同步 +2 API(测试桩)
- [x] 2.4 新增 `FakeUserPrefsStoreTest` 覆盖 4 个 enum 值 round-trip + 未知 String 回退 MINIMAL

## 3. Theme 集成

- [x] 3.1 `app/ui/theme/Theme.kt` 修改 `WritingAppTheme`:`animationStyle: AnimationStyle` 参数(默认 MINIMAL),collect `UserPrefsStore.animationStyleFlow`,`AccessibilityManager.isReduceMotionEnabled` 开启时强制 NONE
- [x] 3.2 Theme 内 `CompositionLocalProvider(LocalAnimationTokens provides tokens)` 注入
- [x] 3.3 Theme 用 Hilt EntryPointAccessor 拿 `UserPrefsStore`(参考 R6 SettingsScreen.kt 模式,避免 Theme 反向依赖 core/prefs 构造参数)

## 4. AnimatedSwitch 封装

- [x] 4.1 新增 `core/ui/AnimatedSwitch.kt` 包装 Material3 `Switch`,内部读 `LocalAnimationTokens.current.switchSpec`

## 5. NavHost / AppShell 接 token

- [x] 5.1 `app/AppNav.kt` NavHost 加 `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition` lambda,从 `LocalAnimationTokens.current` 取值
- [x] 5.2 `app/AppShell.kt` 内部 NavHost 同上

## 6. 现有 Switch / 展开替换

- [x] 6.1 `feature/settings/SettingsScreen.kt` 裸 `Switch` → `AnimatedSwitch`
- [x] 6.2 `feature/settings/NoteAssociationSettingsScreen.kt` 裸 `Switch` → `AnimatedSwitch`
- [x] 6.3 `feature/settings/model/CustomProviderEditScreen.kt` 2 处 `AnimatedVisibility` 接 `LocalAnimationTokens.current.expandSpec` / `collapseSpec`
- [x] 6.4 `feature/settings/prompt/PromptTemplateScreen.kt` 内容区用 `AnimatedContent` + `tabContentSpec`

## 7. SimpleMarkdown 扩展

- [x] 7.1 `feature/onboarding/SimpleMarkdown.kt` 新增 `data class ConsentSection` + `parseGroupedMarkdown(text: String): List<ConsentSection>`,按 H2 分组,内部复调用 `parseSimpleMarkdown`
- [x] 7.2 新增 `ConsentSection.icon` 关键词→ImageVector 映射(`when` 表达式:数据/AI/第三方/撤回/联系)
- [x] 7.3 新增 `SimpleMarkdownTest` 覆盖 5 H2 split + `parseSimpleMarkdown` 签名不变

## 8. 同意页新组件

- [x] 8.1 新增 `feature/onboarding/ConsentSectionCard.kt`(标题 + 图标 + 摘要 + `AnimatedVisibility` 展开内容)
- [x] 8.2 新增 `feature/onboarding/ConsentProgressBar.kt`(`animateFloatAsState` 平滑进度 0→1)
- [x] 8.3 新增 `feature/onboarding/ConsentBottomBar.kt`(同意按钮 alpha 0.38→1.0 + containerColor `tween(300)`;拒绝按钮始终 enabled)

## 9. OnboardingScreen 重写 UI

- [x] 9.1 `feature/onboarding/OnboardingScreen.kt` 重写:品牌头部 + `ConsentProgressBar` + LazyColumn(`ConsentSectionCard` 列表) + `ConsentBottomBar` (NOTE: 由 onboarding-consent-card-redesign change 在 2026-06-27 完成实际接入,新版组件现已挂载到 OnboardingScreen 卡片路径 + 失败 fallback 路径)
- [x] 9.2 保留 `OnboardingConsentViewModel` 接口;滚动解锁逻辑 `listState.layoutInfo` 不动;首卡片默认 expanded,其余 collapsed
- [x] 9.3 政策加载失败回退 `R.string.onboarding_policy_load_failed`

## 10. 动画风格设置页

- [x] 10.1 新增 `feature/settings/animation/AnimationStylePreviewViewModel.kt`(读 `UserPrefsStore.animationStyleFlow` + `setAnimationStyle` + `AccessibilityManager.isReduceMotionEnabled` state)
- [x] 10.2 新增 `feature/settings/animation/AnimationStylePreviewScreen.kt`(TopAppBar + reduce-motion Banner + 4 张单选卡片,每张含 nav/Switch/Tab 迷你预览)

## 11. 路由 / Tab 集成

- [x] 11.1 `feature/my/MeTabTarget.kt` 枚举新增 `SettingsAnimationStyle`
- [x] 11.2 `app/AppNav.kt` 新增 `@Serializable data object SettingsAnimationStyle` 路由 + `composable<SettingsAnimationStyle>` 渲染 `AnimationStylePreviewScreen`
- [x] 11.3 `app/AppShell.kt` MyScreen when 分支接 `SettingsAnimationStyle` → navigate
- [x] 11.4 `feature/my/MyScreen.kt` "显示"区域(或 "AI 配置"下方)新增 "动画风格" ListItem

## 12. i18n 双语

- [x] 12.1 `values/strings.xml` 新增 24 个 scope key:`anim_style_*` (10) + `consent_section_*` (10) + `consent_*` (3) + `onboarding_policy_load_failed` (1)
- [x] 12.2 `values-en/strings.xml` 同步 24 key 英文翻译
- [x] 12.3 验证 key 集合双侧完全一致:`diff <(grep -oE 'name="..."' zh | sort -u) <(grep -oE 'name="..."' en | sort -u)`

## 13. 验证

- [x] 13.1 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:assembleDebug`
- [x] 13.2 `./gradlew :app:ktlintCheck`(全绿)
- [x] 13.3 `./gradlew :app:testDebugUnitTest`(全绿,含新增 4 个测试)
- [ ] 13.4 真机场景:(a) 切 4 风格 NavHost 节奏对得上;(b) 系统 reduce-motion ON → NONE;(c) 卸载重装 → onboarding 卡片式;(d) 同意页滚到底部 → 按钮从 disabled → enabled 有 alpha 过渡
