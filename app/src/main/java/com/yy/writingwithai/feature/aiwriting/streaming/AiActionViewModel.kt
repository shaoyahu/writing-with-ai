package com.yy.writingwithai.feature.aiwriting.streaming

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.api.resolveActualModel
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.prefs.PromptTemplateStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 写作操作 ViewModel(M3 + ai-regenerate-versions 升级)。
 *
 * 持有 [AiActionUiState] 状态机。`start(versionCount=N)` 时串行调
 * [AiGateway.streamWritingOp] N 次(单次 n=1,Anthropic Messages API 不支持 n>1),
 * 共享同一 [lastVersionGroupId],按 position 0..N-1 写入。每条独立 Streaming →
 * Done / Failed;全部完成 → Done;部分 Done 其余仍在 Streaming → PartialDone;
 * 全部 Failed → Failed。所有版本跑完前已开始 Done 的位置可"早接受"。
 *
 * 公开 API:
 * - `start(op, sourceText, noteId, versionCount=3)` — 启动流(consent gate 在内)
 * - `selectVersion(position)` — 切换 tab 高亮某位置,不改 versions 内容
 * - `acceptReplace(position=0)` — 接受指定位置的版本,落库 + 写 lastAiOp;Failed 位置 no-op
 * - `reject()` — 拒绝,不替换,回到 Idle
 * - `cancel()` — Streaming 态取消
 * - `dismiss()` — 任何态关闭(等同 cancel + 状态重置)
 * - `regenerate()` / `retry()` — Done / Failed 态用上次 op / sourceText / noteId / versionCount 重跑
 *
 * M3 单版本行为完全兼容:`versionCount=1` → versions=[AiVersion(0, ...)],`acceptReplace()`
 * 默认参数 position=0 → 与原 `acceptReplace()` 行为一致。
 */
