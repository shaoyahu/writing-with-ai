package com.yy.writingwithai.core.feishu.api

/**
 * feishu-oauth-flow · 飞书 API 域错误 sealed class。
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-api-client/spec.md
 * "Error classification"
 */
sealed class FeishuError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 无 app_id/secret 凭证，从未取过 token。 */
    data object NotAuthorized : FeishuError("飞书凭证未配置")

    /** 飞书响应 `code == 10003` 等业务错误。 */
    data class BadRequest(val code: Int, val msg: String) : FeishuError("飞书业务错误 $code: $msg")

    /** 403 权限不足;`scope` 是猜测的权限名(可能为 null)。 */
    data class Forbidden(val scope: String?) : FeishuError("飞书权限不足${scope?.let { ": $it" } ?: ""}")

    /** 404 文档/资源不存在。 */
    data class NotFound(val resource: String) : FeishuError("飞书资源不存在: $resource")

    /** 429 限流;`retryAfterSeconds` 来自 `Retry-After` header。 */
    data class RateLimited(val retryAfterSeconds: Int) : FeishuError("飞书限流，${retryAfterSeconds}s 后重试")

    /** 5xx 服务器错误。 */
    data class ServerError(val code: Int) : FeishuError("飞书服务器错误: HTTP $code")

    /** 401 重试一次仍失败 → 凭证(app_id/secret)本身错误。 */
    data object AuthExpired : FeishuError("飞书凭证失效，请重新填写 app_id/app_secret")

    /** 网络层错误(超时/连接失败/解析失败)。 */
    data class NetworkError(val detail: String) : FeishuError("飞书网络错误: $detail")

    /** 飞书 code == 99991663(token invalid) — 内部触发重取的标志。 */
    data object TokenInvalid : FeishuError("飞书 token 失效")

    /**
     * fix-2026-06-30-full-review-r1 CRITICAL C2:同步冲突。
     * push/pull 检测到远端与本地同时被改(localRev > lastSyncedAt 且 remoteRevision 变化),
     * 标记 ref.status = CONFLICT 后抛此错，UI 弹 [ConflictResolutionDialog] 让用户选
     * keep local / keep remote，不再静默覆盖对方修改。
     */
    data class Conflict(
        val noteId: String,
        val docId: String,
        val docUrl: String
    ) : FeishuError("飞书同步冲突: $docUrl")
}
