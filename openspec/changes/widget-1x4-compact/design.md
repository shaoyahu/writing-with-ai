## Context

M4-1 home-screen-widget 已实现 2x2 / 4x2 两种 widget 尺寸(归档在 `openspec/changes/archive/2026-06-19-home-screen-widget/`)。真机(PGU110)验证 4x2 widget 显示最近笔记 + 点击进入详情正常,但 **"+"按钮位于右上角**,桌面 2x2 / 4x2 widget 用户点新建需先点 widget 中央唤醒 attention 再点"+",路径不直接。

**新增 1x4 compact widget**:1 列宽 × 4 行高(手机宽度,纵向 4 格),左侧 1x1 加号 + 右侧 1x3 最近 1 条笔记文本。**加号按下直达编辑器**(`prefillFocus=true`),减 1 步。

## Goals / Non-Goals

**Goals:**

- 1x4 widget 在 launcher picker 可见且可添加
- 加号单击直达编辑器(1 步完成"进入 App + 准备输入"),不走 App 内列表
- 最近 1 条笔记 + 相对时间展示,点击进详情
- 复用所有现有 widget 基础设施(Repository / Intent Helpers / Worker / Updater)
- 不破坏现有 2x2 / 4x2 widget 实例

**Non-Goals:**

- 多条笔记堆叠(1x4 高度仅 4 格,展示 1 条最合适)
- widget 内编辑笔记(只读 + 跳转)
- widget 主题切换 / 颜色自定义
- 新增 DAO SQL / Note schema 改动

## Decisions

### D1 — 尺寸判定:`currentSize.width <= 80.dp` 进 1x4 分支

- **方案 A**(采用):Glance `LocalSize.current` 取 `width`,`<= 80.dp` 进 1x4 渲染分支。
- **方案 B**(弃):`widgetCategory=home_screen` 加 `glanceAppWidgetSize` 多尺寸声明,系统自动选最匹配。
- **理由**:现有 M4-1 实现已用方案 A(`currentSize.width <= 160.dp` 判 2x2 / 4x2),沿用一致;Launcher 给 1x4 实际分配 ~70dp 宽,80dp 阈值稳。

### D2 — 加号路径走 `prefillFocus=true` 直达编辑器

- **方案 A**(采用):`createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", requestCode=1003)`,复用 M4-2 `WidgetIntentHelpers`。
- **方案 B**(弃):走 `quicknote/list` 列表页,让用户再点"+"。—— 多 1 步,与 1x4 widget 设计目的矛盾。
- **理由**:桌面 widget 用户心智 = "我要快速记",1x4 加号按下立刻出现键盘 + 输入框 focus 才是"快"。

### D3 — 1x4 widget 不显示标题区,仅"加号 + 笔记"两栏

- **方案 A**(采用):1x4 高度 4 格,布局 `Row { 加号 | 笔记 }`,不放 "随手记" 标题。垂直空间给笔记标题单行 + 时间。
- **方案 B**(弃):保留 `widget_1x4_title` 顶部标题 + 下方 Row。—— 4 格高度不够,标题占 1 格后笔记只剩 2 格。
- **理由**:1x4 的核心是"紧凑 + 直达",不重复 App 内的"随手记"标题。

### D4 — `widget_info.xml` 加 `targetCellWidth=1 targetCellHeight=4`

- **方案 A**(采用):复用现有 `widget_info.xml` 在 `<appwidget-provider>` 加 `targetCellWidth="1" targetCellHeight="4"`,系统 widget picker 自动识别 1x4 / 2x2 / 4x2 三种推荐尺寸。
- **方案 B**(弃):拆多份 `widget_info.xml`(M4-1 路径不灵活)。
- **理由**:Android 12+ `targetCellWidth/Height` 是 hint,launcher 据此显示尺寸选项;同 receiver 同一份 metadata 即可。

### D5 — 新增 2 个 i18n key:`widget_1x4_layout_label` + `widget_1x4_title`

- `widget_1x4_layout_label`:launcher widget picker 显示的尺寸标签,中文 "1x4 紧凑" / 英文 "1x4 Compact"
- `widget_1x4_title`:AndroidManifest `<receiver android:label>` 用 —— 中文 "随手记" / 英文 "Quick Note"(复用现有 `widget_2x2_title` 也行,但 D3 不显示标题,只是 launcher 必备 label)
- **理由**:CLAUDE.md 约定所有用户可见字符串走 R.string,严禁硬编码。

### D6 — 复用 `OpenNoteAction` + `QuickNoteWidgetRepository.observeRecent(1)`

- 笔记项点击 → `OpenNoteAction`(已有,无需改)
- 数据拉取 → `observeRecent(1)`(已有,无需改;4x2 widget 已用 `observeRecent(3)`,这里取 1 条)
- **理由**:CLAUDE.md "Widget 数据访问不破坏 Note schema" 已立约束,1x4 增量不破。

## Risks / Trade-offs

- **[国产 ROM 1x4 不支持]**:部分国产 launcher(小米 MIUI 早期 / 华为 EMUI)可能不展示 1x4 选项 → **Mitigation**:用户回退到 2x2 widget,功能等价;roadmap §8.3 已标"国产 ROM widget 限制"在 M5 polish 处理。
- **[1x4 高度太窄,笔记标题被截断]**:4 格高度约 240-300dp,标题单行 + 时间单行 ≈ 80dp,剩余空白 → **Mitigation**:`Text(maxLines = 1, overflow = TextOverflow.Ellipsis)`;笔记标题超 12 字截断。
- **[Glance Row 在 1x4 宽度可能溢出]**:1x4 实际宽度 ~70dp,左右两栏 + padding 易超 → **Mitigation**:加号用 36dp 固定宽度,右侧 `Text(modifier = Modifier.defaultWeight())` 占满;Glance `fillMaxWidth` 配合 `weight`。
- **[双击加号启动多个编辑器]**:桌面 launcher 偶尔会重发 PendingIntent → **Mitigation**:`launchMode="singleTop"` 已在 MainActivity,新 Intent 不再栈叠;与 M4-2 一致。

## Migration Plan

- **灰度**:本 change 直接 release,无 migration。用户桌面已加 2x2 / 4x2 widget 不受影响,新 widget 添加走 launcher picker "1x4 紧凑" 标签。
- **回滚**:`QuickNoteWidget.kt` 删 `currentSize.width <= 80.dp` 分支,`widget_info.xml` 删 `targetCellWidth=1 targetCellHeight=4`,2 个 string key 删除。
- **CI 验证**:`./gradlew :app:assembleDebug` + 真机 launch widget picker 看 1x4 出现。

## Open Questions

- 1x4 widget **是否需要在 widget 上显示笔记创建时间**(目前 D3 只规划单行标题 + 相对时间,时间是否单列待实机验证)—— 暂定显示,留 M5 polish 调。
- 是否在 1x4 widget 内显示 **笔记 AI 操作 metadata**(小标签 "已润色" 等)—— **不做**,1x4 高度不够,留 4x2 widget 即可。