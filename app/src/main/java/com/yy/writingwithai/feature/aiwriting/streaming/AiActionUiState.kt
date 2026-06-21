package com.yy.writingwithai.feature.aiwriting.streaming

import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp

/**
 * AI 写作操作 UI 状态机(M3 定义)。
 *
 * 状态转移:
 * - Idle --start()--> Streaming
 * - Streaming --Delta*--> Streaming(累加 partialText)
 * - Streaming --Done/Usage--> Done
 * - Streaming --Failed--> Failed
 * - Done/Streaming/Failed --acceptReplace | reject | cancel | dismiss--> Idle
 * - Done --regenerate--> Streaming
 *
 * 状态机不可序列化(`Streaming.partialText` 大文本 / `Done.usage` 来自 Flow),用 ViewModel
 * scope 持有,不走 `rememberSaveable`。
 */
sealed interface AiActionUiState {
    data object Idle : AiActionUiState

    data class Streaming(
        val op: WritingOp,
        val partialText: String = "",
        val isCancelled: Boolean = false
    ) : AiActionUiState

    data class Done(
        val op: WritingOp,
        val finalText: String,
        val usage: AiStreamEvent.Usage?
    ) : AiActionUiState

    data class Failed(
        val op: WritingOp,
        val error: AiError
    ) : AiActionUiState

    /** acceptReplace 后:内容已部分替换,用户可撤回。 */
    data class Replaced(
        val op: WritingOp
    ) : AiActionUiState
}
