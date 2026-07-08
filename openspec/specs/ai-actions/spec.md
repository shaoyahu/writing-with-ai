# ai-actions Specification

## Purpose
TBD - created by archiving change ai-writing-actions. Update Purpose after archive.
## Requirements
### Requirement: ActionSheet shows available AI operations on selection

详情屏 MUST 在用户**选中文本非空**时，通过详情页 FAB(`Icons.Filled.AutoAwesome`)唤起一个 `DropdownMenu`(锚定到 FAB)，提供 4 个菜单项:

| 菜单项 | 文案 key | 触发行为 |
| --- | --- | --- |
| 扩写 | `aiwriting_action_expand` | `AiActionViewModel.start(op=EXPAND, sourceText, noteId)` |
| 润色 | `aiwriting_action_polish` | `AiActionViewModel.start(op=POLISH, sourceText, noteId)` |
| 整理 | `aiwriting_action_organize` | `AiActionViewModel.start(op=ORGANIZE, sourceText, noteId)` |
| 复制 | `aiwriting_action_copy` | `ClipboardManager.setPrimaryClip(sourceText)`(非 AI 操作，不进 AiGateway) |

#### Scenario: FAB 显示条件
- **WHEN** 详情屏当前选区为空(无文本被选中)
- **THEN** 仅显示原有 `Icons.Filled.Share` FAB,**不**显示 AutoAwesome FAB;ActionSheet 不可触发

#### Scenario: FAB 显示条件(有选区)
- **WHEN** 详情屏用户长按选中 5 个字符
- **THEN** AutoAwesome FAB 出现;点击 FAB 弹出 DropdownMenu,4 个菜单项齐全

#### Scenario: 点扩写触发 AI 流
- **WHEN** ActionSheet 展开后用户点击"扩写"
- **THEN** `AiActionViewModel.start(EXPAND, sourceText=<选中 5 字符>, noteId=<当前 note id>)` 被调用;Menu 自动关闭;`ModalBottomSheet` 弹出并立即进入 Streaming 态

#### Scenario: 点复制不触发 AI
- **WHEN** ActionSheet 展开后用户点击"复制"
- **THEN** `ClipboardManager` 写入 `sourceText`;Menu 自动关闭;**不**打开 ModalBottomSheet;**不**调 AiGateway

### Requirement: AiActionViewModel owns the streaming state machine

`AiActionViewModel : ViewModel` MUST 注入 `AiGateway` + `NoteRepository` + `ConsentStore` + `SecureApiKeyStore` + `PromptTemplateStore` + `ProviderPrefsStore`，暴露 `StateFlow<AiActionUiState>` 给 UI 订阅;状态机用 sealed interface 表达:

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
- 若 `apikey == null` → `_state = Failed(op, AiError.ProviderNotConfigured)`,**不**调 `AiGateway.streamWritingOp(...)`,return
- 调 `promptTemplateStore.getForOp(op)` 拿用户自定义 system prompt(custom-prompt-template 落地);`null` → 走 `DefaultPrompts.forOp(op)` fallback
- 调 `AiGateway.streamWritingOp(op, sourceText, providerId, apikey, modelName=null, systemPrompt=<解析结果>)` 订阅

#### Scenario: start() 同步取 apikey 透传 gateway
- **WHEN** `providerPrefsStore.getSelectedProviderId() == "deepseek"`,`secureApiKeyStore.get("deepseek") == "sk-real-123"`,UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** `AiGateway.streamWritingOp(EXPAND, "晨跑", "deepseek", apikey="sk-real-123", modelName=null, systemPrompt=<DefaultPrompts.forOp(EXPAND)>)` 被订阅;首个 event `Started` 到达时 `AiActionUiState` 转为 `Streaming(op=EXPAND, partialText="")`

#### Scenario: 缺 apikey 阻断 AI 调用
- **WHEN** `providerPrefsStore.getSelectedProviderId() == "deepseek"`,`secureApiKeyStore.get("deepseek") == null`(用户配了 provider 但没存 apikey),UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** `_state` 立即转 `Failed(EXPAND, ProviderNotConfigured)`;`aiGateway.streamWritingOp(...)` 0 次调用;UI 显示 `R.string.aiwriting_error_unknown` 或对应"请先在设置 → 模型管理配置"文案

