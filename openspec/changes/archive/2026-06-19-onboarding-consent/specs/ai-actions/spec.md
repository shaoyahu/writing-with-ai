## MODIFIED Requirements

### Requirement: AiActionViewModel gates AI calls behind user consent

`AiActionViewModel` 构造 MUST 注入 `ConsentStore`,初始化时读 `consentStore.consentAccepted` 到内部 `StateFlow<Boolean>`;`start(op, sourceText, noteId)` 调用前 MUST 检查 `consentFlow.value == true`,若 `false` → `aiState = Failed(op, AiError.UserConsentRequired)`(新增 `AiError` 子类,见下)并立即 return,**不** 调 `AiGateway.streamWritingOp(...)`。`AiError` 新增子类型:

```kotlin
sealed interface AiError {
    // ... 既有子类 (Network, Auth, InsufficientBalance, Timeout, ProviderNotFound, Deserialization, Unknown)
    data object UserConsentRequired : AiError
}
```

`AiError.toDisplayMessage(Context)` MUST 映射 `UserConsentRequired` → `R.string.onboarding_required`("请先同意隐私条款")。

`ActionSheet`(锚定到详情屏 FAB 的 `DropdownMenu`)MUST 在用户点击 FAB 时先查 `consentStore.consentAccepted.state`,若 `false` → 不弹 sheet,改 navigate `onboarding/consent`(经 `AiwritingEntry.requestConsent(navController)` 入口);若 `true` → 现有弹 sheet 行为不变。

#### Scenario: 未同意时 start() 失败
- **WHEN** `consentFlow.value = false`,UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** `aiState` 立即转 `Failed(EXPAND, UserConsentRequired)`;`aiGateway.streamWritingOp(...)` 0 次调用;UI 显示 `R.string.onboarding_required` 文案

#### Scenario: 已同意时 start() 正常
- **WHEN** `consentFlow.value = true`,UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** 走既有 M3 行为:订阅 `aiGateway.streamWritingOp(...)`,`aiState` 转 `Streaming`

#### Scenario: ActionSheet 未同意时改走 onboarding
- **WHEN** 用户在详情屏长按选中 5 字符,`consentFlow.value = false`,点击 AutoAwesome FAB
- **THEN** `DropdownMenu` **不** 弹出;`AiwritingEntry.requestConsent(navController)` 被调用,`navController.navigate("onboarding/consent")`;FAB click 处理零异常

#### Scenario: ActionSheet 已同意行为不变
- **WHEN** `consentFlow.value = true`,用户点击 AutoAwesome FAB
- **THEN** `DropdownMenu` 弹出,4 个菜单项(扩写/润色/整理/复制)齐全,行为与 M3 一致

#### Scenario: consent 状态变化时 AiActionUiState 联动
- **WHEN** 用户在 `Streaming` 态过程中,`ConsentStore.setAccepted(false)` 被调用(极端 case:运行时撤回)
- **THEN** 已有 stream Flow 不强制取消(M3 行为保留),但下一次 `start()` 调用必失败;UI 在 `StreamingPanel` 不变(用户完成当前流后再触发才走 gating)

#### Scenario: UserConsentRequired 错误文案
- **WHEN** `aiState = Failed(op, UserConsentRequired)`
- **THEN** `StreamingPanel` Failed 态显示 `R.string.onboarding_required`(中文"请先同意隐私条款");底部"关闭"按钮可点;**不** 替换正文

### Requirement: PROVIDER_ID_FAKE constant is replaced by consented provider

`AiActionViewModel.PROVIDER_ID_FAKE` 常量在 M4-4 完成后 MUST 仍存在(向后兼容)但实际值从 `ConsentStore` 读取:consent 通过时优先用 `SecureApiKeyStore.has("deepseek")` 返回 `true` 的 provider(默认 `"deepseek"`),否则 fallback `"fake"`(M3 FakeProvider 端到端验证不阻塞 v1 内测);M5 polish 时再切完整 provider 切换 UI。

```kotlin
companion object {
    const val PROVIDER_ID_FAKE = "fake"  // 兼容旧引用,M5 polish 删
    private val DEFAULT_PROVIDER = "deepseek"
}
```

`start()` 内部 MUST 用 `private suspend fun resolveProviderId(): String = if (secureApiKeyStore.has(DEFAULT_PROVIDER)) DEFAULT_PROVIDER else PROVIDER_ID_FAKE`(M4-4 实现,M5 polish 改 SettingsRepository 切换)。

#### Scenario: 未配置 apikey 时 fallback fake
- **WHEN** `SecureApiKeyStore.has("deepseek") = false`,`start(EXPAND, "晨跑", "n1")` 调用
- **THEN** `providerId` 解析为 `"fake"`;走 FakeProvider 端到端(M3 行为)

#### Scenario: 已配置 apikey 时用 deepseek
- **WHEN** `SecureApiKeyStore.has("deepseek") = true`,`start(EXPAND, "晨跑", "n1")` 调用
- **THEN** `providerId` 解析为 `"deepseek"`;调 `AiGateway.streamWritingOp(EXPAND, "晨跑", "deepseek", null)`;M5 polish 落地真 provider 联调(v1 内测阶段用 fake + 已配 apikey 但仍走 fake,见 design D2 / D4 注)

#### Scenario: 切换 provider 不影响 consent 门
- **WHEN** `consentFlow.value = true`,`providerId` 从 `"fake"` 切到 `"deepseek"`
- **THEN** consent 门控行为不变,只影响 `AiGateway.streamWritingOp` 第三参数
