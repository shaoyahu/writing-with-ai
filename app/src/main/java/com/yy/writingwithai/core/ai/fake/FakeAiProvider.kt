package com.yy.writingwithai.core.ai.fake

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake AI Provider:用于 M2/M3 所有单测和 UI 验收，不接真实 HTTP。
 *
 * 行为由 [FakeConfigHolder] 控制:固定文本 / 延迟 / token 用量 / 错误注入。
 */
@Singleton
class FakeAiProvider
@Inject
constructor() : AiProvider {
    // fix-2026-06-27-review-r4 L2:ID 提为常量，供外部引用，避免 "fake" magic string 散落。
    override val id = PROVIDER_ID
    override val displayName = "Fake (Testing)"
    override val supportedModels = listOf("fake-model")
    override val defaultModel = "fake-model"

    companion object {
        const val PROVIDER_ID = "fake"

        // fix:提取内联 Regex 常量，避免每次调用重新编译
        private val SPACE_SPLIT = Regex("(?<=\\s)|(?=\\s)")
    }

    override fun stream(request: AiRequest, credentials: AiCredentials): Flow<AiStreamEvent> = flow {
        val cfg = FakeConfigHolder.config
        emit(AiStreamEvent.Started)

        if (cfg.text.isBlank()) {
            emit(
                AiStreamEvent.Failed(
                    AiError.Unknown(code = null, detail = "empty fake text"),
                    recoverable = false
                )
            )
            return@flow
        }

        // 按 token 粒度(每 3 个字符或每个 sense split) emit
        var emittedTokenCount = 0
        val tokens = tokenize(cfg.text)

        for ((index, token) in tokens.withIndex()) {
            if (cfg.delayMs > 0) delay(cfg.delayMs)

            if (cfg.errorAfterTokens != null && index >= cfg.errorAfterTokens) {
                emit(
                    AiStreamEvent.Failed(
                        AiError.Network(code = -1, detail = "Fake error injection"),
                        recoverable = true
                    )
                )
                return@flow
            }

            emit(AiStreamEvent.Delta(token))
            emittedTokenCount = index + 1
        }

        val usage =
            if (cfg.tokenCounts != null) {
                cfg.tokenCounts!!
            } else {
                AiStreamEvent.Usage(
                    inputTokens = request.sourceText.length / 2,
                    outputTokens = emittedTokenCount,
                    totalTokens = request.sourceText.length / 2 + emittedTokenCount
                )
            }
        emit(usage)
        emit(AiStreamEvent.Done)
    }

    /** 简单 tokenize:按空格+标点 split，每个 segment 作为一个 token。 */
    private fun tokenize(text: String): List<String> = text.split(SPACE_SPLIT).filter { it.isNotBlank() }
}