#### Scenario: start() 使用用户自定义 prompt(custom-prompt-template)
- **WHEN** UI 调用 `viewModel.start(POLISH, sourceText="晨跑", noteId="n1")`,`promptTemplateStore.getForOp(POLISH) == "你是一位正式文风润色助手"`
- **THEN** `AiGateway.streamWritingOp(POLISH, "晨跑", providerId=<resolveProviderId()>, modelName=null, systemPrompt="你是一位正式文风润色助手")` 被订阅

#### Scenario: start() 模板空走 fallback(custom-prompt-template)
- **WHEN** `viewModel.start(EXPAND, sourceText, noteId)` 调用，`promptTemplateStore.getForOp(EXPAND) == null`
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

### Requirement: ModelManagementViewModel passes real apikey to gateway.ping

`ModelManagementViewModel.ping(providerId)` MUST 同步从 `SecureApiKeyStore.get(providerId)` 拿 apikey,`null` → emit `PingResult.Failed("apikey 未配置")` 不调 gateway;非 null → 调 `aiGateway.ping(providerId, apikey=apikey, modelName="default")`。理由:`AiGateway.ping` 签名也升级到 `(providerId, apikey, modelName)`，与 `streamWritingOp` 一致。

#### Scenario: ping 不调 fake 路径
- **WHEN** `ModelManagementViewModel.ping("deepseek")` 调用，`secureApiKeyStore.get("deepseek") == "sk-real-123"`
- **THEN** `aiGateway.ping("deepseek", apikey="sk-real-123", modelName="default")` 被调 1 次，无 `"fake-apikey"` 占位字面量出现在调用链上

#### Scenario: 缺 apikey 时 ping 立即失败
- **WHEN** `ModelManagementViewModel.ping("deepseek")` 调用，`secureApiKeyStore.get("deepseek") == null`
- **THEN** `aiGateway.ping(...)` 0 次调用，UI 立即显示 `PingResult.Failed("apikey 未配置")`，不等待网络超时

#### Scenario: saveProvider 失败时 UI 显式 Failed 反馈 + 不切 selected
- **WHEN** `secureApiKeyStore.save(providerId, apiKey)` 抛 `GeneralSecurityException`(模拟 keystore 损坏)
- **THEN** `lastSaveResult = Failed("KeyStore 损坏")`;`selectedProviderId` 与 `hasApiKeyForSelected` **不变**(不切 selected，不冒进);UI 显示 Snackbar `"保存失败:KeyStore 损坏"` + 不触发 onBack

### Requirement: ModelManagementViewModel exposes configuredProviderIds

`ModelManagementViewModel` MUST 暴露 `configuredProviderIds: StateFlow<Set<String>>`，数据源来自 `SecureApiKeyStore.observeConfiguredProviders()`(底层 `EncryptedSharedPreferences` 的 `OnSharedPreferenceChangeListener` 监听所有 `apikey_*` key)。

`ModelManagementUiState` MUST 新增字段 `configuredProviderIds: Set<String> = emptySet()`;`init { }` MUST collect 该 Flow → `_state.update { it.copy(configuredProviderIds = ids) }`。

`SaveResult` MUST 是 `sealed interface { data object Idle; data object InProgress; data object Success; data class Failed(val reason: String) }`;`ModelManagementUiState.lastSaveResult: SaveResult` 默认 `Idle`。

#### Scenario: save 成功后 configuredProviderIds 实时 emit
- **WHEN** `saveProvider("deepseek", "sk-xxx")` 调用成功
- **THEN** `observeConfiguredProviders()` 1 次新 emit 含 `"deepseek"`;VM `_state.configuredProviderIds` 同步更新为 `{"deepseek"}`;`ModelManagementScreen` deepseek 卡片右上角 SuggestionChip 由 "未配置"(灰)变 "已配置"(蓝)+ CheckCircle

#### Scenario: clear 后 configuredProviderIds 同步移除
- **WHEN** `secureApiKeyStore.clear("deepseek")` 调用
- **THEN** `observeConfiguredProviders()` 1 次新 emit 不含 `"deepseek"`;VM `_state.configuredProviderIds` 同步从 set 移除;deepseek 卡片回到 "未配置" 灰 chip

