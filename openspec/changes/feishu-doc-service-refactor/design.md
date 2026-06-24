## Context

`core/feishu/sync/FeishuSyncService.kt` 当前按"push 整篇 note" / "pull 整篇"粗粒度操作,不能直接对接"AI 扩写结果追加一个 block 到飞书文档"或"只更新文档某段"等场景。飞书 CLI 仓库 `lark-doc` skill 把文档操作拆成 4 个 sub-command(`create` / `read` / `update` / `append`),提供清晰的"细粒度操作集合"。本 change 借鉴该设计,把 service 拆成 `FeishuDocService` 4 个高阶方法。

## Goals / Non-Goals

**Goals**
- `FeishuDocService` 提供 4 个细粒度高阶方法(createDoc / readDoc / updateDoc / appendBlock)
- `FeishuSyncService` 公开 API 保留为 facade,内部委托 `FeishuDocService`,无 breaking change
- 现有 `FeishuSyncRepository.push` / `.pull` / 冲突解决保持用户面行为不变
- 复用现有 `FeishuApiClient` + `MarkdownToDocxConverter`,不重写 IO 层

**Non-Goals**
- 不引入 AI 自动同步(留给后续 `feishu-ai-command` change)
- 不重构 `FeishuApiClient`(端点层不动)
- 不改 `feishu_ref` 数据 schema

## Decisions

### 1. 4 个 sub-command 形状(参考 `lark-doc`)

```kotlin
class FeishuDocService @Inject constructor(
    private val apiClient: FeishuApiClient,
    private val converter: MarkdownToDocxConverter,
    private val refDao: FeishuRefDao,
) {
    suspend fun createDoc(note: Note): FeishuRef
    suspend fun readDoc(url: String): FeishuDocContent
    suspend fun updateDoc(note: Note, ref: FeishuRef): FeishuRef
    suspend fun appendBlock(note: Note, ref: FeishuRef, parentBlockId: String, content: String): FeishuRef
}
```

每个方法返回 `FeishuRef`(或扩展 `FeishuDocContent` for read),为后续 AI 编排提供"操作 + 结果"对。

### 2. Facade 模式保留兼容

```kotlin
class FeishuSyncService @Inject constructor(
    private val docService: FeishuDocService,  // 新增依赖
    private val refDao: FeishuRefDao,
    private val conflictResolver: FeishuConflictResolver,
) {
    // 现有 push/pull 公开方法保留
    suspend fun push(noteId: String): Result<FeishuRef> = /* 调 docService.createDoc or updateDoc */
    suspend fun pull(docUrl: String): Result<NoteEntity> = /* 调 docService.readDoc + 写本地 */
}
```

`push` 内部根据 `refDao` 是否已有 ref 决定走 `createDoc` / `updateDoc`,复用现有冲突检测。

### 3. 命令 prompt 模板

`core/ai/prompt/FeishuCommandPrompt.kt` 提供 4 个操作的 JSON schema + 1 个 dispatcher prompt:

```
Available commands:
- create_doc(noteId) → 新建飞书文档
- read_doc(url) → 读取飞书文档内容
- update_doc(noteId) → 更新整篇文档
- append_block(noteId, parentBlockId, content) → 追加一段

User intent: "..."
Output JSON:
{ "op": "...", "args": {...} }
```

后续 `feishu-ai-command` change 复用此 prompt。

### 4. 不改数据 schema

`feishu_ref` 表不变,`FeishuRefDao` 接口不变(只新增查询方法)。

## Risks / Trade-offs

[Risk] **appendBlock 边界情况:parentBlockId 已不存在(远端删除)**
→ Mitigation:`appendBlock` 内部 `try` 一下,远端 404 时回退到 `updateDoc`(整篇追加);记录到 `FeishuSyncEventDao` 状态为 `FALLBACK_TO_UPDATE`。

[Risk] **updateDoc 时远端 doc 已被删**
→ Mitigation:API 返回 404 时,提示用户从 UI 重新走 createDoc 流程;不在后台静默重试(避免覆盖其他人的内容)。

[Risk] **4 个方法边界组合多,测试覆盖成本高**
→ Mitigation:M1 先做 createDoc + readDoc,updateDoc / appendBlock 后续增量 + 各自测试;不强求一次性全覆盖。

## Migration Plan

1. M1 — 新建 `FeishuDocService` 骨架(4 个方法声明 + 单元测试 stub)
2. M2 — 实现 `createDoc` + `readDoc`,复用 `FeishuApiClient` + `MarkdownToDocxConverter`
3. M3 — `FeishuSyncService.push` 内部委托 `createDoc` / `updateDoc`(写 createDoc 实现)
4. M4 — 实现 `updateDoc` + `appendBlock`,`FeishuSyncService.pull` 委托 `readDoc`
5. M5 — 写 `core/ai/prompt/FeishuCommandPrompt.kt`(给未来 AI 编排用,本 change 不强制消费)
6. M6 — 验证:`./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` + `openspec validate` 全绿

**回退**:`FeishuSyncService.push` 内部 switch 回旧实现即可(`FeishuDocService` 是新文件,删掉不影响旧路径)。

## Open Questions

- Q1:`appendBlock` 的 `parentBlockId` 默认值是什么?若不传,是 append 到文档末尾还是替换整篇?待 M2 决定,建议默认 append 到末尾。
- Q2:`updateDoc` 与 `appendBlock` 是否支持混合?(先 updateDoc 再 appendBlock)— 留给后续 change。
- Q3:command prompt 模板是否要支持"批量操作"?(`op: "batch", actions: [...]`)— v1 单操作为主,批量留 v2。