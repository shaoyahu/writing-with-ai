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

## Risks / Trade-offs

**[Risk] 自动匹配已有实体可能影响性能**
- 如果数据库中有大量实体（>1000），每次打开笔记都要全量匹配
- **Mitigation**：在 ViewModel 中缓存实体列表，避免每次打开都查询数据库；使用协程在后台线程执行匹配

**[Risk] AI 拆解提示词被用户改坏**
- 用户可能输入格式错误的提示词，导致 AI 返回无法解析的内容
- **Mitigation**：提供"恢复默认"按钮；在保存前做基本格式校验（如必须包含 `{{noteContent}}` 占位符）

**[Risk] 实体删除后笔记中的高亮残留**
- 删除实体后，已保存的 `note_entities` 记录被删除，但笔记正文的 AnnotatedString 可能还缓存着高亮信息
- **Mitigation**：删除实体后，触发关联笔记的实体列表刷新

## Migration Plan

1. 新增 `custom_prompts` 表（Room Migration）
2. 初始化默认提示词数据
3. 部署后验证：
   - 打开笔记详情页 → 自动匹配已有实体
   - 点击拆解 → AI 提取新实体
   - 实体管理 → 列表/搜索/删除
   - 开发者模式 → 提示词编辑

## Open Questions

- 实体列表的排序/筛选状态是否需要持久化（如保存用户上次选择）？
- 实体管理页面的空状态是否需要引导用户去添加笔记？
