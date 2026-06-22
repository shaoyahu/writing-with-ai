## Context

OAuth(`feishu-oauth-flow`)+ 转换(`markdown-docx-converter`)就绪后,需要把它们串成「点按钮 → 同步 → 处理冲突」的实际工作流。

约束(roadmap §0):
- 飞书 v1 roadmap 不在原始 v1 内,但用户拍板进 v1.x
- **仅手动触发**(用户原话):每次点击按钮 → 完整工作流;**不消耗 AI token**,只调飞书 API
- 关联数据用独立外键表,不改 NoteEntity

## Goals / Non-Goals

**Goals:**
- 详情页「...」菜单:同步到飞书 / 从飞书链接拉取 / 在飞书中打开
- 列表页飞书状态 chip:已同步 / 待同步 / 冲突 / 远程已删
- 设置页同步日志:last 20 events
- 冲突解决:local-vs-remote 比较,UI 让用户选
- 空内容保护 + 远程已删保护
- 增量更新(block-level children API,不全文档重写)

**Non-Goals:**
- 实时同步(WebSocket / 飞书事件订阅)— v2
- 多人协作(飞书本身支持,但 APP 只读 single-user)
- 评论 / @ 提及同步
- Notion / 语雀等其他云文档(走同样的 converter + 私有 client,留 v2)

## Decisions

### D1 · feishu_ref 表结构

```kotlin
@Entity(
    tableName = "feishu_ref",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FeishuRefEntity(
    @PrimaryKey val noteId: String,
    val docId: String,
    val docUrl: String,
    val lastSyncedAt: Long,
    val syncDirection: SyncDirection,
    val localRevision: Long,         // Note.updatedAt
    val remoteRevision: String,      // Docx v1 revision_id
    val status: FeishuRefStatus
)

enum class SyncDirection { PUSH, PULL, BIDIR }
enum class FeishuRefStatus { SYNCED, DIRTY, CONFLICT, REMOTE_DELETED }
```

`localRevision` 复用 Note.updatedAt;不新增列。

### D2 · push 工作流

```
1. 读本地 note (noteId)
2. 读 feishu_ref (noteId):
   - 不存在 → 创建新飞书文档: POST /docx/v1/documents → 拿 docId → 写 feishu_ref
   - 存在 → 比对 localRevision vs remoteRevision:
     - 一致 → 直接走 children API 增量更新
     - 不一致 + remoteRevision > localRevision → 标 CONFLICT,UI 弹选择
3. MarkdownToDocxConverter.convert(note.content)
4. POST /docx/v1/documents/{docId}/blocks/{rootBlockId}/children
   body: {children: [block1, block2, ...]}
5. 飞书返回新 revision_id → 更新 feishu_ref.remoteRevision
6. lastSyncedAt = now, status = SYNCED
```

失败路径:
- 401/403 → 由 FeishuApiClient 自动 refresh 重试;仍失败 → 标 DIRTY + UI 提示
- 404 docId → 标 REMOTE_DELETED + UI 提示「远程已删除」
- 飞书端为空内容(blocks = []) → 拒同步,UI 提示「飞书端为空,不覆盖本地」
- 429 限流 → 退避重试 3 次,仍失败 → 标 DIRTY

### D3 · pull 工作流

```
1. 用户输入 docUrl 或 docId → 解析出 docId
2. GET /docx/v1/documents/{docId}/blocks → 拿 block 列表
3. DocxToMarkdownConverter.convert(blocks)
4. 查 feishu_ref (docId 反查 → noteId?):
   - 不存在 → 新建本地 NoteEntity(用飞书标题) → 写 feishu_ref
   - 存在 → 比对:
     - 本地未修改 → 覆盖
     - 本地已修改 + 远程也新 → 标 CONFLICT,UI 弹选择
     - 本地已修改 + 远程未动 → 跳过,UI 提示「本地有新内容,不同步」
5. lastSyncedAt = now, status = SYNCED
```

### D4 · 冲突解决 UI

弹 dialog 三选一:
- 保留本地:本地内容保留,标 feishu_ref.status = DIRTY,下次 push 推上去
- 保留飞书:飞书内容覆盖本地,标 SYNCED
- 取消:不动

默认焦点「保留飞书」(飞书为权威源)。

### D5 · 增量更新策略

飞书 Docx v1 `POST /docx/v1/documents/{docId}/blocks/{blockId}/children` 支持追加 block;不支持原位替换。**v1 用「全量删除原 blocks + 追加新 blocks」** 模拟原地更新:

```
1. GET /docx/v1/documents/{docId}/blocks → 拿旧 children block_id 列表
2. DELETE /docx/v1/documents/{docId}/blocks/{block_id}/children/batch_delete (每个旧 block)
3. POST .../children → 追加新 blocks
```

v1 走完整 delete + append,后续 v2 评估飞书是否提供原位 update API。

### D6 · 列表页状态 chip

`NoteWithFeishuRef` 数据类(not Room view,运行时组装):

```kotlin
data class NoteWithFeishuRef(val note: Note, val feishuRef: FeishuRefEntity?)
```

UI:
- `feishuRef == null` → 不显示 chip
- `status = SYNCED` → 灰色飞书图标
- `status = DIRTY` → 黄色「待同步」
- `status = CONFLICT` → 红色「冲突」
- `status = REMOTE_DELETED` → 灰色删除线 + 「远程已删」

### D7 · 同步日志

`FeishuSyncEventEntity`(独立表):

```kotlin
@Entity(tableName = "feishu_sync_event", indices = [Index("createdAt")])
data class FeishuSyncEventEntity(
    @PrimaryKey val id: String,
    val noteId: String?,
    val docId: String?,
    val direction: SyncDirection,
    val status: String,        // "OK" / "FAILED" / "CONFLICT" / "REMOTE_DELETED"
    val errorMessage: String?,
    val createdAt: Long
)
```

设置页「同步日志」section:list last 20 events desc by createdAt。

### D8 · 不消耗 AI token

文档化:同步按钮 tooltip / 设置页说明 / 同步日志页面顶部 disclaimer 三处强调「同步不消耗 AI token,只调飞书 API」。

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| Docx v1 增量更新 API 限制(只能 append 不能 replace) | v1 走 delete + append;UI 提示「累积追加」 |
| 飞书端 1 文档 5000 block 上限 | 个人随手记几乎碰不到;触达前弹「文档过大,考虑拆分」提示 |
| 同步过程中网络中断 | push 走 idempotent(block-level children API 失败不污染飞书端);pull 走全块拉,失败回滚本地 |
| 飞书端多人编辑并发 | revision_id 比对防覆盖;冲突 UI 让用户选 |
| 飞书私有 block type(mention / grid / quote_container 等) | converter 不支持 → 走 `Unsupported(raw)` 降级,UI 提示 |
| 同步状态查询频次 | 仅手动触发,无需 WorkManager 监听 |
| 关联 ref 行 + Note 删除顺序 | FK CASCADE 删 note 时 ref 行自动清 |

## Migration Plan

1. Room version `4 → 5` + AutoMigration
2. 增 `feishu_ref` + `feishu_sync_event` 表
3. UI 增量加菜单项 + chip + 日志列表
4. **回滚**:AutoMigration 失败 → 旧 APK 装回;数据保留(新表空)

## Open Questions

- 同步成功后是否要清除 `lastSyncedAt` 之前的旧日志? — 默认保留 100 条,超出循环覆盖
- 飞书块 API 是否提供「整块替换」? — v1 用 delete + append,v2 评估