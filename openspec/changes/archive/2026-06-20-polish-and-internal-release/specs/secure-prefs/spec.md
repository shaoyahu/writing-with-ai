## MODIFIED Requirements

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

实现 MUST 捕获 `GeneralSecurityException` / `KeyStoreException` 等 KeyStore 异常，fallback 行为:清空对应 provider 的 apikey + log 一行(不 log apikey 本身，只 log `providerId` + 异常类型)+ 返回 `null`(`get`) / `false`(`has`) / `KeystoreFailed`(`reveal`)。

`SecureApiKeyStoreImpl` MUST 只允许在 `core/prefs/` 包内被 import;`feature/` / `app/` 层 MUST 通过 Hilt interface 注入，不 import 实现类。

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

#### Scenario: Robolectric test covers real EncryptedSharedPreferences + Lifecycle pause
- **WHEN** `app/src/test/java/com/yy/writingwithai/core/prefs/SecureApiKeyStoreRobolectricTest.kt` 文件存在
- **THEN** 包含 `@RunWith(AndroidJUnit4::class) @Config(sdk = [34])` + 至少 4 个 test(save+get roundtrip `/` Keystore fallback `/` 5s reveal timer `/` Lifecycle pause 后 Hidden)

#### Scenario: feature/ app/ 不 import SecureApiKeyStoreImpl
- **WHEN** `grep -rE "SecureApiKeyStoreImpl" app/src/main/java/com/yy/writingwithai/(feature|app)/`
- **THEN** 0 匹配(实现类只允许在 `core/prefs/` 内被引用)
