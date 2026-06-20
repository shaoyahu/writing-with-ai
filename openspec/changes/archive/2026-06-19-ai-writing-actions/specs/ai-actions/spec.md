# ai-actions

## Purpose

把 M2 落地的 AI 抽象层(`AiGateway` / `FakeProvider` / `AnthropicCompatibleAdapter`)接进 UI 闭环:详情屏选中文本 → 弹 ActionSheet(扩写/润色/整理 + 复制)→ 流式面板(`ModalBottomSheet`)实时渲染 AI 返回 → 用户接受/拒绝/再生成/取消。错误降级保证不白屏,i18n 完整。`providerId` 写死 `"fake"`(真 provider 切换推迟 M5 onboarding-consent)。本 capability 是 v1 价值最核心的 feature,首次让用户感知"AI 助手"。

TBD — synced from OpenSpec change `ai-writing-actions`(2026-06-19)。

## ADDED Requirements

### Requirement: ActionSheet shows available AI operations on selection

详情屏 MUST 在用户**选中文本非空**时,通过详情页 FAB(`Icons.Filled.AutoAwesome`)唤起一个 `DropdownMenu`(锚定到 FAB),提供 4 个菜单项:

| 菜单项 | 文案 key | 触发行为 |
| --- | --- | --- |
| 扩写 | `aiwriting_action_expand` | `AiActionViewModel.start(op=EXPAND, sourceText, noteId)` |
| 润色 | `aiwriting_action_polish` | `AiActionViewModel.start(op=POLISH, sourceText, noteId)` |
| 整理 | `aiwriting_action_organize` | `AiActionViewModel.start(op=ORGANIZE, sourceText, noteId)` |
| 复制 | `aiwriting_action_copy` | `ClipboardManager.setPrimaryClip(sourceText)`(非 AI 操作,不进 AiGateway) |

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

`AiActionViewModel : ViewModel` MUST 注入 `AiGateway` + `NoteRepository`,暴露 `StateFlow<AiActionUiState>` 给 UI 订阅;状态机用 sealed interface 表达:

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

`providerId` 在 M3 MUST 写死为 `"fake"`(常量 `AiActionViewModel.PROVIDER_ID_FAKE = "fake"`);M5 onboarding-consent 上线后改为读 `SettingsRepository`。

#### Scenario: start() 进入 Streaming
- **WHEN** UI 调用 `viewModel.start(EXPAND, sourceText="晨跑", noteId="n1")`
- **THEN** `AiGateway.streamWritingOp(EXPAND, "晨跑", "fake", modelName=null)` 被订阅;首个 event `Started` 到达时 `AiActionUiState` 转为 `Streaming(op=EXPAND, partialText="")`

#### Scenario: Delta 累加 partialText
- **WHEN** AiGateway emit `Delta("你")` → `Delta("好")`
- **THEN** `AiActionUiState.Streaming.partialText` 依次更新为 `"你"` → `"你好"`(UI 应实时看到累加)

#### Scenario: Done 携带 usage
- **WHEN** AiGateway emit `Usage(inputTokens=2, outputTokens=3, totalTokens=5)` → `Done`
- **THEN** `AiActionUiState` 转为 `Done(op, finalText=<累积 partialText>, usage=<Usage 对象>)`

#### Scenario: Failed 携带 AiError
- **WHEN** AiGateway emit `Failed(AiError.Network(code=500, detail="timeout"), recoverable=true)`
- **THEN** `AiActionUiState` 转为 `Failed(op, error=Network(500, "timeout"))`

### Requirement: StreamingPanel renders state-aware UI inside ModalBottomSheet

`StreamingPanel` MUST 是 `ModalBottomSheet` 内容,根据 `AiActionUiState` 渲染不同 UI:

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
- **THEN** UI 显示顶部"出错"、中部"网络连接失败,请检查后重试"(取自 R.string.aiwriting_error_network)、底部仅"关闭"按钮

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

`acceptReplace()` MUST 用 `withContext(NonCancellable)` 包裹"读 + upsert + updateAiMetadata"三步;若 ViewModel 在 acceptReplace 期间被 cleared(进程死 / 系统回收),事务仍能完成(写到 Room 即落盘)。

#### Scenario: 接受替换正文并写 lastAiOp
- **WHEN** state = `Done(op=POLISH, finalText="优化后", usage=...)`,用户点"接受"
- **THEN** 笔记 `content` 从原值变为 "优化后",`updatedAt=<now>`;`Note.lastAiOp="polish"`,`Note.lastAiAt=<now>`;AiHistory 已自动落库(M2);UI 详情页通过 Flow 自动刷新显示新正文

#### Scenario: 拒绝不替换
- **WHEN** state = `Done(op=EXPAND, finalText="扩写后", usage=...)`,用户点"拒绝"
- **THEN** 笔记 `content` 保持原值;`lastAiOp` / `lastAiAt` 不变;AiHistory 仍自动落库(成功调用)