#### Scenario: 多个 provider 并存配置
- **WHEN** 同时配置 deepseek + minimax
- **THEN** `_state.configuredProviderIds = {"deepseek", "minimax"}`;两张卡片同时显示 "已配置" 蓝 chip(不互斥)

#### Scenario: 切换 selected 不影响其他 provider 已配置状态
- **WHEN** deepseek 已配 → 选 mimo 为 selected
- **THEN** `_state.configuredProviderIds` 仍含 `"deepseek"`(selected 切换是 prefs 行为，不删除 apikey);deepseek 卡片 "已配置" 蓝 chip 仍显示

### Requirement: StreamingPanel renders state-aware UI inside ModalBottomSheet

`StreamingPanel` MUST 是 `ModalBottomSheet` 内容，根据 `AiActionUiState` 渲染不同 UI:

| 状态 | 顶部 | 中部 | 底部按钮 |
| --- | --- | --- | --- |
| `Streaming` | `<op> 进行中...` | `Text(partialText)` 实时滚动 | `取消` |
| `Done` | `<op> 完成` | `Text(finalText)` | `接受` / `拒绝` / `再生成` |
| `Failed` | `出错` | `<error.toDisplayMessage()>` | `关闭` |
| `Idle` | (不显示) | (不显示) | (无) |

`ModalBottomSheet` MUST 设 `skipPartiallyExpanded = true`;用户按 back 或点击 sheet 外区域触发 `onDismissRequest` → `viewModel.dismiss()`(等同于 cancel)。

#### Scenario: Streaming 态只显示取消按钮
- **WHEN** state = `Streaming(op=EXPAND, partialText="你好", isCancelled=false)`
- **THEN** UI 显示顶部"扩写进行中"、中部"你好"、底部仅"取消"按钮

#### Scenario: Done 态显示三按钮
- **WHEN** state = `Done(op=POLISH, finalText="优化后文本", usage=...)`
- **THEN** UI 显示顶部"润色完成"、中部"优化后文本"、底部"接受" / "拒绝" / "再生成"三个按钮;接受按钮 enable(依赖 `finalText.isNotBlank()`)

#### Scenario: Failed 态只显示关闭
- **WHEN** state = `Failed(op=ORGANIZE, error=Network(500))`
- **THEN** UI 显示顶部"出错"、中部"网络连接失败，请检查后重试"(取自 R.string.aiwriting_error_network)、底部仅"关闭"按钮

#### Scenario: 关闭 ModalBottomSheet 等同 cancel
- **WHEN** 用户在 Streaming 态点击 sheet 外区域或按 back
- **THEN** `viewModel.dismiss()` 被调用;AiActionUiState 回到 Idle;**不**替换正文;流式 Flow 触发取消订阅(Coroutine 协程被父 scope 取消)

### Requirement: User actions: accept / reject / cancel / regenerate

`AiActionViewModel` MUST 暴露以下公开方法供 UI 调用:

| 方法 | 触发态 | 行为 |
| --- | --- | --- |
| `acceptReplace()` | `Done` | `withContext(NonCancellable)` 事务:读 Note → `note.copy(content=finalText, updatedAt=now)` → `repo.upsert(updated, tags)` → `repo.updateAiMetadata(noteId, op.name.lowercase(), now)` → state 回到 Idle |
| `reject()` | `Done` | state 直接回 Idle;**不**替换正文 |
| `cancel()` | `Streaming` | state 直接回 Idle;Flow 取消订阅 |
| `dismiss()` | 任何 | state 回 Idle;Flow 取消订阅 |
| `regenerate()` | `Done` | 复用上次 `op` / `sourceText` / `noteId` 重新调 `start(...)` |

`acceptReplace()` MUST 用 `withContext(NonCancellable)` 包裹"读 + upsert + updateAiMetadata"三步;若 ViewModel 在 acceptReplace 期间被 cleared(进程死 / 系统回收)，事务仍能完成(写到 Room 即落盘)。

