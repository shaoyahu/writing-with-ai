package com.yy.writingwithai.core.ai.api

import kotlinx.coroutines.flow.Flow

/**
 * 业务侧唯一 AI 入口:不直接调 [AiProvider],不直接调 OkHttp。
 *
 * 实现见 [com.yy.writingwithai.core.ai.CoreAiGateway]。
 */
interface AiGateway {
    suspend fun listProviders(): List<ProviderDescriptor>

    fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        modelName: String?,
    ): Flow<AiStreamEvent>

    suspend fun ping(
        providerId: String,
        modelName: String,
    ): Boolean
}
