package com.yy.writingwithai.core.ai.provider

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * M6 custom-model · 持久化用户自定义 Provider 配置。
 *
 * 用 DataStore 存 JSON 序列化的 [List<ProviderConfig>],
 * 与 [SecureApiKeyStore] 解耦 — 配置不含 apikey。
 *
 * spec: 用户创建的自定义模型与内置模型平等展示, 可选中 / 测试连通 / 编辑 / 删除。
 *
 * 写操作(read-modify-write)全部在 `edit { }` lambda 内,DataStore 串行化写,
 * 避免两个并发 save/delete 互相覆盖。回调 [onInvalidate] 让
 * [com.yy.writingwithai.core.ai.CoreAiGateway] 在每次写入后清 adapter 缓存。
 */
interface CustomProviderStore {
    /** 全部自定义配置(同步取首次值)。 */
    suspend fun getAll(): List<ProviderConfig>

    /** 按 id 查配置;未找到返回 null。 */
    suspend fun getById(id: String): ProviderConfig?

    /** 新增或覆盖(按 id 匹配)。 */
    suspend fun save(config: ProviderConfig)

    /** 按 id 删除。 */
    suspend fun delete(id: String)

    /** Flow 实时刷新,UI 可订阅观察。 */
    fun observeAll(): Flow<List<ProviderConfig>>

    /** 写操作完成后回调(providerId = "" 表示全量变化,适用于 delete 不可枚举时)。 */
    var onInvalidate: (providerId: String) -> Unit
}

private val Context.customProviderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "writingwithai_custom_providers"
)

class CustomProviderStoreImpl(
    private val context: Context
) : CustomProviderStore {
    private val json = Json { ignoreUnknownKeys = true }

    override var onInvalidate: (String) -> Unit = {}

    private fun decode(raw: String?): List<ProviderConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ProviderConfig>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "decode custom providers failed, treating as empty", e)
            emptyList()
        }
    }

    override suspend fun getAll(): List<ProviderConfig> = context.customProviderDataStore.data
        .map { decode(it[KEY_CUSTOM_PROVIDERS]) }
        .first()

    override suspend fun getById(id: String): ProviderConfig? = getAll().find { it.id == id }

    override suspend fun save(config: ProviderConfig) {
        context.customProviderDataStore.edit { prefs ->
            val list = decode(prefs[KEY_CUSTOM_PROVIDERS]).toMutableList()
            val idx = list.indexOfFirst { it.id == config.id }
            if (idx >= 0) list[idx] = config else list.add(config)
            prefs[KEY_CUSTOM_PROVIDERS] = json.encodeToString(list.toList())
        }
        onInvalidate(config.id)
    }

    override suspend fun delete(id: String) {
        context.customProviderDataStore.edit { prefs ->
            val list = decode(prefs[KEY_CUSTOM_PROVIDERS]).filterNot { it.id == id }
            prefs[KEY_CUSTOM_PROVIDERS] = json.encodeToString(list)
        }
        onInvalidate(id)
    }

    override fun observeAll(): Flow<List<ProviderConfig>> = context.customProviderDataStore.data
        .map { decode(it[KEY_CUSTOM_PROVIDERS]) }

    companion object {
        private const val TAG = "CustomProviderStore"
        private val KEY_CUSTOM_PROVIDERS = stringPreferencesKey("custom_providers_json")
    }
}
