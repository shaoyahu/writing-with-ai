## Context

当前 App 视觉系统走 Material 3 默认 ColorScheme + 散落 `Surface(... color = Color.White)` / `Modifier.padding(24.dp)` 等字面量,无统一 design token / spacing / motion / Glass blur。真机显示"UI 丑"(用户反馈)。

`provider-real-integration` 已落地(让 `AiActionViewModel` 读 ProviderPrefsStore 切真实 provider),本 change 在此基础上做 UI 全量重设计 + **新增 ModelManagement UI**(provider 选择屏 + apikey 输入屏),让"模型管理"对用户可见可点。

## Goals / Non-Goals

**Goals:**

- `app/ui/theme/` 重设计 —— Material 3 You ColorScheme(种子色 `#3B82F6` 蓝)+ 统一 `LocalSpacing` CompositionLocal + 自定义 typography(中文优化字号)+ Motion tokens
- **随手记列表**:卡片化(原 `LazyColumn { items(...) }` → `LazyColumn { items { Card { ... } } }`),长按拖拽 + 滑动删除 + 顶部 sticky date header
- **随手记详情**:Full-screen read mode,顶部 toolbar 简洁化,底部栏 ✨ 按钮改用 FAB 风格 surface
- **随手记编辑**:BottomSheet-style autosave 提示
- **AI 流式面板**:Card with rounded corners + glass blur 背景 + typing animation + Markdown preview
- **设置**:Material 3 List 风格 + Group 分类
- **Onboarding**:渐变背景 + Glass card + 三步指示器
- **3 个 widget**(2x2 / 4x2 / 4x1):Material You 圆角 + surface tint + Glance surface elevation
- **ModelManagement UI**:provider 列表 + apikey 输入 + "测试连通"按钮(本 change 顺手做,避免 provider-real-integration 半成品)

**Non-Goals:**

- 后端 provider 切换逻辑(provider-real-integration 已落)
- 自定义 prompt 模板 UI 重设计(M3 polish 阶段续做)
- 暗黑模式单独 token(随系统走 Material 3 You dynamic color)

## Decisions

### D1 — 设计种子色 = `#3B82F6`(蓝)

- **方案 A**(采用):`lightColorScheme(primary = Color(0xFF3B82F6), ...)` + `darkColorScheme(primary = Color(0xFF60A5FA), ...)`
- **方案 B**(弃):Material default 紫(`#6750A4`)—— 产品定位"快速记录"与蓝色更搭
- **理由**:Material You 动态取色受 OEM wallpaper 限制,显式种子色保真

### D2 — Spacing tokens via `LocalSpacing`

- **方案 A**(采用):`val LocalSpacing = staticCompositionLocalOf { Spacing() }`,`Spacing` data class 含 `xs=4dp / sm=8dp / md=12dp / lg=16dp / xl=24dp / xxl=32dp`,Composable 走 `LocalSpacing.current.md` 替代字面量
- **方案 B**(弃):`Modifier.padding(16.dp)` 字面量 —— 散落难统一
- **理由**:CLAUDE.md "自定义设计 token(如统一圆角、间距)放在 Theme.kt 的 CompositionLocal 中"

### D3 — Glass blur via Compose 1.6+ RenderEffect

- **方案 A**(采用):`Modifier.graphicsLayer(renderEffect = BlurEffect(20f, 20f))`(API 31+),API < 31 fallback 70% 透明 surface
- **方案 B**(弃):引 `dev.chrisbanes.haze:compose` 依赖 —— 多一个 lib,Roadmap 没说要
- **理由**:API 31+ 覆盖率 ~85%(2026 年),fallback 70% surface 视觉接近

### D4 — Motion tokens

- `tween(durationMillis = 200, easing = FastOutSlowInEasing)` —— 默认 medium
- `spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)` —— dialog / sheet
- Composable 内用 `AnimatedContent` / `AnimatedVisibility` 包切换

### D5 — ActionSheet 走 ModalBottomSheet(Slide-up)

- **方案 A**(采用):`ModalBottomSheet(onDismissRequest = ...)` 替代当前 `Popup` + `Canvas` 箭头
- **方案 B**(弃):保留当前 Popup + arrow —— 真机验证位置仍偏(箭头方案仍有 UX 缺陷)
- **理由**:BottomSheet 是 Material 3 标准组件,Slide-up 手势 + 圆角 + 阴影全免费

### D6 — StreamingPanel 重设计 = Card + glass + Markdown

- `Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp)` + `Modifier.graphicsLayer(renderEffect = BlurEffect(...))` + Markwon 渲染流式累积文本

### D7 — ModelManagement UI(顺手做)

- `feature/settings/model/ModelManagementScreen.kt` + `ModelProviderDetailScreen.kt` + `ModelManagementEntry.kt`
- 3 个 provider 的 Card(icon + 名称 + baseURL + 默认 model + "选择"按钮)
- "测试连通"按钮调 `AiGateway.ping(providerId)`
- 详情屏 OutlinedTextField + "显示" toggle(5s 自动隐藏)+ "保存"

## Risks / Trade-offs

- **[Glass blur 性能]**:`graphicsLayer(renderEffect = ...)` 每帧 GPU 开销,低端机卡顿 → **Mitigation**:API < 31 fallback 70% surface;只对 StreamingPanel 用 blur(不滥用)
- **[Material You 动态取色被显式种子覆盖]**:用户改主题色 wallpaper 后 App 不会跟随 → **Mitigation**:v1 接受(M5 polish 阶段再考虑)
- **[ModalBottomSheet 与 Navigation 集成]**:Sheet 内含 navigation 跳转 → **Mitigation**:Sheet 关闭后才 navigate,避免 popUpTo 错乱
- **[ModelManagement UI 与 ui-redesign 并入同一 change]**:risk 略大,但 review 时分开视觉 / 数据流两层看,实际不混

## Migration Plan

- **灰度**:apk 直接 release;UI 视觉调整无功能 break,老用户无感
- **回滚**:`git revert <commit>`,数据无 schema 变更
- **CI 验证**:`./gradlew :app:assembleDebug` + `testDebugUnitTest` + 真机 PGU110 全页面 walkthrough

## Open Questions

- **是否拆 2 个 change**(ui-redesign-m5-glass vs model-management-ui)→ 已合并,review 拆数据流 / 视觉两块
- **Glass blur 是否要 settings toggle 关掉** → M5 polish 续做