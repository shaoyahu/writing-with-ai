## Why

当前只有 2x2 和 1x4 widget，缺少 1x1 纯按钮快速记笔记入口；2x2 widget 只显示单条笔记无法切换；无 widget 配置 Activity 让用户选择显示内容。

## What Changes

- 新增 1x1 快速记笔记纯按钮 widget
- 2x2 widget 加点击切换下一条笔记
- widget 配置 Activity（选择标签分类过滤）

## Capabilities

### Modified Capabilities
- `home-screen-widget`: 新增 1x1 widget、2x2 切换、配置 Activity

## Impact

- core/widget/ 新增 1x1 widget 文件 + 配置 Activity + AndroidManifest 注册
