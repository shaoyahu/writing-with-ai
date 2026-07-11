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

### Requirement: AiActionViewModel.start generates N candidate versions serially

`AiActionViewModel.start(op, sourceText, noteId)` MUST 加默认 4 参 `versionCount: Int = 3`
(范围 1..3)。当 `versionCount > 1`,VM MUST **串行**调 `aiGateway.streamWritingOp(...)` N 次
(`Anthropic Messages API` 不支持 `n > 1` 单次多采样,多版本必须在客户端串行实现);
每次调用共享同一 `providerId` / `apikey` / `modelName` / `systemPrompt` / `apiFormatOverride`,
仅 `versionGroupId`(同一组共享的 UUID)+ `versionPosition`(0..N-1)不同,
让 ai_history 表能按 groupId 聚合查询。

`start()` MUST 在调用 N 次 `streamWritingOp` 之前**一次性**完成
consent / apikey / provider / model 检查;后续 N 次不再重复 verify,
与 M3 `start()` 已有 consent / apikey / provider gate 路径共享。

`start()` MUST 为整组 N 次调用共享一个 `streamGeneration` generation 计数器;
同时维护 `currentVersionPosition: AtomicInteger` 跟踪当前在跑第几个版本,
旧版本滞后到达的 `Delta` / `Failed` 事件 MUST NOT 覆盖新版本的 state(generation 比对后丢弃)。

#### Scenario: start() 默认生成 3 个版本
- **WHEN** UI 调用 `viewModel.start(EXPAND, sourceText="晨跑", noteId="n1")`(默认 versionCount=3)
- **THEN** `aiGateway.streamWritingOp(EXPAND, "晨跑", ..., versionGroupId=<同一 UUID>, versionPosition=0/1/2)` 依次被订阅 3 次;每次的 `versionGroupId` 相同,`versionPosition` 0 → 1 → 2

#### Scenario: start() 串行版本 1 失败不阻塞 2/3
- **WHEN** `start(versionCount=3)` 调用,版本 1 第 2 个 Delta 后 emit `Failed(Network)`
- **THEN** VM 继续订阅版本 2 的 `streamWritingOp(...)`,版本 1 状态标记 `Failed`,不影响版本 2/3 的进度

#### Scenario: start() versionCount=1 退化为单版本(M3 行为)
- **WHEN** UI 调用 `viewModel.start(EXPAND, ..., versionCount=1)`
- **THEN** VM 仅订阅 1 次 `streamWritingOp(...)`,`versionGroupId=null`(向后兼容 M3 行为);`AiActionUiState.Done` 的 `versions` 列表 size=1

#### Scenario: versionCount 越界拒绝
- **WHEN** UI 调用 `viewModel.start(EXPAND, ..., versionCount=0)` 或 `versionCount=4`
- **THEN** VM 立即抛 `IllegalArgumentException("versionCount must be 1..3")`,不发起任何 AI 调用

#### Scenario: 串行版本间 consent 不重复检查
- **WHEN** `start(versionCount=3)` 已在入口 verify `consentFlow.value.accepted=true`,版本 2 进行中用户调 `ConsentStore.setAccepted(false)`
- **THEN** 版本 2/3 的 `streamWritingOp` 不会被中断(本组 generation 仍有效);只有下一次 `start()` 才生效新 consent 状态

### Requirement: AiActionUiState exposes versions list and selectedPosition

`AiActionUiState.Streaming` / `PartialDone` / `Done` MUST 携带字段:
- `versions: List<AiVersion>`(任一版本可能处于 `Streaming` / `Done` / `Failed`)
- `selectedPosition: Int`(0..N-1,UI 当前选中的版本号)

新增状态 `PartialDone`(N 次版本中有部分完成、部分进行中、部分失败,用户可挑已完成的
版本提前接受,不必等全部跑完)。`Done` 是全部跑完的终态(全部 Done / 部分 Done +
部分 Failed)。

`AiVersion` 是新 `data class`(`feature/aiwriting/streaming/AiVersion.kt`),字段:
`{ position: Int, finalText: String, usage: AiStreamEvent.Usage?, state: State, accumulatedLength: Int }`
(`State` 枚举 `Streaming / Done / Failed`)。

#### Scenario: Done 态含 N 个版本列表
- **WHEN** `start(versionCount=3)` 完成后 3 个版本均 Done
- **THEN** `state.value = Done(originalText, op, versions=[v0, v1, v2], selectedPosition=0)`,`versions.size == 3`

