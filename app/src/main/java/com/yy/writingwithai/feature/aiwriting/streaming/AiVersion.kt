package com.yy.writingwithai.feature.aiwriting.streaming

import androidx.compose.runtime.Immutable
import com.yy.writingwithai.core.ai.api.AiStreamEvent

/**
 * ai-regenerate-versions:单次多版本生成中的某个版本槽位(0..N-1)。
 *
 * 每个槽位独立跟踪自己的 [state] + 累积文本 + 用量。`selectedPosition` 不在
 * AiVersion 本身,而是 UI 状态机顶层持有的"用户当前选中哪个 tab"。
 *
 * 单版本(M3 行为,versionCount=1)时仍用 AiVersion 包装:positions=[AiVersion(0, ...)],
 * 保持 UI 渲染路径一致。
 */
@Immutable
data class AiVersion(
    val position: Int,
    /** 已 emit 的最新一次 delta 增量(单次 chunk);UI 用 remember 拼接。 */
    val delta: String = "",
    /** 累积总长,用于 partialText progress 估算;UI 仍按需显示拼接后文本。 */
    val accumulatedLength: Int = 0,
    /** 本版本最终的完整输出文本(Done 时有值);Streaming 时为空串。 */
    val finalText: String = "",
    /** 本版本的 token 用量(Done 时可能有值;Failed / Streaming 为 null)。 */
    val usage: AiStreamEvent.Usage? = null,
    /** 本版本的失败原因;state=Failed 时有值。 */
    val error: AiStreamEvent.Failed? = null,
    val state: State = State.Streaming,
    /**
     * fix-2026-06-28-ai-model-selection-actually-used:实际调用 model(已 resolved),
     * 给 tab 显示"AI 正在用 X 跑"。
     */
    val actualModel: String = ""
) {
    enum class State { Streaming, Done, Failed }

    /** 本版本的最终文本是否非空(用于"接受"按钮 enabled 判断)。 */
    val isAcceptable: Boolean get() = state == State.Done && finalText.isNotBlank()
}
