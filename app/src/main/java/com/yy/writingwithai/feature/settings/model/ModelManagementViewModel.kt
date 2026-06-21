package com.yy.writingwithai.feature.settings.model

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.ProviderDescriptor
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.ProviderConfig
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * provider-real-integration · ui-redesign-m5-glass · 模型管理 VM。
 *
 * fix-ai-config-ux: SaveResult / Channel 事件流 / configuredProviderIds 实时 / selectProvider 不导航。
 * C4: VM 文案走 @StringRes + rawDetail。
 * Polish F-17: CancellationException 显式 rethrow。
 *
 * M6 custom-model:
 * - 合并内置 + 自定义 provider 列表(builtin 来自 aiGateway.listProviders(),避免双份硬编码)
 * - deleteCustomProvider: 清 apikey 在前,删 config 在后(cleanup 顺序)
 * - getProviderConfig(providerId): suspend,无 runBlocking(走 DataStore 异步 IO)
 * - ping: 用真实 config.defaultModel,不再传 "default"
 */
@HiltViewModel
class ModelManagementViewModel
@Inject
constructor(
    private val secureApiKeyStore: SecureApiKeyStore,
    private val providerPrefsStore: ProviderPrefsStore,
    private val customProviderStore: CustomProviderStore,
    private val aiGateway: AiGateway
) : ViewModel() {
    private val _state = MutableStateFlow(ModelManagementUiState())
    val state: StateFlow<ModelManagementUiState> = _state.asStateFlow()

    private val _saveEvents = MutableSharedFlow<SaveResult>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val saveEvents: SharedFlow<SaveResult> = _saveEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val selected = providerPrefsStore.getSelectedProviderId()
            val hasKey = selected != "fake" && secureApiKeyStore.has(selected)
            val initialConfigured = secureApiKeyStore.observeConfiguredProviders().first()
            val initialCustom = customProviderStore.getAll()
            _state.update {
                it.copy(
                    selectedProviderId = selected,
                    hasApiKeyForSelected = hasKey,
                    configuredProviderIds = initialConfigured,
                    customProviders = initialCustom
                )
            }
        }
        viewModelScope.launch {
            secureApiKeyStore.observeConfiguredProviders().collect { ids ->
                _state.update { it.copy(configuredProviderIds = ids) }
            }
        }
        viewModelScope.launch {
            customProviderStore.observeAll().collect { configs ->
                _state.update { it.copy(customProviders = configs) }
            }
        }
    }

    /** 合并内置 + 自自定义 provider 描述列表(suspend)。builtin 来自 gateway,避免硬编码。 */
    suspend fun providerDescriptors(): List<ProviderDescriptor> {
        val builtin = aiGateway.listProviders()
        val customIds = builtin.map { it.id }.toSet()
        val custom = _state.value.customProviders.map { config ->
            ProviderDescriptor(
                id = config.id,
                displayName = config.displayName,
                models = config.supportedModels,
                isConfigured = true
            )
        }.filter { it.id !in customIds } // 防 builtin 同名(防御性,gateway 已先返回 builtin)
        return builtin + custom
    }

    /** 供详情页统一获取 provider 配置(内置返回静态,自定义返回 DataStore)。suspend,无 runBlocking。 */
    suspend fun getProviderConfig(providerId: String): ProviderConfig? {
        return when (providerId) {
            "deepseek" -> com.yy.writingwithai.core.ai.provider.deepseek.DeepseekConfig.config
            "minimax" -> com.yy.writingwithai.core.ai.provider.minimax.MinimaxConfig.config
            "mimo" -> com.yy.writingwithai.core.ai.provider.mimo.MimoConfig.config
            else -> customProviderStore.getById(providerId)
        }
    }

    /** 切换选中 provider。持久化失败保持旧 selectedProviderId。 */
    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            try {
                providerPrefsStore.setSelectedProviderId(providerId)
                _state.update { it.copy(selectedProviderId = providerId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 写失败保持旧值
            }
        }
    }

    /** 保存 apikey + 切换 selected providerId。 */
    fun saveProvider(providerId: String, apiKey: String) {
        viewModelScope.launch {
            _state.update { it.copy(lastSaveResult = SaveResult.InProgress) }
            try {
                secureApiKeyStore.save(providerId, apiKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val result = SaveResult.Failed(
                    messageRes = R.string.model_management_error_unknown,
                    rawDetail = e.message
                )
                _state.update { it.copy(lastSaveResult = result) }
                _saveEvents.tryEmit(result)
                return@launch
            }
            try {
                providerPrefsStore.setSelectedProviderId(providerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val result = SaveResult.Failed(
                    messageRes = R.string.model_management_error_unknown,
                    rawDetail = e.message
                )
                _state.update { it.copy(lastSaveResult = result) }
                _saveEvents.tryEmit(result)
                return@launch
            }
            _state.update {
                it.copy(
                    selectedProviderId = providerId,
                    hasApiKeyForSelected = true,
                    pingResult = PingResult.Idle
                )
            }
            val success = SaveResult.Success
            _state.update { it.copy(lastSaveResult = success) }
            _saveEvents.tryEmit(success)
        }
    }

    /**
     * 删自定义 provider。顺序:清 apikey 在前 → 删 config。
     * store 写后回调 CoreAiGateway 清 adapter 缓存。
     */
    fun deleteCustomProvider(providerId: String) {
        viewModelScope.launch {
            try {
                secureApiKeyStore.clear(providerId)
                customProviderStore.delete(providerId)
            } catch (_: Exception) {
                // 静默,UI 重新拉取
            }
            if (_state.value.selectedProviderId == providerId) {
                try {
                    providerPrefsStore.setSelectedProviderId("fake")
                } catch (_: Exception) {}
                _state.update { it.copy(selectedProviderId = "fake") }
            }
        }
    }

    /** 测连通。缺 apikey 立即 fail;错误细节从 gateway 直传 UI。用 config.defaultModel。 */
    fun ping(providerId: String) {
        viewModelScope.launch {
            _state.update { it.copy(pingResult = PingResult.InProgress) }
            val apikey = secureApiKeyStore.get(providerId)
            if (apikey == null) {
                _state.update {
                    it.copy(
                        pingResult = PingResult.Failed(
                            R.string.model_management_error_no_apikey,
                            null
                        )
                    )
                }
                return@launch
            }
            val config = getProviderConfig(providerId)
            val effectiveModel = config?.defaultModel
                ?: com.yy.writingwithai.core.ai.provider.deepseek.DeepseekConfig.config.defaultModel
            val start = System.currentTimeMillis()
            val mapped: PingResult =
                try {
                    val reason = aiGateway.ping(
                        providerId = providerId,
                        apikey = apikey,
                        modelName = effectiveModel
                    )
                    if (reason == null) {
                        PingResult.Success(System.currentTimeMillis() - start)
                    } else {
                        PingResult.Failed(R.string.model_management_error_unknown, reason)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PingResult.Failed(R.string.model_management_error_unknown, e.message)
                }
            _state.update { it.copy(pingResult = mapped) }
        }
    }
}

data class ModelManagementUiState(
    val selectedProviderId: String = "fake",
    val hasApiKeyForSelected: Boolean = false,
    val pingResult: PingResult = PingResult.Idle,
    val configuredProviderIds: Set<String> = emptySet(),
    val customProviders: List<ProviderConfig> = emptyList(),
    val lastSaveResult: SaveResult = SaveResult.Idle
)

sealed interface PingResult {
    data object Idle : PingResult
    data object InProgress : PingResult
    data class Success(val latencyMs: Long) : PingResult
    data class Failed(@StringRes val messageRes: Int, val rawDetail: String?) : PingResult
}

sealed interface SaveResult {
    data object Idle : SaveResult
    data object InProgress : SaveResult
    data object Success : SaveResult
    data class Failed(@StringRes val messageRes: Int, val rawDetail: String?) : SaveResult
}