@HiltViewModel
class AiActionViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val aiGateway: AiGateway,
    private val noteRepository: NoteRepository,
    private val widgetUpdater: QuickNoteWidgetUpdater,
    private val consentStore: ConsentStore,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val promptTemplateStore: PromptTemplateStore,
    private val providerPrefsStore: ProviderPrefsStore,
    private val userPrefsStore: UserPrefsStore
) : ViewModel() {
    companion object {
        /** M4-4 默认 provider(SecureApiKeyStore.has(<id>) 决定走真 apikey)。 */
        const val DEFAULT_PROVIDER = "deepseek"

        /**
         * ai-regenerate-versions:versionCount 上限(≥ 1 ≤ 3)。=1 等价 M3 单版本;
         * >1 走 multi-version,共享 lastVersionGroupId。
         */
        const val MAX_VERSION_COUNT = 3
    }

    init {
        // review-M1:init 期 eager 拉一次 provider list,缓存 id → defaultModel,
        // start() 命中即用,避免每次 AI 调用穿透到 CustomProviderStore.getAll()。
        viewModelScope.launch {
            try {
                defaultModelsByProvider.value =
                    aiGateway.listProviders().associate { it.id to it.defaultModel }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 保持空 cache;start() 会在 cache miss 时 fallback inline 再拉一次。
            }
        }
    }

    private val _state = MutableStateFlow<AiActionUiState>(AiActionUiState.Idle)
    val state: StateFlow<AiActionUiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    // review-M1:defaultModel 缓存。
    private val defaultModelsByProvider = MutableStateFlow<Map<String, String>>(emptyMap())

    // M6 / M11 fix:`streamJob?.cancel()` 之后旧协程可能还在 `_state.update` 调用栈上,
    // 新协程已置 Streaming → 旧协程在取消检查点前最后 emit 一条 Delta / Failed 覆盖
    // 新状态。generation 计数器,emit 前比对,不一致就丢。
    // ai-regenerate-versions:沿用同一个 generation 控制 N-version 的串行 emit,
    // cancel 中途时所有版本残余事件都被 generation 检查过滤掉。
    private val streamGeneration = AtomicInteger(0)

    private var lastOp: WritingOp? = null
    private var lastSourceText: String? = null
    private var lastNoteId: String? = null
    private var lastVersionCount: Int = 1

    /**
     * ai-regenerate-versions:多版本生成的同组 id(UUID)。单版本 (versionCount=1) 时
     * 保留 null,gateway / repo record 时同样 null,DB 列 null = M3 单版本兼容行。
     * 撤回 regenerate / retry 时复用同组 id 便于历史聚合。
     */
    private var lastVersionGroupId: String? = null

    private var lastOriginalContent: String? = null

    /**
     * 启动一次 AI 写作操作。
     *
     * ai-regenerate-versions:`versionCount` ≥ 1 ≤ [MAX_VERSION_COUNT]。`>1` 时
     * 串行调 gateway N 次,共享 [lastVersionGroupId],按 position 0..N-1 标记;
     * 任一版本独立 Streaming → Done / Failed。`=1` 行为与 M3 一致(gateway groupId = null)。
     */
    fun start(op: WritingOp, sourceText: String, noteId: String, versionCount: Int = 1) {
        require(versionCount in 1..MAX_VERSION_COUNT) {
            "versionCount must be in 1..$MAX_VERSION_COUNT, got $versionCount"
        }
        streamJob?.cancel()
        val currentGeneration = streamGeneration.incrementAndGet()
        lastOp = op
        lastSourceText = sourceText
        lastNoteId = noteId
        lastVersionCount = versionCount
        // ai-regenerate-versions:多版本时分配共享 groupId,单版本留 null。
        lastVersionGroupId = if (versionCount > 1) UUID.randomUUID().toString() else null
        lastOriginalContent = null

        streamJob =
            viewModelScope.launch {
                val consented = consentStore.isConsented(BuildConfig.CONSENT_VERSION)
                if (!consented) {
                    if (streamGeneration.get() == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.UserConsentRequired)
                    }
                    return@launch
                }
                val acked = userPrefsStore.isApikeyPromptAcked()
                if (!acked) {
                    if (streamGeneration.get() == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.ApikeyPromptNotAcked)
                    }
                    return@launch
                }
                val providerId = providerPrefsStore.getSelectedProviderId()
                if (providerId == null) {
                    if (streamGeneration.get() == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.ProviderNotConfigured)
                    }
                    return@launch
                }
                val apikey = secureApiKeyStore.get(providerId)
                val apiFormatOverride = providerPrefsStore.getApiFormat(providerId)
                if (apikey == null) {
                    if (streamGeneration.get() == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.ProviderNotConfigured)
                    }
                    return@launch
                }
                val selectedModel = providerPrefsStore.getSelectedModel(providerId)
                val cachedDefault = defaultModelsByProvider.value[providerId]
                val defaultModel = if (!cachedDefault.isNullOrBlank()) {
                    cachedDefault
                } else {
                    aiGateway.listProviders()
                        .firstOrNull { it.id == providerId }
                        ?.defaultModel
                        .orEmpty()
                }
                val actualModel = resolveActualModel(selectedModel, defaultModel)
                val groupId = lastVersionGroupId
                val systemPrompt =
                    promptTemplateStore.getForOp(op) ?: DefaultPrompts.forOp(op)

                // 初始 Streaming 状态:所有 N 个版本占位 Streaming。selectedPosition 默认 0。
                if (streamGeneration.get() == currentGeneration) {
                    _state.value = AiActionUiState.Streaming(
                        op = op,
                        versions = buildInitialVersions(versionCount, actualModel),
                        selectedPosition = 0,
                        originalText = sourceText
                    )
                }

                // ai-regenerate-versions:串行跑 N 次 streamWritingOp,共享 groupId,
                // 按 idx 0..N-1 写入 versionPosition。每次 collect 各自独立的 AiVersion
                // 槽位,emit 前比对 generation。
                for (idx in 0 until versionCount) {
                    if (streamGeneration.get() != currentGeneration) return@launch
                    val builder = StringBuilder()
                    var lastUsage: AiStreamEvent.Usage? = null
                    try {
                        aiGateway
                            .streamWritingOp(
                                op = op,
                                sourceText = sourceText,
                                providerId = providerId,
                                apikey = apikey,
                                modelName = actualModel,
                                systemPrompt = systemPrompt,
                                apiFormatOverride = apiFormatOverride,
                                versionGroupId = groupId,
                                versionPosition = if (versionCount > 1) idx else null
                            ).collect { event ->
                                if (streamGeneration.get() != currentGeneration) {
                                    return@collect
                                }
                                when (event) {
                                    is AiStreamEvent.Started -> Unit
                                    is AiStreamEvent.Delta -> {
                                        builder.append(event.text)
                                        updateVersion(idx) { v ->
                                            v.copy(
                                                delta = event.text,
                                                accumulatedLength = builder.length
                                            )
                                        }
                                    }
                                    is AiStreamEvent.Usage -> {
                                        lastUsage = event
                                        updateVersion(idx) { v -> v.copy(usage = event) }
                                    }
                                    is AiStreamEvent.Done -> {
                                        updateVersion(idx) { v ->
                                            v.copy(
                                                state = AiVersion.State.Done,
                                                finalText = builder.toString(),
                                                accumulatedLength = builder.length,
                                                usage = lastUsage ?: v.usage
                                            )
                                        }
                                    }
                                    is AiStreamEvent.Failed -> {
                                        updateVersion(idx) { v ->
                                            v.copy(
                                                state = AiVersion.State.Failed,
                                                error = event
                                            )
                                        }
                                    }
                                }
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.e(
                                "AiVM",
                                "streamWritingOp position=$idx threw",
                                e
                            )
                        }
                        updateVersion(idx) { v ->
                            v.copy(
                                state = AiVersion.State.Failed,
                                error = AiStreamEvent.Failed(
                                    AiError.Unknown(null, e.message ?: e::class.simpleName ?: "unknown"),
                                    false
                                )
                            )
                        }
                    }
                }

                // N 个版本全跑完(可能混 Done/Failed):决定落终态。
                if (streamGeneration.get() != currentGeneration) return@launch
                finalizeAfterAllVersions(op, sourceText)
            }
    }

    /**
     * 用户切 tab 高亮 position(0..N-1)。不改 versions 数组内容,
     * 只更新 state.selectedPosition。仅当 state 是 Streaming / PartialDone / Done
     * 时生效(Idle / Failed / Replaced 态 no-op)。
     */
    fun selectVersion(position: Int) {
        val current = _state.value
        when (current) {
            is AiActionUiState.Streaming -> {
                if (position in current.versions.indices) {
                    _state.value = current.copy(selectedPosition = position)
                }
            }
            is AiActionUiState.PartialDone -> {
                if (position in current.versions.indices) {
                    _state.value = current.copy(selectedPosition = position)
                }
            }
            is AiActionUiState.Done -> {
                if (position in current.versions.indices) {
                    _state.value = current.copy(selectedPosition = position)
                }
            }
            else -> Unit
        }
    }

    /**
     * 接受指定 position 的 AI 输出,替换 Note 中的 sourceText。
     *
     * ai-regenerate-versions:默认 position=0,向后兼容 M3 单版本调用。
     * - state 必须是 Done / PartialDone / Streaming(早接受已 Done 的 position)
     * - version.state == Failed → no-op,不静默丢数据
     * - version.finalText 空串 → no-op(未真正完成)
     * - 成功 → state = Replaced(op);失败 → state = Failed(op, error)
     */
    fun acceptReplace(position: Int = 0) {
        if (BuildConfig.DEBUG) android.util.Log.d("AiVM", "acceptReplace position=$position")
        val current = _state.value
        val versions: List<AiVersion> = when (current) {
            is AiActionUiState.Done -> current.versions
            is AiActionUiState.PartialDone -> current.versions
            is AiActionUiState.Streaming -> current.versions
            else -> {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("AiVM", "acceptReplace: invalid state ${current::class.simpleName}")
                }
                return
            }
        }
        if (position !in versions.indices) {
            if (BuildConfig.DEBUG) android.util.Log.d("AiVM", "acceptReplace: position out of range")
            return
        }
        val version = versions[position]
        // ai-regenerate-versions:Failed 位置不接受 → no-op,不静默丢数据。
        if (version.state == AiVersion.State.Failed) {
            if (BuildConfig.DEBUG) android.util.Log.d("AiVM", "acceptReplace: position $position Failed, no-op")
            return
        }
        if (!version.isAcceptable) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("AiVM", "acceptReplace: position $position not acceptable")
            }
            return
        }
        val noteId = lastNoteId ?: run {
            if (BuildConfig.DEBUG) android.util.Log.d("AiVM", "acceptReplace: lastNoteId null")
            return
        }
        val op = lastOp ?: return
        val aiText = version.finalText
        viewModelScope.launch {
            val outcome: Boolean
            try {
                outcome = withContext(NonCancellable) {
                    val existingFlow = noteRepository.observeNoteWithTags(noteId)
                    val existing = existingFlow.first() ?: return@withContext false
                    val now = System.currentTimeMillis()
                    val originalContent = existing.note.content
                    val sourceText = lastSourceText ?: return@withContext false
                    // H6 修:`String.replace(sourceText, aiText)` 在原文不含 / 多次匹配时静默,
                    // 改用 indexOf 严格校验,缺失/多匹配 emit Failed,避免"接受了但内容没动"。
                    val idx = originalContent.indexOf(sourceText)
                    if (idx < 0) {
                        _state.value = AiActionUiState.Failed(
                            op = op,
                            error = AiError.Unknown(null, "原文已被修改,请重新生成")
                        )
                        return@withContext false
                    }
                    if (originalContent.indexOf(sourceText, idx + sourceText.length) >= 0) {
                        _state.value = AiActionUiState.Failed(
                            op = op,
                            error = AiError.Unknown(null, "原文有多处匹配,请手动选择")
                        )
                        return@withContext false
                    }
                    val updatedContent = originalContent.replaceRange(
                        idx,
                        idx + sourceText.length,
                        aiText
                    )
                    lastOriginalContent = originalContent
                    val updated = existing.note.copy(content = updatedContent, updatedAt = now)
                    noteRepository.upsert(updated, existing.tags)
                    noteRepository.updateAiMetadata(noteId, op.name.lowercase(), now)
                    widgetUpdater.updateAll(context)
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("AiVM", "acceptReplace ${op.name}", e)
                _state.value = AiActionUiState.Failed(
                    op = op,
                    error = AiError.Unknown(null, e.message ?: "unknown")
                )
                return@launch
            }
            if (outcome) {
                _state.value = AiActionUiState.Replaced(op = op)
            }
        }
    }

    /** 撤回 AI 替换,恢复原始内容。 */
    fun undo() {
        val noteId = lastNoteId ?: return
        val original = lastOriginalContent ?: return
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val existingFlow = noteRepository.observeNoteWithTags(noteId)
                    val existing = existingFlow.first() ?: return@withContext
                    val now = System.currentTimeMillis()
                    val reverted = existing.note.copy(content = original, updatedAt = now)
                    noteRepository.upsert(reverted, existing.tags)
                    widgetUpdater.updateAll(context)
                }
                lastOriginalContent = null
                _state.value = AiActionUiState.Idle
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val currentOp = (_state.value as? AiActionUiState.Replaced)?.op ?: WritingOp.POLISH
                if (BuildConfig.DEBUG) android.util.Log.e("AiVM", "undo failed", e)
                _state.value = AiActionUiState.Failed(
                    op = currentOp,
                    error = AiError.Unknown(null, "撤回失败:${e.message}")
                )
            }
        }
    }

    fun reject() {
        // Done / PartialDone / Streaming 任意态都可拒绝(中途拒绝 = abort 整个 N-version)。
        val current = _state.value
        if (current !is AiActionUiState.Done &&
            current !is AiActionUiState.PartialDone &&
            current !is AiActionUiState.Streaming
        ) {
            return
        }
        streamGeneration.incrementAndGet()
        streamJob?.cancel()
        _state.value = AiActionUiState.Idle
    }

    fun cancel() {
        if (_state.value !is AiActionUiState.Streaming &&
            _state.value !is AiActionUiState.PartialDone
        ) {
            return
        }
        streamGeneration.incrementAndGet()
        streamJob?.cancel()
        _state.value = AiActionUiState.Idle
    }

    fun dismiss() {
        streamGeneration.incrementAndGet()
        streamJob?.cancel()
        lastOriginalContent = null
        _state.value = AiActionUiState.Idle
    }

    /**
     * Done / PartialDone 态用 lastVersionCount 重跑(同 versionCount,共享新 groupId)。
     * ai-regenerate-versions:复用 lastVersionCount,确保再次跑相同数量的版本。
     */
    fun regenerate() {
        val current = _state.value as? AiActionUiState.Done
            ?: return
        val op = lastOp ?: current.op
        val sourceText = lastSourceText ?: return
        val noteId = lastNoteId ?: return
        start(op = op, sourceText = sourceText, noteId = noteId, versionCount = lastVersionCount)
    }

    /** Failed 态重试:复用 lastVersionCount。 */
    fun retry() {
        val current = _state.value as? AiActionUiState.Failed ?: return
        val op = lastOp ?: current.op
        val sourceText = lastSourceText ?: return
        val noteId = lastNoteId ?: return
        start(op = op, sourceText = sourceText, noteId = noteId, versionCount = lastVersionCount)
    }

    /**
     * 构建初始 N 个占位 Streaming 版本。`actualModel` 全相同(同一 provider/model),
     * 但每个 version 独立的 state 演进路径。
     */
    private fun buildInitialVersions(count: Int, actualModel: String): List<AiVersion> =
        List(count) { idx -> AiVersion(position = idx, actualModel = actualModel) }

    /**
     * 更新 versions 数组中 [position] 槽位的状态。State 必须是 Streaming / PartialDone /
     * Done(其它态 no-op,避免 Idle 状态被 race 覆盖)。
     */
    private fun updateVersion(position: Int, transform: (AiVersion) -> AiVersion) {
        val current = _state.value
        val versions: List<AiVersion> = when (current) {
            is AiActionUiState.Streaming -> current.versions
            is AiActionUiState.PartialDone -> current.versions
            is AiActionUiState.Done -> current.versions
            else -> return
        }
        if (position !in versions.indices) return
        val updated = versions.toMutableList().also { it[position] = transform(it[position]) }
        when (current) {
            is AiActionUiState.Streaming -> {
                _state.update {
                    if (it is AiActionUiState.Streaming) {
                        it.copy(versions = updated)
                    } else {
                        it
                    }
                }
            }
            is AiActionUiState.PartialDone -> {
                _state.update {
                    if (it is AiActionUiState.PartialDone) {
                        it.copy(versions = updated)
                    } else {
                        it
                    }
                }
            }
            is AiActionUiState.Done -> {
                _state.update {
                    if (it is AiActionUiState.Done) {
                        it.copy(versions = updated)
                    } else {
                        it
                    }
                }
            }
            else -> Unit
        }
        // ai-regenerate-versions:每次 emit 终态事件后,检查整体是否需要
        // Streaming → PartialDone 转移(任一 Done + 仍有 Streaming)。
        transitionAfterVersionTerminal()
    }

    /**
     * N 个版本全跑完后决定终态。
     *
     * - 全部 Failed → Failed(op, error) — error 用"全部 N 个版本生成失败"摘要
     * - 至少 1 个 Done → Done(versions, selectedPosition = 首个 Done 位置)
     *
     * note:此函数本身不做 Streaming → PartialDone / PartialDone → Done 的转移;
     * 那些转移由 updateVersion() 在每次 emit 终态事件后实时判断(见 transitionAfterVersionTerminal)。
     */
    private fun finalizeAfterAllVersions(op: WritingOp, originalText: String) {
        val current = _state.value
        val versions: List<AiVersion>
        val currentSelectedPosition: Int
        when (current) {
            is AiActionUiState.Streaming -> {
                versions = current.versions
                currentSelectedPosition = current.selectedPosition
            }
            is AiActionUiState.PartialDone -> {
                versions = current.versions
                currentSelectedPosition = current.selectedPosition
            }
            is AiActionUiState.Done -> {
                versions = current.versions
                currentSelectedPosition = current.selectedPosition
            }
            else -> return
        }
        val anyDone = versions.any { it.state == AiVersion.State.Done }
        if (!anyDone) {
            // ai-regenerate-versions:全部失败,error 摘要 + 首个 error detail。
            val firstError = versions.firstNotNullOfOrNull { it.error }
            val summaryError = firstError?.error
                ?: AiError.Unknown(null, "全部 ${versions.size} 个版本生成失败")
            _state.value = AiActionUiState.Failed(op = op, error = summaryError)
            return
        }
        val firstDonePos = versions.indexOfFirst { it.state == AiVersion.State.Done }
        val stillStreaming = versions.any { it.state == AiVersion.State.Streaming }
        val nextSelectedPosition =
            if (currentSelectedPosition in versions.indices &&
                versions[currentSelectedPosition].state == AiVersion.State.Done
            ) {
                currentSelectedPosition
            } else {
                firstDonePos
            }
        _state.value = when {
            stillStreaming -> {
                AiActionUiState.PartialDone(
                    op = op,
                    versions = versions,
                    selectedPosition = nextSelectedPosition,
                    originalText = originalText
                )
            }
            else -> {
                AiActionUiState.Done(
                    op = op,
                    versions = versions,
                    selectedPosition = nextSelectedPosition,
                    originalText = originalText
                )
            }
        }
    }

    /**
     * ai-regenerate-versions:每次 emit 单个版本的终态事件(Done / Failed)后,
     * 检查整体状态是否需要 Streaming → PartialDone 的转换。
     *
     * 决策:
     * - 仍在 Streaming 的版本数 > 0 + 至少 1 个 Done → PartialDone(保持终态进度可见)
     * - 否则保持 Streaming(等待其余版本)
     *
     * 由 [updateVersion] 在每次写完 versions 后调用。
     */
    private fun transitionAfterVersionTerminal() {
        val current = _state.value as? AiActionUiState.Streaming ?: return
        val anyDone = current.versions.any { it.state == AiVersion.State.Done }
        val stillStreaming = current.versions.any { it.state == AiVersion.State.Streaming }
        when {
            anyDone && stillStreaming -> {
                _state.value = AiActionUiState.PartialDone(
                    op = current.op,
                    versions = current.versions,
                    selectedPosition = current.selectedPosition,
                    originalText = current.originalText
                )
            }
            // ai-regenerate-versions:单版本 Done 时立刻 Streaming→Done;
            // finalizeAfterAllVersions 要等所有流 collect 完毕(channel close)才触发,
            // 单版本 + channel.UNLIMITED 测试场景下不会触发,故此处补 Direct transition。
            anyDone && !stillStreaming -> {
                val firstDonePos = current.versions.indexOfFirst { it.state == AiVersion.State.Done }
                val keepOrFallBack = if (current.selectedPosition == firstDonePos) {
                    current.selectedPosition
                } else {
                    firstDonePos
                }
                _state.value = AiActionUiState.Done(
                    op = current.op,
                    versions = current.versions,
                    selectedPosition = keepOrFallBack,
                    originalText = current.originalText
                )
            }
            else -> Unit
        }
    }
}
