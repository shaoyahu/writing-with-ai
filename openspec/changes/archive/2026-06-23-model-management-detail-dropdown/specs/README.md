# Specs

本 change 不引入新 capability，也不修改任何现有 capability 的 requirement。

依据:`proposal.md` §Capabilities 明确列出 **New Capabilities: 无 / Modified Capabilities: 无**。理由:

- `ai-actions` spec 中"ProviderPrefsStore.getSelectedProviderId + apikey 透传 AiGateway"的语义不变;新增 `selectedModel` 字段仅替换 `modelName=null` 占位的来源，**不引入新 requirement**。
- `secure-prefs` spec 中 `SecureApiKeyStore` 接口未变。
- `ai-gateway` spec 中 `AiProvider.stream` / `ProviderConfig` 数据结构未变。

按 `openspec/specs/<capability>/spec.md` 目录约定，**本目录无 spec 文件**。`/opsx:sync` 阶段确认无 spec 增量 / 改动后，本目录归档时随之移除。