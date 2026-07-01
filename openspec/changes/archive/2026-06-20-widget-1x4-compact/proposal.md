## Why

M4-1 widget 已落地 2x2 / 4x2 两种尺寸，但 **桌面 widget 没有"一键新建"的标准形态**——现有 2x2 / 4x2 都把"+"放角落，用户需要先点 widget 区域唤醒 attention 再点"+"，心智路径多一步。**1x4 compact widget**(1 列宽 × 4 行高)提供"加号 + 最近笔记"单行并列布局，桌面添加更窄、视觉更聚焦，加号按下直达编辑器(prefillFocus=true)减少 1 步点击。

## What Changes

- 新增 1x4 widget 尺寸布局:左侧 1x1 加号按钮 + 右侧 1x3 最近 1 条笔记文本(标题 + relative time，单行省略)
- 新增 `widget_1x4_title` / `widget_1x4_layout_label` i18n key(中/英 双语)
- 扩展 `widget_info.xml` 加 `targetCellWidth=1 targetCellHeight=4` 配置(系统自带 widget picker 即可选 1x4)
- 复用现有 `WidgetIntentHelpers.createTaskStackPendingIntent("quicknote/edit?prefillFocus=true")` 加号路径;复用 `OpenNoteAction` 笔记项路径
- 复用 `QuickNoteWidgetRepository.observeRecent(1)` 数据源(不新增 DAO SQL)
- 新增 1x4 Glance Composable(在 `QuickNoteWidget.kt` 现有 2x2 / 4x2 分支下加 `currentSize.width <= 80.dp` 分支)

**非 BREAKING**:新增尺寸，不影响现有 2x2 / 4x2 widget 实例。

## Capabilities

### New Capabilities

- `home-screen-widget-1x4`:1x4 compact widget 布局 + 加号直达编辑器 + 最近 1 条笔记展示

### Modified Capabilities

- `home-screen-widget`:追加 1x4 尺寸 Requirement + Scenario(原有 2x2 / 4x2 / Glance / Worker / 测试 等 Requirement 不动)

## Impact

- **新代码**:`QuickNoteWidget.kt` 加 `currentSize.width <= 80.dp` 分支(预计 ~30 行 Glance Composable)
- **新资源**:`res/values/strings.xml` + `res/values-en/strings.xml` 加 2 个 key;`res/xml/widget_info.xml` 加 `targetCellWidth=1 targetCellHeight=4`
- **复用**:WidgetIntentHelpers / OpenNoteAction / QuickNoteWidgetRepository / QuickNoteWidgetUpdater / WorkManager 周期任务 — 全部 0 改动
- **测试**:扩 `WidgetIntentHelpersTest` 加 1x4 requestCode 用例(若 helper 接受尺寸参数)
- **真机**:小米 / OPPO / 原生 AOSP launcher widget picker 需显示 1x4 选项