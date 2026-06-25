# Code Review: ui-redesign-v2 — UI 整体重设计

**Reviewed**: 2026-06-25
**Change**: ui-redesign-v2
**Decision**: REQUEST CHANGES

## Summary

ui-redesign-v2 完成了设计系统 token 重建(Color/Spacing/CornerRadius/Shape)和 7 个核心页面的 Composable 重写，编译+ktlint+单测全部通过。但存在 3 个 HIGH 级别问题(Compose 性能反模式、i18n 硬编码、API 类型安全)和若干 MEDIUM 级别问题(功能回归、可访问性、token 迁移不完整)，需要在合并前修复。

## Findings

### HIGH

#### H1. `collectAsStateWithLifecycle()` 在 LazyColumn items 内 — 每项创建独立 Flow 订阅

**File**: `QuickNoteListScreen.kt:191`

```kotlin
items(items = s.notes, key = { it.note.id }) { item ->
    val feishuRefsMap = viewModel.feishuRefs.collectAsStateWithLifecycle().value
```

`feishuRefsMap` 对所有 item 完全相同，却在每个 item 的 lambda 内创建独立订阅。LazyColumn 回收 item 时旧订阅 dispose、新订阅 create，滚动时产生大量无意义的 Flow re-subscription。

**Fix**: 提升到 LazyColumn 外部：
```kotlin
val feishuRefsMap by viewModel.feishuRefs.collectAsStateWithLifecycle()
// ... LazyColumn 内:
feishuRefsMap[item.note.id]?.status
```

---

#### H2. 硬编码中文字符串绕过 i18n — string 资源已存在但未使用

**File**: `QuickNoteListScreen.kt:194-197`

```kotlin
FeishuRefStatus.SYNCED -> "已同步"
FeishuRefStatus.DIRTY -> "待同步"
FeishuRefStatus.CONFLICT -> "冲突"
FeishuRefStatus.REMOTE_DELETED -> "远程已删"
```

`R.string.quicknote_feishu_status_*` 资源已在 `strings.xml` + `values-en/strings.xml` 中定义，但新代码绕过了它们。非中文 locale 用户仍看到中文。

**Fix**: 将 `feishuRefsMap` 提升到 LazyColumn 外(H1 fix)，然后在 Composable scope 内用 `stringResource()` 解析状态标签，传入 NoteRow。

**File**: `QuickNoteDetailScreen.kt:353,637,678,689,695` — 同类问题：`"重新同步为新文档"`, `"拉取"`, `"飞书同步成功"/"飞书同步失败"`, `"复制链接"/"复制错误"`, `"关闭"` 均为硬编码中文，无 string 资源。

---

#### H3. `SectionCard` 参数类型 Float → Dp 双重转换，丢失类型安全

**File**: `MyScreen.kt:62,81,112,137,143`

```kotlin
SectionCard(cornerRadiusDp = cornerRadius.md.value)  // Dp → Float
private fun SectionCard(cornerRadiusDp: Float, ...) {
    Card(shape = RoundedCornerShape(cornerRadiusDp.dp), ...)  // Float → Dp
}
```

`Dp.value` 返回 Float，再 `.dp` 转回 Dp — 双重转换无意义，且破坏类型安全。如果 density 变化或 Dp 值为非整数，可能引入精度问题。

**Fix**: 参数改为 `Dp`：
```kotlin
private fun SectionCard(cornerRadius: Dp, ...) {
    Card(shape = RoundedCornerShape(cornerRadius), ...)
}
// 调用: SectionCard(cornerRadius = cornerRadius.md)
```

---

### MEDIUM

#### M1. CapsuleSearchBar BasicTextField 未使用 decorationBox — placeholder 与文本字段作为 Box 兄弟放置

**File**: `QuickNoteListScreen.kt:297-313`

`BasicTextField` 的 placeholder 用 `if (query.isEmpty()) Text(...)` 作为 Box 兄弟，而非 `decorationBox` lambda 内的 `innerTextField()` 模式。这可能导致视觉对齐问题(placeholder 和输入文本位置不一致)和触摸目标异常。

**Fix**: 改用 `decorationBox` 模式(同 QuickNoteEditorScreen 的写法)：
```kotlin
BasicTextField(
    ...,
    decorationBox = { innerTextField ->
        Box(contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, ...)
            innerTextField()
        }
    }
)
```

---

#### M2. BasicTextField content 字段: `weight(1f).fillMaxHeight()` 冗余 + decorationBox 内 Box 无 fillMaxHeight

**File**: `QuickNoteEditorScreen.kt:170-177`

```kotlin
modifier = Modifier.weight(1f).fillMaxHeight().fillMaxWidth()
// ...
decorationBox = { innerTextField ->
    Box(contentAlignment = Alignment.TopStart) {  // 无 fillMaxHeight
```

