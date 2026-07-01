## Context

M0–M4-3 已完成:v1 应用骨架 + 随手记 + AI 抽象层 + AI 操作 UI + widget + 手势 + 导出导入全部就绪，跑 56 个单测全绿。当前 v1 缺两道闸:

1. **同意门**:CLAUDE.md "AI 集成约定" + roadmap §9.2 硬要求"首次 AI 调用前必须有用户同意(条款、隐私、可能的成本)，同意状态持久化在 DataStore"。当前零实现，M3 的 `providerId = "fake"` 只是延迟。
2. **apikey 加密**:roadmap §9.1 硬要求"走 EncryptedSharedPreferences 或 Tink 包装的 Android Keystore，绝不进 Room / 明文 SharedPreferences / logcat / Auto Backup / BuildConfig"。M2 已把 `ProviderConfig` 准备好，缺存储位。

`data_extraction_rules.xml` M0 留作 forward-looking 配置(roadmap §9.1 注释)，现在正好落地。

外部依赖变化:加 `androidx.security:security-crypto-ktx` 进 Version Catalog,Robolectric 首次启用。

## Goals / Non-Goals

**Goals:**
- 冷启同意门控:未同意 → 全屏 onboarding 页，同意后单向进入主路由
- 同意版本号:升级条款时强制重新同意(留 v1 升级空间)
- apikey EncryptedSharedPreferences 仓库:Hilt 单例注入，UI 层只看到 interface
- AI 入口 gating:`AiActionViewModel` + `ActionSheet` 在未同意时禁用 AI 入口，改走 onboarding
- 备份排除:`data_extraction_rules.xml` 显式 exclude secure prefs 文件
- 5s 自动清屏:apikey UI 显示 5s 后自动隐藏(Lifecycle pause 时间戳方案)
- apikey 永不进 logcat / Room / 明文 SharedPreferences

**Non-Goals:**
- 真实 AI provider 切换(M5 polish):本 change 只做"门 + 存储"，不动 `providerId` 切换逻辑，留 `SecureApiKeyStore` API 给 M5 用
- 设置页 apikey 管理 UI(M5 polish):本 change 只暴露 `SecureApiKeyStore` API,UI 入口留 M5
- 条款内容动态拉取:v1 条款是 `assets/privacy_policy.md` 静态资源，不做远程拉取
- 多语言条款:v1 条款中英双语 `assets/privacy_policy_zh.md` / `assets/privacy_policy_en.md`，无系统语言 fallback(zh 默认 + en 系统切到英文时)
- ShakeDetector 5s 清屏(M5 polish):M4-4 用 Lifecycle pause 时间戳简化实现
- 用户撤回同意(v1 必带，但 scope 限定在"清除 apikey + 重新走 onboarding"，不增加"撤回"独立页面)

## Decisions

### D1 · ConsentStore 走 DataStore Preferences，不用 Room

**Decision:** `ConsentStore` 走 `androidx.datastore.preferences.core.Preferences`,key 三项:`consent_accepted: Boolean` / `consent_accepted_at: Long` / `consent_version: Int`。

**Rationale:** 仅 3 个标量值，Room over-engineer;DataStore 异步 + 持久化 + Flow 订阅已够用;与项目其他 prefs 走 DataStore 风格一致(虽然目前还没有，但 M5 polish 会加)。

**Alternatives:**
- SharedPreferences:同步，无 Flow 订阅，UI 改写麻烦 ❌
- Room:需新建表 + DAO，杀鸡用牛刀 ❌
- DataStore Proto:Schema 维护成本，3 个标量不值 ❌

### D2 · SecureApiKeyStore 走 EncryptedSharedPreferences + Tink

**Decision:** `SecureApiKeyStore` 用 `EncryptedSharedPreferences.create(...)` 包装 `MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()`，文件名 `writingwithai_secure_prefs.xml`,key 集合:`apikey_<providerId>: String`(密文)。

**Rationale:** `androidx.security:security-crypto-ktx` 1.1.x stable，内部走 Tink + Android Keystore，自动处理 key 轮换;与 roadmap §9.1 拍板一致;ProviderConfig M2 已就位，接入路径短。

**Alternatives:**
- 裸 Keystore + Tink 自己包:控制力强但工程量大，v1 用不到 ❌
- DataStore + 自实现加密:DataStore 透明，易写错(Nonce / IV 复用 / 认证)❌
- 第三方库(如 Conceal):依赖多，社区维护弱 ❌

### D3 · ConsentGate 启动强制 onboarding 路由

