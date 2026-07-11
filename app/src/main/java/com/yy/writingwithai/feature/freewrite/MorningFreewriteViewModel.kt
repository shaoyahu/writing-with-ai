package com.yy.writingwithai.feature.freewrite

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.prefs.PromptTemplateStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import com.yy.writingwithai.core.prefs.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.function.Supplier
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * morning-freewrite · 沉浸晨写屏 ViewModel(design §3.2)。
 *
 * 状态机:
 * - [FreewriteUiState.NoProvider] — 进屏时无 provider apikey → 渲染"去设置"提示
 * - [FreewriteUiState.Writing] — 5 分钟倒计时中,BasicTextField 编辑中
 * - [FreewriteUiState.Polishing] — AI 润色中(Polish 流)
 * - [FreewriteUiState.Organizing] — AI 整理中(Organize 流)
 * - [FreewriteUiState.Saved] — 落库完成(fallback 标志决定 Snackbar 文案)
 * - [FreewriteUiState.Failed] — 致命错误(无 provider / 数据库写异常 / AI 链全失败兜底)
 *
 * AI 串行链(design §4.1,沿用 M3 既有 AiActionViewModel 模式 — **不**扩展 AiActionViewModel):
 * - Polishing(content) → aiVm.start(POLISH, content, journalId)
 * - collect aiVm.state → Done → 切 Organizing → start(ORGANIZE, polished, journalId)
 * - collect → Done → saveJournal(original, polished, organized, fallback=false)
 *
 * 失败兜底(design §4.2,CLAUDE.md 硬规则):
 * - Polish Failed → saveJournal(original=raw, polished=null, organized=null, fallback=true)
 * - Organize Failed → saveJournal(original=raw, polished=polished, organized=null, fallback=true)
 * - 两 op 全 Failed → 等同 Polish Failed 路径(原文不丢)
 */