#### Scenario: PartialDone 态 1 个 Done + 2 个 Streaming
- **WHEN** `start(versionCount=3)` 进行中,版本 0 已 Done,版本 1/2 仍在 Streaming
- **THEN** `state.value = PartialDone(op, versions=[Done, Streaming, Streaming], selectedPosition=0)`,UI 允许用户"接受版本 0"提前退出

#### Scenario: Done 态部分失败
- **WHEN** `start(versionCount=3)` 完成,版本 0 Done / 版本 1 Failed / 版本 2 Done
- **THEN** `state.value = Done(versions=[Done, Failed, Done], selectedPosition=0)`(默认选第 1 个 Done);Failed 版本 tab 显示 ✗ 角标,**不**可接受

#### Scenario: 全部 Failed 走 Failed 态
- **WHEN** `start(versionCount=3)` 完成,3 个版本均 Failed
- **THEN** `state.value = Failed(op, AiError.Unknown(detail="全部 3 个版本生成失败"))`,UI 显示"全部 3 个版本生成失败" + 重试按钮(走 `retry()` 重新跑整组)

### Requirement: AiActionViewModel.selectVersion and acceptReplace(position)

`AiActionViewModel` MUST 暴露:
- `fun selectVersion(position: Int)` — 在 `Done` 或 `PartialDone` 态下切换 `selectedPosition`;
  越界或非 Done/PartialDone 态 MUST no-op
- `fun acceptReplace(position: Int = 0)` — 接受指定版本的输出替换正文;
  越界或该版本非 `Done` 态 MUST no-op(防止用户点 Failed 版本 tab 的接受按钮)

`acceptReplace(position)` MUST 复用 M3 `acceptReplace()` 的 `withContext(NonCancellable)`
事务路径:读 note → `note.copy(content=version.finalText, updatedAt=now)` →
`noteRepository.upsert(updated, tags)` → `noteRepository.updateAiMetadata(noteId, op.name.lowercase(), now)` →
`widgetUpdater.updateAll(context)` → state 转 `Replaced(op)`。
仅把替换文本从 `current.finalText` 改为 `versions[position].finalText`。

#### Scenario: acceptReplace(0) 默认接受第 1 个版本
- **WHEN** state = `Done(versions=[v0, v1, v2], selectedPosition=0)`,用户点"接受此版本"(button 不带 position)
- **THEN** `acceptReplace()`(position 默认 0)被调,笔记 content 替换为 `v0.finalText`,`lastAiOp="expand"` 写入

#### Scenario: acceptReplace(2) 接受第 3 个版本
- **WHEN** state = `Done(versions=[v0, v1, v2], selectedPosition=2)`,用户点 tab 3 + "接受此版本"
- **THEN** `acceptReplace(position=2)` 被调,笔记 content 替换为 `v2.finalText`

#### Scenario: selectVersion 越界 no-op
- **WHEN** state = `Done(versions=[v0, v1, v2])`,用户调 `viewModel.selectVersion(99)`
- **THEN** `_state.value` 不变,UI 仍渲染 `selectedPosition=0`

#### Scenario: Failed 版本 tab 的接受按钮 no-op
- **WHEN** state = `Done(versions=[Done, Failed, Done], selectedPosition=1)`,用户点"接受此版本"
- **THEN** `acceptReplace(position=1)` 检测到 versions[1].state == Failed,no-op,UI 不替换正文

### Requirement: StreamingPanel renders version tabs and accept-this-version actions

`StreamingPanel` MUST 在 `Streaming` / `PartialDone` / `Done` 态下渲染 `VersionTabs` Composable
(放 HeaderRow 与中部 ScrollableBody 之间),`VersionTabs` MUST 是 `TabRow` 或 `SecondaryTabRow`,
每个 tab MUST 包含:
- 标题(`版本 %d`,走 `R.string.aiwriting_version_tab_label_fmt`)
- 角标:Done = ✓(走 `R.string.aiwriting_version_tab_done`)/ Failed = ✗(走 `R.string.aiwriting_version_tab_failed`)
  / Streaming = ⏳(走 `R.string.aiwriting_version_tab_streaming`)
- 当前 `selectedPosition` 加粗 + underline 表达选中态

中部 MUST 渲染**当前 selectedPosition 版本**的 finalText + diff highlight(`diffHighlight`
函数复用 M3 既有逻辑)。

底部按钮 MUST 区分状态:
| 状态 | 底部按钮 |
| --- | --- |
| `Streaming` | `取消` |
| `PartialDone` 且 selectedPosition 版本 = Done | `取消` + `接受此版本`(Button,enable) |
| `PartialDone` 且 selectedPosition 版本 = Failed | `取消` |
| `Done` 且 selectedPosition 版本 = Done | `拒绝全部` + `再生成` + `接受此版本`(Button,enable) |
| `Done` 且 selectedPosition 版本 = Failed | `拒绝全部` + `再生成`(接受按钮 enable=false) |

