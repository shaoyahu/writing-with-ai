## 1. 依赖与脚手架

- [x] 1.1 `gradle/libs.versions.toml` 加 `androidx-security-crypto-ktx`(1.1.0-alpha06 stable)+ `compose-markdown`(org.jetbrains.compose.markdown 走 context7 resolve 当前版本)+ Robolectric 启用注解;`app/build.gradle.kts` 引用
- [x] 1.2 `app/build.gradle.kts` 加 `buildConfigField("Boolean", "CONSENT_GATE_ENABLED", "true")` + `buildConfigField("Int", "CONSENT_VERSION", "1")`(`R.integer.consent_version` 派生自此)
- [x] 1.3 `res/values/integers.xml` 加 `<integer name="consent_version">1</integer>`;`res/values-en/integers.xml` 同值
- [x] 1.4 `app/src/main/AndroidManifest.xml` `<application>` 加 `android:dataExtractionRules="@xml/data_extraction_rules"`,确认 `android:allowBackup="false"`
- [x] 1.5 新建 `app/src/main/res/xml/data_extraction_rules.xml`(content 见 design D7)

## 2. 资源与本地化

- [x] 2.1 `res/values/strings.xml` 加 6 个 `onboarding_*` key(titles/accept/reject/required/scroll_hint)+ 1 个 `aiwriting_consent_required`(若 ai-actions 复用)+ 4 个 `secure_prefs_*` key(hidden_label / reveal_button / copy_button / clear_button)
- [x] 2.2 `res/values-en/strings.xml` 同步加 11 个 key,值 `TODO(en): <key_name>`(M5 polish 翻译)
- [x] 2.3 新建 `app/src/main/assets/privacy_policy_zh.md`(中文隐私条款,模板结构:数据本地存储说明 / AI 调用数据流 / 第三方 provider 列表 / 退出 App 撤回方式 / 联系方式)
- [x] 2.4 新建 `app/src/main/assets/privacy_policy_en.md`(英文版本,内容与中文版语义对齐)

## 3. core/prefs/ 落地

- [x] 3.1 新建 `core/prefs/ConsentStore.kt`:interface + `ConsentStoreImpl`,DataStore Preferences 包装,key 集合 `consent_accepted` / `consent_accepted_at` / `consent_version`,`ConsentState(accepted, acceptedAt, version)` data class
- [x] 3.2 新建 `core/prefs/SecureApiKeyStore.kt`:interface + `SecureApiKeyStoreImpl`,`EncryptedSharedPreferences` 包装 + `MasterKey.AES256_GCM`,`save/get/has/clear/clearAll` + `reveal()` 返回 `StateFlow<RevealState>`(LifecycleObserver 跟踪 `lastPauseAt`)
- [x] 3.3 新建 `core/prefs/PrefsModule.kt`:Hilt `@Module @InstallIn(SingletonComponent::class)`,提供 `ConsentStore` + `SecureApiKeyStore` 单例
- [x] 3.4 新建 `core/prefs/FakeSecureApiKeyStore.kt` + `FakeConsentStore.kt`:测试用纯内存实现,避免 Robolectric 跑 KeyStore

## 4. feature/onboarding/ 落地

- [x] 4.1 新建 `feature/onboarding/OnboardingEntry.kt`:跨 feature 入口 object,`fun requestConsent(navController: NavController)` 暴露 `navigate("onboarding/consent")`
- [x] 4.2 新建 `feature/onboarding/OnboardingViewModel.kt`:`@HiltViewModel`,依赖 `ConsentStore`,`accept()` / `reject()` 方法;`accept()` 调 `consentStore.setAccepted(version=CURRENT_CONSENT_VERSION, at=now)`;`reject()` 调 `Activity.finishAffinity()`(通过 `application.finishAffinity()` 或 `LocalContext`)
- [x] 4.3 新建 `feature/onboarding/OnboardingRoute.kt`:`@Composable fun OnboardingRoute(navController, viewModel: OnboardingViewModel = hiltViewModel())`,内部 `LaunchedEffect(consentState)` 监听 consent 变 true → nav 主路由
- [x] 4.4 新建 `feature/onboarding/OnboardingScreen.kt`:顶部标题 + 副标题 + 中部 `LazyColumn` Markdown 渲染(`androidx.compose.foundation.lazy.LazyColumn` + `Markdown` composable from compose-markdown)+ 底部两按钮(同意 / 拒绝),`derivedStateOf` 跟踪最后可见 item → 控制"同意"按钮 enabled
- [x] 4.5 `app/src/main/java/com/yy/writingwithai/feature/onboarding/` 内部目录自包含,跨 feature 引用走 `OnboardingEntry.kt`

## 5. ConsentGate 接入

