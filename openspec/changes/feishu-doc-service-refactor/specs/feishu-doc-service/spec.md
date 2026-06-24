## ADDED Requirements

### Requirement: FeishuDocService exposes four high-level document operations

`core/feishu/sync/FeishuDocService.kt` MUST expose 4 suspend methods(参考飞书 CLI `lark-doc` skill 的 sub-command 设计):

```kotlin
suspend fun createDoc(note: Note): FeishuRef
suspend fun readDoc(url: String): FeishuDocContent
suspend fun updateDoc(note: Note, ref: FeishuRef): FeishuRef
suspend fun appendBlock(note: Note, ref: FeishuRef, parentBlockId: String?, content: String): FeishuRef
```

每个方法 MUST 复用现有 `FeishuApiClient` + `MarkdownToDocxConverter`,不重写 IO 层。

#### Scenario: createDoc 新建飞书文档
- **WHEN** `createDoc(note)` 收到一个 `Note` 实例
- **THEN** 调 `FeishuApiClient.createDocument(title = note.title)` 拿 `docId` → `appendChildren` 写入 Markdown 转换后的 blocks → `refDao.upsert(FeishuRef(noteId, docId, docUrl, status=SYNCED))` → 返回 ref

#### Scenario: readDoc 解析 url 拿 docId
- **WHEN** `readDoc(url)` 收到飞书文档链接
- **THEN** 从 url 提取 docId → `getDocumentBlocks(docId)` → 反序列化为 `FeishuDocContent(title, blocks)` → 返回

#### Scenario: updateDoc 替换整篇
- **WHEN** `updateDoc(note, ref)` 收到本地 note + 已有 ref
- **THEN** `batchDelete(ref.allBlockIds)` 清空旧 blocks → `appendChildren(convertedBlocks)` 写入新 blocks → 刷新 `ref.revision` → 返回 ref

#### Scenario: appendBlock 追加到指定 parent
- **WHEN** `appendBlock(note, ref, parentBlockId = null, content = "新段落")` 调用
- **THEN** `appendChildren(parentBlockId, singleBlock)` 追加单段(若 parentBlockId 为 null 则 append 到文档末尾)

#### Scenario: appendBlock 远端 404 回退
- **WHEN** `appendBlock` 调 `appendChildren` 收到 404(parentBlockId 已被远端删)
- **THEN** 走 fallback `updateDoc(note, ref)` 整篇重写;写 `FeishuSyncEventDao(status = FALLBACK_TO_UPDATE, noteId = note.id, reason = "parent_block_missing")`

### Requirement: FeishuSyncService delegates to FeishuDocService via facade

`core/feishu/sync/FeishuSyncService.kt` 公开 API(`push` / `pull`)保持不变,内部 MUST 委托 `FeishuDocService`。

#### Scenario: push 无 ref 走 createDoc
- **WHEN** `push(noteId)` 查 `refDao.findByNoteId(noteId)` 返回 null
- **THEN** 走 `docService.createDoc(note)`;返回 ref

#### Scenario: push 有 ref 走 updateDoc
- **WHEN** `push(noteId)` 查 `refDao.findByNoteId(noteId)` 返回已有 ref
- **THEN** 走 `docService.updateDoc(note, ref)`;返回更新后的 ref(冲突检测仍走 `FeishuConflictResolver`)

#### Scenario: pull 走 readDoc
- **WHEN** `pull(docUrl)` 调用
- **THEN** 走 `docService.readDoc(docUrl)` 拿 `FeishuDocContent` → 转 Markdown → 写本地 `NoteEntity` → `refDao.upsert`

#### Scenario: 公开 API 行为不变
- **WHEN** `FeishuSyncRepository.push` / `.pull` 现有 caller 调用
- **THEN** 行为与重构前一致(同输入同输出,无 breaking)

### Requirement: FeishuCommandPrompt provides 4-operation JSON schema

`core/ai/prompt/FeishuCommandPrompt.kt` MUST 提供 4 个飞书操作的 JSON schema + 1 个 dispatcher system prompt,供未来 AI 编排使用(本 change 不强制消费,留接口)。

#### Scenario: 4 操作 schema
- **WHEN** 读 `FeishuCommandPrompt.systemPrompt`
- **THEN** 包含 4 个操作的 JSON 形态:`create_doc` / `read_doc` / `update_doc` / `append_block`,每个有必填字段说明

#### Scenario: dispatcher 模板
- **WHEN** 把用户输入 + systemPrompt 喂给 AiGateway
- **THEN** 模型输出 JSON 形如 `{"op": "create_doc", "args": {"noteId": "..."}}`,可被后续 dispatcher 解析执行

#### Scenario: i18n 字符串
- **WHEN** `R.string.feishu_command_prompt_intro` 在 strings.xml / values-en 中存在
- **THEN** 中文"可用飞书操作:..." + 英文"Available Feishu operations:..."(具体文案走 M5 任务细节)