# secure-prefs Specification (delta)

## MODIFIED Requirements (fix-ai-config-ux)

### Requirement: SecureApiKeyStore persists apikeys via EncryptedSharedPreferences (delta)

继承原 Requirement 不变;**新增方法 + Scenario**:

#### 新增方法:`fun observeConfiguredProviders(): Flow<Set<String>>`

返回当前所有已配置 apikey 的 `providerId` 集合。底层实现 MUST 用 `android.content.SharedPreferences.OnSharedPreferenceChangeListener` 监听 `writingwithai_secure_prefs.xml` 文件中所有以 `apikey_` 为前缀的 key;初始 emit 当前 set → 后续 key 增删时 emit 新 set。Flow cancel 时 MUST unregister listener(防泄漏)。

`FakeSecureApiKeyStore` MUST 实现等价行为:StateFlow<MutableSet<String>>,`save` 时 add,`clear` 时 remove,`clearAll` 时清空。

#### Scenario: save 后 Flow emit 新 set

- **WHEN** `observeConfiguredProviders()` 被 collect + `save("deepseek", "sk-xxx")` 调用
- **THEN** Flow emit `{"deepseek"}`(初始空 set → save 后 1 元素)

#### Scenario: clear 后 Flow emit 不含该 provider

- **WHEN** `observeConfiguredProviders()` 已在 collect + `clear("deepseek")` 调用
- **THEN** Flow emit 空 set(若仅 deepseek 一个 provider)

#### Scenario: Keystore 损坏时 Flow 仍 emit 当前 set(不抛)

- **WHEN** `EncryptedSharedPreferences` 初始化抛 `GeneralSecurityException`
- **THEN** `observeConfiguredProviders()` emit 空 set(且 Flow 不中断;后续 save 调用走 catch 分支，不更新 set)
