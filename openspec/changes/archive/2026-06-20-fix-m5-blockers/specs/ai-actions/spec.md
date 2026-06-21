# ai-actions Delta Spec (fix-m5-blockers)

## MODIFIED Requirements

### Requirement: AiActionViewModel owns the streaming state machine

`AiActionViewModel : ViewModel` MUST 注入 `AiGateway` + `NoteRepository` + `ConsentStore` + `SecureApiKeyStore` + `PromptTemplateStore` + `ProviderPrefsStore`,暴露 `StateFlow<AiActionUiState>` 给 UI 订阅;状态机用 sealed interface 表达:

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
- 同步读 `providerPrefsStore.getSelectedProviderId()`(user-configured provider id) + `secureApiKeyStore.get(providerId)`(真 apikey)
- 若 `providerId != "fake"` 且 `apikey == null` → `_state = Failed(op, AiError.ProviderNotConfigured)`,**不**调 `AiGateway.streamWritingOp(...)`,return
- 调 `promptTemplateStore.getForOp(op)` 拿用户自定义 system prompt(custom-prompt-template 落地);`null` → 走 `DefaultPrompts.forOp(op)` fallback
- 调 `AiGateway.streamWritingOp(op, sourceText, providerId, **apikey**, modelName=null, systemPrompt=<解析结果>)` 订阅

#### Scenario: start() 同步取 apikey 透传 gateway
- **WHEN** `providerPrefsStore.getSelectedProviderId() == "deepseek"`,`secureApiKeyStore.get("deepseek") == "sk-real-123"`,UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** `AiGateway.streamWritingOp(EXPAND, "晨跑", "deepseek", apikey="sk-real-123", modelName=null, systemPrompt=<DefaultPrompts.forOp(EXPAND)>)` 被订阅;首个 event `Started` 到达时 `AiActionUiState` 转为 `Streaming(op=EXPAND, partialText="")`

#### Scenario: 缺 apikey 阻断 AI 调用
- **WHEN** `providerPrefsStore.getSelectedProviderId() == "deepseek"`,`secureApiKeyStore.get("deepseek") == null`(用户配了 provider 但没存 apikey),UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** `_state` 立即转 `Failed(EXPAND, ProviderNotConfigured)`;`aiGateway.streamWritingOp(...)` 0 次调用;UI 显示 `R.string.aiwriting_error_unknown` 或对应"请先在设置 → 模型管理配置"文案

#### Scenario: start() 使用用户自定义 prompt(custom-prompt-template)
- **WHEN** UI 调用 `viewModel.start(POLISH, sourceText="晨跑", noteId="n1")`,`promptTemplateStore.getForOp(POLISH) == "你是一位正式文风润色助手"`
- **THEN** `AiGateway.streamWritingOp(POLISH, "晨跑", providerId=<resolveProviderId()>, apikey=<resolveProviderId() 取的真 apikey 或 fake 路径>, modelName=null, systemPrompt="你是一位正式文风润色助手")` 被订阅

#### Scenario: 模板空走 fallback(custom-prompt-template)
- **WHEN** `viewModel.start(EXPAND, sourceText, noteId)` 调用,`promptTemplateStore.getForOp(EXPAND) == null`
- **THEN** `systemPrompt = DefaultPrompts.forOp(EXPAND)`,AiGateway 收到 fallback 默认 prompt

#### Scenario: Delta 累加 partialText
- **WHEN** AiGateway emit `Delta("你")` → `Delta("好")`
- **THEN** `AiActionUiState.Streaming.partialText` 依次更新为 `"你"` → `"你好"`(UI 应实时看到累加)

#### Scenario: Done 携带 usage
- **WHEN** AiGateway emit `Usage(inputTokens=2, outputTokens=3, totalTokens=5)` → `Done`
- **THEN** `AiActionUiState` 转为 `Done(op, finalText=<累积 partialText>, usage=<Usage 对象>)`

#### Scenario: Failed 携带 AiError
- **WHEN** AiGateway emit `Failed(AiError.Network(code=500, detail="timeout"), recoverable=true)`
- **THEN** `AiActionUiState` 转为 `Failed(op, error=Network(500, "timeout"))`

## ADDED Requirements

### Requirement: ModelManagementViewModel passes real apikey to gateway.ping

`ModelManagementViewModel.ping(providerId)` MUST 同步从 `SecureApiKeyStore.get(providerId)` 拿 apikey,`null` → emit `PingResult.Failed("apikey 未配置")` 不调 gateway;非 null → 调 `aiGateway.ping(providerId, apikey=apikey, modelName="default")`。理由:`AiGateway.ping` 签名也升级到 `(providerId, apikey, modelName)`,与 `streamWritingOp` 一致。

#### Scenario: ping 不调 fake 路径
- **WHEN** `ModelManagementViewModel.ping("deepseek")` 调用,`secureApiKeyStore.get("deepseek") == "sk-real-123"`
- **THEN** `aiGateway.ping("deepseek", apikey="sk-real-123", modelName="default")` 被调 1 次,无 `"fake-apikey"` 占位字面量出现在调用链上

#### Scenario: 缺 apikey 时 ping 立即失败
- **WHEN** `ModelManagementViewModel.ping("deepseek")` 调用,`secureApiKeyStore.get("deepseek") == null`
- **THEN** `aiGateway.ping(...)` 0 次调用,UI 立即显示 `PingResult.Failed("apikey 未配置")`,不等待网络超时
