## Why

`FeishuSyncService` 当前操作粒度粗(一次只能整个 note 同步)，无法直接对接"AI 写作流 → 飞书"场景(如:AI 扩写后只追加一个 block、或只更新文档某段)。参考飞书 CLI 仓库 `lark-doc` skill 的 sub-command 设计(`create` / `read` / `update` / `append`)，把 service 拆成 4 个高阶操作，降低未来 AI 编排调用成本。

## What Changes

- **拆** `FeishuSyncService` → 4 个高阶 service:`FeishuDocService.createDoc(note)` / `.readDoc(url)` / `.updateDoc(note, ref)` / `.appendBlock(note, ref, blockId, content)`
- **保留** `FeishuSyncRepository.push` / `.pull` / 冲突解决 — 它们是面向用户的高层动作(详情页按钮触发)，由 `FeishuDocService` 提供底座
- **复用** 现有 `FeishuApiClient` + `MarkdownToDocxConverter`(不动 IO 细节)
- **新增** `feature/feishu/command/FeishuCommandPrompt.kt`(系统 prompt，供未来 AI 编排使用)
- **无 BREAKING**:`FeishuSyncService` 公开 API 保留为 facade，内部委托给新 service

## Capabilities

### New Capabilities

- `feishu-doc-service`:高阶飞书文档操作服务(4 个 sub-command)，为 AI 写作流提供细粒度飞书操作接口

### Modified Capabilities

- `feishu-bidir-sync`:`FeishuSyncService` 公开 API 保持 facade，底层实现由 `FeishuDocService` 委托

## Impact

- **改动代码**:
  - `core/feishu/sync/FeishuDocService.kt`(新)— 4 个 sub-command
  - `core/feishu/sync/FeishuSyncService.kt`(改)— facade 委托
  - `core/feishu/sync/FeishuSyncRepository.kt`(改)— 走新 service
  - `core/ai/prompt/FeishuCommandPrompt.kt`(新)— 系统 prompt 模板
- **资源**:无新增 strings / 无新增依赖
- **风险**:4 个 sub-command 边界情况(append 到已删除 block / update 时 doc 已被远端删)— 见 design.md "风险"
- **回退**:`FeishuDocService` 内部实现可单点回滚，facade 仍走旧路径