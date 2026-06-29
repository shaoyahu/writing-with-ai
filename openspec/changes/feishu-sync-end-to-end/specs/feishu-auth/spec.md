## MODIFIED Requirements

### Requirement: Token persisted across process restarts

#### Scenario: Connected settings page shows sync log section
- **WHEN** `FeishuAuthState.connected = true` and user opens `FeishuAuthScreen`
- **THEN** screen MUST render `FeishuSyncLogSection(eventDao)` showing last 20 sync events (newest first) with timestamp / direction / status / error columns
- **AND** each event row MUST display direction icon (PUSH / PULL) and status badge (SUCCESS / FAILURE / CONFLICT)
- **AND** disclaimer line "同步不消耗 AI token,只调飞书 API" MUST be visible above the list

#### Scenario: Disconnected settings page hides sync log
- **WHEN** `FeishuAuthState.connected = false`
- **THEN** `FeishuSyncLogSection` MUST NOT be rendered;screen MUST show only "连接飞书" CTA