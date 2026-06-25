## Context

应用「小札」当前 UI 基于纯蓝 #3B82F6 种子色 + Material 3 默认 token 构建，经过 M0~M5 多轮迭代后积累了以下设计债务：

1. **色彩**：Light 用蓝灰冷色调，与 launcher icon 深墨绿、品牌名「小札」的文人气韵不搭；暗色模式 primaryContainer 过深
2. **token 粒度**：Spacing 只有 5 档(xs/sm/md/lg/xl)，CornerRadius 只有 3 档(sm/md/lg)，实际页面到处写裸 `.dp`
3. **视觉层次**：卡片靠 1dp elevation 阴影区分，在白底上几乎不可见；"我的"页用 0dp 圆角直角卡片打破 M3 一致性
4. **空状态**：纯 Text + Button，无插画/图标/品牌感
5. **编辑器**：OutlinedTextField 固定 280dp 高度，不支持自适应；标题和正文视觉权重相同
6. **Onboarding**：纯文字条款页，无品牌调性，用户首次打开无印象

**约束**：不改 ViewModel/Repository/DAO/数据库/Hilt/导航结构；纯 Composable 层 + token 文件重写。

## Goals / Non-Goals

**Goals:**
- 建立与「小札」品牌呼应的「墨绿+琥珀」色彩体系
- 统一圆角/间距/阴影/动效 token，消除页面散落的裸 `.dp`
- 7 个核心页面视觉升级：列表/详情/编辑/我的/Onboarding/模型管理/设置
- 暗色模式同步适配
- 编辑器体验提升（标题突出、正文自适应、Tag 区视觉分离）

**Non-Goals:**
- 不改业务逻辑/数据流/ViewModel 状态机
- 不改导航路由结构
- 不引入新第三方 UI 库（Compose Markdown 除外，以后考虑）
- 不做动画/转场动效（留 v2.x 打磨）
- 不做响应式/大屏适配
- 不改桌面 Widget UI（独立 change）

## Decisions

### D1: 种子色从蓝 #3B82F6 改为墨绿 #1B6B4A

**选择**: primary = #1B6B4A (深墨绿), secondary = #D4940A (琥珀金), tertiary = #2BAD8E (薄荷绿)

**理由**:
- Launcher icon 背景已是深墨绿色，品牌一致性
- 「小札」= 文人墨客 + 手札，墨绿色调更贴合文气
- 琥珀金作 accent 色在墨绿上对比鲜明（AI 按钮、FAB、选中态）
- 避开蓝/紫色泛滥的 AI 应用视觉同质化

**备选**: (A) 纯蓝维持 — 否决，与品牌无关联 (B) 暖棕 #8B6914 — 对比度不足

### D2: 卡片边框线取代阴影

**选择**: Card 默认 elevation=0 + 1dp BorderStroke(outlineVariant)，选中态用 2dp primary 边框

**理由**:
- 1dp 阴影在白底几乎可见度极低，等于没有层次
- 边框线在明暗主题下都清晰可见
- Google Keep / Apple Notes 风格验证：边框比阴影更适合笔记类 App
- 选中态用 primary 色边框比绿色 success 边框更统一

### D3: 圆角统一 12dp (Card), 16dp (Sheet), 24dp (SearchBar)

**选择**:
- CornerRadius 扩展为 xs(4dp)/sm(8dp)/md(12dp)/lg(16dp)/xl(24dp)
- Card 统一 md(12dp)
- ModalBottomSheet 统一 lg(16dp) (Material 3 默认)
- 搜索框统一 xl(24dp) (胶囊形)

**理由**: 当前 3 档(sm4/md8/lg16)不够用，12dp 是主流笔记 App 卡片圆角标配

### D4: 编辑器标题/正文分离方案

**选择**: 标题用 `BasicTextField` + `headlineMedium` textStyle + 无边框 + 下划线分隔；正文用 `BasicTextField` + `bodyLarge` + `Modifier.weight(1f).fillMaxHeight()` + 无边框

**理由**:
- OutlinedTextField 的边框/label 占空间，编辑器应最大化内容区域
- 标题大号字 + 正文常规字自然形成视觉层次
- `weight(1f)` 让正文填满剩余空间，比固定 280dp 好

**备选**: (A) 继续用 OutlinedTextField — 否决，边框占空间且视觉吵 (B) rich text editor — 超范围

### D5: 「我的」页面 Section 标题 + 图标

**选择**: 每个 SectionCard 上方加 `Text(label, style=titleSmall, color=primary)` 作为 section header；每个 ListItem 加 leading icon

**理由**: 当前纯 ListItem 罗列缺少分类引导，加 section header + icon 是设置页标配

### D6: Onboarding 品牌头部用 Surface + primaryContainer

**选择**: OnboardingScreen 顶部加 `Box(modifier=background(primaryContainer))` 区域，内含 headlineLarge 标题 + bodyMedium 副标题

**理由**: 条款页太素，加品牌色头部提升第一印象；用 primaryContainer 纯色即可，无需渐变

### D7: 详情页底部操作栏取代 DropdownMenu 藏操作

**选择**: 固定 BottomBar 放 Share + AutoAwesome 图标；Pin/Delete/Export 等低频操作保留在 TopAppBar 的 MoreVert 菜单

**理由**: 当前所有操作藏 DropdownMenu 发现性差；Share + AI 是高频操作应常驻底部

## Risks / Trade-offs

- **[色彩切换]** 用户习惯了蓝色主题 → 墨绿与蓝色同属冷色调，过渡不突兀；品牌一致性收益更大
- **[边框线暗色模式]** 暗色下 outline 可能不够可见 → 暗色 outline 用 onSurface.copy(alpha=0.2f)
- **[编辑器 BasicTextField]** 无 decoration box，placeholder 需手写 → 用 `decorationBox` 参数
- **[回归范围大]** 7 个页面 + token 全改 → 按 token → 列表 → 详情 → 编辑 → 我的 → Onboarding → 设置 顺序逐步改
- **[Spacing token 扩展]** 新增 xs2/sm2/md2 触发大量文件改动 → 只在重写的 Composable 中使用新 token
