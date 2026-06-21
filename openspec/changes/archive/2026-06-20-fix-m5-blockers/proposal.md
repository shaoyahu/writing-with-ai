## Why

`main` 当前不能编、不能 lint 过、不能跑测试 — M5 polish 阶段落地的 `widget-1x4-compact` 和 `provider-real-integration` 两个 change 标注"完成"但实际 AC 未达成,把仓库锁在 broken state。本 change 一次性修 2 CRITICAL + 2 HIGH,恢复 main green 状态。

## What Changes

- **修复 C1**:`core/widget/QuickNote1x4Widget.kt` 引用不存在的 Glance API(`RoundedCornerRadius` / `fillMaxHeight` / `background()` 第二参),改用当前 Glance 版本支持的 `Modifier.cornerRadius(...)` 链式 modifier + `defaultWeight()` + 固定 `height(...)` 替代。`./gradlew :app:assembleDebug` 必须过。
- **修复 C2**:`core/ai/CoreAiGateway.kt` 硬编码 `apikey = "fake-apikey"` 改掉 — `AiGateway.streamWritingOp` / `ping` 加 `apikey: String` 参数(由 caller 从 `SecureApiKeyStore.get(providerId)` 取),gateway 内部用传入 apikey 构造 `AiCredentials`;`AiActionViewModel.start()` 同步取 apikey,`null` → `ProviderNotConfigured`,非 null 传入。加 MockWebServer 端到端测试,断言 `Authorization: Bearer <真 apikey>` 落到 HTTP header。
- **修复 H2**:跑 `./gradlew :app:ktlintFormat` 自动修大部分 + 手工处理 `Type.kt` 整文件 indent + `AiActionViewModelTest.kt` 多参数行(`argument-list-wrapping` 60 处)。目标:`./gradlew :app:ktlintCheck` 0 violation。
- **修复 H3**:删 `config/ktlint/.editorconfig` 和项目根 `.editorconfig` 里 obsolete 的 `ktlint_disabled_rules` property,改 ktlint 1.0 per-rule 写法或集中到 `app/build.gradle.kts` 的 `ktlint {}` 块。

## Capabilities

### New Capabilities

无。本 change 是修复,无新 capability。

### Modified Capabilities

- **`ai-gateway`**:`CoreAiGateway` 透传真 apikey,`AiGateway` 接口增 `apikey: String` 参数。`(BREAKING)` — `AiGateway` 接口签名变更,所有 caller 必须更新。
- **`ai-actions`**:`AiActionViewModel.start()` 调度时同步取 `SecureApiKeyStore.get(providerId)`,判 `null` → `ProviderNotConfigured` Failed,非 null 透传给 `AiGateway.streamWritingOp`。
- **`home-screen-widget`**:`QuickNote1x4Widget.kt` 改用当前 Glance 版本支持的 API 写法,build 复绿。
- **`android-build-system`**:`config/ktlint/.editorconfig` 清 obsolete property,`app/build.gradle.kts` 的 `ktlint {}` 块集中管 disabled rules;M5 落地后 `Type.kt` 等多文件 ktlint 违规统一 format。

## Impact

- **代码**:`core/widget/QuickNote1x4Widget.kt`、`core/ai/CoreAiGateway.kt`、`core/ai/api/AiGateway.kt`、`core/ai/provider/AnthropicCompatibleAdapter.kt`、`feature/aiwriting/streaming/AiActionViewModel.kt`、`feature/settings/model/ModelManagementViewModel.kt`(ping 调用改签名)、`config/ktlint/.editorconfig`、根 `.editorconfig`、所有 `app/src/main` + `app/src/test` 下 ktlint 违规文件。
- **API**:`AiGateway.streamWritingOp(...)` 加 `apikey: String` 必填参数;`AiGateway.ping(...)` 同样加 `apikey: String` 必填参数。所有 `AiGateway` 实现 / stub / fake / 测试调用点需更新。
- **依赖**:无新增。
- **测试**:新增 `AnthropicCompatibleAdapterIntegrationTest` / `CoreAiGatewayApikeyTest`(MockWebServer 验 header);更新 `AiActionViewModelTest` / `AiActionViewModelConsentTest` 适配新签名。
- **不涉及**:`release.isMinifyEnabled` 留 `release-readiness` change 处理;Widget color token 重复(r1 M1)在 L5 已隐式修;PromptTemplate / 数据导入导出 / onboarding / quick-note 业务逻辑不在本 change 范围。
