package com.yy.writingwithai.core.ai.provider

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArraySet
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
 * spec: 用户创建的自定义模型与内置模型平等展示， 可选中 / 测试连通 / 编辑 / 删除。
 *
 * 写操作(read-modify-write)全部在 `edit { }` lambda 内，DataStore 串行化写，
 * 避免两个并发 save/delete 互相覆盖。回调 [addInvalidateListener] 让
 * [com.yy.writingwithai.core.ai.CoreAiGateway] 在每次写入后清 adapter 缓存。
 *
 * review r1 M1:用 CopyOnWriteArraySet 维护多 listener，支持多 caller 订阅且线程安全，
 * 替代原来单一 `var onInvalidate: ...` 在 Hilt 多次创建时互相覆盖。
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

    /** Flow 实时刷新，UI 可订阅观察。 */
    fun observeAll(): Flow<List<ProviderConfig>>

    /** 注册一个 invalidate 监听;同一 listener 多次添加只算一次。 */
    fun addInvalidateListener(listener: (providerId: String) -> Unit)

    /** 移除一个先前注册的 invalidate 监听。 */
    fun removeInvalidateListener(listener: (providerId: String) -> Unit)
}

private val Context.customProviderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "writingwithai_custom_providers"
)

class CustomProviderStoreImpl(
    // fix-review-r4 L6:显式 @ApplicationContext，防御性标记 — 当前由 PrefsModule @Provides
    // 注入(已保证)，若未来改 @Inject constructor 则此注解生效。
    @ApplicationContext private val context: Context
) : CustomProviderStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val invalidateListeners = CopyOnWriteArraySet<(String) -> Unit>()

    override fun addInvalidateListener(listener: (String) -> Unit) {
        invalidateListeners.add(listener)
    }

    override fun removeInvalidateListener(listener: (String) -> Unit) {
        invalidateListeners.remove(listener)
    }

    private fun fireInvalidate(providerId: String) {
        invalidateListeners.forEach { listener ->
            // fix-review-r3-medium M5:`runCatching` 会吞 CancellationException，导致
            // 协程取消传播到 listener 时被截断;改成显式 catch 普通异常，让 CancellationException
            // 沿调用栈往外抛(由 save/delete 的 suspend caller 接收)。
            try {
                listener(providerId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "invalidate listener threw: ${e.javaClass.simpleName}")
            }
        }
    }

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
        fireInvalidate(config.id)
    }

    override suspend fun delete(id: String) {
        context.customProviderDataStore.edit { prefs ->
            val list = decode(prefs[KEY_CUSTOM_PROVIDERS]).filterNot { it.id == id }
            prefs[KEY_CUSTOM_PROVIDERS] = json.encodeToString(list)
        }
        fireInvalidate(id)
    }

    override fun observeAll(): Flow<List<ProviderConfig>> = context.customProviderDataStore.data
        .map { decode(it[KEY_CUSTOM_PROVIDERS]) }

    companion object {
        private const val TAG = "CustomProviderStore"
        private val KEY_CUSTOM_PROVIDERS = stringPreferencesKey("custom_providers_json")
    }
}
