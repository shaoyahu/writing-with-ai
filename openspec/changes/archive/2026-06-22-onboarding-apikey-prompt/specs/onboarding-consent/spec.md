## ADDED Requirements

### Requirement: Apikey prompt screen shown after consent

The system MUST display a full-screen `ApikeyPromptScreen` immediately after the user accepts the privacy policy on `OnboardingScreen`. This screen explains why an API key is needed, what AI capabilities are enabled, and the estimated token/cost range. The screen MUST be skippable via a "稍后设置" button.

#### Scenario: Apikey prompt shown after consent
- **WHEN** user taps "同意并继续" on `OnboardingScreen` and `ConsentStore.consentAccepted` transitions to true
- **THEN** `AppNav` MUST navigate to `onboarding/apikey-prompt` route before navigating to main content

#### Scenario: User skips apikey prompt
- **WHEN** user taps "稍后设置" on `ApikeyPromptScreen`
- **THEN** `ack_apikey_prompt_v1` is set to `true`; `AppNav` navigates to main content; all AI features remain inaccessible (guarded by apikey presence check, not by ack flag)

#### Scenario: User acknowledges and proceeds
- **WHEN** user taps "我知道了" on `ApikeyPromptScreen`
- **THEN** `ack_apikey_prompt_v1` is set to `true`; `AppNav` navigates to main content; user can now configure apikey in settings

### Requirement: AI capability guard on first use

Any AI-triggering action (expand / polish / organize / entity extraction / semantic linking) MUST check `ack_apikey_prompt_v1`. If the flag is `false`, the action MUST be intercepted and `ApikeyPromptScreen` shown as a dialog before proceeding.

#### Scenario: AI action intercepted
- **WHEN** user taps "扩写" button and `ack_apikey_prompt_v1 == false`
- **THEN** the system MUST show `ApikeyPromptDialog` instead of launching the AI action

#### Scenario: AI action proceeds after ack
- **WHEN** user taps "扩写" button and `ack_apikey_prompt_v1 == true`
- **THEN** the system MUST proceed to the AI action normally (apikey presence still checked separately)

### Requirement: Token cost reference displayed

The `ApikeyPromptScreen` MUST display a hardcoded cost reference table showing estimated token consumption and RMB cost range for each AI capability: expand/polish/organize (~500-1500 input, ~1000-3000 output), entity extraction (~300-600 input, ~100-300 output), semantic linking (~2000-4000 input). Each row MUST include a disclaimer that actual costs depend on the provider's current pricing.

#### Scenario: Cost table rendered
- **WHEN** `ApikeyPromptScreen` is displayed
- **THEN** the screen MUST show a scrollable section with at least 3 rows of capability-cost pairs, each ending with "以 provider 实际账单为准"

### Requirement: Reset apikey prompt in settings

The settings page MUST provide a "重新显示 API Key 说明" button that clears `ack_apikey_prompt_v1` to `false`, causing the prompt to reappear on the next AI action attempt.

#### Scenario: Reset from settings
- **WHEN** user taps "重新显示 API Key 说明" in settings
- **THEN** `ack_apikey_prompt_v1` is set to `false`; the next AI action trigger shows `ApikeyPromptDialog`