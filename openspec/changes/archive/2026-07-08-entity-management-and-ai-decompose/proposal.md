## Why

当前笔记详情页已有"拆解"菜单入口，但 AI 实体拆解功能尚未实现。同时，用户需要一个统一的入口来查看和管理所有实体（包括 AI 提取的和用户手动添加的），并能在开发者模式下自定义 AI 拆解的提示词。

## What Changes

- **实现 AI 实体拆解功能**：笔记详情页"拆解"按钮调用 AI 分析笔记内容，提取新实体并匹配已有实体
- **新增实体管理页面**：在"我的" tab 页的数据管理分类下新增"实体管理"入口，支持列表查看、搜索、筛选、排序、多选删除
- **新增实体详情页面**：展示实体完整信息及关联笔记列表（带上下文片段）
- **新增开发者模式**：连续点击版本号随机 5-12 次开启，支持编辑 AI 拆解提示词
- **自动刷新实体关联**：打开笔记详情页时自动匹配已有实体（不调用 AI）
- **修改实体高亮样式**：从"下划线"改为"蓝色字体 + 右上角蓝色十字星星标志"

## Capabilities

### New Capabilities
- `entity-management`: 实体列表、搜索、筛选、排序、删除等管理功能
- `entity-detail`: 实体详情页展示及关联笔记列表
- `ai-decompose-implementation`: AI 实体拆解功能完整实现
- `developer-mode`: 开发者模式（连续点击版本号开启、提示词编辑）

### Modified Capabilities
- `note-decompose-highlight`: 拆解功能从"仅 UI 入口"变为"完整功能实现"，增加自动匹配已有实体逻辑；实体高亮样式改为"蓝色字体 + 右上角蓝色十字星星"
- `note-entity-extraction`: 增加"重新拆解"确认对话框、全屏 loading 状态

## Impact

- 新增数据库表/字段：`custom_prompts` 表（存储开发者自定义提示词）
- 修改 `QuickNoteDetailScreen`：实现拆解功能、自动匹配已有实体、实体高亮样式改为蓝色字体+十字星星
- 新增 `EntityManagementScreen`、`EntityDetailScreen`
- 修改 `MyScreen`：添加实体管理入口、开发者模式开关
- 新增 `DeveloperModeScreen`、`PromptEditorScreen`
