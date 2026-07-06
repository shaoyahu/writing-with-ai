package com.yy.writingwithai.core.feishu.sync

/** feishu-bidir-sync · 同步方向(design D1)。 */
enum class SyncDirection { PUSH, PULL, BIDIR }

/** feishu-bidir-sync · feishu_ref 行状态(design D1)。 */
enum class FeishuRefStatus {
    SYNCED,
    DIRTY,
    CONFLICT,
    REMOTE_DELETED,

    // feishu-import-from-folder:从飞书导入,markdown 成功 + 部分图片失败
    PARTIAL_IMPORT_FAIL
}
