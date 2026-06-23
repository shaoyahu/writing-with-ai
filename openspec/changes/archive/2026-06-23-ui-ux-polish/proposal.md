## Why
功能稳定后打磨 UI/UX：动画、空状态、骨架屏、暗色模式、无障碍、微交互。
## What Changes
- 列表 LazyColumn animateItemPlacement + 详情共享元素转场
- 空状态插画+各子屏空态
- 骨架屏(列表/详情加载态)
- 暗色模式色值微调
- 无障碍 contentDescription 补全
- 微交互(置顶动画/Tag chip/search bar)
## Capabilities
### New Capabilities
- `ui-ux-polish`: UI 打磨规范
## Impact
- 所有 Screen 文件分散小改 + core/ui/ 新建 Shimmer 组件