#### Scenario: 接受替换正文并写 lastAiOp
- **WHEN** state = `Done(op=POLISH, finalText="优化后", usage=...)`，用户点"接受"
- **THEN** 笔记 `content` 从原值变为 "优化后",`updatedAt=<now>`;`Note.lastAiOp="polish"`,`Note.lastAiAt=<now>`;AiHistory 已自动落库(M2);UI 详情页通过 Flow 自动刷新显示新正文

#### Scenario: 拒绝不替换
- **WHEN** state = `Done(op=EXPAND, finalText="扩写后", usage=...)`，用户点"拒绝"
- **THEN** 笔记 `content` 保持原值;`lastAiOp` / `lastAiAt` 不变;AiHistory 仍自动落库(成功调用)

#### Scenario: 取消中止流
- **WHEN** state = `Streaming(op=ORGANIZE, partialText="<部分>")`，用户点"取消"
- **THEN** 笔记 `content` 保持原值;AiHistory 落库(error=<cancellation reason> 或 success 但 output 截断)

#### Scenario: 再生成用同 op 重跑
- **WHEN** state = `Done(op=EXPAND, finalText="第1次", usage=...)`，用户点"再生成"
- **THEN** ViewModel 复用上次参数调 `start(EXPAND, sourceText=<上次 selectedText>, noteId=<上次 noteId>)`;AiActionUiState 重新进入 Streaming 态;原 `finalText` 被丢弃

#### Scenario: acceptReplace 事务不被中断
- **WHEN** `acceptReplace()` 正在 `withContext(NonCancellable)` 中执行，用户按 back 关闭 ModalBottomSheet，触发 ViewModel.clear()
- **THEN** Room 写入与 `updateAiMetadata` 仍能完成(NonCancellable 保护);AiHistory 已自动落库(M2)

### Requirement: Error fallback never leaves a blank UI

AiError MUST 在 `AiActionUiState.Failed` 中以 `AiError.toDisplayMessage(Context)` 映射到用户文案:

```kotlin
fun AiError.toDisplayMessage(ctx: Context): String = when (this) {
    is Network -> ctx.getString(R.string.aiwriting_error_network)
    is Auth -> ctx.getString(R.string.aiwriting_error_auth)
    is InsufficientBalance -> ctx.getString(R.string.aiwriting_error_balance)
    is Timeout -> ctx.getString(R.string.aiwriting_error_timeout)
    is ProviderNotFound -> ctx.getString(R.string.aiwriting_error_unknown)
    is Deserialization -> ctx.getString(R.string.aiwriting_error_unknown)
    is Unknown -> ctx.getString(R.string.aiwriting_error_unknown)
}
```

`StreamingPanel` 在 `Failed` 态 MUST 显示该文案 + "关闭"按钮(点击关闭 sheet，回 Idle);**禁止**弹 Dialog / Snackbar / 任何额外模态(简化 UX)。

#### Scenario: 网络错误显示 fallback 文案
- **WHEN** FakeProvider 注入 `errorAfterTokens=1`(M2 已支持，模拟中断)
- **THEN** UI 进入 `Failed(op, Network)`;中部显示 `R.string.aiwriting_error_network` 对应中文"网络连接失败，请检查后重试";底部"关闭"按钮可点

#### Scenario: 余额不足文案
- **WHEN** AiGateway emit `Failed(InsufficientBalance(code=402, detail="..."))`
- **THEN** UI 进入 `Failed(op, InsufficientBalance)`;中部显示 `R.string.aiwriting_error_balance` 对应中文"账户余额不足"

#### Scenario: 关闭 Failed 面板回到 Idle
- **WHEN** 用户在 Failed 态点"关闭"
- **THEN** ModalBottomSheet 关闭;AiActionUiState 回到 Idle;下次 FAB 点击可重新进入流程

### Requirement: i18n for AI writing UI

所有 `ai-actions` 相关 UI 文案 MUST 出现在 `values/strings.xml`(中文，权威)与 `values-en/strings.xml`(英文 TODO 占位)，命名空间为 `aiwriting_*`,19 个 key 集合:

