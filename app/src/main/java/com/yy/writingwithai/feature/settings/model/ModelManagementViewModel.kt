package com.yy.writingwithai.feature.settings.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * provider-real-integration · ui-redesign-m5-glass · 模型管理 VM。
 */
@HiltViewModel
class ModelManagementViewModel
@Inject
constructor(
    private val secureApiKeyStore: SecureApiKeyStore,
    private val providerPrefsStore: ProviderPrefsStore,
    private val aiGateway: AiGateway
) : ViewModel() {
    private val _state = MutableStateFlow(ModelManagementUiState())
    val state: StateFlow<ModelManagementUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val selected = providerPrefsStore.getSelectedProviderId()
            val hasKey = selected != "fake" && secureApiKeyStore.has(selected)
            _state.update {
                it.copy(
                    selectedProviderId = selected,
                    hasApiKeyForSelected = hasKey
                )
            }
        }
    }

    /** 保存 apikey + 切换 selected providerId。 */
    fun saveProvider(providerId: String, apiKey: String) {
        viewModelScope.launch {
            secureApiKeyStore.save(providerId, apiKey)
            providerPrefsStore.setSelectedProviderId(providerId)
            _state.update {
                it.copy(
                    selectedProviderId = providerId,
                    hasApiKeyForSelected = true,
                    pingResult = PingResult.Idle
                )
            }
        }
    }

    /** 测连通。fix-m5-blockers: 同步取真 apikey 透传 gateway;缺 apikey 立即 fail。 */
    fun ping(providerId: String) {
        viewModelScope.launch {
            _state.update { it.copy(pingResult = PingResult.InProgress) }
            val apikey = secureApiKeyStore.get(providerId)
            if (apikey == null) {
                _state.update { it.copy(pingResult = PingResult.Failed("apikey 未配置")) }
                return@launch
            }
            val start = System.currentTimeMillis()
            val mapped =
                runCatching { aiGateway.ping(providerId, apikey = apikey, modelName = "default") }
                    .fold(
                        onSuccess = { ok ->
                            if (ok) {
                                PingResult.Success(System.currentTimeMillis() - start)
                            } else {
                                PingResult.Failed("apikey 无效或网络不通")
                            }
                        },
                        onFailure = { e -> PingResult.Failed(e.message ?: "未知错误") }
                    )
            _state.update { it.copy(pingResult = mapped) }
        }
    }
}

data class ModelManagementUiState(
    val selectedProviderId: String = "fake",
    val hasApiKeyForSelected: Boolean = false,
    val pingResult: PingResult = PingResult.Idle
)

sealed interface PingResult {
    data object Idle : PingResult
    data object InProgress : PingResult
    data class Success(val latencyMs: Long) : PingResult
    data class Failed(val reason: String) : PingResult
}
