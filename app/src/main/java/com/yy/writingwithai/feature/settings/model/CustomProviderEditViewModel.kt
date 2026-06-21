package com.yy.writingwithai.feature.settings.model

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.provider.AnthropicCompatibleAdapter
import com.yy.writingwithai.core.ai.provider.AuthStyle
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.ProviderConfig
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * M6 custom-model · 自定义 Provider 编辑表单 VM。
 *
 * 支持新建 + 编辑两种模式:
 * - providerId == null → 新建:空白表单 + 保存后选中
 * - providerId != null → 编辑:预填已有配置 + 保存后覆盖
 *
 * 表单内 ping 用表单字段临时构造 [AnthropicCompatibleAdapter] 调 stream,
 * 不经过持久化, 让用户填完立即验证连通性。
 *
 * 校验:
 * - providerId 不得与内置 id(deepseek/minimax/mimo)冲突
 * - CUSTOM_HEADER 认证必须填 customAuthHeaderName
 * - loadExisting 找不到记录时视为"该 provider 已不存在",触发 SaveFailed + 退出
 */
@HiltViewModel
class CustomProviderEditViewModel
@Inject
constructor(
    private val customProviderStore: CustomProviderStore,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val providerPrefsStore: ProviderPrefsStore,
    @Named("ai") private val okHttpClient: OkHttpClient,
    // H9 新增:走 gateway.ping 让 ai_history 记录(observability),
    // 同时不在此 VM 临时构造 AnthropicCompatibleAdapter(provider lifecycle 由 gateway 管理)。
    private val coreAiGateway: AiGateway
) : ViewModel() {
    private val _state = MutableStateFlow(CustomProviderEditUiState())
    val state: StateFlow<CustomProviderEditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CustomProviderEditEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CustomProviderEditEvent> = _events.asSharedFlow()

    /**
     * 加载编辑模式下已有配置。找不到时 emit SaveFailed + NotFound 退出编辑模式。
     */
    fun loadExisting(providerId: String) {
        viewModelScope.launch {
            val config = customProviderStore.getById(providerId)
            if (config == null) {
                _state.update { it.copy(isEditMode = false) }
                _events.tryEmit(
                    CustomProviderEditEvent.SaveFailed(
                        Res(R.string.custom_provider_error_not_found)
                    )
                )
                return@launch
            }
            val savedKey = secureApiKeyStore.get(providerId).orEmpty()
            _state.update {
                it.copy(
                    isEditMode = true,
                    providerId = config.id,
                    displayName = config.displayName,
                    baseUrl = config.baseUrl,
                    apiKey = savedKey,
                    authStyle = config.authStyle,
                    customAuthHeaderName = config.customAuthHeaderName.orEmpty(),
                    defaultModel = config.defaultModel,
                    supportedModels = config.supportedModels,
                    endpointPath = config.endpointPath,
                    apiFormat = config.apiFormat,
                    customHeaders = config.customHeaders
                )
            }
        }
    }

    /**
     * 显示名称变化时自动生成 providerId(kebab-case)。
     * - 新建模式:实时跟随,生成一次就锁定避免用户编辑 id 时被覆盖
     * - 编辑模式:不动(保留原 id)
     */
    fun onDisplayNameChanged(name: String) {
        val current = _state.value
        val newId = if (!current.isEditMode && !current.idLocked) {
            name.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .trim('-')
        } else {
            current.providerId
        }
        _state.update {
            it.copy(
                displayName = name,
                providerId = newId,
                idLocked = !it.isEditMode && name.isNotBlank()
            )
        }
    }

    fun onProviderIdChanged(id: String) = _state.update { it.copy(providerId = id, idLocked = true) }
    fun onBaseUrlChanged(url: String) = _state.update { it.copy(baseUrl = url) }
    fun onApiKeyChanged(key: String) = _state.update { it.copy(apiKey = key) }
    fun onAuthStyleChanged(style: AuthStyle) = _state.update { it.copy(authStyle = style) }
    fun onCustomAuthHeaderNameChanged(name: String) = _state.update { it.copy(customAuthHeaderName = name) }
    fun onDefaultModelChanged(model: String) = _state.update { it.copy(defaultModel = model) }
    fun onEndpointPathChanged(path: String) = _state.update { it.copy(endpointPath = path) }
    fun onApiFormatChanged(format: ApiFormat) = _state.update { it.copy(apiFormat = format) }
    fun toggleModelsAuthExpanded() = _state.update {
        it.copy(isModelsAuthExpanded = !it.isModelsAuthExpanded)
    }
    fun toggleAdvancedExpanded() = _state.update {
        it.copy(isAdvancedExpanded = !it.isAdvancedExpanded)
    }
    fun toggleRevealApiKey() = _state.update { it.copy(revealApiKey = !it.revealApiKey) }
    fun clearSaving() = _state.update { it.copy(isSaving = false) }

    fun onNewModelInputChanged(input: String) = _state.update { it.copy(newModelInput = input) }

    fun addModel() {
        val model = _state.value.newModelInput.trim()
        if (model.isBlank()) return
        _state.update {
            if (it.supportedModels.contains(model)) {
                it.copy(newModelInput = "")
            } else {
                it.copy(supportedModels = it.supportedModels + model, newModelInput = "")
            }
        }
    }

    fun removeModel(model: String) {
        _state.update { it.copy(supportedModels = it.supportedModels - model) }
    }

    /** 表单内 ping:临时构造 adapter,不持久化。 */
    fun pingFromForm() {
        val s = _state.value
        val config = buildConfig(s)
        if (config == null) {
            _state.update {
                it.copy(pingResult = PingResult.Failed(R.string.custom_provider_error_missing_fields, null))
            }
            return
        }
        if (s.apiKey.isBlank()) {
            _state.update {
                it.copy(pingResult = PingResult.Failed(R.string.custom_provider_error_missing_apikey, null))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(pingResult = PingResult.InProgress) }
            val start = System.currentTimeMillis()
            // H9 修:走 coreAiGateway.ping(内部 observer 写 ai_history,符合 CLAUDE.md "Token / 成本可观测" 规则)。
            // apikey 走局部变量,函数退出 GC,不延长生命周期。
            val reason = try {
                coreAiGateway.ping(
                    providerId = config.id,
                    apikey = s.apiKey,
                    modelName = config.defaultModel,
                    apiFormatOverride = config.apiFormat
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: e::class.simpleName ?: "unknown exception"
            }
            val result = if (reason == null) {
                PingResult.Success(System.currentTimeMillis() - start)
            } else {
                PingResult.Failed(R.string.model_management_error_unknown, reason)
            }
            _state.update { it.copy(pingResult = result) }
        }
    }

    /** 保存:校验 + 持久化 + 选中。失败路径清 isSaving。 */
    fun save() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true) }
        val config = buildConfig(s)
        if (config == null) {
            _events.tryEmit(
                CustomProviderEditEvent.SaveFailed(Res(R.string.custom_provider_error_missing_fields))
            )
            _state.update { it.copy(isSaving = false) }
            return
        }
        val apiKey = s.apiKey.trim()
        if (apiKey.isBlank()) {
            _events.tryEmit(
                CustomProviderEditEvent.SaveFailed(Res(R.string.custom_provider_error_missing_apikey))
            )
            _state.update { it.copy(isSaving = false) }
            return
        }
        if (config.id in BUILTIN_IDS) {
            _events.tryEmit(
                CustomProviderEditEvent.SaveFailed(Res(R.string.custom_provider_error_id_builtin_collision))
            )
            _state.update { it.copy(isSaving = false) }
            return
        }
        viewModelScope.launch {
            try {
                customProviderStore.save(config)
                secureApiKeyStore.save(config.id, apiKey)
                providerPrefsStore.setSelectedProviderId(config.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(
                    CustomProviderEditEvent.SaveFailed(
                        Res(R.string.model_management_error_unknown, e.message)
                    )
                )
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            _state.update { it.copy(isSaving = false) }
            _events.tryEmit(CustomProviderEditEvent.Saved(config.id))
        }
    }

    private fun buildConfig(s: CustomProviderEditUiState): ProviderConfig? {
        val name = s.displayName.trim()
        val id = s.providerId.trim()
        val url = s.baseUrl.trim()
        val model = s.defaultModel.trim()
        if (name.isBlank() || id.isBlank() || url.isBlank() || model.isBlank()) return null
        if (s.authStyle == AuthStyle.CUSTOM_HEADER &&
            s.customAuthHeaderName.trim().isBlank()
        ) {
            return null
        }
        val models = if (s.supportedModels.isEmpty()) listOf(model) else s.supportedModels
        return ProviderConfig(
            id = id,
            displayName = name,
            baseUrl = url.removeSuffix("/"),
            endpointPath = s.endpointPath.trim().ifBlank { "/chat/completions" },
            authStyle = s.authStyle,
            customAuthHeaderName = s.customAuthHeaderName.trim().ifBlank { null },
            defaultModel = model,
            supportedModels = models,
            customHeaders = s.customHeaders,
            apiFormat = s.apiFormat
        )
    }

    private fun Res(@StringRes id: Int, detail: String? = null): ErrorReason = ErrorReason(id, detail)

    companion object {
        val BUILTIN_IDS = setOf("deepseek", "minimax", "mimo")
    }
}

data class CustomProviderEditUiState(
    val displayName: String = "",
    val providerId: String = "",
    val idLocked: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val authStyle: AuthStyle = AuthStyle.AUTHORIZATION,
    val customAuthHeaderName: String = "",
    val defaultModel: String = "",
    val supportedModels: List<String> = emptyList(),
    val newModelInput: String = "",
    val endpointPath: String = "/chat/completions",
    val apiFormat: ApiFormat = ApiFormat.ANTHROPIC,
    val customHeaders: Map<String, String> = emptyMap(),
    val isModelsAuthExpanded: Boolean = false,
    val isAdvancedExpanded: Boolean = false,
    val pingResult: PingResult = PingResult.Idle,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val revealApiKey: Boolean = false
)

data class ErrorReason(@StringRes val messageRes: Int, val rawDetail: String? = null)

sealed interface CustomProviderEditEvent {
    data class Saved(val providerId: String) : CustomProviderEditEvent
    data class SaveFailed(val reason: ErrorReason) : CustomProviderEditEvent
}