| key | 中文 | 用途 |
| --- | --- | --- |
| `aiwriting_action_expand` | 扩写 | ActionSheet 项 |
| `aiwriting_action_polish` | 润色 | ActionSheet 项 |
| `aiwriting_action_organize` | 整理 | ActionSheet 项 |
| `aiwriting_action_copy` | 复制 | ActionSheet 项 |
| `aiwriting_panel_title_expand` | 扩写 | StreamingPanel 顶部(EXPAND) |
| `aiwriting_panel_title_polish` | 润色 | StreamingPanel 顶部(POLISH) |
| `aiwriting_panel_title_organize` | 整理 | StreamingPanel 顶部(ORGANIZE) |
| `aiwriting_panel_streaming` | 进行中... | Streaming 顶部副标题 |
| `aiwriting_panel_cancel` | 取消 | Streaming 底部按钮 |
| `aiwriting_panel_accept` | 接受 | Done 底部按钮 |
| `aiwriting_panel_reject` | 拒绝 | Done 底部按钮 |
| `aiwriting_panel_regenerate` | 再生成 | Done 底部按钮 |
| `aiwriting_panel_close` | 关闭 | Failed 底部按钮 |
| `aiwriting_panel_usage_fmt` | 输入 %1$d · 输出 %2$d | token 用量 chip |
| `aiwriting_error_network` | 网络连接失败，请检查后重试 | Network |
| `aiwriting_error_auth` | 认证失败，请检查 API key | Auth |
| `aiwriting_error_balance` | 账户余额不足 | InsufficientBalance |
| `aiwriting_error_timeout` | 请求超时，请稍后重试 | Timeout |
| `aiwriting_error_unknown` | 出错了，请稍后重试 | 兜底 |

Composable 内 MUST 通过 `stringResource(R.string.aiwriting_xxx)` 引用;**禁止**硬编码中文 / 英文。

#### Scenario: 系统语言为英文时显示 TODO 占位
- **WHEN** 系统语言为英文，`values-en/strings.xml` 中 `aiwriting_action_expand="TODO(en): aiwriting_action_expand"`
- **THEN** ActionSheet 显示 `TODO(en): aiwriting_action_expand`(可读 + 不阻断构建);M5 polish 时替换为正式英文

#### Scenario: 中文文案来自 R.string
- **WHEN** 系统语言为中文，UI 渲染 ActionSheet 项"扩写"
- **THEN** 该文本通过 `stringResource(R.string.aiwriting_action_expand)` 取值，源码 grep 不到中文字面量

### Requirement: ai-actions does not introduce direct network calls

`feature/aiwriting/**` 包内 MUST **不** 直接 import `okhttp3.*` / `java.net.*` / `retrofit2.*` / `kotlinx.serialization.*`(JSON 走 AiGateway 内部);所有 AI 调用 MUST 经过 `AiGateway.streamWritingOp(...)`。`ClipboardManager` 例外(系统 API，非网络)。

#### Scenario: ViewModel 不持有 OkHttp
- **WHEN** grep `feature/aiwriting/**/*.kt`
- **THEN** 0 个匹配 `okhttp3` / `retrofit2` / `HttpURLConnection` 的 import 行

#### Scenario: AI 调用走 AiGateway
- **WHEN** `AiActionViewModel.start(EXPAND, ...)` 执行
- **THEN** 内部唯一调用是 `aiGateway.streamWritingOp(EXPAND, sourceText, providerId, apikey)`

### Requirement: package layout follows feature self-containment

`feature/aiwriting/` MUST 自包含，跨 feature 引用(若有)走 `feature/aiwriting/AiwritingEntry.kt` object 暴露，不允许 `feature/quicknote/**` 直接 import `feature/aiwriting/**` 内部文件(除了 `Entry`)。`feature/aiwriting/` 内子目录:

```
feature/aiwriting/
├── action/                 # ActionSheet + ActionSelectionViewModel
├── streaming/              # StreamingPanel + AiActionViewModel + AiActionUiState
├── error/                  # AiError.toDisplayMessage() 扩展
└── AiwritingEntry.kt       # 跨 feature 入口(如需要)
```

#### Scenario: quick-note 不直接 import aiwriting 内部
- **WHEN** grep `feature/quicknote/**/*.kt`
- **THEN** import 不出现 `feature.aiwriting.streaming.AiActionViewModel` 等具体文件(只允许 `feature.aiwriting.AiwritingEntry` 之类入口 object)


## MODIFIED Requirements (M4-4 onboarding-consent)