在 Column 中 `weight(1f)` 已分配剩余空间，`fillMaxHeight()` 冗余。更关键的是 `decorationBox` 内的 `Box` 没有 `fillMaxHeight()`，BasicTextField 可能不会真正扩展到 `weight(1f)` 分配的空间。

**Fix**: 移除 `fillMaxHeight()`，给 decorationBox 内 Box 加 `Modifier.fillMaxHeight()`：
```kotlin
modifier = Modifier.weight(1f).fillMaxWidth()
// ...
decorationBox = { innerTextField ->
    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.TopStart) {
```

---

#### M3. `tagAccentColor()` 每次重组都重新计算 Color.hsl()，且暗色模式下对比度不足

**File**: `NoteRow.kt:136-142`

```kotlin
private fun tagAccentColor(tags: List<String>): Color {
    if (tags.isEmpty()) return MaterialTheme.colorScheme.primary
    val hue = tags.first().hashCode().toFloat().mod(360f).let { if (it < 0) it + 360f else it }
    return Color.hsl(hue, 0.6f, 0.5f)
}
```

两个问题：
1. `tags` 是 `List<String>`(不稳定类型)，父级任何重组都触发 `Color.hsl()` 重新计算和分配
2. 固定 lightness=0.5 在暗色模式下某些色相(黄/橙)与深色背景对比度不足

**Fix**: 用 `remember(tags)` 缓存颜色；暗色模式用更高 lightness：
```kotlin
val accentColor = remember(tags) { tagAccentColor(tags) }
// tagAccentColor 内根据 isSystemInDarkTheme() 调整 lightness
```

---

#### M4. NoteRow 不再显示 isPinned 指示器和 updatedAt 时间戳 — 功能回归

**File**: `NoteRow.kt`

旧 NoteRow 显示 pin 图标和最后更新时间。新 NoteRow 签名 `(title, content, tags, syncStatus, onClick)` 丢失了这两个字段。用户无法在列表中区分置顶笔记和看到更新时间。

**Fix**: 添加 `isPinned: Boolean = false` 和 `updatedAt: String? = null` 参数，在 UI 中显示 pin 图标和时间戳。

---

#### M5. NoteRow SuggestionChip `onClick = {}` — 空操作，误导用户

**File**: `NoteRow.kt:97-98`

```kotlin
SuggestionChip(onClick = {}, ...)
```

旧版 `InlineTagChip` 有 `onTagClick(tagName)` 回调。新版移除了 `onTagClick` 参数，chip 看起来可点击但实际无响应，违反可访问性原则(有视觉 affordance 但无功能)。

**Fix**: 要么恢复 `onTagClick` 参数并传入，要么改用非交互式 `Text` + 背景样式模拟 chip 外观。

---

#### M6. "Entity" 硬编码英文字符串，其他 signal chip 均用 stringResource

**File**: `RelatedNotesSection.kt:222`

```kotlin
if (note.signals.contains(LinkType.ENTITY_HIT)) {
    SignalChip(text = "Entity")
}
```

其他 signal 都用 `stringResource(R.string.note_association_signal_*)`，唯独 ENTITY_HIT 用了裸英文。缺少 `note_association_signal_entity` string 资源。

**Fix**: 在 `strings.xml` 添加 `note_association_signal_entity`，改用 `stringResource()`。

---

#### M7. `LocalCustomColors` 用 `compositionLocalOf` 而非 `staticCompositionLocalOf`

**File**: `Theme.kt:38`

```kotlin
private val LocalCustomColors = compositionLocalOf { DefaultLightCustomColors }
```

`LocalSpacing` 和 `LocalCornerRadius` 正确使用了 `staticCompositionLocalOf`(值在 App 生命周期内不变)。`LocalCustomColors` 只在主题切换时变化(全量重组)，同样适用 `staticCompositionLocalOf`。当前用 `compositionLocalOf` 增加了不必要的读追踪开销。

**Fix**: 改为 `staticCompositionLocalOf { DefaultLightCustomColors }`。

---

#### M8. NoteRow 左侧彩色竖条固定 72dp 高度，不随内容自适应

**File**: `NoteRow.kt:63-68`

```kotlin
Spacer(
    modifier = Modifier.width(3.dp).height(72.dp)  // 固定高度
        .clip(RoundedCornerShape(...)).background(accentColor)
)
```

内容短于 72dp 时竖条溢出卡片；内容长于 72dp(多行 tag 换行)时竖条不够长。Row 的 cross-axis 对齐本可让竖条自动匹配兄弟 Column 高度。

**Fix**: 移除 `.height(72.dp)`，让 Row 布局自动决定高度。如需确保竖条至少占满卡片，用 `IntrinsicSize.Min` + `matchParentSize()`。

---

#### M9. CapsuleSearchBar 关闭按钮 IconButton 触摸目标 20dp — 低于 48dp 最低标准

