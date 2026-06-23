## Context

现有 2x2(QuickNoteWidget)和 1x4(QuickNote1x4Widget)两个 widget。Glance 1.1 API。

## Goals / Non-Goals

**Goals:**
- 1x1 纯按钮 widget 点击直接跳编辑页
- 2x2 点击 body 区域切换下一条笔记
- Widget 配置 Activity 选标签过滤

**Non-Goals:**
- Android 12+ 壁纸动态取色（v2 考虑）
- widget 上直接编辑内容

## Decisions

### D1: 1x1 widget 用 Glance Button

`QuickNote1x1Widget` 提供单个 `Button`，`actionClick` 启动 `QuicknoteEdit(prefillFocus=true)`。

### D2: 2x2 切换用 ActionCallback

2x2 widget body 点击触发 `ActionCallback`，`WidgetStateStore` 维护 `currentNoteIndex`，循环递增。

### D3: 配置 Activity 用 ActivityResultContracts

配置页选择标签后回写 `WidgetStateStore`，触发 widget 更新。
