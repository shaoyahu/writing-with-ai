## Context

`feature/settings/model/ModelProviderDetailScreen.kt` 当前是 `fix-ai-config-ux` change 的产物(line 62 注释)，职责:展示单个 provider 的 baseURL / 默认模型 + 录入 apikey + 区分"新配置 vs 覆盖"。但它只读 `ProviderConfig.baseUrl` / `defaultModel` 渲染成静态 `Text(...)`，既不能切协议也不能切模型。

`ModelManagementViewModel.kt` 已具备 `getProviderConfig(providerId): ProviderConfig?`(line 102)，可拿到 `apiFormat` / `supportedModels`，但 UI 没消费。

`ProviderPrefsStore`(`core/ai/provider/ProviderPrefsStore.kt`)目前只持久化 `selected_provider_id` 一个 key(line 55)，无 per-provider 模型选择能力。

约束:
- 预置三家 provider 协议由 `ProviderConfig.apiFormat` 写死(roadmap §6.3)，不允许 UI 覆盖
- `AiActionViewModel.start(...)` 当前传 `modelName = null`(ai-actions spec Scenario "start() 同步取 apikey 透传 gateway")，本 change 不应回退此行为，**只新增** "如果 prefs 有 selectedModel 就传它，否则仍 null"
- 自定义 provider 流程走 `CustomProviderEditScreen`，不受影响

## Goals / Non-Goals

**Goals:**
- 详情页把"协议类型 + 选择模型"暴露为下拉框，让内测用户能在不重配 apikey 的前提下切换协议/模型
- baseURL 视觉弱化(disabled 样式)，保留可见性以便排错
- 切换 provider 时已选模型自动重置为该 provider 默认模型
- 改动控制在 `feature/settings/model/` + `core/ai/provider/ProviderPrefsStore.kt`，无 adapter / config / spec 变更

**Non-Goals:**
- 不改 `AnthropicCompatibleAdapter` 协议分发逻辑
- 不改 `ProviderConfig` 数据结构
- 不改 `SecureApiKeyStore` 接口
- 不为预置 provider 加 apiFormat 持久化字段(协议由 config 写死)
- 不动 `CustomProviderEditScreen`(自定义 provider 流程独立)
- 不引入新依赖

## Decisions

### D1 · 兼容类型下拉 readOnly(provider 协议锁定)

- **选择**:`ApiFormatDropdown(readOnly = true)`，值由 `config.apiFormat` 决定，UI 不可改
- **替代方案**:完全不加下拉框，只显示文本。**否决**:用户需要"看到协议"作为反馈，但没有切换需求
- **理由**:roadmap §6.3 已锁定协议;readOnly 比"不显示"更符合 M5 polish 的"信息透明"原则，且不影响后续 v2+ 若放开自定义 provider 协议切换(那时改 readOnly 参数即可)

### D2 · baseURL 改为 disabled 样式而非删除

- **选择**:line 161-166 当前 `Text(stringResource(model_provider_detail_base_url))` + `Text(baseUrl)`，改为 `Text(..., color = MaterialTheme.colorScheme.onSurfaceVariant)` + 不允许复制/长按选择
- **替代方案**:
  - 完全删除行 — **否决**:内测反馈希望"看到 provider 在用哪个端点"以便排错
  - 折叠到"高级"区 — **否决**:多一层点击不划算，且 baseURL 不算高级配置
- **理由**:弱化 + 可见 兼顾"防止误改"和"信息透明"

### D3 · 模型下拉默认值带「(默认)」后缀

- **选择**:`ModelDropdown` 渲染每个选项时，若 `model == config.defaultModel`，文案加 ` ${stringResource(R.string.model_provider_detail_model_default_suffix)}`
- **替代方案**:放第一项，无后缀。**否决**:用户看不出哪个是默认，反而不友好
- **理由**:visual hint 比 ordinal hint 更可靠(后续若 re-order，默认项不会跑掉)

### D4 · 切换 provider 时已选模型自动重置

- **选择**:`ModelManagementViewModel.selectProvider(providerId)` 调用时，若 `providerId != 当前 selectedProviderId`，清除 `setSelectedModel(providerId = old)`(不是 `setSelectedModel(model = null)` — 这里"切换 provider"应理解为"新 provider 的 selectedModel 重置为它的 default"，而不是"清掉旧 provider 的偏好")
- **更精确**:`selectProvider(newId)` → `providerPrefsStore.setSelectedModel(oldId, /* 清掉旧 provider 的选择 */)` + 写新 provider 时由 UI 进入 detail 屏的回填逻辑自动取 `defaultModel`(因为新 provider 没 prefs 记录)
- **替代方案**:保留跨 provider 选择。**否决**:模型名跨 provider 不互通，保留会造成旧 model 名在新 provider 的 supportedModels 里找不到 → UI 状态错乱

### D5 · selectedModel 持久化走 ProviderPrefsStore 新方法，不动 SecureApiKeyStore

- **选择**:`ProviderPrefsStore` interface 加 `getSelectedModel(providerId): String?` + `setSelectedModel(providerId, model)` + `observeSelectedModel(providerId): Flow<String?>`,DataStore 新 key `stringPreferencesKey("selected_model_<providerId>")`
- **理由**:与现有 `selected_provider_id` 同一 DataStore 文件，避免 DataStore 数量膨胀

### D6 · VM getProviderConfig 返回类型扩展(不引入新 data class)

- **选择**:保持返回 `ProviderConfig?`,**不**增加 `currentSelectedModel` 字段。在 `ModelProviderDetailScreen` 内独立读 `providerPrefsStore.getSelectedModel(providerId)` 回填
- **替代方案**:改 `getProviderConfig` 返回 `ProviderConfigWithSelectedModel` data class。**否决**:影响范围大，且 `providerDescriptors()` / `ping()` / `deleteCustomProvider()` 都不需要此字段
- **理由**:最小侵入;UI 局部 state 处理回填

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| `setSelectedModel(oldId, null)` 与"切换 provider"语义混淆 | D4 已明确:切换时只清旧 provider 的 selectedModel，新 provider 留到 detail 屏回填 |
| DataStore 写失败(IO 错误)未捕获 | 复用 `selectProvider` 已有的 try-catch 模式(`catch (_: Exception) {}` 静默 + 保留旧值) |
| 弱化的 baseURL 在深色模式对比度不足 | 用 `onSurfaceVariant` MaterialTheme token，自动适配 dark/light |
| ModelDropdown 选项多( minmax 有 8 个)撑爆屏 | `ExposedDropdownMenuBox` 自带 scroll，无需手动限制 |
| 三家预置 provider 的协议 + 模型组合均通过 provider-real-integration 联调验证过 | 无新风险 |

## Migration Plan

无 schema 迁移。DataStore 增 key 是向后兼容的 — 老用户首次进入 detail 页会回退 `defaultModel`，无数据丢失。

## Open Questions

- **Q**:切换 provider 时，旧 provider 的 selectedModel 是删还是留?
  - **倾向**:留(v2+ 用户切回时还在，避免"选了又被清");本 change 暂不动旧 selectedModel，只确保新 provider 首次进入 detail 屏回填 default

  - **决策**:不动旧值(v2+ 验证);实现层 `selectProvider` 不清旧 prefs。

- **Q**:下拉框变化是否需要"保存"按钮?
  - **倾向**:不需要(roadmap §3.1 "prompt 自动保存"是 prompt template 的策略;model 选择是高频切换，每次保存会打断流)
  - **决策**:立即写 prefs，无保存按钮;`saveProvider` 仅管 apikey