## Why

OAuth 通了(`feishu-oauth-flow`)+ 转换层有了(`markdown-docx-converter`)，现在需要把它们串成「点击按钮 → 同步 → 冲突处理」的实际工作流。本 change 完成双向同步业务流、冲突 UI、增量更新策略、关联表 + 列表状态展示。手动触发，不消耗 AI token(只调飞书 API)。

## What Changes

- **新增** 数据表 `feishu_ref`(`noteId PK`, `docId`, `docUrl`, `lastSyncedAt`, `syncDirection` enum `PUSH / PULL / BIDIR`, `localRevision` Long, `remoteRevision` String, `status` enum `SYNCED / DIRTY / CONFLICT / REMOTE_DELETED`);FK CASCADE 删 note
- **新增** `FeishuSyncRepository`:push(noteId) / pull(docUrl) / pullByDocId(docId);每个动作是 idempotent
- **新增** `FeishuSyncService`:核心工作流
  - **push(local note → 飞书)**:读 note → `MarkdownToDocxConverter` → 飞书 `POST /docx/v1/documents/{docId}/blocks/{blockId}/children` 增量追加 → 比对 `remoteRevision` 防并发覆盖 → 失败回滚到 `DIRTY`
  - **pull(飞书 → local note)**:`GET /docx/v1/documents/{docId}/blocks` → `DocxToMarkdownConverter` → 新建/更新 `NoteEntity` → 写 `feishu_ref` 行
  - **conflict resolution**:`localRevision > remoteRevision` 且反向也脏 → 标 `CONFLICT`;UI 让用户选「保留本地 / 保留飞书 / 取消」;默认「保留飞书」(服务端覆盖)
  - **空内容保护**:飞书端文档为空 → 不同步，提示「飞书端为空，不覆盖本地」
  - **远程已删保护**:push 时遇 404 docId → 标记 `REMOTE_DELETED`;UI 提示「远程已删除，是否转为本地?」+ 「重新同步为新文档」
- **新增** 详情页「...」菜单:「同步到飞书」/「从飞书链接拉取」/「在飞书中打开」
- **新增** 列表页:note 有 `feishu_ref` 时显示飞书图标 + 状态 chip(已同步 / 待同步 / 冲突 / 远程已删)
- **新增** 设置页「同步日志」section:`last 20 sync events`(时间 / 方向 / 状态 / 错误信息)
- **新增** **仅手动触发**:无 auto-push / auto-pull;每次点击按钮触发完整工作流;不消耗 AI token(只调飞书 API);文档化在「不消耗 AI token」的说明里(避免误以为同步会增加 AI 账单)
- **新增** 限流:飞书 1000 req/min/tenant_token，本应用单用户场景远低于，记录但不强制节流
- **新增** 飞书端版本号比对:Docx v1 文档返回 `revision_id`;push 前比对防覆盖并发
- **修改** `NoteEntity` 不动;`feishu_ref` 是独立外键表
- **修改** 增量更新策略:push 用 block-level children API 而非全文档重写(节省飞书 API 配额 + 降低冲突概率)

## Capabilities

### New Capabilities

- `feishu-bidir-sync`:push / pull 工作流 + `feishu_ref` 数据模型 + 冲突解决 + 远程已删保护 + 列表页状态展示 + 详情页触发入口 + 设置页同步日志

### Modified Capabilities

无。`quick-note` 数据模型零修改;UI 加菜单项属于 feature 内，不影响 spec-level REQUIREMENTS。`markdown-docx-converter` 不修改(只作为依赖)。

## Impact

- **代码**:`core/data/db/` 增 `FeishuRefEntity` + `FeishuRefDao` + `FeishuSyncEventEntity` + Room version `4 → 5` AutoMigration;`core/feishu/sync/` 新包:`FeishuSyncRepository` / `FeishuSyncService` / `FeishuConflictResolver`;`feature/quicknote/detail/` 增「...」菜单;`feature/quicknote/list/` 增飞书状态 chip;`feature/settings/` 增「同步日志」section
- **依赖**:依赖 `feishu-oauth-flow` 的 `FeishuAuthStore` / `FeishuApiClient`;依赖 `markdown-docx-converter` 的 `MarkdownToDocxConverter` / `DocxToMarkdownConverter`
- **apikey / token 消耗**:本 change **不**消耗 AI provider token;只调飞书 API(限流 ~1000 req/min，个人场景远低于);用户首次看到「同步」按钮时已有 apikey 教育页拦截
- **UI**:Material 3 既有 token;详情页菜单 1 项 + 列表页 chip + 设置页日志列表
- **测试**:`FeishuSyncRepository` 单测(push / pull / 冲突 / 远程删);`FeishuConflictResolver` 单测(本地新 / 远程新 / 双向脏 / 空内容);`FeishuRefDao` 单测(cascade + revision 比对);集成测试 fake 飞书 server + `FakeConverter` 跑全流程
- **不在范围**:实时双向同步(WebSocket / 飞书事件订阅)— v2;协作场景(多人同时编辑)— 飞书本身就支持，APP 只读 single-user;飞书评论 / @ 提及同步 — v2