**Decision:** `AppNav` 启动时 `LaunchedEffect(Unit) { consentStore.consentAccepted.first() }`，未同意或版本过期 → `navController.navigate("onboarding/consent") { popUpTo(0) { inclusive = true } }`(清空 back stack)。同意后回主路由，系统 back 不会回到 onboarding(pop 已清)。

**Rationale:** 启动期强制门控，UI 层零感知;`popUpTo(0)` 清栈保证 back 不可回，符合首次启动 + 升级条款场景;不依赖 Activity `finish()`，避免冷启 race。

**Alternatives:**
- Activity 启动时 decide + 显式 intent flag:侵入 MainActivity，跟 M4-2 `enableOnBackInvokedCallback` 复杂交错 ❌
- Dialog 弹同意:与全屏 onboarding 比，沉浸感弱，用户易点"同意"而不读条款 ❌

### D4 · AI 入口 gating 走 ViewModel 读 + UI 路由

**Decision:** `AiActionViewModel` 构造时 `private val consentStore: ConsentStore`，初始化读 `consentStore.consentAccepted` 到 `StateFlow<Boolean>`;`start(...)` 检测到 `false` → `aiState = Failed(UserConsentRequired)`(新增 `AiError` 子类，`toDisplayMessage` 映射到 R.string.onboarding_required);`ActionSheet` 入口先查 `consentStore.consentAccepted` state,`false` 时 FAB click → 不弹 sheet，改 navigate `onboarding/consent`。

**Rationale:** 两层防御:ViewModel 防误调，UI 防误点;`UserConsentRequired` 走 AiError 已有的 toDisplayMessage 通道，UX 一致。

**Alternatives:**
- 只在 ViewModel 检查:UI 弹了 sheet 再失败，UX 跳变 ❌
- 只在 UI 检查:VM 是 public API，其他 caller 绕过门控 ❌
- Hilt Qualifier 强制注入:复杂，v1 不值 ❌

### D5 · 5s 自动清屏用 Lifecycle pause 时间戳

**Decision:** `SecureApiKeyStore.reveal(context, providerId): StateFlow<String?>` 内部用 `LifecycleObserver` 记 `lastPauseAt: Long`;UI 订阅时 `elapsed = now - lastPauseAt`,`> 5_000ms` → emit `null`(空密)。M5 polish 再换 `ShakeDetector`。

**Rationale:** 5s 是从用户角度看"离开屏幕 / 切后台" 的合理时长;Lifecycle pause 不需要 sensor 权限，不增加 manifest 复杂度;v1 内测够用，复杂方案(ShakeDetector / 前置摄像头人脸检测)留 polish。

**Alternatives:**
- ShakeDetector:实现复杂，误触多 ❌
- 摄像头人脸检测:权限 + 性能 ❌
- 直接 5s Timer:不可见时仍跑，浪费电 ❌

### D6 · 条款内容放 `assets/`，不走 R.string

**Decision:** `app/src/main/assets/privacy_policy_zh.md` + `privacy_policy_en.md`,`OnboardingScreen` 用 `context.assets.open(...).bufferedReader().readText()` 读出，经 `compose-markdown`(走 JetBrains official)渲染。

**Rationale:** 条款是长文，塞 R.string 难维护 + i18n 工具不识别 Markdown;`assets/` 是 Android 标准的 raw file 位置。

**Alternatives:**
- 塞 R.string:长文 HTML / Markdown 不友好 ❌
- 远程拉取:断网 + 隐私风险 ❌
- `res/raw/`:可行但 `assets/` 约定更适合"未编译进 R"的资源 ❌

**Dependency add:** `compose-markdown`(org.jetbrains.compose.markdown)走 context7 resolve 版本，M4-4 tasks 验。

### D7 · `data_extraction_rules.xml` forward-looking 落地

**Decision:** 新建 `app/src/main/res/xml/data_extraction_rules.xml`，内容:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="writingwithai_secure_prefs.xml" />
        <exclude domain="database" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="writingwithai_secure_prefs.xml" />
        <exclude domain="database" />
    </device-transfer>
