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
            // ux-2026-06-28 #3:endpoint + apiFormat 不再出现在表单(完整 URL 输入,
            // 协议只走 anthropic 兼容),回填时跳过这两个字段。
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
                    customHeaders = config.customHeaders
                )
            }
        }
    }

    /**
     * 显示名称变化时自动生成 providerId(kebab-case)。
     * - 新建模式:实时跟随,生成一次就锁定避免用户编辑 id 时被覆盖
     * - 编辑模式:不动(保留原 id)
     *
     * fix-2026-06-26-review-r3 H16:regex `[^a-z0-9\\s-]` 在中英混合输入时把中文段全部删掉,
     * 极端情况下(`我的笔记`)name 完全是中文 → 派生 id 是空字符串 → 落库后 provider.id 冲突。
     * 改为"派生 id 为空时,基于 name 的 hash + 短随机后缀兜底",保证 id 始终非空且不可预测。
     */
    fun onDisplayNameChanged(name: String) {
        val current = _state.value
        val newId = if (!current.isEditMode && !current.idLocked) {
            val derived = name.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .trim('-')
            if (derived.isBlank()) {
                // H16 fix:派生 id 为空(全中文 / 全符号)→ 用 name 的稳定 hash + 短随机
                // 后缀兜底,避免空 id 写入 store。
                val salt = (0..3)
                    .map { kotlin.random.Random.nextInt(0, RANDOM_SALT_MASK).toString(16).padStart(4, '0') }
                    .joinToString("")
                "provider-${name.hashCode().toString(16).removePrefix("-")}-$salt"
            } else {
                derived
            }
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
    fun toggleModelsAuthExpanded() = _state.update {
        it.copy(isModelsAuthExpanded = !it.isModelsAuthExpanded)
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
                // ux-2026-06-28 #3:协议只走 anthropic 兼容,不再传 apiFormatOverride。
                coreAiGateway.ping(
                    providerId = config.id,
                    apikey = s.apiKey,
                    modelName = config.defaultModel
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
        if (apiKey.isBlank() && !s.isEditMode) {
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
            // ux-2026-06-28 P1:编辑模式空 apiKey + 已有密钥 → 跳过 key 写入。
            val skipKeyWrite = apiKey.isBlank() && s.isEditMode && secureApiKeyStore.has(s.providerId)
            if (apiKey.isBlank() && !skipKeyWrite) {
                _events.tryEmit(
                    CustomProviderEditEvent.SaveFailed(Res(R.string.custom_provider_error_missing_apikey))
                )
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            // fix-2026-06-26-review-r3 H17:原顺序 `save(config) → save(key) → setSelected` —
            // 第二步失败 → config 已落库 + key 缺失,UI 假成功。
            // 改为:key 先 → config 后 → selectedProviderId 最后;失败时回滚已成功步骤。
            var keySaved = false
            var configSaved = false
            try {
                // ux-2026-06-28 P1:编辑模式已有密钥 → 跳过 key 写入,保留原值。
                if (!skipKeyWrite) {
                    secureApiKeyStore.save(config.id, apiKey)
                    keySaved = true
                }
                customProviderStore.save(config)
                configSaved = true
                providerPrefsStore.setSelectedProviderId(config.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 回滚:已落盘的 key / config 撤回,避免用户界面假成功但 store 半挂。
                if (configSaved) {
                    runCatching { customProviderStore.delete(config.id) }
                }
                if (keySaved) {
                    runCatching { secureApiKeyStore.clear(config.id) }
                }
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
        // ux-2026-06-28 #3:完整 URL 输入 — baseUrl 已是用户给的完整地址,
        // 不再追加 path;endpointPath 留空让 adapter 直用 baseUrl(见 AnthropicCompatibleAdapter)。
        return ProviderConfig(
            id = id,
            displayName = name,
            baseUrl = url.removeSuffix("/"),
            endpointPath = "",
            authStyle = s.authStyle,
            customAuthHeaderName = s.customAuthHeaderName.trim().ifBlank { null },
            defaultModel = model,
            supportedModels = models,
            customHeaders = s.customHeaders,
            apiFormat = ApiFormat.ANTHROPIC
        )
    }

    private fun Res(@StringRes id: Int, detail: String? = null): ErrorReason = ErrorReason(id, detail)

    companion object {
        val BUILTIN_IDS = setOf("deepseek", "minimax", "mimo")

        // fix-2026-06-26-review-r3 LOW:随机盐掩码常量,避免 magic number 0xFFFF。
        private const val RANDOM_SALT_MASK = 0xFFFF
    }
}

data class CustomProviderEditUiState(
    val displayName: String = "",
    val providerId: String = "",
    val idLocked: Boolean = false,
    // ux-2026-06-28 #3:baseUrl 改成"完整 URL" — 用户输入 https://api.example.com/v1/messages,
    // adapter 不再追加 path(endpointPath 留空,见 buildConfig)。
    val baseUrl: String = "",
    val apiKey: String = "",
    val authStyle: AuthStyle = AuthStyle.AUTHORIZATION,
    val customAuthHeaderName: String = "",
    val defaultModel: String = "",
    val supportedModels: List<String> = emptyList(),
    val newModelInput: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    // ux-2026-06-28 #3:协议只走 anthropic 兼容,不再需要模型&认证段折叠;保留字段避免破坏
    // 旧 state 结构,默认收起对用户无感(屏幕上不再用)。
    val isModelsAuthExpanded: Boolean = false,
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