**File**: `QuickNoteListScreen.kt:316-318`

```kotlin
IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp))
```

Material Design 可访问性指南要求最小触摸目标 48dp。`Modifier.size(20.dp)` 覆盖了 IconButton 默认的 48dp 最小尺寸。

**Fix**: 移除 `Modifier.size(20.dp)`，或改为 `Modifier.size(48.dp)` + 内部 Icon 保持 `Modifier.size(16.dp)`。

---

#### M10. NavController 作为 Composable 参数传入 — 导致不必要的重组

**File**: `QuickNoteDetailScreen.kt:113`

```kotlin
navController: NavController
```

NavController 不是 stable 类型，传入 Composable 会导致不必要的重组。两处使用：line 568 和 591。

**Fix**: 替换为 lambda 参数：`onNavigateToNote: (String) -> Unit` 和 `onNavigateToSettings: () -> Unit`。

---

### LOW

#### L1. 大量硬编码 dp 值未迁移到 token 系统

**Files**: MyScreen.kt, RelatedNotesSection.kt, OnboardingScreen.kt, ActionSheet.kt, QuickNoteDetailScreen.kt, Shimmer.kt, NoteRow.kt

ui-redesign-v2 引入了 `LocalSpacing` 和 `LocalCornerRadius` token，但约 40+ 处仍使用原始 `.dp` 值(如 `12.dp` → 应为 `cornerRadius.md`，`16.dp` → 应为 `spacing.md`)。token 系统的价值被稀释。

**Fix**: 后续 change 做全量 token 迁移，当前不阻塞。

---

#### L2. OnboardingScreen LazyColumn 内嵌 `weight(1f)` 无效

**File**: `OnboardingScreen.kt:120`

```kotlin
Surface(modifier = Modifier.weight(1f)) {  // 外层 Surface 已有 weight(1f)
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth())  // 内层 weight 无效
```

LazyColumn 在 Surface(非 Column)内，`weight(1f)` 无意义。应改为 `Modifier.fillMaxSize()`。

---

#### L3. Shape.kt 硬编码 dp 值与 CornerRadius 默认值重复

**File**: `Shape.kt:11-17`

`Shapes` 的 4/8/12/16/24dp 与 `CornerRadius` 的 xs/sm/md/lg/xl 完全相同，但代码未引用共享常量。注释说"使用 LocalCornerRadius.md(12dp)"但实际未引用。修改一方时另一方会漂移。

**Fix**: 提取共享常量，或修正注释。

---

#### L4. QuickNoteListScreen LazyColumn 缺少 item 间距和水平边距

**File**: `QuickNoteListScreen.kt:187`

```kotlin
contentPadding = PaddingValues(bottom = 0.dp)
```

NoteRow 自身无 padding，LazyColumn 也无 `verticalArrangement = Arrangement.spacedBy(...)` 和水平 contentPadding。卡片可能紧贴屏幕边缘和彼此。

**Fix**: 添加 `verticalArrangement = Arrangement.spacedBy(spacing.sm)` 和 `contentPadding = PaddingValues(horizontal = spacing.md)`。

---

## Validation Results

| Check | Result |
|---|---|
| ktlintCheck | ✅ Pass |
| testDebugUnitTest | ✅ Pass |
| assembleDebug | ✅ Pass |

## Files Reviewed

| File | Change Type |
|---|---|
| `app/ui/theme/Color.kt` | Modified |
| `app/ui/theme/Theme.kt` | Modified |
| `app/ui/theme/Spacing.kt` | Modified |
| `app/ui/theme/CornerRadius.kt` | Modified |
| `app/ui/theme/Shape.kt` | Modified |
| `feature/quicknote/list/NoteRow.kt` | Rewritten |
| `feature/quicknote/list/QuickNoteListScreen.kt` | Modified |
| `feature/quicknote/detail/QuickNoteDetailScreen.kt` | Modified |
| `feature/quicknote/detail/RelatedNotesSection.kt` | Modified |
| `feature/quicknote/edit/QuickNoteEditorScreen.kt` | Rewritten |
| `feature/my/MyScreen.kt` | Rewritten |
| `feature/onboarding/OnboardingScreen.kt` | Modified |
| `feature/settings/model/ModelManagementScreen.kt` | Modified |
| `feature/aiwriting/action/ActionSheet.kt` | Modified |
| `app/AppShell.kt` | Modified |
| `core/ui/Shimmer.kt` | Modified |

## Next Steps

1. **必须修复(HIGH)**: H1 + H2 + H3 — 3 个 HIGH 问题需在合并前修复
2. **建议修复(MEDIUM)**: M1~M10 — 特别是 M4(功能回归)和 M1(Compose 反模式)影响用户体验
3. **可选修复(LOW)**: L1~L4 — token 迁移和布局微调可后续 change 处理