</data-extraction-rules>
```
`AndroidManifest.xml` `<application>` 加 `android:dataExtractionRules="@xml/data_extraction_rules"`,`allowBackup` 仍为 `false`(roadmap §9.1 拍板"完全关闭 Auto Backup")。

**Rationale:** `dataExtractionRules` 在 `allowBackup=false` 时不生效，但 forward-looking 友好:M5 polish 真开 backup 时只需 flip 一个属性。`writingwithai_secure_prefs.xml` 显式 exclude 双保险。

## Risks / Trade-offs

- **[Risk] KeyStore 损坏时 apikey 丢失** → `SecureApiKeyStore.init` catch `GeneralSecurityException` + `KeyStoreException` → 走 fallback:清空 + log 一行 tag-aliased 提示(绝不 log apikey)+ 触发"请重新输入 apikey" UI(M5 polish 落地 UI)。Mitigation:Keystore 损坏极罕见，真出 v1 接受"重输一次"成本。
- **[Risk] `ConsentGate` 启动阻塞冷启** → DataStore `first()` 是 suspend,`LaunchedEffect` 不阻塞 Composable 渲染，主屏可先渲染空态等待 consent 结果。Mitigation:实测延迟 < 50ms,UX 不可感。
- **[Risk] Robolectric 跑 EncryptedSharedPreferences 需要 AndroidKeyStore mock** → Robolectric 默认不支持，需在 `robolectric.properties` 加 `config = "..."` 选支持 KeyStore 的 runtime，或用 `androidx.security:security-crypto-ktx` 提供的 `MasterKey` 测试 stub。Mitigation:`SecureApiKeyStore` 设计为 interface，测试用 `FakeSecureApiKeyStore` 替换。
- **[Risk] 备份排除验证复杂** → 需 `adb bmgr backupnow com.yy.writingwithai` + 解包 `.ab` 看是否含 `writingwithai_secure_prefs.xml`。Mitigation:r1/r2 review 加 1 个 review item 手动验。
- **[Risk] `consent_accepted` race** → 多 Composable 并发读 → 各自订阅 Flow,DataStore 保证一致性，无 race。Mitigation:无需额外 sync。
- **[Risk] onboarding 单向门挡住 widget 入口** → M4-1 widget 直接拉起 `quicknote/edit` 路由，绕过 onboarding 屏。Mitigation:`MainActivity.onCreate` / `onNewIntent` 解析 `intent.getStringExtra("route")` 前先查 consent，未同意 → 改 navigate `onboarding/consent` 并把 `pendingRoute` 暂存，同意后回放。
- **[Trade-off] 增加 Robolectric 依赖** → 单测 jar 增大 ~5MB,v1 内测可接受;M5 polish 可换纯 JVM in-memory SecureApiKeyStore stub 减小体积。

## Migration Plan

1. **依赖入 catalog**:`gradle/libs.versions.toml` 加 `androidx-security-crypto-ktx` + `compose-markdown` + Robolectric(已 M0 准备但未启用)
2. **ConsentStore + SecureApiKeyStore 落地**:Hilt module 注入，VM/UI 接入
3. **OnboardingScreen + ViewModel 落地**:静态 Markdown 渲染 + 滚动解锁 + 同意/拒绝
4. **ConsentGate 接入 AppNav**:未同意强制路由，同意后清栈
5. **AI 入口 gating**:`AiActionViewModel` + `ActionSheet` 接入 consent state
6. **Widget 入口 gating**:`MainActivity` 解析 intent route 前先查 consent
7. **data_extraction_rules.xml** 落地 + manifest 引用
8. **测试**:`ConsentStoreTest` / `SecureApiKeyStoreTest`(FakeSecureApiKeyStore)/ `OnboardingViewModelTest` / `AiActionViewModelConsentTest`
9. **回滚策略**:本 change 全 feature 关闭 gating 一行 flag(`BuildConfig.CONSENT_GATE_ENABLED` 默认 true)即可回退到 M4-3 状态。

## Open Questions

- **Q1** `compose-markdown` 包选型(context7 验:JetBrains official / third-party 哪个活跃)?  → tasks.md 阶段 resolve，优先 JetBrains official(`org.jetbrains.compose.markdown:markdown`)。
- **Q2** `consent_version` 升级策略:是 hardcode 数字(1, 2, 3...)，还是 `app.versionCode` 派生? → 倾向 hardcode 数字 + 留 R.integer.consent_version(改动条款时手动 bump),versionCode 派生太激进(每次 release 都要重新同意)。
- **Q3** 拒绝按钮 = 退出 App 行为:用 `Activity.finishAffinity()` 还是 `System.exit(0)`? → 倾向 `finishAffinity()`(Android 推荐，留系统正常清理路径);`System.exit(0)` 太暴力，可能丢未保存的草稿。
- **Q4** `UserConsentRequired` 是不是要单独一个 AiError 子类，还是复用 `Auth`? → 倾向新子类 `UserConsentRequired`，语义清晰，toDisplayMessage 文案独立。
