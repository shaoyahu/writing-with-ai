## 1. 设计 tokens(theme/)

- [ ] 1.1 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Color.kt` 重写:种子色 `#3B82F6` 蓝,`lightColorScheme` + `darkColorScheme` 全 token 显式定义
- [ ] 1.2 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Type.kt` 重写:中文字号优化(标题 22sp / 副标 16sp / 正文 15sp / 标签 12sp)
- [ ] 1.3 新建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Spacing.kt`:`Spacing` data class + `LocalSpacing` CompositionLocal(xs/sm/md/lg/xl/xxl)
- [ ] 1.4 新建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Motion.kt`:`tween(200, FastOutSlowInEasing)` + spring 标准 token
- [ ] 1.5 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Theme.kt` 加 `CompositionLocalProvider(LocalSpacing provides Spacing())`

## 2. 随手记列表卡片化

- [ ] 2.1 `feature/quicknote/list/QuickNoteListScreen.kt`:item 改 `Card(shape = RoundedCornerShape(16.dp))`,改用 `LocalSpacing.current.md` 等 token
- [ ] 2.2 `feature/quicknote/list/QuickNoteListViewModel.kt`(若有):不动
- [ ] 2.3 长按拖拽 + 滑动删除 —— 用 `SwipeToDismiss`(M5 polish 续,本期跳过)

## 3. 随手记详情 read mode

- [ ] 3.1 `feature/quicknote/detail/QuickNoteDetailScreen.kt`:顶部 toolbar 极简化(`CenterAlignedTopAppBar` + 仅 back + edit + overflow),正文大字号 `headlineSmall` 标题 + `bodyLarge` 内容
- [ ] 3.2 底部栏 ✨ 按钮改 `FilledIconButton` 风格 surface(更突出 AI 操作)
- [ ] 3.3 `ActionSheet` 改 `ModalBottomSheet`(本 change 顺手做,user bug 也已存在)

## 4. AI 流式面板重设计

- [ ] 4.1 `feature/aiwriting/streaming/StreamingPanel.kt`:`Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp)` + glass blur(API 31+ `BlurEffect(20f, 20f)`,< 31 fallback 70% surface)
- [ ] 4.2 流式累积文本用 `Text(style = bodyLarge)` + typing animation(光标闪烁)
- [ ] 4.3 按钮组(接受 / 拒绝 / 重新生成)用 `FilledTonalButton` 风格

## 5. 设置页 Material 3 List 风格

- [ ] 5.1 `feature/settings/SettingsScreen.kt`:ListItem 统一用 M3 标准(leadingIcon + headline + supporting),Group 分类(AI / 数据 / 关于)
- [ ] 5.2 新增"AI 模型管理"入口(在"AI 提示词模板"前)—— 由 `provider-real-integration` 拆过来的 UI 工作

## 6. ModelManagement UI

- [ ] 6.1 新建 `feature/settings/model/ModelManagementEntry.kt` —— `ModelManagementRoute` + `ModelProviderDetailRoute`
- [ ] 6.2 新建 `feature/settings/model/ModelManagementScreen.kt` —— 3 个 provider Card + 当前选中 + "测试连通"按钮 + ping 状态显示
- [ ] 6.3 新建 `feature/settings/model/ModelManagementViewModel.kt` —— `@HiltViewModel`,接 `SecureApiKeyStore` + `ProviderPrefsStore` + `AiGateway`,提供 `setProvider(id, apiKey)` + `ping()`
- [ ] 6.4 新建 `feature/settings/model/ModelProviderDetailScreen.kt` —— OutlinedTextField + "显示" toggle + "保存"按钮
- [ ] 6.5 `app/AppNav.kt` 加 2 个 `@Serializable` route + 2 个 `composable<>` block

## 7. AppNav 路由补齐

- [ ] 7.1 `SettingsScreen` 加 `onModelManagementClick` lambda → 跳 `SettingsModelManagement`
- [ ] 7.2 `SettingsEntry.SettingsRoute` 加 `onModelManagementClick` 形参
- [ ] 7.3 `AppNav.kt` `composable<Settings>` 块传 lambda

## 8. i18n(模型管理)

- [ ] 8.1 `values/strings.xml` 加:`model_management_title` / `model_management_subtitle` / `model_management_current` / `model_management_test_ping` / `model_management_ping_success` / `model_management_ping_failed` / `model_provider_detail_base_url` / `model_provider_detail_save` / `model_provider_detail_show_key` / `model_provider_detail_saved_toast` 共 10 个 key
- [ ] 8.2 `values-en/strings.xml` 同步 TODO 占位

## 9. widget 视觉重设计

- [ ] 9.1 `core/widget/QuickNoteWidget.kt`:加 `MaterialTheme.colorScheme.surface` 显式背景(避免硬编码 `Color.White`),文字走 `ColorProvider.color(MaterialTheme.colorScheme.onSurface)`
- [ ] 9.2 `core/widget/QuickNote1x4Widget.kt`:同样走 Material You token;按钮改圆角 `RoundedCornerShape(12.dp)` 风格

## 10. Onboarding 屏

- [ ] 10.1 `feature/onboarding/OnboardingScreen.kt`:背景 `Brush.verticalGradient(listOf(primary, secondary))` + Glass card(`Surface(... tonalElevation = 6.dp, color = surface.copy(alpha = 0.85f))`)

## 11. 编译验证

- [ ] 11.1 `./gradlew :app:assembleDebug` 通过
- [ ] 11.2 `./gradlew :app:testDebugUnitTest` 通过
- [ ] 11.3 `./gradlew :app:lintDebug` 0 errors
- [ ] 11.4 `./gradlew :app:ktlintCheck` 无新增 violation

## 12. 真机验证

- [ ] 12.1 installDebug 到 PGU110
- [ ] 12.2 随手记列表卡片视觉 OK(中文 typography + spacing)
- [ ] 12.3 详情页底部栏 ✨ → 点出 ModalBottomSheet(替代 Popup 箭头)
- [ ] 12.4 AI 流式面板(选 deepseek apikey 已填情况下)走真实流式
- [ ] 12.5 设置 → 模型管理 → 选 deepseek → 填 apikey → 测试连通 → "可用"
- [ ] 12.6 Widget(2x2 / 4x2 / 4x1)视觉刷新 OK

## 13. spec 同步 + 归档

- [ ] 13.1 跑 `/opsx:sync ui-redesign-m5-glass` 合入主 spec
- [ ] 13.2 跑 `/opsx:archive ui-redesign-m5-glass` 收口
- [ ] 13.3 `docs/progress.md` 加 1 条"M5 UI 全量重设计" 进度条目