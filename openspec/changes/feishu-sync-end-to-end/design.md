## Context

飞书 6 个底层 change 已落盘 + 单测通过。**端到端 UI 暴露已完成 4/5**:

- ✅ 详情页 push/pull 菜单(`QuickNoteDetailScreen.kt:324-369`)+ 状态机(`QuickNoteDetailViewModel.kt:127-247`)
- ✅ 详情页内联 URL 输入 dialog(`QuickNoteDetailScreen.kt:624-653`)
- ✅ 冲突解决 dialog(`feature/quicknote/detail/ConflictResolutionDialog.kt`)
- ✅ 列表项 feishu ref 状态 chip(`QuickNoteListScreen.kt:196-216`,AssistChip + D6 状态色)
- ❌ 设置页同步日志 section 挂载缺失
- ❌ `FeishuSyncEventDao` 响应式升级缺失
- ❌ i18n 9/12 key 缺失
- ❌ 独立可测 `FeishuShareViewModel` 缺失
- ❌ JVM 单测覆盖缺失

CLAUDE.md 硬规则:
- "apikey 仅本机加密存储":飞书 `appSecret` 已落 `EncryptedSharedPreferences`,本 change 不动
- "v1 备份策略":`allowBackup="false"` 已全局关闭,本 change 0 风险
- "feature 必须自包含":新建 `feature/quicknote/share/FeishuShareViewModel` 不跨 feature 引用
- "字符串一律走 strings.xml":9 个新 key 双语

## 飞书 OAuth 协议参考(必读)

飞书授权协议(authorize / token / refresh)+ 错误码表见 **`docs/usage/api-feishu.md`**。任何字段疑问(尤其是 `redirect_uri` 必填 vs `refresh_token` 不需要 redirect_uri)先查那篇,**不要瞎猜**(历史踩坑:漏 `redirect_uri` → 20063 "请求体缺少必要字段" 且 msg 空)。

官方三篇文档:
- 获取 authorization code: https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code
- 换取 user_access_token: https://open.feishu.cn/document/authentication-management/access-token/get-user-access-token
- 刷新 user_access_token: https://open.feishu.cn/document/authentication-management/access-token/refresh-user-access-token

## Goals / Non-Goals

**Goals:**
- 设置页飞书授权页已连接时挂载 `FeishuSyncLogSection`,显示最近 20 条 sync event
- `FeishuSyncEventDao` 增 `observeLast(limit): Flow<List<...>>` 响应式版本(沿用 `listLast` 不删)
- 补 9 个缺失 string key,共 12 个 chip / share dialog / conflict / log 双语
- 新建 `FeishuShareViewModel` 薄包装,内含 `ShareState` 状态机,供 JVM 单测覆盖
- 1 个新增 JVM 单测文件覆盖 `FeishuShareViewModel` 5 状态机

**Non-Goals:**
- 不动 `FeishuSyncService` / `FeishuAuthStore` / `FeishuApiClient` / converter / DAO(除 `listLast` 加 Flow 版本)
- 不动详情页 push/pull 入口(已在 `feishu-bidir-sync` change 落地)
- 不动列表 chip(已在 `feishu-bidir-sync` change 落地)
- 不动冲突 dialog(已在 `feishu-bidir-sync` change 落地)
- 不动 OAuth 流程(已在 `feishu-oauth-flow` / `feishu-user-oauth` 落地)
- 不做后台自动同步
- 不引入新依赖

## Decisions

### D1: 设置页日志挂载点 — 复用 `FeishuSyncLogSection`

**选复用**:section 已存在(`feature/settings/feishu/FeishuSyncLogSection.kt`),本 change 只在 `FeishuAuthScreen` 已连接状态时拼装;未连接时隐藏。

**否决新建**:section 已通过单测,代码稳定。

### D2: DAO 响应式升级 — 新增 `observeLast`,沿用 `listLast`

**选增量**:Room DAO 加 `observeLast(limit): Flow<List<FeishuSyncEventEntity>>`,Room 内部用 `LIMIT` 包装 + `InvalidationTracker` 自动响应写入。**不删 `listLast`**,保持向后兼容。

**否决重写**:Flow 包装比 `suspend fun` 不会更复杂,只需加 1 函数。

### D3: ViewModel 不挂 UI,只供单测

**选薄包装**:`FeishuShareViewModel` 是 `@HiltViewModel`,暴露 `ShareState` 状态机,`push(noteId)` / `pull(docUrl)` / `resolveConflictKeepLocal` / `resolveConflictKeepRemote` / `clearState`。**不**绑 UI —— 详情页 `QuickNoteDetailViewModel` 已用 inline state,迁移有重复开发风险;本 VM 是为单测存在。

**否决迁移 UI**:详情页 push/pull 已稳定(R6/R7 review 修过 conflict dialog catch 块),迁移会引入回归。

### D4: 9 个缺失 string key — 双侧完全一致

| key | zh | en | 用途 |
| --- | --- | --- | --- |
| `feishu_chip_synced` | 已同步 | Synced | 列表 chip SYNCED |
| `feishu_chip_conflict` | 冲突 | Conflict | 列表 chip CONFLICT |
| `feishu_chip_remote_deleted` | 远端已删 | Remote deleted | 列表 chip REMOTE_DELETED |
| `feishu_chip_pending` | 同步中 | Syncing | 列表 chip DIRTY/PENDING |
| `feishu_conflict_title` | 检测到冲突 | Conflict detected | 冲突 dialog 标题 |
| `feishu_conflict_keep_local` | 保留本地 | Keep local | 冲突 dialog 选项 |
| `feishu_conflict_keep_remote` | 保留飞书 | Keep Feishu | 冲突 dialog 选项(注:实际措辞"保留飞书"而非"保留远端",与已有 dialog 对齐) |
| `share_to_feishu` | 推送到飞书 | Push to Feishu | 详情菜单 |
| `feishu_sync_log_disclaimer` | 最近 20 条同步记录 | Last 20 sync events | 日志 section disclaimer |

(已有 3 key:`quicknote_detail_pull_from_feishu` / `quicknote_feishu_pull_dialog_title` / `quicknote_feishu_conflict_remote_placeholder`,不在本 change 范围)

### D5: 端到端验证 = USER-OWNED,AI 不跑

**选用户跑**:真机连飞书开放平台自建 app + OAuth 走通 + 详情页 push + 列表 chip + 冲突 dialog + 日志 section,都是真 endpoint 任务,AI 在容器内做不了。本 change 在 §9 列任务,**不**标 AI 完成,仅跟踪用户反馈。

## Risks / Trade-offs

- **[R1] 新增 `FeishuShareViewModel` 不挂 UI → 死代码?** → 单测覆盖 5 用例证明 state machine 正确;未来若详情 VM 需要测试,直接复用本 VM
- **[R2] `FeishuSyncEventDao.observeLast` Flow 触发频率** → `LIMIT 20` 内置,写入触发 InvalidationTracker,实测是写一次触发一次,20 条 list 渲染成本低
- **[R3] 9 个 string key 翻译质量** → en 文案由 AI 起草,用户 review 阶段有歧义可调
