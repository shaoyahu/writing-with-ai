# secure-prefs Specification

## Purpose
TBD - created by archiving change `onboarding-consent`(2026-06-19)。定义 AI provider apikey 加密存储 + 备份排除 + 5s 自动清屏的契约(roadmap §9.1 拍板)。

## Requirements

### Requirement: SecureApiKeyStore persists apikeys via EncryptedSharedPreferences

`SecureApiKeyStore` MUST 用 `EncryptedSharedPreferences.create(...)` 包装 `MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()` 落地;文件名 MUST 为 `writingwithai_secure_prefs.xml`;key 集合按 `providerId` 命名:

| key | 类型 | 用途 |
| --- | --- | --- |
| `apikey_<providerId>` | `String`(密文) | provider 的 apikey |

`SecureApiKeyStore` MUST 暴露 interface:
- `suspend fun save(providerId: String, apikey: String)`
- `suspend fun get(providerId: String): String?`
- `suspend fun has(providerId: String): Boolean`
- `suspend fun clear(providerId: String)`
- `suspend fun clearAll()`
- `fun reveal(providerId: String): StateFlow<RevealState>`
- `fun observeConfiguredProviders(): Flow<Set<String>>` — 实时返回所有已配置 apikey 的 `providerId` 集合,初始 emit 当前 set,后续 key 增删时 emit 新 set,Flow cancel 时 unregister listener(防泄漏)

实现 MUST 捕获 `GeneralSecurityException` / `KeyStoreException` 等 KeyStore 异常,fallback 行为:清空对应 provider 的 apikey + log 一行(不 log apikey 本身,只 log `providerId` + 异常类型)+ 返回 `null`(`get`) / `false`(`has`) / `KeystoreFailed`(`reveal`)。

`observeConfiguredProviders()` 底层实现 MUST 用 `android.content.SharedPreferences.OnSharedPreferenceChangeListener` 监听 `writingwithai_secure_prefs.xml` 文件中所有以 `apikey_` 为前缀的 key;初始 emit 当前 set → 后续 key 增删时 emit 新 set;Flow cancel 时 MUST unregister listener(防泄漏)。`FakeSecureApiKeyStore` MUST 实现等价行为:StateFlow<MutableSet<String>>,`save` 时 add,`clear` 时 remove,`clearAll` 时清空。

#### Scenario: 保存 apikey
- **WHEN** `save("deepseek", "sk-xxx")` 调用
- **THEN** `writingwithai_secure_prefs.xml` 内 `apikey_deepseek` key 写入密文;明文不落盘

#### Scenario: 读取 apikey
- **WHEN** `get("deepseek")` 在 save 后调用
- **THEN** 返回 `"sk-xxx"`(明文);若 KeyStore 损坏 → 返回 `null` + 触发 fallback

#### Scenario: KeyStore 损坏 fallback
- **WHEN** `EncryptedSharedPreferences` 初始化抛 `GeneralSecurityException`(模拟)
- **THEN** 内部 catch + log `Tag.SecurePrefs` + `e.message`(不含 apikey)+ `get` 返回 `null`;`clearAll` 被调用以防残留

#### Scenario: clear 清除单个 provider
- **WHEN** `clear("deepseek")` 调用
- **THEN** `apikey_deepseek` key 从 prefs 移除;`get("deepseek")` 返回 `null`;其他 provider 不受影响

#### Scenario: clearAll 清空所有
- **WHEN** `clearAll()` 调用
- **THEN** `writingwithai_secure_prefs.xml` 所有 `apikey_*` key 移除;`get` 对所有 provider 返回 `null`

#### Scenario: save 后 observeConfiguredProviders emit 新 set
- **WHEN** `observeConfiguredProviders()` 被 collect + `save("deepseek", "sk-xxx")` 调用
- **THEN** Flow emit `{"deepseek"}`(初始空 set → save 后 1 元素)

#### Scenario: clear 后 observeConfiguredProviders emit 不含该 provider
- **WHEN** `observeConfiguredProviders()` 已在 collect + `clear("deepseek")` 调用
- **THEN** Flow emit 空 set(若仅 deepseek 一个 provider)

#### Scenario: Keystore 损坏时 observeConfiguredProviders 仍 emit 当前 set
- **WHEN** `EncryptedSharedPreferences` 初始化抛 `GeneralSecurityException`
- **THEN** `observeConfiguredProviders()` emit 空 set(且 Flow 不中断;后续 save 调用走 catch 分支,不更新 set)

### Requirement: apikey 5-second auto-hide via Lifecycle pause

`SecureApiKeyStore` MUST 暴露 `reveal(providerId: String): StateFlow<RevealState>`,`RevealState` 是 sealed interface:
```kotlin
sealed interface RevealState {
    data object Hidden : RevealState
    data class Revealed(val apikey: String, val expiresAt: Long) : RevealState
    data object KeystoreFailed : RevealState
}
```

`reveal()` 内部跟踪 `lastPauseAt: Long`(走 `Application.ActivityLifecycleCallbacks.onActivityPaused`);StateFlow 发射规则:
- 启动 / ON_RESUME 后调用 `reveal()` → 读 prefs → emit `Revealed(apikey, expiresAt=now+5_000ms)`
- 距离 lastPauseAt 超过 5_000ms → emit `Hidden`
- 起一次性 `delay(5_000)` coroutine,过期 emit `Hidden`
- Keystore 损坏 → emit `KeystoreFailed`

#### Scenario: 首次 reveal 返回明文 + 5s 过期
- **WHEN** 用户进入设置页 apikey 显示,App 未进入后台过,`reveal("deepseek")` 调用
- **THEN** emit `Revealed("sk-xxx", expiresAt=now+5_000)`;UI 显示明文

