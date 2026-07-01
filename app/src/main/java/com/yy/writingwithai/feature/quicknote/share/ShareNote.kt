package com.yy.writingwithai.feature.quicknote.share

import android.content.Context
import android.content.Intent
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.model.Note

/**
 * 单条笔记 Markdown 分享(spec §"Single-note Markdown share via system Intent"):
 * - title 为空 → `EXTRA_TEXT = content`
 * - title 非空 → `EXTRA_TEXT = "$title\n\n$content"`
 * - `Intent.ACTION_SEND` + `type = "text/markdown"`
 * - 不写文件，无权限
 *
 * H5 修:Android TV / 极简 ROM 上可能没有 app 处理 `ACTION_SEND text/markdown`,
 * `startActivity` 会抛 `ActivityNotFoundException`，这里 catch 后 toast 提示。
 */
internal fun Context.shareNoteMarkdown(note: Note) {
    val markdown =
        buildString {
            if (note.title.isNotBlank()) {
                append(note.title)
                append("\n\n")
            }
            append(note.content)
        }
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_TEXT, markdown)
            putExtra(Intent.EXTRA_TITLE, note.title.ifBlank { note.id })
        }
    val chooser =
        Intent.createChooser(
            sendIntent,
            getString(R.string.quicknote_share_chooser_title)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        startActivity(chooser)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast
            .makeText(
                this,
                getString(R.string.quicknote_share_no_app),
                android.widget.Toast.LENGTH_SHORT
            ).show()
    }
}
