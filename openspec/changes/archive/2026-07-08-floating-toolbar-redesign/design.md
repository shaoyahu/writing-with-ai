# floating-toolbar-redesign · design

## Context

`SelectionFloatingToolbar` 是详情屏(M3+ `note-detail-polish` change 引入)选词后浮出的快捷操作栏,目前实现是 "两个 FilledTonalButton + Row + 硬编码色块"。

`design-system-v2` 已落地的 design system 强调:
- 卡片用 **border + 0 elevation**(不是 shadow)
- corner radius 用 `cornerRadius.md` = 12dp
- 配色走 token,禁止 `Color(0x...)` 字面量

但浮层(Floating toolbar / BottomSheet / Dialog)是一类特殊组件,在 App 内容之上,不直接属于"卡片",所以设计 token 不复用 Card 的 border 处理;浮层风格应该是:
- **`surfaceContainerHigh`**(M3 标准浮层色,M3 1.2+ ColorScheme 自动派生)
- **`tonalElevation`** 6dp(轻微 elevation,不投阴影)
- **cornerRadius.xl** = 24dp(浮层比卡片更圆,跟 Material 3 浮层语一致)
- 不画 BorderStroke(`tonalElevation` 已经提供了 surface tint)

## Goals / Non-Goals

**Goals:**

- `SelectionFloatingToolbar` 配色 100% 走 token,零硬编码
- 视觉风格归到"浮层 surface"类(跟 BottomSheet / DropdownMenu 同族)
- 已添加态跟默认态在同一色系内(色相一致,仅饱和度 / 明度变化)
- AI 操作下拉项视觉同步 M3 standard MenuItem(leading icon + text + optional trailing)

**Non-Goals:**

- 不动 QuickNoteDetailScreen 调用点(签名兼容)
- 不动下拉项触发逻辑(`aiMenuExpanded` state 保留)
- 不动 `isEntityAdded` 计算逻辑(在 caller 算好的,本 change 不动 caller)
- 不动 strings.xml 文案
- 不动 DropdownMenu 项的功能 / 数量
- 不做 toolbar 动画(目前 LinearProgressIndicator / Snackbar 已经够了,M5 polish 续)

## Decisions

### D1 — 背景走 `surfaceContainerHigh`,不画 BorderStroke

- **方案 A**(采用):
  ```kotlin
  Surface(
    shape = RoundedCornerShape(cornerRadius.xl),
    color = colorScheme.surfaceContainerHigh,
    tonalElevation = 6.dp,
    shadowElevation = 8.dp
  ) { ... }
  ```
- **方案 B**(弃):`surface` + 1dp `outline` BorderStroke —— BorderStroke 跟 DropdownMenu 视觉冲突,工具栏会被认为"是卡片"
- **方案 C**(弃):`secondaryContainer` 琥珀底 —— 跟实体高亮蓝绿色系不搭,再次回到"丑"反馈
- **理由**:M3 spec 把浮层分为"卡片 surface"(border-card)和"浮层 surface"(tonalElevation)两类,工具栏属于后者;`surfaceContainerHigh` 是 M3 1.2 派生 token,`LightColorScheme` / `DarkColorScheme` 已自动给出

### D2 — cornerRadius 用 `cornerRadius.xl` = 24dp

- **方案 A**(采用):`RoundedCornerShape(cornerRadius.xl)`
- **方案 B**(弃):`cornerRadius.md` = 12dp —— 跟 Card 同质,浮层感弱
- **理由**:Material 3 floating toolbar reference 用 16-24dp 圆角;24dp 是现有 token 最高档,跟 BottomSheet `lg`=16dp 区分开

### D3 — 已添加态:星 icon `Icons.Filled.Star` + 同色系高亮,不变底色

- **方案 A**(采用):
  - 默认:`IconButton(onClick = onAddEntity, icon = Outlined.StarOutline, tint = onSurface)`
  - 已添加:`IconButton(icon = Filled.Star, tint = primary)` —— 整图换实心 + 主色 tint,底色不变
- **方案 B**(弃):切到 `primaryContainer` 浅绿底 —— 上次改动用过,反馈是色块割裂
- **理由**:用 tint 表达状态而不是底色,色块不切换,视觉更安静

### D4 — 操作区用 IconButton + label,去掉 FilledTonalButton

- **方案 A**(采用):
  ```
  Row {
    IconButton(onAddEntity, outlined/filled star + label "加入实体")
    IconButton(onAiMenu, sparkle + label "AI" + caret)
  }
  ```
  label 是 `Text(label, style = labelSmall)`,在 icon 下方
- **方案 B**(弃):保留 FilledTonalButton 横排 —— FilledTonalButton 视觉重,工具栏会"压住"内容
- **理由**:浮层工具栏应该比内容更"轻",IconButton + 下方 label 是 M3 standard floating action group 推荐形态(参考 Gmail 邮件回复后的 quick reply)

### D5 — AI 角标用 M3 AssistChip-style,不画第二个 Button

- **方案 A**(采用):右上角一个小 `AssistChip(icon = AutoAwesome, label = "AI")`,点击展开 DropdownMenu
- **方案 B**(弃):保留 FilledTonalButton("✨ AI") —— 视觉重
- **理由**:AssistChip 是 M3 designed-for-quick-action 组件,跟"已添加态"用 IconButton 形成"轻 + 轻"组合

### D6 — DropdownMenu 项:leading icon + label + 可选 trailing hint

- **方案 A**(采用):用现有 `AiMenuItem`,扩展 leading icon(扩写=ShortText / 润色=Brush / 整理=AccountTree / 摘要=Summarize / 翻译=Translate),trailing 暂留空(留扩展)
- **方案 B**(弃):纯 Text,无 icon —— 5 项并列视觉单调
- **理由**:leading icon 帮助用户快速识别,这是 Material 3 standard MenuItem pattern

## Risks / Trade-offs

- **[surfaceContainerHigh 在 M3 1.1 不可用]**:`surfaceContainerHigh` 是 M3 1.2+ 才有的 ColorScheme token;**Mitigation**:当前项目用 `androidx.compose.material3:material3:1.4.x`(M3 1.4,包含 surfaceContainerHigh),已 build 验证可用
- **[用户可能觉得新工具栏"看不见"]**:用 `surfaceContainerHigh` 后底色更克制,可能被误认为"底色消失"——**Mitigation**:`tonalElevation = 6.dp` + `shadowElevation = 8.dp` 提供 elevation 感;真机走完后如反馈"看不见"再调高 elevation
- **[AI AssistChip 视觉抢眼]**:右上角的 AssistChip 可能比 Star 抢眼——**Mitigation**:AssistChip 用 `AssistChipDefaults.assistChipColors()` 默认态(不染色),保持低饱和

## Migration Plan

- 单步切换,无灰度;UI 视觉调整无功能 break
- 真机验证:`installDebug` 到 Pixel_7_API_35,详情屏选词 → 工具栏浮出 → 视觉对齐 App 主题
- 回滚:`git revert <commit>`;`SelectionFloatingToolbar` 函数签名没变,无 caller 适配问题

## Open Questions

- 是否要 toolbar 浮现/消失动画(spec 已有 `AnimatedVisibility` 计划,M5 polish 阶段续)—— 本 change **不做**
- 是否要把"加入实体"挪到 DropdownMenu 内(只留 ✨ AI 一个按钮)—— 本 change **保留双按钮**,因为"加入实体"是高频本地操作,放 DropdownMenu 会多一次点击