### Requirement: AiActionViewModel gates AI calls behind user consent

`AiActionViewModel` 构造 MUST 注入 `ConsentStore` + `SecureApiKeyStore`;构造期同步 `runBlocking { consentStore.isConsented(BuildConfig.CONSENT_VERSION) }` 拿权威 `initialConsented: Boolean`(避免 `stateIn(Eagerly, EMPTY)` 冷启动 race);`start(op, sourceText, noteId)` 调用前 MUST 检查 `initialConsented == true`，若 `false` → `aiState = Failed(op, AiError.UserConsentRequired)`(新增 `AiError` 子类，见下)并立即 return,**不** 调 `AiGateway.streamWritingOp(...)`。`AiError` 新增子类型:

```kotlin
sealed interface AiError {
    // ... 既有子类 (Network, Auth, InsufficientBalance, Timeout, ProviderNotFound, Deserialization, Unknown)
    data object UserConsentRequired : AiError
}
```

`AiError.toDisplayMessage(Context)` MUST 映射 `UserConsentRequired` → `R.string.onboarding_required`("请先同意隐私条款")。

`ActionSheet`(锚定到详情屏 FAB 的 `DropdownMenu`)MUST 在用户点击 FAB 时先查 `consentStore.consentFlow.value.accepted`，若 `false` → 不弹 sheet，改 navigate `onboarding/consent`(经 `AiwritingEntry.requestConsent(navController)` 入口);若 `true` → 现有弹 sheet 行为不变。

#### Scenario: 未同意时 start() 失败
- **WHEN** `initialConsented == false`,UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** `aiState` 立即转 `Failed(EXPAND, UserConsentRequired)`;`aiGateway.streamWritingOp(...)` 0 次调用;UI 显示 `R.string.onboarding_required` 文案

#### Scenario: 已同意时 start() 正常
- **WHEN** `initialConsented == true`,UI 调用 `viewModel.start(EXPAND, "晨跑", "n1")`
- **THEN** 走既有 M3 行为:订阅 `aiGateway.streamWritingOp(...)`,`aiState` 转 `Streaming`

#### Scenario: ActionSheet 未同意时改走 onboarding
- **WHEN** 用户在详情屏长按选中 5 字符，`consentFlow.value.accepted == false`，点击 AutoAwesome FAB
- **THEN** `DropdownMenu` **不** 弹出;`AiwritingEntry.requestConsent(navController)` 被调用，`navController.navigate("onboarding/consent")`;FAB click 处理零异常

#### Scenario: ActionSheet 已同意行为不变
- **WHEN** `consentFlow.value.accepted == true`，用户点击 AutoAwesome FAB
- **THEN** `DropdownMenu` 弹出，4 个菜单项(扩写/润色/整理/复制)齐全，行为与 M3 一致

#### Scenario: consent 状态变化时 AiActionUiState 联动
- **WHEN** 用户在 `Streaming` 态过程中，`ConsentStore.setAccepted(false)` 被调用(极端 case:运行时撤回)
- **THEN** 已有 stream Flow 不强制取消(M3 行为保留)，但下一次 `start()` 调用必失败;UI 在 `StreamingPanel` 不变(用户完成当前流后再触发才走 gating)

#### Scenario: UserConsentRequired 错误文案
- **WHEN** `aiState = Failed(op, UserConsentRequired)`
- **THEN** `StreamingPanel` Failed 态显示 `R.string.onboarding_required`(中文"请先同意隐私条款");底部"关闭"按钮可点;**不** 替换正文

### Requirement: AI op 调用必须使用真 AI provider(debug 包同 release 行为)

`AiActionViewModel` 扩写 / 润色 / 整理 / 摘要 / 翻译 5 个 op MUST 仅在用户配置真实 AI provider apikey 时调用 LLM。`BuildConfig.DEBUG` **不**再是「无 apikey 时回退 fake」的合法理由:

- 无任何真实 provider apikey → `AiActionViewModel.start(...)` 检测 `secureApiKeyStore.observeConfiguredProviders().first().isEmpty()` → 立即 emit `AiError.ProviderNotConfigured` → UI Snackbar「请先配置 AI 模型」 + 「去设置」action,跳 AI 设置页
- `PROVIDER_ID_FAKE` 常量已删除;全链路不再认 `"fake"` 作为合法 provider id
- `CoreAiGateway.stream(...)` 收到 `providerId == "fake"` 时不再走 fake 特殊路径;provider map 不含 "fake",自动 fall through 到通用"无 provider"错误处理

#### Scenario: debug 包无 apikey 触发扩写 → 请先配置
- **WHEN** debug 包跑在真机/模拟器,用户未配置 AI provider apikey,在详情屏选中文本点扩写
- **THEN** `AiActionViewModel.start(EXPAND, ...)` 立即 emit `AiError.ProviderNotConfigured`;UI Snackbar 显示「请先配置 AI 模型」 + 「去设置」按钮;点击 action 跳 `ModelManagementScreen`;**不**调用 FakeAiProvider,**不**出现 fake 文本

#### Scenario: debug 包有 apikey 触发扩写 → 真 provider 流式
- **WHEN** debug 包跑在真机/模拟器,用户已配置 deepseek apikey,选中文本点扩写
- **THEN** `AiActionViewModel` 走真 provider HTTP,SSE 流式返回 Delta,UI StreamingPanel 逐 token 渲染;debug 与 release 行为一致

#### Scenario: 扩写完成 token 落 ai_history 表
- **WHEN** 真 provider 扩写流式完成
- **THEN** ai_history 表写入一条记录(`op=expand`, `providerId="deepseek"`, `inputTokens` / `outputTokens` / `totalTokens`);debug 与 release 一致

#### Scenario: grep 验证 AiActionViewModel 无 PROVIDER_ID_FAKE 常量
- **WHEN** `grep "PROVIDER_ID_FAKE\|\"fake\"" app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt`
- **THEN** 0 匹配

#### Scenario: grep 验证 CoreAiGateway 无 fake 特殊处理
- **WHEN** `grep "FakeAiProvider" app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt`
- **THEN** 0 匹配


# ai-actions Delta Spec

## MODIFIED Requirements

### Requirement: ActionSheet shows available AI operations on selection

ActionSheet 菜单项从 4 个扩展为 6 个：

| 菜单项 | 文案 key | 触发行为 |
| --- | --- | --- |
| 扩写 | `aiwriting_action_expand` | `AiActionViewModel.start(op=EXPAND, sourceText, noteId)` |
| 润色 | `aiwriting_action_polish` | `AiActionViewModel.start(op=POLISH, sourceText, noteId)` |
| 整理 | `aiwriting_action_organize` | `AiActionViewModel.start(op=ORGANIZE, sourceText, noteId)` |
| 摘要 | `aiwriting_action_summarize` | `AiActionViewModel.start(op=SUMMARIZE, sourceText, noteId)` |
| 翻译 | `aiwriting_action_translate` | `AiActionViewModel.start(op=TRANSLATE, sourceText, noteId)` |
| 复制 | `aiwriting_action_copy` | `ClipboardManager.setPrimaryClip(sourceText)` |

#### Scenario: 6 个菜单项齐全
- **WHEN** 详情屏用户长按选中 5 个字符，点击 AutoAwesome FAB
- **THEN** DropdownMenu 显示 6 个菜单项（扩写/润色/整理/摘要/翻译/复制）

### Requirement: AiActionViewModel owns the streaming state machine

`AiActionUiState.Done` 新增 `originalText: String` 字段。`AiActionViewModel` 新增 `retry()` 公开方法，行为：复用 `lastOp / lastSourceText / lastNoteId` 重新调 `start()`，与 `regenerate()` 对称（retry 在 Failed 态，regenerate 在 Done 态）。

#### Scenario: retry 从 Failed 态重试
- **WHEN** state = `Failed(op=EXPAND, error=Network(500))`，用户点"重试"
- **THEN** `retry()` 被调用，内部调 `start(EXPAND, sourceText=<上次>, noteId=<上次>)`，state 进入 Streaming

#### Scenario: Done 携带 originalText
- **WHEN** `start(EXPAND, sourceText="晨跑", noteId="n1")` 执行后 Done
- **THEN** `Done(op=EXPAND, originalText="晨跑", finalText=<AI输出>, usage=...)`
