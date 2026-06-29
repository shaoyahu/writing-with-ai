## Context

现状:`ProviderPrefsStore` 已有 per-provider `selected_model_<providerId>` key,但写入路径有 3 个并存入口,语义不一致:

1. `saveProvider` — 同步 viewModelScope.launch,**正确**(model 写失败静默但与 apikey 写共享事务语义)
2. `onModelSelected` — fire-and-forget viewModelScope.launch,**不可靠**(返回即 cancel,UI 撒谎)
3. `CoreAiGateway.streamWritingOp` fallback — 静默用 `supportedModels.firstOrNull()`,跟 list 顺序强绑定

`AiActionViewModel.start()` 调 `providerPrefsStore.getSelectedModel(providerId)`,值为 null → gateway 兜底,用户无感。模型管理卡片订阅 `observeSelectedModel`,即便 DataStore 写没落盘,UI 也不报警(只依赖本地 `currentModel` state)。

约束:
- 零新依赖(纯 Kotlin + DataStore + Flow)
- 不动 `AiGateway` 接口签名(纯实现层)
- 不动 `providerPrefsDataStore` schema
- 不动 provider config 文件
- 改动跨 4 模块:core/ai、settings/model VM+UI、aiwriting/streaming VM、ProviderDescriptor

## Goals / Non-Goals

**Goals:**
- 用户在「设置 → AI 模型管理」选的 model,真机 provider 后台必看到的就是这个 model
- 写失败时 UI 显错(Snackbar),不静默
- 模型管理卡片显示「实际将调用 model」(无歧义)
- 现有用户无需手动重配 apikey,启动时自动补齐缺失的 `selected_model_<id>`(→ `defaultModel`)
- 改 4 个文件,新增 1 个能力 spec,改 1 个现有 spec

**Non-Goals:**
- 不引入多 model 选 1 调度(round-robin / 负载均衡)
- 不改 provider config 字段(3 家 defaultModel 不动)
- 不做 DataStore schema 迁移(用 lazy init on read)
- 不动 `ai-history` 表 schema(只是把 actualModel 透传到现有字段)
- 不改 apikey 加密存储路径(`SecureApiKeyStore` 完整不动)
- 不修「下拉选完 model 后立即返回」的取消竞态(用 suspend + UI 反馈替代,语义更清晰)

## Decisions

### D1. gateway fallback 单一来源: `provider.defaultModel`

`CoreAiGateway.streamWritingOp` 当 `modelName == null` 时,fallback 从 `provider.supportedModels.firstOrNull()` 改成 `provider.defaultModel`。理由:
- `defaultModel` 是 provider 显式声明的「无用户偏好时用这个」,有业务语义
- list[0] 是开发便利副作用,deepseek 的 flash 在前完全因为按「lite → 贵」排
- 单一来源(gateway 是唯一兜底点),VM/UI 都从同一条规则算「实际将调用」

替代方案:VM 端兜底,gateway 拿到 null 直接 fail
- 缺点:VM 重复 fallback 逻辑,跟 gateway 不一致会出诡异 bug
- 不采纳

### D2. 首次自举时机: `saveProvider` 同步 + 启动 lazy init 兜底

`ModelManagementViewModel.saveProvider` 成功落 apikey 后,**同步**调 `providerPrefsStore.setSelectedModelIfMissing(providerId, getProviderConfig(providerId).defaultModel)`。语义:「apikey 已落 → 必须有 selectedModel」是 save 流程的不变式。

兜底:`init` 块启动时扫一遍,凡是有 apikey 但 `selectedModel_<id> == null` 的 provider,补写 `defaultModel`。处理存量用户(改 change 前已设 apikey 但没选过 model 的)。

`ProviderPrefsStore.setSelectedModelIfMissing(providerId, default)` 是新加的 atomic 方法:`DataStore.edit { if (selectedModelKey not present) put(it, default) }`。单 key 写入不需要事务,DataStore.edit 本身就是原子的。

替代方案:在 `AiActionViewModel` 调用时 lazy init
- 缺点:每次首次调用都走一次写,加大 hot path;且需要先知道 provider 的 defaultModel(VM 没这个)
- 不采纳

### D3. `onModelSelected` 事务化: suspend + SharedFlow 事件反馈

`onModelSelected` 从 `fun (...) → Unit` fire-and-forget 改成 `suspend fun (...)` await 写盘,**失败 throw**。VM 内部 `viewModelScope.launch` 调它,`try/catch (e: CancellationException) throw / catch (e: Exception) { _saveEvents.tryEmit(Failed(...)) }`。

调用方 `ModelProviderDetailScreen` 收 `_saveEvents` 事件流,失败时弹 Snackbar(`SnackbarHostState.showSnackbar("模型切换失败,请重试")`)。

为什么不直接 `Result<Unit>` 返回:`Result` 不能跨 launch 边界(必须 await),snackbar 收事件流更自然,且跟 `saveProvider` 复用 `_saveEvents` 事件流,UI 只用 `collect` 一处。

替代方案:UI 用 `rememberCoroutineScope().launch { vm.onModelSelected(...) }` 等结果
- 缺点:UI 拿不到 `Result`(suspend 抛异常而非返回),UI 必须 try/catch
- 选 SharedFlow 事件流,UI 跟 save 路径复用同一条事件链

