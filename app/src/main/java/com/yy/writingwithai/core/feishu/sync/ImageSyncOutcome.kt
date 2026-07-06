package com.yy.writingwithai.core.feishu.sync

/**
 * feishu-sync-image-support · syncAttachments 的返回结果。
 *
 * - [Success]:没有附件,或全部附件成功上传 + appendChildren 成功(无 IMAGE_FAIL_PARTIAL 事件)
 * - [PartialFail]:upload 或 appendChildren 任一步失败,降级成文本占位符后写入 IMAGE_FAIL_PARTIAL 事件;
 *   ref 仍标 SYNCED,因为 markdown 文本部分同步成功
 */
sealed class ImageSyncOutcome {
    data object Success : ImageSyncOutcome()

    /**
     * @param failedIds 上传或追加失败的 attachment id 列表(降级时全部走占位符,不一定每张都失败)
     * @param reason 失败原因摘要,记到 FeishuSyncEventEntity.errorMessage
     */
    data class PartialFail(
        val failedIds: List<String>,
        val reason: String
    ) : ImageSyncOutcome()
}