- [x] 5.1 `app/AppNav.kt` 加 `ConsentGate` composable:在 `NavHost` 外 `LaunchedEffect(Unit) { val state = consentStore.consentFlow.first(); if (!state.accepted || state.version < CONSENT_VERSION) nav.navigate("onboarding/consent") { popUpTo(0) } }`;`ConsentStateFlow` 收集到变 true → 主动 navigate 主路由
- [x] 5.2 `app/AppNav.kt` `NavHost` 注册 `composable("onboarding/consent") { OnboardingRoute(navController) }`
- [x] 5.3 `app/AppNav.kt` 主路由(`quicknote/list` 等)起点加 `LaunchedEffect` 监听 consent 撤回 → navigate 回 `onboarding/consent`
- [x] 5.4 `app/MainActivity.kt` 加 `var pendingRoute: String? = null` 字段;`onCreate` / `onNewIntent` 解析 `intent.getStringExtra("route")` 前 `runBlocking { consentStore.isConsented() }`;未同意 → 暂存 `pendingRoute` + navigate `onboarding/consent`;已同意 → 走既有 route 解析
- [x] 5.5 `OnboardingRoute.kt` `LaunchedEffect(consentState) { if (consentState.accepted && consentState.version >= CONSENT_VERSION) { val pending = mainActivity.pendingRoute; if (pending != null) { nav.navigate(pending) { popUpTo(0) }; mainActivity.pendingRoute = null } else { nav.navigate("quicknote/list") { popUpTo(0) } } } }`

## 6. AI 入口 gating

- [x] 6.1 `core/ai/api/AiError.kt` 加 `data object UserConsentRequired : AiError` 子类
- [x] 6.2 `core/ai/api/AiError.kt` `toDisplayMessage` 加 `is UserConsentRequired -> ctx.getString(R.string.onboarding_required)` 分支
- [x] 6.3 `feature/aiwriting/streaming/AiActionViewModel.kt` 构造注入 `ConsentStore` + `SecureApiKeyStore`;内部 `_consentAccepted: StateFlow<Boolean> = consentStore.consentFlow.map { it.accepted }.stateIn(...)`;`start()` 入口检查 `_consentAccepted.value == false` → `aiState = Failed(op, UserConsentRequired)` + return
- [x] 6.4 `feature/aiwriting/streaming/AiActionViewModel.kt` `start()` 内部用 `private suspend fun resolveProviderId(): String = if (secureApiKeyStore.has(DEFAULT_PROVIDER)) DEFAULT_PROVIDER else PROVIDER_ID_FAKE` 替代原 `PROVIDER_ID_FAKE` 直传
- [x] 6.5 `feature/aiwriting/action/ActionSheet.kt` FAB click 入口查 `consentFlow.value`;false → 调 `AiwritingEntry.requestConsent(navController)`;true → 弹 `DropdownMenu` 既有行为
- [x] 6.6 `feature/aiwriting/AiwritingEntry.kt` 加 `fun requestConsent(navController: NavController) = navController.navigate("onboarding/consent")`

## 7. widget 入口 gating

- [x] 7.1 `core/widget/QuickNoteWidget.kt` 启动 Intent 不变(已 M4-1 落地),`MainActivity.onCreate` 已 step 5.4 处理 gating,无需 widget 侧改动
- [x] 7.2 `core/widget/WidgetIntentHelpers.kt` 加注释:`startActivities` 前假设 MainActivity 已做 consent 检查,不重复

## 8. 测试

- [x] 8.1 `app/src/test/.../core/prefs/ConsentStoreTest.kt`:in-memory DataStore,验 save/load/version bump/reset
- [x] 8.2 `app/src/test/.../core/prefs/SecureApiKeyStoreTest.kt`:用 `FakeSecureApiKeyStore` 验 save/get/clear/clearAll + `reveal()` 5s 过期 / Lifecycle pause 时间戳(注入 fake clock)
- [x] 8.3 `app/src/test/.../feature/onboarding/OnboardingViewModelTest.kt`:用 `FakeConsentStore`,验 `accept()` 写 store + `reject()` 不写
- [x] 8.4 `app/src/test/.../feature/aiwriting/AiActionViewModelConsentTest.kt`:注入 `FakeConsentStore(false)`,验 `start()` 立即 `Failed(UserConsentRequired)` 且 `aiGateway` 0 调用
- [x] 8.5 `app/src/test/.../app/AppNavConsentGateTest.kt`:`FakeConsentStore` 注入,验未同意时 first route = `onboarding/consent`,同意后 = `quicknote/list`

## 9. 验收

- [x] 9.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 9.2 `./gradlew :app:testDebugUnitTest` 全绿(M4-4 新增 ≥ 8 个 test,累计 ≥ 64 个)
- [x] 9.3 `./gradlew :app:lintDebug` 0 errors
- [x] 9.4 `./gradlew :app:ktlintCheck` 不引入新违规(已知 Compose PascalCase baseline 21 个,M4-4 期望 +0~2 个)
- [x] 9.5 `./gradlew :app:check` 全绿
- [x] 9.6 `adb shell run-as com.yy.writingwithai cat shared_prefs/writingwithai_secure_prefs.xml` 0 个明文 apikey(手动验,r1/r2 review 抽查)
