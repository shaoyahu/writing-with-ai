# Tasks: fix-m5-blockers

## 1. 验证 Glance 版本(D1 行动步骤)

- [x] 1.1 Glance 1.1.1 实有 `androidx.glance.appwidget.CornerRadiusKt.cornerRadius(radius: Dimension)`,无 `RoundedCornerRadius` / `fillMaxHeight` 类
- [x] 1.2 `gradle/libs.versions.toml` `glance = "1.1.1"` ≥ 1.1,走标准 modifier 链

## 2. C1 修 QuickNote1x4Widget

- [x] 2.1 import — `RoundedCornerRadius` → `androidx.glance.appwidget.cornerRadius` + `androidx.glance.layout.height`
- [x] 2.2 Row 背景 — `background(cp(cWhite))` + `.cornerRadius(16.dp)` 链式
- [x] 2.3 按钮高度 — `.defaultWeight().height(48.dp)`(`fillMaxHeight` Glance 1.1.1 无)
- [x] 2.4 按钮背景 — `background(cp(cBlue))` + `.cornerRadius(16.dp)`(Glance 1.1.1 `cornerRadius` 单一 `radius: Dimension` 重载,无 per-corner;统一 16dp 接受视觉降级)
- [x] 2.5 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL ✓

## 3. C2 改 AiGateway 接口签名(BREAKING)

- [x] 3.1 改 `core/ai/api/AiGateway.kt` — `streamWritingOp(...)` 增 `apikey: String` 必填参数
- [x] 3.2 改 `core/ai/api/AiGateway.kt` — `ping(...)` 增 `apikey: String` 必填参数
- [x] 3.3 grep `streamWritingOp`/`ping` caller 清单:`AiActionViewModel.kt:121` + `ModelManagementViewModel.kt:69` + `AiActionViewModelTest.kt`(6 处 mock)+ `AiActionViewModelConsentTest.kt`(2 stub class);`FakeAiProviderTest.kt` 不调 gateway,不动

## 4. C2 改 CoreAiGateway 实现

- [x] 4.1 删 `apikey = "fake-apikey"` 硬编码,改 `apikey = apikey`(用入参)
- [x] 4.2 ping 同上
- [x] 4.3 构造签名仅 `(providers: Map<String, AiProvider>, historyRepo: Lazy<AiHistoryRepository>)`,无 prefs/context 依赖 ✓

## 5. C2 改 AiActionViewModel caller

- [x] 5.1 同步 `secureApiKeyStore.get(providerId)` 拿真 apikey
- [x] 5.2 缺 key → `Failed(op, ProviderNotConfigured)` + return
- [x] 5.3 `aiGateway.streamWritingOp(..., apikey = apikey ?: "")` 透传
- [x] 5.4 死代码 `resolveProviderId()` 留待 H2 处理

## 6. C2 改 ModelManagementViewModel caller

- [x] 6.1 同步取 apikey,`null` → `PingResult.Failed("apikey 未配置")`
- [x] 6.2 `aiGateway.ping(providerId, apikey = apikey, modelName = "default")` 透传

## 7. C2 加 MockWebServer 端到端测试

- [x] 7.1 新建 `AnthropicCompatibleAdapterApikeyTest.kt`
- [x] 7.2 AUTHORIZATION → `Authorization: Bearer sk-real-test-123` ✓
- [x] 7.3 X_API_KEY → `x-api-key: sk-real-test-123` ✓
- [x] 7.4 CUSTOM_HEADER(`X-Custom-Auth`)→ `X-Custom-Auth: sk-real-test-123` ✓
- [x] 7.5 全测过(隐含在 `:app:testDebugUnitTest` BUILD SUCCESSFUL)

## 8. C2 更新现有 caller 测试适配新签名

- [x] 8.1 `AiActionViewModelTest.kt` — 5 处 `every { ... }` 加 apikey `any()` matcher
- [x] 8.2 `AiActionViewModelConsentTest.kt` — 2 个 stub class (`ThrowingAiGateway` / `RecordingAiGateway`) override 签名加 `apikey`
- [x] 8.3 `FakeAiProviderTest.kt` 不调 gateway,不动
- [x] 8.4 `:app:testDebugUnitTest` BUILD SUCCESSFUL ✓

## 9. H2 ktlintFormat 自动修

- [x] 9.1 `ktlintFormat` 自动修大量(import order / trailing comma / argument-list-wrapping / function-signature)
- [x] 9.2 `ktlintCheck` 列剩余(Type.kt indent + AiActionViewModelTest 8 参调用等手工项)

## 10. H2 手工修 Type.kt indent

- [x] 10.1 `ktlintFormat` 自动处理了 Type.kt 整 indent(12 → 8 全自动)
- [x] 10.2 `ktlintMainSourceSetCheck` 0 violation ✓

## 11. H2 手工拆 AiActionViewModelTest 多参数单行

- [x] 11.1+11.2 `ktlintFormat` 自动拆了 8 参构造调用每参一行(:47, :73, :94, :112, :128, :148, :165)
- [x] 11.3 `ktlintTestSourceSetCheck` 0 violation ✓

## 12. H3 清 obsolete ktlint property

- [x] 12.1 `config/ktlint/.editorconfig` 本来就没 obsolete property(只项目根有)
- [x] 12.2 删项目根 `.editorconfig` `ktlint_disabled_rules = ...` 行(per-rule `ktlint_standard_*` 写法保留)
- [x] 12.3 `ktlintCheck` 0 violation + 0 obsolete warning ✓

## 13. 全量验证

- [x] 13.1 `assembleDebug` BUILD SUCCESSFUL ✓
- [x] 13.2 `ktlintCheck` 0 violation + 0 obsolete warning ✓
- [x] 13.3 `testDebugUnitTest` BUILD SUCCESSFUL,含新 `AnthropicCompatibleAdapterApikeyTest` 3 case ✓
- [x] 13.4 grep `fake-apikey` 只剩 1 处(`AnthropicCompatibleAdapterApikeyTest.kt:19` 测试 KDoc 描述,非代码字面量)✓

## 14. docs/progress.md 追加 + 准备 commit(不提交)

- [x] 14.1 读 `docs/progress.md` 维护规则段
- [x] 14.2 追加 1 条:fix-m5-blockers 修复 main broken state + 全量 review r2(已落到最前)
- [x] 14.3 **不** commit。等用户说"commit"或"提交"再走 `git commit`。