#### Scenario: 5s 后自动隐藏
- **WHEN** `Revealed` 已 emit 5s
- **THEN** emit `Hidden`;UI 显示占位符(如 `••••••••`)

#### Scenario: 切后台 5s 后回来仍隐藏
- **WHEN** App 进入 ON_PAUSE → 5s 后 ON_RESUME,`reveal("deepseek")` 调用
- **THEN** emit `Hidden`(因 lastPauseAt 超过 5s 阈值);需用户重新点击"显示"按钮才再 reveal

#### Scenario: 切后台不到 5s 回来仍可见
- **WHEN** App 进入 ON_PAUSE → 3s 后 ON_RESUME,`reveal("deepseek")` 调用
- **THEN** emit `Revealed("sk-xxx", expiresAt=now+5_000)`(剩余时间重置为 5s)

### Requirement: apikey never enters logs, Room, or plaintext SharedPreferences

`SecureApiKeyStore` 内部 MUST **不** 调用 `Log.*` 传 apikey 字符串(可 log `providerId` 与异常类型);apikey MUST **不** 进入 Room 任何表 / DAO;apikey MUST **不** 写入 `EncryptedSharedPreferences` 之外的任何文件;apikey MUST **不** 出现在 `BuildConfig` / 编译时常量。

`build.gradle.kts` MUST 设 `buildConfigField("Boolean", "CONSENT_GATE_ENABLED", "true")` + `buildConfigField("int", "CONSENT_VERSION", "1")` 不含 apikey 字段;`gradle.properties` MUST 不含 apikey 值。

#### Scenario: logcat 不出现 apikey
- **WHEN** `save("deepseek", "sk-xxx")` 调用
- **THEN** `adb logcat | grep sk-xxx` 0 匹配;`SecureApiKeyStore.kt` 源码 grep `Log\.(v|d|i|w|e)` 调用点不传 apikey 形参

#### Scenario: Room 不含 apikey 列
- **WHEN** `grep -rE "apikey|api_key|apiKey" app/src/main/java/com/yy/writingwithai/core/data/`
- **THEN** 0 匹配(Room Entity 字段不含 apikey)

#### Scenario: SharedPreferences 文件不含明文
- **WHEN** App 运行后 `adb shell run-as com.yy.writingwithai cat shared_prefs/*.xml`
- **THEN** 0 个 `.xml` 含 `"sk-xxx"` 明文(`writingwithai_secure_prefs.xml` 是 Tink 加密后的 base64 密文)

### Requirement: data_extraction_rules.xml excludes secure prefs from backup

`app/src/main/res/xml/data_extraction_rules.xml` MUST 显式 exclude `writingwithai_secure_prefs.xml`(cloud-backup + device-transfer 双 exclude);`AndroidManifest.xml` `<application>` MUST 加 `android:dataExtractionRules="@xml/data_extraction_rules"`;`allowBackup` 保持 `false`(roadmap §9.1 拍板 v1 完全关闭 Auto Backup)。

#### Scenario: manifest 引用 dataExtractionRules
- **WHEN** `AndroidManifest.xml` 检查
- **THEN** `<application>` 含 `android:dataExtractionRules="@xml/data_extraction_rules"` + `android:allowBackup="false"`

#### Scenario: data_extraction_rules.xml 双 exclude
- **WHEN** `app/src/main/res/xml/data_extraction_rules.xml` 内容检查
- **THEN** `<cloud-backup>` + `<device-transfer>` 均含 `<exclude domain="sharedpref" path="writingwithai_secure_prefs.xml" />`

### Requirement: SecurePrefsModule provides Hilt singleton

`SecureApiKeyStore` MUST 通过 `@Module @InstallIn(SingletonComponent::class) object SecurePrefsModule { @Provides @Singleton fun provideSecureApiKeyStore(@ApplicationContext context: Context): SecureApiKeyStore = SecureApiKeyStoreImpl(context) }` 暴露;Hilt consumer 通过 `@Inject constructor(private val secureApiKeyStore: SecureApiKeyStore)` 注入,UI 层只看到 interface。

#### Scenario: Hilt 注入成功
- **WHEN** `SettingsViewModel(@Inject secureApiKeyStore: SecureApiKeyStore)` 编译 + 启动
- **THEN** 注入 `SecureApiKeyStoreImpl` 单例;`secureApiKeyStore === otherInjected` 返回 `true`

#### Scenario: UI 层不直接 import 实现类
- **WHEN** `grep -rE "SecureApiKeyStoreImpl" app/src/main/java/com/yy/writingwithai/feature/`
- **THEN** 0 匹配(实现类只允许在 `core/prefs/` 内被引用,`feature/` 只见 interface)

#### Scenario: app 层也不直接 import 实现类(M5 polish)
- **WHEN** `grep -rE "SecureApiKeyStoreImpl" app/src/main/java/com/yy/writingwithai/app/`
- **THEN** 0 匹配(实现类只允许在 `core/prefs/` 内被引用)

#### Scenario: Robolectric test covers real EncryptedSharedPreferences(M5 polish)
- **WHEN** `app/src/test/java/com/yy/writingwithai/core/prefs/SecureApiKeyStoreRobolectricTest.kt` 文件存在
- **THEN** 用 `RobolectricTestRunner` + `@Config(sdk=[34])` 运行 4 个 test(save+get roundtrip / has / clear / reveal with expiry)

#### Scenario: Robolectric test covers real EncryptedSharedPreferences(M5 polish)
- **WHEN** `app/src/test/java/com/yy/writingwithai/core/prefs/SecureApiKeyStoreRobolectricTest.kt` 文件存在
- **THEN** 用 `RobolectricTestRunner` + `@Config(sdk=[34])` 运行 4 个 test(save+get roundtrip / has / clear / reveal with expiry)
