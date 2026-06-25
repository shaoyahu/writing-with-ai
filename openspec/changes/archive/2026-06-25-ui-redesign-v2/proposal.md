## Why

当前应用 UI 存在系统性设计缺陷：色彩单调（全蓝 #3B82F6 一色撑场）、视觉层次弱（白卡片+1dp 阴影无区分度）、风格不统一（"我的"页 iOS 直角卡片混搭 M3 黆角圆角）、空状态纯文字无品牌感、编辑器体验粗糙（固定高度文本框）、Onboarding 无第一印象。作为核心定位"writing-with-ai"的笔记 App，需要一套现代、温暖、有辨识度的视觉语言。

## What Changes

- **设计系统 token 重建**：色彩体系从纯蓝升级为「墨绿+琥珀」双色系（与 app 名称「小札」、launcher icon 深墨绿背景呼应）；圆角/间距/阴影/动效 token 扩展为五档体系
- **笔记列表页**：搜索栏改为填充背景圆角搜索框；列表项白底+1dp 边框线+12dp 圆角+左侧彩色竖条标识；空状态增加大尺寸图标+品牌文案
- **笔记详情页**：标题区域放大；底部操作栏固定 Share+AI 图标（不再藏 DropdownMenu）；关联笔记区用 Surface 卡片包裹
- **笔记编辑器**：标题用大号无边框 TextField；正文自适应高度；Tag 区用独立 Surface 包裹
- **「我的」页面**：卡片改 12dp 圆角 + 每项加 leading icon + Section 标题标签
- **Onboarding**：顶部品牌区域（大字标题+副标题+背景渐变）；条款用 Surface 卡片包裹；底部按钮品牌色渐变
- **模型管理/设置页**：Provider 卡片简化信息层次；Ping 结果用 inline badge；设置页补全更多开关项展示

## Capabilities

### New Capabilities
- `design-system-v2`: 全新设计 token 体系（色彩、圆角、间距、阴影、动效），取代当前粗糙的 token 定义
- `ui-page-redesign`: 7 个核心页面的视觉重设计（列表/详情/编辑/我的/Onboarding/模型管理/设置）

### Modified Capabilities
- `quick-note`: 列表页/详情页/编辑器 UI 布局变化（纯 UI 层，不改变数据/业务逻辑）
- `ai-streaming-ux`: StreamingPanel/ActionSheet 视觉升级（跟随新 design token）

## Impact

- **代码范围**：`app/ui/theme/` 全部 token 文件重写；7 个 Screen Composable 重写；`core/ui/Shimmer.kt` 更新
- **资源文件**：`strings.xml` 新增品牌文案/Section 标签；可能新增 drawable 空状态插画
- **不改变**：ViewModel / Repository / DAO / 数据库 / 导航结构 / Hilt 依赖注入
- **不改变**：业务逻辑层的任何行为（AI 调用、飞书同步、数据导入导出等）
- **兼容性**：暗色模式需要同步适配新色彩体系
