package com.yy.writingwithai.core.ai.api

import kotlinx.coroutines.flow.Flow

/**
 * 业务侧唯一 AI 入口:不直接调 [AiProvider]，不直接调 OkHttp。
 *
 * 实现见 [com.yy.writingwithai.core.ai.CoreAiGateway]。
 */
interface AiGateway {
    suspend fun listProviders(): List<ProviderDescriptor>

    suspend fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        apikey: String,
        modelName: String?,
        systemPrompt: String? = null,
        apiFormatOverride: ApiFormat? = null,
        /**
         * ai-regenerate-versions:多版本生成时同一 sourceText + op 的 N 次调用共享同一 groupId,
         * 写 ai_history 行时落同一字段便于按组聚合。null = 单版本(M3 行为,向后兼容)。
         */
        versionGroupId: String? = null,
        /**
         * ai-regenerate-versions:本行在多版本生成中的位置(0..N-1),用于 ai_history 行排序
         * 与 UI tab 顺序。null = 单版本(M3 行为)。
         */
        versionPosition: Int? = null
    ): Flow<AiStreamEvent>

    /**
     * 测连通。
     *
     * @return `null` 表示成功(收到非 Failed 事件流);非 `null` 表示失败原因(从 provider 返回的
     *   [AiError.summary] 抽取，用于 UI 直接展示给用户)。
     *
     * 之前签名是 `Boolean`，失败细节被丢弃，UI 只能写死「apikey 无效或网络不通」，无法定位是
     * 真 apikey 错(401)还是请求体被 server 拒绝(400)还是网络层 timeout。
     *
     * `apiFormatOverride` 是 fix-review-r2-high H1 新增:让 caller 在 ping 时也能切
     * OpenAI/Anthropic 协议，无需 gateway 内部读 DataStore(原实现 `runBlocking` 主线程 ANR)。
     */
    suspend fun ping(
        providerId: String,
        apikey: String,
        modelName: String,
        apiFormatOverride: ApiFormat? = null
    ): String?
}
