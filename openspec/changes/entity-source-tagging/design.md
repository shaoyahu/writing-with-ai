## Context

当前笔记详情页的实体弹窗（ModalBottomSheet）标题格式为 `surfaceForm · entityType`，其中 `entityType` 直接使用 `EntityType` 枚举的 `toString()` 值（如 `CONCEPT`），导致显示为英文且全大写。用户反馈无法理解其含义。

此外，系统无法区分用户手动添加的实体（通过浮选工具栏"添加实体"）和 AI 自动提取的实体（通过"拆解"功能）。用户明确要求用户自定义实体需要标识为"自定义"。

用户原话："这种用户自己添加的实体，需要明确标识为用户自定义，需要在实体词右边添加一个 tag，显示为自定义。"

## Goals / Non-Goals

**Goals:**
- 为 `note_entities` 表添加 `source` 字段，区分 `USER_ADDED` 和 `AI_EXTRACTED`
- 实体弹窗标题根据来源显示不同标签：用户自定义显示"自定义"，AI 提取显示本地化类型名
- 为 `EntityType` 枚举提供本地化显示名称（中文/英文）
- 现有数据迁移：默认标记为 `AI_EXTRACTED`

**Non-Goals:**
- 不在弹窗中添加实体定义/解释（当前数据模型不支持）
- 不改动物理下划线渲染逻辑
- 不修改实体抽取算法（LLM prompt 不变）

## Decisions

### Decision 1: `source` 字段用 String 而非 Enum
在 Room 中存储为 `TEXT` 类型，取值 `"USER_ADDED"` 或 `"AI_EXTRACTED"`。不用 Kotlin enum 映射到 INTEGER，因为：
- 未来可能扩展更多来源（如导入、同步等），String 更灵活
- Room 的 enum 映射需要 `@TypeConverter`，增加复杂度
- String 在数据库中可读性更好，方便调试

### Decision 2: 本地化走 `strings.xml` 而非 enum 内置
`EntityType` 枚举本身不携带显示名称，由 UI 层通过 `stringResource()` 根据类型和 locale 获取。因为：
- 本地化资源是 Android 的标准做法
- 避免在数据层（`NoteEntityRow`）混入 UI 字符串
- 方便后续添加更多语言

### Decision 3: 用户自定义实体统一显示 `CONCEPT` 类型但标记 `USER_ADDED`
用户手动添加实体时，目前硬编码 `entityType = EntityType.CONCEPT`。保持不变，但通过 `source = USER_ADDED` 区分。弹窗标题显示"自定义"而非"概念"，因为用户自定义的实体不需要关心具体类型。

## Risks / Trade-offs

- **数据库迁移风险**：`note_entities` 表新增列需 Room Migration。风险低，该表数据量小（单笔记实体数通常 < 50）。
- **现有实体全部标记为 AI_EXTRACTED**：可能误标用户之前手动添加的实体。但历史数据无法回溯，接受此限制。
- **本地化字符串膨胀**：12 个类型 × 2 种语言 = 24 个字符串资源。可接受。

## Migration Plan

1. Room Migration：版本号 +1，添加 `source` 列，默认 `'AI_EXTRACTED'`
2. 无需数据回填（现有实体默认 AI 提取）
3. 回滚：降级 Room 版本即可（列会被忽略）
