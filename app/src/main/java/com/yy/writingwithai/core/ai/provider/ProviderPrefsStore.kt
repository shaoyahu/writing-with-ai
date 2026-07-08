package com.yy.writingwithai.core.ai.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yy.writingwithai.core.ai.api.ApiFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * M5 polish · provider-real-integration · 持久化用户选定的 AI provider id。
 *
 * spec: openspec/changes/provider-real-integration/specs/model-management/spec.md
 * "ProviderPrefsStore 持久化 selected provider id"
 *
 * 单 string key，默认 `"fake"`(让老用户平滑过渡，`AiActionViewModel.resolveProviderId`
 * 仍走 FakeProvider 直到用户在设置 → 模型管理改)。
 *
 * apikey 不在本 store —— 走 [com.yy.writingwithai.core.prefs.SecureApiKeyStore] 加密存。
 * 本 store 仅存"哪个 provider"的明文 id，无敏感数据。
 */
interface ProviderPrefsStore {
    /**
     * fix-2026-06-24-review-r1-critical:返回 `String?`(null = 未选定，首次安装 / 清数据)。
     * 旧版本默认 `"fake"` 改为 `null`，避免 release 用户首启默认走 FakeAiProvider。
     */
    suspend fun getSelectedProviderId(): String?

    // remove-debug-fake-fallback §6.2:setSelectedProviderId 接 null — 删除/清 apikey 后
    // selected 设为 null(未选)而非降到 fake provider(FakeAiProvider 不再注入)
    suspend fun setSelectedProviderId(providerId: String?)

    /** Flow,UI 可订阅实时刷新;值可为 `null`。 */
    fun observeSelectedProviderId(): Flow<String?>

    /** per-provider 用户选定的 model 名;无值 = `null`(走 ProviderConfig.defaultModel 兜底)。 */
    suspend fun getSelectedModel(providerId: String): String?

    suspend fun setSelectedModel(providerId: String, model: String)

    /**
     * fix-2026-06-28-ai-model-selection-actually-used:仅在 `selected_model_<providerId>` key
     * 不存在时落 [defaultModel];已存在则不覆盖(尊重用户显式选择)。在 `saveProvider` 成功
     * 落 apikey + 启动 `init` 块扫存量时调用，确保「apikey 已落 → 必有 selectedModel」是
     * save 流程的不变式。
     */
    suspend fun setSelectedModelIfMissing(providerId: String, defaultModel: String)

    /** Flow,UI 可订阅实时刷新;值为 `null` 表示未设置。 */
    fun observeSelectedModel(providerId: String): Flow<String?>

    /**
     * model-management-detail-dropdown X 方案:per-provider 用户选定的 API 格式覆盖。
     * 无值 = `null`(走 ProviderConfig.apiFormat 兜底)。
     * 存的是字符串，避免 enum 序列化耦合;解析失败回退 null。
     */
    suspend fun getApiFormat(providerId: String): ApiFormat?

    suspend fun setApiFormat(providerId: String, format: ApiFormat)

    fun observeApiFormat(providerId: String): Flow<ApiFormat?>
}

private val Context.providerPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "writingwithai_provider_prefs"
)

class ProviderPrefsStoreImpl(
    // fix-review-r4 L6:显式 @ApplicationContext，防御性标记 — 当前由 PrefsModule @Provides
    // 注入(已保证)，若未来改 @Inject constructor 则此注解生效。
    @ApplicationContext private val context: Context
) : ProviderPrefsStore {
    override suspend fun getSelectedProviderId(): String? = context.providerPrefsDataStore.data
        .map { it[KEY_SELECTED_PROVIDER_ID] }
        .first()

    override suspend fun setSelectedProviderId(providerId: String?) {
        context.providerPrefsDataStore.edit { prefs ->
            if (providerId == null) {
                prefs.remove(KEY_SELECTED_PROVIDER_ID)
            } else {
                prefs[KEY_SELECTED_PROVIDER_ID] = providerId
            }
        }
    }

    override fun observeSelectedProviderId(): Flow<String?> = context.providerPrefsDataStore.data
        .map { it[KEY_SELECTED_PROVIDER_ID] }

    override suspend fun getSelectedModel(providerId: String): String? {
        return context.providerPrefsDataStore.data
            .map { it[selectedModelKey(providerId)] }
            .first()
    }

    override suspend fun setSelectedModel(providerId: String, model: String) {
        context.providerPrefsDataStore.edit { it[selectedModelKey(providerId)] = model }
    }

    override suspend fun setSelectedModelIfMissing(providerId: String, defaultModel: String) {
        // 单 `edit` 块内做存在性检查 + put,DataStore 串行化保证并发 caller 不会同时写。
        // 已经在 edit lambda 里，后到的 caller 看到 key 已存在就跳过，不覆盖用户显式选择。
        context.providerPrefsDataStore.edit { prefs ->
            val key = selectedModelKey(providerId)
            if (prefs[key].isNullOrBlank()) {
                prefs[key] = defaultModel
            }
        }
    }

    override fun observeSelectedModel(providerId: String): Flow<String?> {
        return context.providerPrefsDataStore.data
            .map { it[selectedModelKey(providerId)] }
    }

    override suspend fun getApiFormat(providerId: String): ApiFormat? {
        return context.providerPrefsDataStore.data
            .map { it[apiFormatKey(providerId)]?.let { name -> parseApiFormat(providerId, name) } }
            .first()
    }

    override suspend fun setApiFormat(providerId: String, format: ApiFormat) {
        context.providerPrefsDataStore.edit { it[apiFormatKey(providerId)] = format.name }
    }

    override fun observeApiFormat(providerId: String): Flow<ApiFormat?> {
        return context.providerPrefsDataStore.data
            .map { it[apiFormatKey(providerId)]?.let { name -> parseApiFormat(providerId, name) } }
    }

    /**
     * M12 修:解析失败时 `Log.w` 而非静默 — 旧版本升上来的脏数据(老 enum name)
     * 应该让开发 / 用户能看到，而不是悄悄回退 ProviderConfig 默认。
     */
    private fun parseApiFormat(providerId: String, name: String): ApiFormat? = try {
        ApiFormat.valueOf(name)
    } catch (e: IllegalArgumentException) {
        android.util.Log.w(TAG, "Unknown apiFormat name=$name for provider=$providerId, resetting to default")
        null
    }

    companion object {
        // fix-2026-06-24-review-r1-critical:默认 null(首次安装需用户主动选 provider)
        val DEFAULT_PROVIDER_ID: String? = null
        private val KEY_SELECTED_PROVIDER_ID = stringPreferencesKey("selected_provider_id")
        private const val TAG = "ProviderPrefsStore"
        // TAG 与 KEY 合并到同一 companion object，避免拆分导致 IDE / compiler 看不到 const 引用。

        /** per-provider selectedModel 的 Preferences.Key 工厂(动态 providerId 拼接)。
         *  fix-2026-06-27-review-r4 M15:require providerId 非空，防止拼出无意义 key。 */
        fun selectedModelKey(providerId: String): Preferences.Key<String> {
            require(providerId.isNotBlank()) { "providerId must not be blank for selectedModelKey" }
            return stringPreferencesKey("selected_model_$providerId")
        }

        /** per-provider apiFormat 覆盖的 Preferences.Key 工厂(存枚举 name 字符串)。
         *  fix-2026-06-27-review-r4 M15:同上，require providerId 非空。 */
        fun apiFormatKey(providerId: String): Preferences.Key<String> {
            require(providerId.isNotBlank()) { "providerId must not be blank for apiFormatKey" }
            return stringPreferencesKey("api_format_$providerId")
        }
    }
}
