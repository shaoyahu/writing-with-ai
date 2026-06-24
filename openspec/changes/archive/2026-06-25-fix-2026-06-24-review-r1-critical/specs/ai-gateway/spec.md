## ADDED Requirements

### Requirement: FakeAiProvider only registered in debug build

`core/ai/di/AiModule.kt` `provideAiProviders()` MUST conditionally register `FakeAiProvider` only when `BuildConfig.DEBUG == true`. In release builds, the `Map<String, AiProvider>` MUST NOT contain a `fake` key, so callers asking for `"fake"` provider id receive the standard `ProviderNotFound` Failed event instead of mock output.

#### Scenario: Debug build registers fake
- **WHEN** `./gradlew :app:assembleDebug` runs and `BuildConfig.DEBUG == true`
- **THEN** the `Map<String, AiProvider>` provided to `CoreAiGateway` includes key `"fake"` mapping to `FakeAiProvider`

#### Scenario: Release build excludes fake
- **WHEN** `./gradlew :app:assembleRelease` runs and `BuildConfig.DEBUG == false`
- **THEN** the `Map<String, AiProvider>` provided to `CoreAiGateway` does NOT contain key `"fake"`

#### Scenario: Fake id in release returns Failed
- **WHEN** `CoreAiGateway.streamWritingOp(providerId = "fake")` is called in a release build
- **THEN** the first emitted event MUST be `AiStreamEvent.Failed(AiError.ProviderNotFound, recoverable = false)` rather than FakeAiProvider's mock stream

### Requirement: Default selected provider id is null on first install

`ProviderPrefsStore.DEFAULT_PROVIDER_ID` MUST change from `"fake"` to `null`. `getSelectedProviderId()` MUST return `null` when no provider has been explicitly selected (first install, after data clear, or after `clearSelectedProviderId()`).

#### Scenario: Fresh install returns null
- **WHEN** a user installs the app and has not yet opened "模型管理" (Model Management)
- **THEN** `ProviderPrefsStore.getSelectedProviderId()` returns `null`, NOT `"fake"`

#### Scenario: First AI action surfaces "provider not configured"
- **WHEN** `AiActionViewModel.start()` runs and `providerPrefsStore.getSelectedProviderId() == null`
- **THEN** the ViewModel MUST emit `Failed(op, ProviderNotConfigured)` without calling `AiGateway.streamWritingOp(...)` (0 gateway calls)

#### Scenario: After explicit user selection
- **WHEN** user opens Model Management, picks `deepseek`, saves
- **THEN** `getSelectedProviderId()` returns `"deepseek"` and subsequent AI actions route through `deepseek` provider