#### Scenario: 取消中止流
- **WHEN** state = `Streaming(op=ORGANIZE, partialText="<部分>")`,用户点"取消"
- **THEN** 笔记 `content` 保持原值;AiHistory 落库(error=<cancellation reason> 或 success 但 output 截断)

#### Scenario: 再生成用同 op 重跑
- **WHEN** state = `Done(op=EXPAND, finalText="第1次", usage=...)`,用户点"再生成"
- **THEN** ViewModel 复用上次参数调 `start(EXPAND, sourceText=<上次 selectedText>, noteId=<上次 noteId>)`;AiActionUiState 重新进入 Streaming 态;原 `finalText` 被丢弃

#### Scenario: acceptReplace 事务不被中断
- **WHEN** `acceptReplace()` 正在 `withContext(NonCancellable)` 中执行,用户按 back 关闭 ModalBottomSheet,触发 ViewModel.clear()
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

`StreamingPanel` 在 `Failed` 态 MUST 显示该文案 + "关闭"按钮(点击关闭 sheet,回 Idle);**禁止**弹 Dialog / Snackbar / 任何额外模态(简化 UX)。

#### Scenario: 网络错误显示 fallback 文案
- **WHEN** FakeProvider 注入 `errorAfterTokens=1`(M2 已支持,模拟中断)
- **THEN** UI 进入 `Failed(op, Network)`;中部显示 `R.string.aiwriting_error_network` 对应中文"网络连接失败,请检查后重试";底部"关闭"按钮可点

#### Scenario: 余额不足文案
- **WHEN** AiGateway emit `Failed(InsufficientBalance(code=402, detail="..."))`
- **THEN** UI 进入 `Failed(op, InsufficientBalance)`;中部显示 `R.string.aiwriting_error_balance` 对应中文"账户余额不足"

#### Scenario: 关闭 Failed 面板回到 Idle
- **WHEN** 用户在 Failed 态点"关闭"
- **THEN** ModalBottomSheet 关闭;AiActionUiState 回到 Idle;下次 FAB 点击可重新进入流程

### Requirement: i18n for AI writing UI

所有 `ai-actions` 相关 UI 文案 MUST 出现在 `values/strings.xml`(中文,权威)与 `values-en/strings.xml`(英文 TODO 占位),命名空间为 `aiwriting_*`,19 个 key 集合:

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
| `aiwriting_error_network` | 网络连接失败,请检查后重试 | Network |
| `aiwriting_error_auth` | 认证失败,请检查 API key | Auth |
| `aiwriting_error_balance` | 账户余额不足 | InsufficientBalance |
| `aiwriting_error_timeout` | 请求超时,请稍后重试 | Timeout |
| `aiwriting_error_unknown` | 出错了,请稍后重试 | 兜底 |

Composable 内 MUST 通过 `stringResource(R.string.aiwriting_xxx)` 引用;**禁止**硬编码中文 / 英文。

#### Scenario: 系统语言为英文时显示 TODO 占位
- **WHEN** 系统语言为英文,`values-en/strings.xml` 中 `aiwriting_action_expand="TODO(en): aiwriting_action_expand"`
- **THEN** ActionSheet 显示 `TODO(en): aiwriting_action_expand`(可读 + 不阻断构建);M5 polish 时替换为正式英文

#### Scenario: 中文文案来自 R.string
- **WHEN** 系统语言为中文,UI 渲染 ActionSheet 项"扩写"
- **THEN** 该文本通过 `stringResource(R.string.aiwriting_action_expand)` 取值,源码 grep 不到中文字面量

### Requirement: ai-actions does not introduce direct network calls

`feature/aiwriting/**` 包内 MUST **不** 直接 import `okhttp3.*` / `java.net.*` / `retrofit2.*` / `kotlinx.serialization.*`(JSON 走 AiGateway 内部);所有 AI 调用 MUST 经过 `AiGateway.streamWritingOp(...)`。`ClipboardManager` 例外(系统 API,非网络)。

#### Scenario: ViewModel 不持有 OkHttp
- **WHEN** grep `feature/aiwriting/**/*.kt`
- **THEN** 0 个匹配 `okhttp3` / `retrofit2` / `HttpURLConnection` 的 import 行

#### Scenario: AI 调用走 AiGateway
- **WHEN** `AiActionViewModel.start(EXPAND, ...)` 执行
- **THEN** 内部唯一调用是 `aiGateway.streamWritingOp(EXPAND, sourceText, PROVIDER_ID_FAKE, null)`

### Requirement: package layout follows feature self-containment

`feature/aiwriting/` MUST 自包含,跨 feature 引用(若有)走 `feature/aiwriting/AiwritingEntry.kt` object 暴露,不允许 `feature/quicknote/**` 直接 import `feature/aiwriting/**` 内部文件(除了 `Entry`)。`feature/aiwriting/` 内子目录:

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