package com.yy.writingwithai.feature.settings.model

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.api.ProviderDescriptor
import com.yy.writingwithai.core.ai.fake.FakeAiProvider
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.ProviderConfig
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
 * - 合并内置 + 自定义 provider 列表(builtin 来自 aiGateway.listProviders()，避免双份硬编码)
 * - deleteCustomProvider: 清 apikey 在前，删 config 在后(cleanup 顺序)
 * - getProviderConfig(providerId): suspend，无 runBlocking(走 DataStore 异步 IO)
 * - ping: 用真实 config.defaultModel，不再传 "default"
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

    private companion object {
        // review-H2:builtin provider id 单源 — 同步两份会随 provider 增删漏改。
        // 静态列表覆盖默认三家;mimo/deepseek/minimax 由 [aiGateway.listProviders()]
        // 决定顺序与存在性，本列表仅在 init 块用，顺序无所谓。
        val BUILTIN_PROVIDER_IDS = listOf("deepseek", "minimax", "mimo")

        // review-L1:提取 TAG，避免字符串字面量散落 + 拼写漂移。
        const val TAG = "ModelMgmtVM"
    }
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
            // fix-2026-06-24-review-r1-critical:selected 可能为 null(首次安装未配置)
            val hasKey = selected != null && selected != FakeAiProvider.PROVIDER_ID && secureApiKeyStore.has(selected)
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
        // ux-2026-06-28 #2:模型管理卡片显示「当前已选 model」。combine 监听 builtin + custom
        // 每个 provider 的 observeSelectedModel，任意一个写 prefs 都会刷新 map。
        // builtin 列表走 [BUILTIN_PROVIDER_IDS] 单源，custom 变化时由内层 combine 重建订阅集合。
        // review-M2:每个 observeSelectedModel 加 catch { emit(null) }，单 provider 抛错不
        // 取消整订阅。
        viewModelScope.launch {
            customProviderStore.observeAll().collect { customs ->
                val ids = BUILTIN_PROVIDER_IDS + customs.map { it.id }
                if (ids.isEmpty()) {
                    _state.update { it.copy(selectedModelByProvider = emptyMap()) }
                } else {
                    combine(
                        ids.map { id ->
                            providerPrefsStore.observeSelectedModel(id)
                                .map { id to it }
                                .catch { e ->
                                    // review-M2:单 provider 抛错不影响其他订阅。
                                    android.util.Log.w(
                                        TAG,
                                        "observeSelectedModel($id) failed, isolating with null",
                                        e
                                    )
                                    emit(id to null)
                                }
                        }
                    ) { pairs ->
                        pairs.toMap().filterValues { it != null }.mapValues { it.value!! }
                    }.collect { map ->
                        _state.update { it.copy(selectedModelByProvider = map) }
                    }
                }
            }
        }
        // fix-2026-06-28-ai-model-selection-actually-used:启动懒加载 — 扫所有已配 apikey
        // 但 selectedModel_<id> 为空的 provider，补写 defaultModel。处理存量用户(改
        // change 前已设 apikey 但没选过 model,getSelectedModel 返回 null，会走
        // gateway fallback → 错调 list[0] 而非 defaultModel)。失败 Log 不阻塞启动。
        // review-H2:走 [BUILTIN_PROVIDER_IDS] 单源。
        // review-L2:coroutineScope + async 并发，N provider 自举不再串行。
        viewModelScope.launch {
            val customIds = customProviderStore.getAll().map { it.id }
            val allIds = BUILTIN_PROVIDER_IDS + customIds
            coroutineScope {
                allIds
                    .map { id ->
                        async {
                            if (!secureApiKeyStore.has(id)) return@async
                            val current = try {
                                providerPrefsStore.getSelectedModel(id)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    TAG,
                                    "getSelectedModel($id) failed during init",
                                    e
                                )
                                return@async
                            }
                            if (!current.isNullOrBlank()) return@async
                            val cfg = getProviderConfig(id)
                            val default = cfg?.defaultModel
                            if (default.isNullOrBlank()) return@async
                            try {
                                providerPrefsStore.setSelectedModelIfMissing(id, default)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    TAG,
                                    "init: setSelectedModelIfMissing($id, $default) failed",
                                    e
                                )
                            }
                        }
                    }.awaitAll()
            }
        }
    }

    /** 合并内置 + 自自定义 provider 描述列表(suspend)。builtin 来自 gateway，避免硬编码。 */
    suspend fun providerDescriptors(): List<ProviderDescriptor> {
        val builtin = aiGateway.listProviders()
        val customIds = builtin.map { it.id }.toSet()
        val custom = _state.value.customProviders.map { config ->
            ProviderDescriptor(
                id = config.id,
                displayName = config.displayName,
                models = config.supportedModels,
                isConfigured = true,
                // fix-2026-06-28-ai-model-selection-actually-used:custom 走 ProviderConfig.defaultModel。
                defaultModel = config.defaultModel
            )
        }.filter { it.id !in customIds } // 防 builtin 同名(防御性，gateway 已先返回 builtin)
        return builtin + custom
    }

    /** 供详情页统一获取 provider 配置(内置返回静态，自定义返回 DataStore)。suspend，无 runBlocking。 */
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

    /** 保存 apikey + 切换 selected providerId;可选 model 同步落 selectedModel。
     *
     * fix-2026-06-26-review-r3 H23:原实现 setSelectedProviderId 失败回滚时，自己的 try-catch
     * 静默吞;且 setSelectedProviderId 已在第一步持久化，失败回滚到旧值，可能在旧值
     * 与新值都已落盘后，用户看到不一致。改为"先 backup → 内存态 → 全成功才持久化":
     * 把 selectedProviderId 的更新推迟到最后一步;中间任何一步失败 → 不持久化 selected,
     * 内存态同步回到原值。
     *
     * ux-2026-06-28 #1:apikey 切换模型 — 当 apiKey 为空但 provider 已有 key,
     * 跳过 apikey 写盘(避免覆盖成空)，只更新 selected + model。
     */
    fun saveProvider(providerId: String, apiKey: String, model: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(lastSaveResult = SaveResult.InProgress) }
            val previousSelected = _state.value.selectedProviderId
            // 仅更新内存态，先不持久化。
            _state.update { it.copy(selectedProviderId = providerId) }
            val skipKeyWrite = apiKey.isBlank() && secureApiKeyStore.has(providerId)
            if (!skipKeyWrite) {
                try {
                    secureApiKeyStore.save(providerId, apiKey)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 内存态回滚到原值;不持久化任何东西。
                    _state.update { it.copy(selectedProviderId = previousSelected) }
                    val result = SaveResult.Failed(
                        messageRes = R.string.model_management_error_unknown,
                        rawDetail = e.message,
                        operationKind = SaveResult.OperationKind.SAVE
                    )
                    _state.update { it.copy(lastSaveResult = result) }
                    _saveEvents.tryEmit(result)
                    return@launch
                }
            }
            // apikey 落盘成功 → 才落 selected。
            try {
                providerPrefsStore.setSelectedProviderId(providerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // setSelected 失败:apikey 已落 → 内存态 selected 回滚，UI 反映旧 selected;
                // 但 apikey 已存，下次重启仍会用 providerId，需 Log 告知用户。
                _state.update { it.copy(selectedProviderId = previousSelected) }
                android.util.Log.e(
                    TAG,
                    "setSelectedProviderId($providerId) failed; apikey persisted without selection",
                    e
                )
                val result = SaveResult.Failed(
                    messageRes = R.string.model_management_error_unknown,
                    rawDetail = e.message,
                    operationKind = SaveResult.OperationKind.SAVE
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
            // fix-2026-06-28-ai-model-selection-actually-used:apikey + selectedProviderId
            // 落盘成功后，自举 selectedModel(若 key 不存在)。不覆盖用户显式选择;
            // 若用户传了 model,setSelectedModel 已落 key，这里 setSelectedModelIfMissing 跳过。
            val cfg = getProviderConfig(providerId)
            val bootstrapModel = cfg?.defaultModel
            if (!bootstrapModel.isNullOrBlank()) {
                try {
                    providerPrefsStore.setSelectedModelIfMissing(providerId, bootstrapModel)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 自举失败不阻塞 save 成功反馈(下次启动 init 块会再试)
                    android.util.Log.e(
                        TAG,
                        "setSelectedModelIfMissing($providerId) failed",
                        e
                    )
                }
            }
            // model-management-detail-dropdown: model 非空时同步落 per-provider selectedModel。
            if (model != null) {
                try {
                    providerPrefsStore.setSelectedModel(providerId, model)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 静默:apikey 已落，model 写失败不阻塞 save 成功反馈
                }
            }
            val success = SaveResult.Success
            _state.update { it.copy(lastSaveResult = success) }
            _saveEvents.tryEmit(success)
        }
    }

    /**
     * model-management-detail-dropdown: 详情页切换模型下拉时调用。
     *
     * fix-2026-06-28-ai-model-selection-actually-used:改为 `suspend fun` + 失败 emit
     * [SaveResult.Failed] 走 [OperationKind.MODEL_SELECT] 分支，UI 弹「模型切换失败」
     * Snackbar。写 prefs 成功 → 静默(纯本地切换，无 save 事件);写失败 → emit 失败事件。
     *
     * 调用方需在 `viewModelScope.launch` 包装(Composable 用 `rememberCoroutineScope`)。
     */
    suspend fun onModelSelected(providerId: String, model: String) {
        try {
            providerPrefsStore.setSelectedModel(providerId, model)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setSelectedModel $providerId/$model failed", e)
            _saveEvents.tryEmit(
                SaveResult.Failed(
                    messageRes = R.string.model_management_dropdown_save_failed,
                    rawDetail = e.message,
                    operationKind = SaveResult.OperationKind.MODEL_SELECT
                )
            )
        }
    }

    /** model-management-detail-dropdown: 详情屏进屏时回填 selectedModel(无值时 UI 回退 defaultModel)。 */
    suspend fun loadSelectedModel(providerId: String): String? {
        return providerPrefsStore.getSelectedModel(providerId)
    }

    /** X 方案:详情页切 OpenAI/Anthropic 兼容协议时调用。写 prefs，无 save 事件。 */
    fun onApiFormatSelected(providerId: String, format: ApiFormat) {
        viewModelScope.launch {
            try {
                providerPrefsStore.setApiFormat(providerId, format)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // M15 修:同上，Log 留 trace。
                android.util.Log.e(TAG, "setApiFormat $providerId/$format failed", e)
            }
        }
    }

    /** X 方案:详情屏回填当前 apiFormat(prefs 覆盖或 fallback config.apiFormat)。 */
    suspend fun loadApiFormat(providerId: String): ApiFormat {
        val override = providerPrefsStore.getApiFormat(providerId)
        if (override != null) return override
        val cfg = getProviderConfig(providerId)
        return cfg?.apiFormat ?: ApiFormat.ANTHROPIC
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 静默，UI 重新拉取
            }
            if (_state.value.selectedProviderId == providerId) {
                try {
                    providerPrefsStore.setSelectedProviderId(FakeAiProvider.PROVIDER_ID)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
                _state.update { it.copy(selectedProviderId = FakeAiProvider.PROVIDER_ID) }
            }
        }
    }

    /** 测连通。缺 apikey 立即 fail;错误细节从 gateway 直传 UI。用 config.defaultModel。 */
    fun ping(providerId: String?) {
        if (providerId == null) return
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
            // model-management-detail-dropdown: 优先用用户在详情页选的 model，无值回退 config 默认
            val effectiveModel = loadSelectedModel(providerId)
                ?: config?.defaultModel
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
                    // M5 fix:移除冗余 CancellationException 检查 — 已在上方 catch 过
                    PingResult.Failed(R.string.model_management_error_unknown, e.message)
                }
            _state.update { it.copy(pingResult = mapped) }
        }
    }
}