HeaderRow 进度副标题:`<op> · 已生成 X/N` 走 `R.string.aiwriting_version_progress_fmt`
(%1$d=已完成 Done 数,%2$d=总版本数)。

`reject()` MUST 走 M3 既有路径(`_state.value = Idle`),不删除 ai_history 行;
`regenerate()` MUST 复用 `lastOp / lastSourceText / lastNoteId / versionCount` 重跑(默认 3)。

#### Scenario: Done 态默认选第 1 个版本 + diff 高亮
- **WHEN** state = `Done(versions=[v0, v1, v2], selectedPosition=0)`,v0.finalText="扩写后 A"
- **THEN** HeaderRow 显示"扩写 · 完成 · 1/3"(X=已完成,N=3);VersionTabs 3 个 tab,tab 0 加粗;中部渲染 v0 与 originalText 的 diff 高亮;底部"拒绝全部 / 再生成 / 接受此版本"3 个按钮

#### Scenario: 用户切到版本 2 + 接受
- **WHEN** 用户点 tab 2 → `viewModel.selectVersion(position=2)` 被调,`state.value = Done(... selectedPosition=2)`
- **THEN** HeaderRow progress 不变;VersionTabs tab 2 加粗;中部重渲 v2 的 finalText + diff;底部接受按钮仍 enable

#### Scenario: Failed 版本 tab 显示 ✗ + 接受按钮 disabled
- **WHEN** state = `Done(versions=[Done, Failed, Done], selectedPosition=1)`
- **THEN** VersionTabs tab 1 显示 ✗ 角标(非 ✓);底部"接受此版本"按钮 enable=false(灰);点击 no-op

#### Scenario: PartialDone 态 1/3 完成可提前接受
- **WHEN** state = `PartialDone(versions=[Done, Streaming, Streaming], selectedPosition=0)`
- **THEN** HeaderRow 显示"扩写 · 已生成 1/3";VersionTabs tab 0 ✓ / tab 1,2 ⏳;底部"接受此版本"Button enable(选中 tab 是 Done);用户点接受 → 第 1 个版本的 finalText 替换正文,剩余 2 个版本继续后台跑(协程不强制取消,本组 generation 仍有效)

#### Scenario: 拒绝全部回到 Idle
- **WHEN** state = `Done(versions=[v0, v1, v2])`,用户点"拒绝全部"
- **THEN** `viewModel.reject()` 被调,`_state.value = Idle`,ModalBottomSheet 关闭;3 个版本的 ai_history 行保留(用户后续可在 AI 历史页查)

### Requirement: AiHistoryEntity tracks versionGroupId for cross-version queries

`AiHistoryEntity` MUST 新增可空字段 `versionGroupId: String?`;同一 sourceText + op
一次多版本生成的 N 行 MUST 共享同一非空 `versionGroupId`(UUID 格式);单版本
(M3 行为,`versionCount=1`)的 `versionGroupId` MUST 为 `null`,与既有数据兼容。

`AiHistoryEntity` MUST 新增联合索引 `@Index(value = ["noteId", "versionGroupId"])`
加速按 note + group 查询。

`AiHistoryDao` MUST 新增:
- `fun observeByVersionGroup(groupId: String): Flow<List<AiHistoryEntity>>` —
  按 groupId 升序返回所有同组版本行(典型场景:多版本接受某行后,UI 展示"另 2 个版本预览")
- `fun observeVersionGroupsByNote(noteId: String, op: String): Flow<List<AiHistoryEntity>>` —
  列某 note 上某 op 的所有版本组首行(AI 历史页聚合展示用,v1 UI 暂不渲染但 SQL 已落)

`Room AutoMigration(13, 14)` MUST 自动添加 `versionGroupId` 可空列 + 联合索引,
既有 v13 数据无损(旧行 `versionGroupId=null`,视为"单版本")。

#### Scenario: 3 个版本共享同一 groupId
- **WHEN** `start(EXPAND, "晨跑", "n1", versionCount=3)` 完成,3 次 `streamWritingOp` 各 emit Done
- **THEN** `ai_history` 表新增 3 行,3 行 `versionGroupId` 字段**完全相同**(同一 UUID),`noteId="n1"`,`op="expand"`;每行 `id` 独立 UUID

#### Scenario: 单版本 v1 兼容(groupId=null)
- **WHEN** UI 调用 `viewModel.start(EXPAND, ..., versionCount=1)`(M3 行为)
- **THEN** ai_history 新增 1 行,`versionGroupId=null`(与既有 v13 单版本数据 schema 一致)

