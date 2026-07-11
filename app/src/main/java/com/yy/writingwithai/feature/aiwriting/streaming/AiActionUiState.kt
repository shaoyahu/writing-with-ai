package com.yy.writingwithai.feature.aiwriting.streaming

import androidx.compose.runtime.Immutable
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.WritingOp

/**
 * AI 写作操作 UI 状态机(M3 + ai-regenerate-versions)。
 *
 * 状态转移:
 * - Idle --start(versionCount=N)--> Streaming(versions=N, 各自独立 Streaming)
 * - Streaming --all Done--> Done(versions, selectedPosition=0)
 * - Streaming --any Done + 其它仍在 Streaming--> PartialDone(versions, selectedPosition)
 *   - 早接受:用户可在 PartialDone 状态下接受已 Done 的版本,VM 写 Note 后 emit Replaced
 * - Streaming --all Failed--> Failed(op, error);error 用"全部 N 个版本生成失败"摘要
 * - Done / PartialDone / Failed --acceptReplace | reject | cancel | dismiss--> Idle
 * - Done / PartialDone --regenerate--> Streaming(同 versionCount 重跑)
 *
 * 状态机不可序列化,ViewModel scope 持有,不走 `rememberSaveable`。
 */
@Immutable
sealed interface AiActionUiState {
    data object Idle : AiActionUiState

    /**
     * 多版本生成进行中。`versions` 数组长度 = 本次 start 的 versionCount(1..3),
     * 每个元素各自独立推进 Streaming / Done / Failed。
     * `selectedPosition` 是 UI 当前高亮的 tab(用户可点击切到任一位置查看)。
     *
     * fix-2026-06-26-review-r3 H21:每个 AiVersion.delta 只暴露最新一次增量,
     * UI 用 remember 累加拼接,避免 O(n²) 内存与整段 recompose。
     */
    data class Streaming(
        val op: WritingOp,
        val versions: List<AiVersion>,
        val selectedPosition: Int,
        val originalText: String
    ) : AiActionUiState

    /**
     * ai-regenerate-versions:部分完成态 — 至少 1 个版本 Done,其它仍在 Streaming。
     * 用户可切 tab 看已 Done 的版本,或点"接受"早接受某已 Done 的版本。
     * VM 不会自动 emit Done;只有当所有版本都终态时才转 Done。
     */
    data class PartialDone(
        val op: WritingOp,
        val versions: List<AiVersion>,
        val selectedPosition: Int,
        val originalText: String
    ) : AiActionUiState

    /**
     * 全部版本完成(可能含 Failed / Done 混合,只要至少 1 个 Done + 没有
     * 正在 Streaming 就算完成)。`selectedPosition` 默认指向第一个 Done 的位置,
     * UI 可切 tab 接受任一 Done 版本。
     */
    data class Done(
        val op: WritingOp,
        val versions: List<AiVersion>,
        val selectedPosition: Int,
        val originalText: String
    ) : AiActionUiState

    /**
     * 所有版本全部 Failed(典型:apikey 失效 / 网络持续不通)。
     * `error` 用"全部 N 个版本生成失败"摘要 + 首个 error 的 detail。
     */
    data class Failed(
        val op: WritingOp,
        val error: AiError
    ) : AiActionUiState

    /** acceptReplace 后:内容已部分替换,用户可撤回。 */
    data class Replaced(
        val op: WritingOp
    ) : AiActionUiState
}
