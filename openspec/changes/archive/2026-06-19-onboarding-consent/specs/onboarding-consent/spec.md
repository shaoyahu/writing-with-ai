## ADDED Requirements

### Requirement: OnboardingScreen shown on first launch and after consent version bump

App MUST 在用户**未同意** 或 **同意版本号过期** 时，显示全屏 `OnboardingScreen`(单路由 `onboarding/consent`),UI 由 `feature/onboarding/OnboardingScreen.kt` 落地;`AppNav` 启动时读 `ConsentStore.consentFlow`，未满足 → 强制 navigate `onboarding/consent` + `popUpTo(0) { inclusive = true }`(清空 back stack)。同意后回到主路由，系统 back 不可返回 onboarding。

#### Scenario: 首次启动未同意
- **WHEN** 用户首次安装 App 启动，`ConsentStore.consentAccepted = false`
- **THEN** `OnboardingScreen` 全屏显示;`AppNav` 不渲染主路由内容;back stack 仅有 `onboarding/consent`

#### Scenario: 已同意但版本号过期
- **WHEN** `ConsentStore.consentAccepted = true` 但 `consentVersion < CURRENT_CONSENT_VERSION`(R.integer.consent_version)
- **THEN** `OnboardingScreen` 重新显示;用户需再次阅读 + 同意新版本条款

#### Scenario: 已同意且版本号匹配
- **WHEN** `ConsentStore.consentAccepted = true` 且 `consentVersion = CURRENT_CONSENT_VERSION`
- **THEN** `OnboardingScreen` 不显示;`AppNav` 直接渲染主路由(随手记列表)

#### Scenario: 系统 back 不返回 onboarding
- **WHEN** 用户在主路由按系统 back
- **THEN** 退出 App(走 `enableOnBackInvokedCallback` 系统 back 行为，roadmap §7.4 拍板)，不返回 `onboarding/consent` 路由

### Requirement: Privacy policy rendered as Markdown with scroll-to-bottom unlock

`OnboardingScreen` MUST 从 `assets/privacy_policy_<lang>.md` 读取条款文本(系统语言为英文时读 `en`，其他读 `zh`)，用 Markdown 渲染器(`compose-markdown`)显示;底部"同意"按钮在用户**未滚动到内容底部** 时 MUST 处于 `enabled = false` 状态;滚动到底部时启用。

#### Scenario: 加载中文条款
- **WHEN** 系统语言为中文，`OnboardingScreen` 初始化
- **THEN** 读 `assets/privacy_policy_zh.md`,Markdown 渲染;屏幕顶部标题 `stringResource(R.string.onboarding_title)`;条款下方按钮 "同意并继续" disabled

#### Scenario: 加载英文条款
- **WHEN** 系统语言为英文，`OnboardingScreen` 初始化
- **THEN** 读 `assets/privacy_policy_en.md`,Markdown 渲染;按钮文案 `stringResource(R.string.onboarding_accept)` disabled

#### Scenario: 滚动到底部解锁
- **WHEN** 用户滚动 `LazyColumn` 至最后一项可见
- **THEN** "同意"按钮 `enabled = true`;未滚动到底时 `enabled = false`(无法点击)

#### Scenario: 同意后写入 ConsentStore
- **WHEN** 用户点击"同意"按钮
- **THEN** `OnboardingViewModel.accept()` 调用 `ConsentStore.setAccepted(version=CURRENT_CONSENT_VERSION, at=now)`;`AppNav` 监听到 `consentAccepted = true` → `popUpTo(0) { inclusive = true }` + navigate 主路由

### Requirement: Reject exits the app cleanly

`OnboardingScreen` MUST 在用户点击"拒绝并退出" 按钮(条款上方 secondary button)时调用 `Activity.finishAffinity()`;拒绝后 App 退出到 launcher，不留后台残留进程。

#### Scenario: 拒绝并退出
- **WHEN** 用户在 `OnboardingScreen` 点击"拒绝并退出"按钮
- **THEN** `Activity.finishAffinity()` 被调用;App 进程退出;下次启动仍显示 `OnboardingScreen`(`ConsentStore.consentAccepted` 仍为 `false`)

#### Scenario: 拒绝按钮始终可用
- **WHEN** `OnboardingScreen` 任意状态下
- **THEN** "拒绝并退出"按钮 MUST 处于 `enabled = true`(不与"同意"按钮的滚动解锁联动)

### Requirement: ConsentStore persists consent state via DataStore

`ConsentStore` MUST 走 `androidx.datastore.preferences.core.Preferences`,key 集合:

| key | 类型 | 用途 |
| --- | --- | --- |
| `consent_accepted` | `Boolean` | 是否已同意 |
| `consent_accepted_at` | `Long` | 同意时间戳(epoch millis) |
| `consent_version` | `Int` | 同意的条款版本号 |

`ConsentStore` MUST 暴露 `consentFlow: Flow<ConsentState>`(combine 三 key),`setAccepted(version: Int, at: Long)`,`isConsented(): Boolean`(suspend first())。DataStore 文件名 MUST 为 `consent_store`(默认 preferences 即可，无需加密，只是布尔 + 数字)。

#### Scenario: 初始状态未同意
- **WHEN** App 首次启动，`ConsentStore` 读到 key 全部为 null(未写入)
- **THEN** `ConsentState(accepted=false, acceptedAt=0L, version=0)`;`AppNav` 触发 onboarding 路由

#### Scenario: 写入后读取一致
- **WHEN** `setAccepted(version=1, at=1700000000000L)` 调用
- **THEN** 后续 `consentFlow.first()` 返回 `ConsentState(accepted=true, acceptedAt=1700000000000L, version=1)`;进程重启后值不变

#### Scenario: 同意后改回未同意(撤回)
- **WHEN** `setAccepted(version=0, at=0L)` 调用
- **THEN** `ConsentState` 转回 `accepted=false`;下次 `AppNav` 启动仍走 onboarding 路由(用于 M5 polish 的"撤回同意"功能，本 change 暴露 API 不落地 UI)

### Requirement: i18n for onboarding UI

所有 `onboarding-consent` 相关 UI 文案 MUST 出现在 `values/strings.xml`(中文，权威)与 `values-en/strings.xml`(英文 TODO 占位)，命名空间 `onboarding_*`,6 个 key 集合:

| key | 中文 | 用途 |
| --- | --- | --- |
| `onboarding_title` | 欢迎使用 writing-with-ai | 顶部标题 |
| `onboarding_subtitle` | 请阅读并同意以下条款 | 副标题 |
| `onboarding_accept` | 同意并继续 | 主按钮 |
| `onboarding_reject` | 拒绝并退出 | secondary 按钮 |
| `onboarding_required` | 请先同意隐私条款 | AiError.UserConsentRequired 文案 |
| `onboarding_scroll_hint` | 请滚动到底部以继续 | 滚动解锁提示(可选) |

#### Scenario: 系统语言为英文时显示 TODO 占位
- **WHEN** 系统语言为英文，`values-en/strings.xml` 中 `onboarding_title="TODO(en): onboarding_title"`
- **THEN** `OnboardingScreen` 显示 `TODO(en): onboarding_title`;不阻断构建;M5 polish 替换

#### Scenario: 中文文案来自 R.string
- **WHEN** 系统语言为中文，UI 渲染"同意并继续"
- **THEN** 该文本通过 `stringResource(R.string.onboarding_accept)` 取值;源码 grep 不到中文字面量
