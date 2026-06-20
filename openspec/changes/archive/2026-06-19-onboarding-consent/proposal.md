## Why

v1 上线前必须解决两个 v1 红线项:用户首次启动时显式同意隐私条款(roadmap §9.2),以及 AI provider apikey 的本机加密存储(roadmap §9.1,CLAUDE.md "AI 集成约定" 硬规则)。M3 AI 操作 UI 已经写死 `providerId = "fake"` 等真 provider 切换锚点;M4-3 导出导入已落地但未触及同意门控。当前 v1 缺同意门,真 provider 接入也无安全存储位——这是 v1 内测前最后一道闸。

## What Changes

- **新增** `feature/onboarding/`:首次启动全屏同意页(`OnboardingScreen`),含隐私条款 Markdown 渲染 + 滚动到底部才允许"同意"按钮 + 拒绝则退出 App
- **新增** `core/prefs/ConsentStore`(DataStore Preferences):持久化 `consent_accepted: Boolean` + `consent_accepted_at: Long` + `consent_version: Int`
- **新增** `core/prefs/SecureApiKeyStore`(EncryptedSharedPreferences + Tink AES256_GCM):apikey 读写 + 5s 自动清屏 hook(`Lifecycle` pause 时间戳简化实现,`ShakeDetector` 留 M5 polish)
- **修改** `app/AppNav.kt` 启动时 `ConsentGate`:未同意 → 强制 `onboarding/consent` 路由;同意后回主路由且不可再返回
- **修改** `feature/aiwriting/AiActionViewModel`:启动时读 `ConsentStore.consentAccepted`,未同意 → `aiState = Failed(UserConsentRequired)` 阻断所有 AI 调用入口
- **修改** `feature/aiwriting/ActionSheet` UI:未同意状态下 ActionSheet 入口改走 onboarding 路由(而非直接弹 Sheet)
- **修改** `feature/settings/SettingsScreen`(M5 polish 时落地):新增 "AI Provider / apikey 管理" 入口,接 `SecureApiKeyStore` 读/写/清空
- **修改** `AndroidManifest.xml` `<application>` 确认 `android:allowBackup="false"` 仍生效;新增 `android:dataExtractionRules` 引用 forward-looking 规则(显式 exclude `writingwithai_secure_prefs.xml`)
- **新增** `res/xml/data_extraction_rules.xml`:`<cloud-backup><exclude domain="file" path="writingwithai_secure_prefs.xml"/></cloud-backup>` + `<device-transfer>` 同款
- **修改** `res/values/strings.xml` + `values-en/strings.xml`:加 `onboarding_*` 系列(标题/正文/按钮/版本号) + `secure_prefs_*` 提示文案

## Capabilities

### New Capabilities

- `onboarding-consent`: 首次启动全屏同意页 UI + 状态机 + 同意/拒绝行为 + 滚动到底部解锁 + 同意版本号管理 + 升级时重新同意
- `secure-prefs`: EncryptedSharedPreferences apikey 仓库 + 5s 自动清屏 + 同意门控读/写/清空 + 备份排除 + apikey 永不进日志

### Modified Capabilities

- `app-shell`: 启动 `ConsentGate` — 未同意强制 onboarding 路由,同意后单向进入主路由(back 不可回 onboarding)
- `ai-actions`: 同意门控 — `AiActionViewModel` / `ActionSheet` 在未同意时禁用 AI 入口,改走 onboarding 路由

## Impact

**新文件**(预估):
- `app/src/main/java/com/yy/writingwithai/feature/onboarding/{OnboardingScreen, OnboardingViewModel, OnboardingEntry, OnboardingRoute}.kt`
- `app/src/main/java/com/yy/writingwithai/core/prefs/{ConsentStore, SecureApiKeyStore, ConsentModule}.kt`
- `app/src/main/res/xml/data_extraction_rules.xml`
- 测试:`OnboardingViewModelTest` / `ConsentStoreTest`(in-memory DataStore)/ `SecureApiKeyStoreTest`(Robolectric + Tink)

**修改文件**:
- `app/src/main/java/com/yy/writingwithai/app/{AppNav, MainActivity, WritingApp}.kt` — `ConsentGate` 启动判断
- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/{AiActionViewModel, ActionSheet}.kt` — 同意门控
- `app/src/main/AndroidManifest.xml` — `dataExtractionRules` 引用
- `gradle/libs.versions.toml` — `androidx.security:security-crypto` 依赖入 Version Catalog
- `app/src/main/res/values/strings.xml` + `values-en/strings.xml` — i18n key 增量

**依赖**:
- 新增 `androidx.security:security-crypto-ktx`(1.1.x stable,Tink 自动带入)
- Robolectric(M0 已有)首次落地测试,跑 EncryptedSharedPreferences + Tink

**回归风险**:
- `ConsentGate` 阻断冷启 → 必须保证同意状态读取在主线程内可读(DataStore 已异步,需在 App 启动早期注入 fallback)
- apikey 加密 → KeyStore 损坏时 fallback 行为(直接清空重建,让用户重新填)
- 备份排除 → 需手动验证 `adb backup` / `bmgr` 不会带走 `writingwithai_secure_prefs.xml`
