package com.yy.writingwithai.feature.aiwriting.action

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 把 [text] 写入系统剪贴板(plain text)。
 *
 * 不走 [com.yy.writingwithai.core.ai.api.AiGateway](非 AI 操作,系统 API);
 * Clipboard label 写 `writing-with-ai` 方便系统 debug。
 */
internal fun Context.copyToClipboard(text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("writing-with-ai", text))
}