#### Scenario: observeByVersionGroup 取出整组
- **WHEN** ai_history 表有 groupId="g-abc" 的 3 行,UI collect `dao.observeByVersionGroup("g-abc")`
- **THEN** Flow emit List 大小为 3,按 `createdAt ASC` 排序(第 1 个版本最早)

#### Scenario: observeVersionGroupsByNote 取每组首行
- **WHEN** noteId="n1" + op="expand" 在 ai_history 有 2 组(groupA 3 行、groupB 2 行),共 5 行
- **THEN** `observeVersionGroupsByNote("n1", "expand")` emit List 大小为 2(每组最早一行,按 `createdAt DESC` 排序)

#### Scenario: Room v13 → v14 AutoMigration 无损升级
- **WHEN** 既有用户从 v13 升到 v14 安装新版 APK
- **THEN** `ai_history.versionGroupId` 列被添加(默认 NULL);既有行的 `versionGroupId=null`;
  Room AutoMigration(13,14) 自动完成,无数据丢失

### Requirement: CoreAiGateway passes versionGroupId through to history record

`CoreAiGateway.streamWritingOp(...)` MUST 新增 2 个可空形参:
- `versionGroupId: String? = null`(默认 null,向后兼容 M3 调用方)
- `versionPosition: Int? = null`(默认 null,0..N-1,落库 traceability)

调用方(`AiActionViewModel`)在第 N 次调用 `streamWritingOp` 时把 `versionGroupId`
(同组共享 UUID)+ `versionPosition`(0/1/2)传入;`AiHistoryRepository.record()` MUST
把这些字段写入对应 `AiHistoryEntity` 行;旧 caller(单版本调用,M3 既有路径)不传,默认
`versionGroupId=null`,行为不变。

#### Scenario: 多版本落库带 groupId
- **WHEN** VM 第 2 次调 `streamWritingOp(..., versionGroupId="g-abc", versionPosition=1)`
- **THEN** `AiHistoryRepository.record(...)` 写入 ai_history 表的 1 行,`versionGroupId="g-abc"`,该行其它字段同 M3 行为(providerId/model/inputTokens/outputTokens/...)

#### Scenario: 单版本 caller 不传 groupId
- **WHEN** 旧 caller(若有)调 `streamWritingOp(..., versionGroupId=null, versionPosition=null)`(默认值)
- **THEN** ai_history 表的对应行 `versionGroupId=null`,`versionPosition=null`,与 v13 schema 行为一致

### Requirement: i18n coverage for version tabs

所有多版本相关 UI 文案 MUST 出现在 `values/strings.xml`(中文权威)+ `values-en/strings.xml`(TODO 占位),
命名空间 `aiwriting_version_*`,至少 8 个 key:

| key | 中文 | 用途 |
| --- | --- | --- |
| `aiwriting_version_progress_fmt` | `%1$d/%2$d` | "1/3" 进度副标题 |
| `aiwriting_version_tab_label_fmt` | `版本 %1$d` | Tab 标题 |
| `aiwriting_version_tab_done` | `✓` | Tab 角标(Done) |
| `aiwriting_version_tab_failed` | `✗` | Tab 角标(Failed) |
| `aiwriting_version_tab_streaming` | `⏳` | Tab 角标(Streaming) |
| `aiwriting_version_all_failed_fmt` | `全部 %1$d 个版本生成失败` | Failed 态文案 |
| `aiwriting_version_accept_this` | `接受此版本` | 接受按钮 |
| `aiwriting_version_reject_all` | `拒绝全部` | 拒绝按钮 |

Composable 内 MUST 通过 `stringResource(R.string.aiwriting_version_*)` 引用;**禁止**硬编码中文 / 英文。

#### Scenario: 中文文案来自 R.string
- **WHEN** 系统语言为中文,`StreamingPanel` 渲染 VersionTabs tab 0
- **THEN** tab 标题 `Text("版本 1")`,走 `stringResource(R.string.aiwriting_version_tab_label_fmt, 1)`,源码 grep 不到中文字面量

#### Scenario: 英文 TODO 占位不阻断构建
- **WHEN** 系统语言为英文,`values-en/strings.xml` 中 `aiwriting_version_tab_label_fmt = "TODO(en): aiwriting_version_tab_label_fmt"`
- **THEN** APK 仍能正常构建并启动,运行时显示占位文本;`./gradlew :app:assembleDebug` 与 `:app:check` 全部通过

#### Scenario: Failed 态文案带版本数
- **WHEN** state = `Failed(op, AiError.Unknown(detail="全部 3 个版本生成失败"))`,versionCount=3

