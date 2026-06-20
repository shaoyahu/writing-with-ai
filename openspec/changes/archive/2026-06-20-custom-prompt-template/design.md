## Context

M3 AI 操作(扩写 / 润色 / 整理)的 system prompt 在 `core/ai/prompt/{Expand,Polish,Organize}.kt` 3 个文件写死。`AiActionViewModel.start(op, sourceText, noteId)` 内部 `val providerId = "fake"` 写死,`systemPrompt` 也写死,用户无法配置。M4-4 `secureApiKeyStore.resolveProviderId()` 解决了 provider 选择,但 system prompt 仍是 static。

v1 用户群(自用 + 朋友内测)用 AI 写作会有"想要不同语气 / 风格"的需求,例如:
- 润色要"更正式" vs "更口语化"
- 扩写要"小红书爆款" vs "学术论文"
- 整理要"Mermaid 流程图" vs "PPT 大纲"

把 system prompt 模板化、用户可配,实现"低代码可玩性"。

## Goals / Non-Goals

**Goals:**
- 3 类操作(扩写 / 润色 / 整理)的 system prompt 用户可配
- DataStore 持久化模板,key 命名清晰
- Settings 屏提供编辑 UI + "恢复默认" 按钮
- AiActionViewModel.start() 走模板(空时 fallback 到 M3 写死默认)
- 不破坏 M3 已落地的 AI 操作 UI 闭环

**Non-Goals:**
- 不做模板市场 / 模板分享 / 模板导入导出
- 不做 per-note prompt override(全局模板)
- 不做 system prompt 变量插值(如 `{{noteTitle}}`)
- 不改 M3 写死的 providerId 路径(已 M4-4 走 secureApiKeyStore)
- 不重做 i18n key(只 +12 个,不改既有 11+ 个)

## Decisions

### D1: DataStore 3 key 扁平存储

**选型**:`prompt_template_expand` / `prompt_template_polish` / `prompt_template_organize`,各存 `String?`(空字符串视为"恢复默认")。

**理由**:
- 单用户 3 个模板,扁平 key 够用,无需 nested JSON
- 读写简单,无 schema 迁移成本
- 跟 M4-4 `ConsentStore` 模式一致(DataStore Preferences + combine + stateIn)

**替代方案**:
- Proto DataStore:过度设计,3 个 String 不值得
- Room:写 DataStore / Room 两个存储路径成本高
- JSON 序列化单 key:有 schema 演进负担

### D2: System prompt 空 → fallback 默认

**规则**:
- `PromptTemplateStore.getForOp(op)`:
  - key 不存在 OR 值为 `null` OR 值为 `""`(空字符串) → return `null`
  - 否则 return `String`
- `AiActionViewModel.start()`:`val prompt = templateStore.getForOp(op) ?: DefaultPrompts.forOp(op)`

**理由**:
- 用户在 Settings 屏清空 TextField(想"恢复默认")→ 实际 value 是 `""` 而非 `null`;`""` 也走 fallback
- 避免空字符串发给 LLM 引发奇怪行为

### D3: Settings UI 用 OutlinedTextField + 3 个 Tab

**选型**:
- Settings 主屏(M4-3 overflow menu 入口)用 `LazyColumn` 列出功能项 → 点击"AI 提示词模板" → `PromptTemplateScreen`
- `PromptTemplateScreen` 用 `TabRow` 切换 3 个操作(扩写 / 润色 / 整理),每 Tab 1 个 `OutlinedTextField`(multiline,maxLines 10) + "恢复默认"按钮
- 保存走 VM `debounce 500ms` 自动写 DataStore(避免每按一键写一次)

**理由**:
- 3 个 prompt 可能很长(几十行),`TabRow` 切换比 scroll 3 个大 field 体验好
- 自动 debounce 写避免抖动

**替代方案**:
- 3 个 OutlinedTextField 放一个屏(不切 Tab):太挤
- 每个 prompt 一个独立屏 + 独立 Nav route:Nav 栈深,过设计
- 编辑时实时调 AI 看效果(M3 "再生成"模式已部分覆盖):不做

### D4: providerId 走 secureApiKeyStore(已 M4-4 落地)

**复用** M4-4 `SecureApiKeyStore.resolveProviderId()`:
- 有 deepseek apikey → `"deepseek"`
- 否则 → `"fake"`

`AiActionViewModel.start()` 改:`val providerId = secureApiKeyStore.resolveProviderId()`(同步,suspend fun 调一次)。

### D5: M3 写死 system prompt 合并到 `DefaultPrompts`

`core/ai/prompt/{Expand,Polish,Organize}.kt` 3 个文件合并到 `core/ai/prompt/DefaultPrompts.kt`:
```kotlin
object DefaultPrompts {
    fun forOp(op: WritingOp): String = when (op) {
        WritingOp.EXPAND -> "你是一位写作助手,请扩写以下文本,保持原意,..."  // M3 写死内容
        WritingOp.POLISH -> "..."
        WritingOp.ORGANIZE -> "..."
    }
}
```

提供单点访问,删除 3 个分散文件。

### D6: Settings 屏入口在 QuickNoteListScreen overflow menu

M4-3 已加 overflow menu("数据迁移" 1 项)。本 change 在同一 menu 加"设置" → 跳 `SettingsScreen`,`SettingsScreen` 列出 1 项"AI 提示词模板" → 跳 `PromptTemplateScreen`。两级 Nav 避免污染 TopAppBar。

## Risks / Trade-offs

- **[风险] 用户写空 prompt → 走 fallback** → 已有 D2 防御;空字符串 / null 都走 fallback
- **[风险] 用户写恶意 prompt 引导 LLM 越权** → CLAUDE.md "prompt 注入防御" 仍适用:用户文本(user 消息的 content)不进 system 段拼接;本 change 仅替换 system 段内容,不引入新风险
- **[风险] DataStore 冷启动 read ~30ms** → 跟 M4-4 `isConsented` 模式同,主线程阻塞可接受
- **[风险] debounce 500ms 用户切 Tab 可能丢字** → 切 Tab 立即 flush 一次写入,避免丢
- **[风险] 改 system prompt 路径可能破 M3 测试** → 既有 `AiActionViewModelTest` 5 tests 走 Fake + 不依赖 system prompt,只需补 mock `PromptTemplateStore`

## Open Questions

1. 模板版本号:用户升级到 v2 时,模板是否 bump?(M3 模板硬编码,无 schema)— 暂不做,模板当 user data,卸载重装即丢
2. 模板导出/导入:与 M4-3 数据迁移一起做?留 M5.1
3. "恢复默认"按钮位置:Tab 内 / 全局?Tab 内(每个操作独立)
