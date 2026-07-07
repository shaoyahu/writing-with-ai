## Context

当前笔记详情页已有"拆解"菜单入口（`note-decompose-highlight` change 已实现 UI），但点击后仅调用 `LlmEntityExtractor.extractAndPersist()` 做 AI 实体抽取，缺少：
1. 自动匹配已有实体（不调用 AI）
2. 未配置 AI 时的错误处理
3. 重新拆解的确认对话框
4. 全屏 loading 状态

同时，用户需要一个统一的实体管理入口，以及开发者模式来自定义 AI 提示词。

## Goals / Non-Goals

**Goals:**
- 实现完整的 AI 实体拆解功能（AI 提取新实体 + 匹配已有实体）
- 新增实体管理页面（列表、搜索、筛选、排序、删除）
- 新增实体详情页面（展示关联笔记及上下文片段）
- 新增开发者模式（连续点击版本号开启、提示词编辑）
- 自动刷新实体关联（打开笔记时匹配已有实体）
- 修改实体高亮样式为"蓝色字体 + 右上角蓝色十字星星"

**Non-Goals:**
- 修改实体类型系统（沿用现有的 12 种类型）
- 修改 AI 网关底层实现
- 实现实体别名合并（已有功能）

## Decisions

### Decision 1: 自动匹配已有实体的时机

**选择**：打开笔记详情页时自动匹配，不调用 AI。

**理由**：
- 用户在笔记 B 中添加了实体 X，笔记 A 中也应该能识别出 X
- 如果只在 AI 拆解时匹配，笔记 A 中的实体 X 永远不会被高亮（除非重新 AI 拆解）
- 自动匹配是轻量操作（纯本地数据库查询），不会影响性能

**实现**：在 `QuickNoteDetailViewModel.loadCachedEntities()` 中，除了加载已有实体，还执行一次文本匹配：
1. 获取所有已有实体的 `surfaceForm`
2. 在笔记内容中查找匹配（忽略大小写）
3. 对匹配到的实体创建 `note_entities` 关联

### Decision 2: 提示词存储方式

**选择**：存储在数据库中（`custom_prompts` 表），而非 SharedPreferences/DataStore。

**理由**：
- 提示词可能较长（几百字），DataStore 有 1MB 限制
- 需要支持恢复默认，需要区分"自定义"和"默认"
- 数据库支持版本管理和迁移

**表结构**：
```sql
CREATE TABLE custom_prompts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    prompt_type TEXT NOT NULL,  -- "entity_extract"
    content TEXT NOT NULL,
    is_default INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

### Decision 3: 实体列表的数据源

**选择**：从 `note_entities` 表聚合查询，而非新建实体主表。

**理由**：
- 当前设计是 `note_entities` 存储所有实体信息（`entityKey`, `entityType`, `surfaceForm` 等）
- 实体去重通过 `entityKey` 实现
- 列表页查询：`SELECT entityKey, entityType, surfaceForm, COUNT(noteId) as noteCount FROM note_entities GROUP BY entityKey`

### Decision 4: 开发者模式的持久化

**选择**：使用 DataStore 存储 `isDeveloperModeEnabled` 布尔值。

**理由**：
- 简单键值对，DataStore 足够
- 不需要加密，非敏感信息
- 应用卸载后重置（符合预期）

### Decision 5: 实体高亮样式

**选择**：蓝色字体 + 右上角蓝色十字星星标志（替代原有的下划线样式）。

**理由**：
- 用户反馈下划线样式不够醒目
- 蓝色字体 + 十字星星更具辨识度，能一眼看出是实体
- 十字星星占据固定宽度，不会与相邻文字重叠

**实现**：
- 使用 `AnnotatedString` + `SpanStyle` 设置实体文本颜色为 `colorScheme.primary`
- 在实体文本最后一个字符的右上角绘制一个小的十字星星图标（`✦` 或自定义 drawable）
- 十字星星使用 `InlineTextContent` 或 `Overlay` 实现，宽度约 8-12dp