### D4. 「实际将调用」算法: `selectedModel ?: defaultModel`

模型管理卡片 + 详情页 + AI 操作前断言,统一这条算法:
```kotlin
fun resolveActualModel(providerId: String, selectedModel: String?, defaultModel: String): String =
    selectedModel?.takeIf { it.isNotBlank() } ?: defaultModel
```

`ProviderDescriptor` 加 `defaultModel: String` 字段;`providerDescriptors()` 把 `ProviderConfig.defaultModel` 映射进去。`ProviderInfoCard` 在「已选 X」/「X 个模型」之外新增一行小字「实际调用: Y」,Y 走上面这个函数。

替代方案:在 UI 直接读 `descriptor.supportedModels.firstOrNull()`
- 缺点:回到原 bug 根因(list order 副作用)
- 不采纳

### D5. `AiActionViewModel` 透传 actualModel

`AiActionViewModel.Streaming(op)` 状态加 `actualModel: String` 字段,值是 `resolveActualModel(...)` 算出来的;`ai-history` 落表时用 `actualModel`(替换原 `modelName` 字段)。这样历史记录能查「这次实际用了哪个 model」,排查「用户说选了 X 但后端说调 Y」时直接看 history 即可。

替代方案:不动 history,只 UI 显示
- 缺点:bug 复现时还是要去 logcat 抓 model 字段
- 不采纳(history 是本地 SQLite 落表,加字段成本低)

## Risks / Trade-offs

- **[Risk] 启动 lazy init 大量 provider 时阻塞 init** → 每个 provider 一次 `DataStore.edit`,1 个 provider <10ms;内置 3 + 用户自定义 N(一般 <5),启动 < 50ms,可接受。如果将来加 batch api,再优化
- **[Risk] `setSelectedModelIfMissing` race condition: 两个并发 caller 同时写** → DataStore 串行化 `edit` 块,两个 caller 会 serialize;`takeIf absent` 检查在 edit 块内,后到的看到 key 已存在就跳过。安全
- **[Risk] 旧用户数据迁移:已有 apikey 没 selectedModel 的用户,启动时 init 块触发 setSelectedModelIfMissing → 写盘失败怎么办** → `try/catch` 包住,失败 Log.e 不阻塞启动;用户首次 AI 调用时 `getSelectedModel == null` 还能走 gateway fallback(`provider.defaultModel`),不破功能
- **[Trade-off] `_saveEvents` 同时承载 save + model 切换事件,UI 需区分** → 加 `SaveResult` sealed class 区分 operation kind(`OperationKind.SAVE` / `OperationKind.MODEL_SELECT`),UI 按 kind 选文案。复杂度 +1
- **[Risk] `ProviderDescriptor.defaultModel` 新字段破坏现有 call site** → grep 全部 call site,补默认值或显式传;用 IDE refactor 一次性改完
- **[Risk] `CoreAiGateway` fallback 改语义后,真机某 provider 没有 `defaultModel` 字段(老 schema)→ NPE** → 3 家 preset config 都有 `defaultModel`,`FakeAiProvider` 也有;自定义 provider 走 `CustomProviderEditScreen` 强约束;`getById` 返回 nullable,VM 已经在 `getProviderConfig()` 处 null-safe

## Migration Plan

- **存量用户**:启动 `init` 块触发 lazy init,把已有 apikey 但没 selectedModel 的 provider 补齐 defaultModel。无感,不弹任何 UI
- **回滚**:3 个改动都收口在 VM + gateway,改回原 fallback `firstOrNull()` 即可;`ProviderDescriptor.defaultModel` 字段保留(只 mark deprecated);数据无需回滚(DataStore 多 1 个 key 不影响)
- **真机验证步骤**:
  1. 选 deepseek pro → 真机后台确认 `model=deepseek-v4-pro`
  2. 选 mimo pro → 反验
  3. 卸载重装(deepseek apikey 没了,重输)→ 启动后 init 块补 defaultModel,选 pro 前调一次,后台应是 defaultModel 而非 list[0]
  4. 下拉选 pro 后**立即**返回 → 卡片仍显示「已选 pro」(本地 state),再次进详情页 → 「已选 pro」仍在(说明写盘成功),UI 不报错(SaveResult.Success 静默)
  5. 模拟写盘失败(Mock ProviderPrefsStore 抛 IOException)→ 详情页 Snackbar「模型切换失败,请重试」

## Open Questions

- 启动 lazy init 失败 Log.e 后,**要不要**给用户 Snackbar 提示「部分 provider 模型已重置为默认」?倾向**不要**(后台 Log 即可,用户没感知 bug 就不要打扰);但 user-visible 的「silent recovery」是另一种产品哲学,等你拍
- ai-history 表 `actualModel` 字段,要不要顺便把"原本用户选的 model"也存(`requestedModel` vs `actualModel`)?便于排查「用户 X 选了 A 实际调 B」类工单。倾向**要**(成本 = 1 个 column),但需你确认 history schema 改动的接受度
- `ProviderDescriptor.defaultModel` 是不是要 expose 给 `ProviderInfoCard`?卡片文案「实际调用: Y」需要这个字段。如果不加,卡片只能在「selectedModel 为 null」时显示「将使用默认」无具体值,体感差。倾向**加**(`providerDescriptors()` 函数补一行 mapping,无侵入)