@HiltViewModel
class MorningFreewriteViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val aiGateway: AiGateway,
    private val noteRepository: NoteRepository,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val consentStore: ConsentStore,
    private val providerPrefsStore: ProviderPrefsStore,
    private val promptTemplateStore: PromptTemplateStore,
    private val userPrefsStore: UserPrefsStore,
    private val nowProvider: Supplier<LocalDate>
) : ViewModel() {

    private val _state = MutableStateFlow<FreewriteUiState>(FreewriteUiState.Writing())
    val state: StateFlow<FreewriteUiState> = _state.asStateFlow()

    private val _saveEvents = MutableSharedFlow<SaveEvent>(extraBufferCapacity = 4)
    val saveEvents: SharedFlow<SaveEvent> = _saveEvents.asSharedFlow()

    private val _secondsLeft = MutableStateFlow(DEFAULT_DURATION_SECONDS)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private var tickJob: Job? = null
    private var chainJob: Job? = null
    private var contentText: String = ""
    private val journalNoteId: String = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            // 进屏时检查 provider 配置;无 → 直接进入 NoProvider 态(spec §4.4)。
            val configured = secureApiKeyStore.observeConfiguredProviders().first()
            if (configured.isEmpty()) {
                _state.value = FreewriteUiState.NoProvider
            } else {
                startCountdown()
            }
        }
    }

    /** 屏渲染:用户输入文本(由屏内 BasicTextField onValueChange 调)。 */
    fun setContent(text: String) {
        contentText = text
    }

    /** 用户点"完成"或倒计时归零 → 启动 Polish→Organize 链。 */
    fun finish() {
        val current = _state.value
        if (current !is FreewriteUiState.Writing) return
        tickJob?.cancel()
        // 空内容直接当 fallback 保存
        if (contentText.isBlank()) {
            viewModelScope.launch {
                persistFallback(original = "", polished = null, organized = null)
            }
            return
        }
        _state.value = FreewriteUiState.Polishing
        val raw = contentText
        chainJob = viewModelScope.launch { runChain(raw) }
    }

    /** 用户点"跳过" — 立即停止倒计时 + 落库原文。 */
    fun skip() {
        tickJob?.cancel()
        val raw = contentText
        viewModelScope.launch {
            persistFallback(original = raw, polished = null, organized = null)
        }
    }

    /** UI 关掉屏后的清理(避免 Snackbar 跨屏泄漏)。 */
    fun consumeSaveEvent() {
        // SharedFlow.emit 已 buffer;屏在渲染完一次性消费即可,这里保留 hook 给屏 dispose 用。
    }

    private fun startCountdown() {
        tickJob = viewModelScope.launch {
            while (_secondsLeft.value > 0) {
                kotlinx.coroutines.delay(1_000)
                _secondsLeft.value = (_secondsLeft.value - 1).coerceAtLeast(0)
                if (_secondsLeft.value == 0) {
                    finish()
                    break
                }
            }
        }
    }

    private suspend fun runChain(raw: String) {
        // Step 1: Polish
        val polishedResult = runOp(op = WritingOp.POLISH, source = raw)
        if (polishedResult is OpResult.Failed) {
            // Polish 失败 → 兜底保存原文(任何错误都不丢用户输入)
            persistFallback(original = raw, polished = null, organized = null)
            return
        }
        val polished = (polishedResult as OpResult.Ok).text

        // Step 2: Organize(用 polished 当输入)
        _state.value = FreewriteUiState.Organizing
        val organizedResult = runOp(op = WritingOp.ORGANIZE, source = polished)
        if (organizedResult is OpResult.Failed) {
            // Organize 失败 → 保存原文 + 润色结果(op="polish", fallback=true)
            persistFallback(original = raw, polished = polished, organized = null)
            return
        }
        val organized = (organizedResult as OpResult.Ok).text

        // 全部成功 → 双 op 元数据,fallback=false
        persistSuccess(original = raw, polished = polished, organized = organized)
    }

    private suspend fun runOp(op: WritingOp, source: String): OpResult {
        // consent gate(同 AiActionViewModel.start)
        val consented = consentStore.isConsented(BuildConfig.CONSENT_VERSION)
        if (!consented) return OpResult.Failed(AiError.UserConsentRequired)
        val acked = userPrefsStore.isApikeyPromptAcked()
        if (!acked) return OpResult.Failed(AiError.ApikeyPromptNotAcked)
        val providerId = providerPrefsStore.getSelectedProviderId()
            ?: return OpResult.Failed(AiError.ProviderNotConfigured)
        val apikey = secureApiKeyStore.get(providerId)
            ?: return OpResult.Failed(AiError.ProviderNotConfigured)
        val apiFormatOverride = providerPrefsStore.getApiFormat(providerId)
        val selectedModel = providerPrefsStore.getSelectedModel(providerId)
        val providers = runCatching { aiGateway.listProviders() }.getOrDefault(emptyList())
        val defaultModel = providers.firstOrNull { it.id == providerId }?.defaultModel.orEmpty()
        val actualModel = if (selectedModel.isNullOrBlank()) defaultModel else selectedModel
        val systemPrompt = promptTemplateStore.getForOp(op) ?: DefaultPrompts.forOp(op)

        val builder = StringBuilder()
        var failure: AiError? = null
        try {
            aiGateway
                .streamWritingOp(
                    op = op,
                    sourceText = source,
                    providerId = providerId,
                    apikey = apikey,
                    modelName = actualModel,
                    systemPrompt = systemPrompt,
                    apiFormatOverride = apiFormatOverride
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Started -> Unit
                        is AiStreamEvent.Delta -> builder.append(event.text)
                        is AiStreamEvent.Usage -> Unit
                        is AiStreamEvent.Done -> return@collect
                        is AiStreamEvent.Failed -> {
                            failure = event.error
                            return@collect
                        }
                    }
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return OpResult.Failed(AiError.Unknown(null, e.message ?: "AI chain unknown error"))
        }
        failure?.let { return OpResult.Failed(it) }
        return OpResult.Ok(builder.toString())
    }

    private suspend fun persistFallback(original: String, polished: String?, organized: String?) {
        val now = System.currentTimeMillis()
        val (title, content, op, at) = computeFields(
            original = original,
            polished = polished,
            organized = organized,
            fallback = true,
            now = now
        )
        runCatching {
            noteRepository.createJournalEntry(
                noteId = journalNoteId,
                title = title,
                content = content,
                lastAiOp = op,
                lastAiAt = at
            )
            _saveEvents.emit(SaveEvent.Saved(fallback = true))
            _state.value = FreewriteUiState.Saved(fallback = true)
        }.onFailure {
            _state.value = FreewriteUiState.Failed
        }
    }

    private suspend fun persistSuccess(original: String, polished: String, organized: String) {
        val now = System.currentTimeMillis()
        val (title, content, op, at) = computeFields(
            original = original,
            polished = polished,
            organized = organized,
            fallback = false,
            now = now
        )
        runCatching {
            noteRepository.createJournalEntry(
                noteId = journalNoteId,
                title = title,
                content = content,
                lastAiOp = op,
                lastAiAt = at
            )
            _saveEvents.emit(SaveEvent.Saved(fallback = false))
            _state.value = FreewriteUiState.Saved(fallback = false)
        }.onFailure {
            // 成功链尾巴 DB 写失败 → 也兜底到 fallback 路径(原文不丢)
            persistFallback(original = original, polished = polished, organized = organized)
        }
    }

    /**
     * 字段决策(design §4.2):
     * - fallback=true + polished/organized 都 null → title=日期, content=原文, op=null, at=null
     * - fallback=true + polished 非 null + organized null → title=日期, content=polished, op="polish", at=now
     * - fallback=false(全成功) → title=日期, content=organized, op="organize", at=now
     */
    private fun computeFields(
        original: String,
        polished: String?,
        organized: String?,
        fallback: Boolean,
        now: Long
    ): JournalFields {
        val title = nowProvider.get().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return when {
            fallback && polished == null && organized == null -> {
                JournalFields(title = title, content = original, op = null, at = null)
            }
            fallback && polished != null && organized == null -> {
                JournalFields(title = title, content = polished, op = WritingOp.POLISH.name.lowercase(), at = now)
            }
            !fallback && organized != null -> {
                JournalFields(title = title, content = organized, op = WritingOp.ORGANIZE.name.lowercase(), at = now)
            }
            else -> {
                // 其它组合(organized=null + fallback=false 不该发生)→ 兜底存原文
                JournalFields(title = title, content = original, op = null, at = null)
            }
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        chainJob?.cancel()
        super.onCleared()
    }

    /** AI op 单步结果。 */
    private sealed interface OpResult {
        data class Ok(val text: String) : OpResult
        data class Failed(val error: AiError) : OpResult
    }

    private data class JournalFields(
        val title: String,
        val content: String,
        val op: String?,
        val at: Long?
    )

    companion object {
        /** spec §3.1:5 分钟 = 300 秒。 */
        const val DEFAULT_DURATION_SECONDS = 300
    }
}

/**
 * morning-freewrite UI 状态(design §3.2 + spec §3.2)。
 */
sealed interface FreewriteUiState {
    /** 倒计时进行中,text 是当前输入(屏侧 collect)。 */
    data class Writing(val text: String = "") : FreewriteUiState

    /** AI 润色中(屏渲染 spinner + "AI 整理中..."). */
    data object Polishing : FreewriteUiState

    /** AI 整理中。 */
    data object Organizing : FreewriteUiState

    /** 落库完成;`fallback` 决定 Snackbar 文案。 */
    data class Saved(val fallback: Boolean) : FreewriteUiState

    /** 致命错误(DB 写失败且原文也无法保留 — 极罕见)。 */
    data object Failed : FreewriteUiState

    /** 无 provider apikey — 渲染「去设置」提示。 */
    data object NoProvider : FreewriteUiState
}

/** 落库完成事件 — SharedFlow 让屏消费 Snackbar。 */
sealed interface SaveEvent {
    data class Saved(val fallback: Boolean) : SaveEvent
}
