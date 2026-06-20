## MODIFIED Requirements

### Requirement: AiActionViewModel owns the streaming state machine

`AiActionViewModel : ViewModel` MUST 注入 `AiGateway` + `NoteRepository` + `PromptTemplateStore` + `SecureApiKeyStore`,暴露 `StateFlow<AiActionUiState>` 给 UI 订阅;状态机用 sealed interface 表达:

```kotlin
sealed interface AiActionUiState {
    data object Idle : AiActionUiState
    data class Streaming(
        val op: WritingOp,
        val partialText: String,
        val isCancelled: Boolean = false,
    ) : AiActionUiState
    data class Done(
        val op: WritingOp,
        val finalText: String,
        val usage: AiStreamEvent.Usage?,
    ) : AiActionUiState
    data class Failed(val op: WritingOp, val error: AiError) : AiActionUiState
}
```

`start(op, sourceText, noteId)` MUST:
- 调 `secureApiKeyStore.resolveProviderId()` 拿 provider id(已 M4-4 落地,有 deepseek apikey → `"deepseek"`,否则 → `"fake"`)
- 调 `promptTemplateStore.getForOp(op)` 拿用户自定义 system prompt;`null` → 走 `DefaultPrompts.forOp(op)` fallback
- 调 `AiGateway.streamWritingOp(op, sourceText, providerId, systemPrompt, modelName=null)` 订阅

#### Scenario: start() 使用用户自定义 prompt
- **WHEN** UI 调用 `viewModel.start(POLISH, sourceText="晨跑", noteId="n1")`,`promptTemplateStore.getForOp(POLISH) == "你是一位正式文风润色助手"`
- **THEN** `AiGateway.streamWritingOp(POLISH, "晨跑", providerId=<resolveProviderId()>, systemPrompt="你是一位正式文风润色助手", modelName=null)` 被订阅

#### Scenario: start() 模板空走 fallback
- **WHEN** `viewModel.start(EXPAND, sourceText, noteId)` 调用,`promptTemplateStore.getForOp(EXPAND) == null`
- **THEN** `systemPrompt = DefaultPrompts.forOp(EXPAND)`,AiGateway 收到 fallback 默认 prompt

#### Scenario: providerId 走 secureApiKeyStore resolve
- **WHEN** `viewModel.start(op, sourceText, noteId)` 调用
- **THEN** `providerId = secureApiKeyStore.resolveProviderId()`(suspend,同步调用);有 deepseek apikey → "deepseek",否则 → "fake"