data class ModelManagementUiState(
    val selectedProviderId: String? = null,
    val hasApiKeyForSelected: Boolean = false,
    val pingResult: PingResult = PingResult.Idle,
    val configuredProviderIds: Set<String> = emptySet(),
    val customProviders: List<ProviderConfig> = emptyList(),
    val lastSaveResult: SaveResult = SaveResult.Idle,
    // ux-2026-06-28 #2:模型管理卡片显示「当前已选 model」，不再固定「默认」;
    // 订阅每个 provider 的 observeSelectedModel 实时更新，未设置(null)走 defaultModel 兜底。
    val selectedModelByProvider: Map<String, String> = emptyMap()
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

    /**
     * fix-2026-06-28-ai-model-selection-actually-used:区分 [Failed] 来源，UI 据此选不同文案:
     * - [SAVE] — apikey / selectedProviderId 落盘失败
     * - [MODEL_SELECT] — 详情页下拉选 model 时 setSelectedModel 写盘失败
     */
    enum class OperationKind { SAVE, MODEL_SELECT }

    data class Failed(
        @StringRes val messageRes: Int,
        val rawDetail: String?,
        // 默认 SAVE，向后兼容老 caller;新 caller 显式传 MODEL_SELECT。
        val operationKind: OperationKind = OperationKind.SAVE
    ) : SaveResult
}
