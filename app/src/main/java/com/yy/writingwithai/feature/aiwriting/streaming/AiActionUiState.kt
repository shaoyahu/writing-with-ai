package com.yy.writingwithai.feature.aiwriting.streaming

import androidx.compose.runtime.Immutable
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
 * 状态机不可序列化(`Streaming.partialText` 大文本 / `Done.usage` 来自 Flow)，用 ViewModel
 * scope 持有，不走 `rememberSaveable`。
 */
@Immutable
sealed interface AiActionUiState {
    data object Idle : AiActionUiState

    /**
     * Streaming 期间只暴露最新一次增量 `delta`,UI 自行累加;`partialText` 仅在 done / restart
     * 路径下回填完整文本，避免 O(n²) 内存与重组。
     *
     * fix-2026-06-26-review-r3 H21:之前每次 Delta 都 `builder.toString()` 把累积文本整体
     * emit，长 prompt 下 O(n²) 内存 + 整段 recompose。改为 emit 单次增量 chunk,UI 拼接。
     */
    data class Streaming(
        val op: WritingOp,
        val delta: String = "",
        /** 累积总长(用于 progress 估算),UI 仍按需显示拼接后的文本。 */
        val accumulatedLength: Int = 0,
        val isCancelled: Boolean = false,
        /**
         * fix-2026-06-28-ai-model-selection-actually-used:实际将调用 model 名(经过
         * selectedModel ?: defaultModel fallback)，给 UI 显示"AI 正在用 X 跑" —
         * 与 `ModelManagementScreen` 卡片显示的"实际将调用"行算法一致
         * (统一走 [resolveActualModel])。空串 = 计算失败(无 provider 描述),
         * UI 应隐藏。
         */
        val actualModel: String = ""
    ) : AiActionUiState

    data class Done(
        val originalText: String,
        val op: WritingOp,
        val finalText: String,
        val usage: AiStreamEvent.Usage?
    ) : AiActionUiState

    data class Failed(
        val op: WritingOp,
        val error: AiError
    ) : AiActionUiState

    /** acceptReplace 后:内容已部分替换，用户可撤回。 */
    data class Replaced(
        val op: WritingOp
    ) : AiActionUiState
}
