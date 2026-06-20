package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.model.Note
import kotlinx.coroutines.flow.first

/**
 * M5 widget-1x4-compact · 4x1 horizontal Glance widget.
 * Color tokens from QuickNoteWidget.kt(same package): cBlue, cWhite, cBg, cTitle, cBody, cMeta, cp().
 */
class QuickNote1x4Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val notes = QuickNoteWidgetHiltBridge.repository?.observeRecent(LIMIT)?.first() ?: emptyList()
        provideContent { GlanceTheme { Widget1x4Content(notes = notes) } }
    }

    private companion object {
        const val LIMIT = 1
    }
}

@Composable
private fun Widget1x4Content(notes: List<Note>) {
    val ctx = LocalContext.current
    val note = notes.firstOrNull()
    val title = note?.title?.ifBlank { note.content.take(SNIPPET_LEN) } ?: ctx.getString(R.string.widget_empty)
    val time = note?.let { formatRelativeTimeCompact(ctx, it.updatedAt) }.orEmpty()
    val params = note?.let { actionParametersOf(OpenNoteAction.KEY_NOTE_ID to it.id) }

    // Widget card = white. Left: note title + time on white. Right: blue "+" button
    // fills full widget height and is flush to the right edge (zero right margin).
    Row(
        GlanceModifier
            .fillMaxSize()
            .background(cp(cWhite))
            .cornerRadius(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            GlanceModifier
                .defaultWeight()
                .let { if (params != null) it.clickable(actionRunCallback<OpenNoteAction>(params)) else it }
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
        ) {
            Text(
                title,
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cp(cTitle)),
                maxLines = 1
            )
            if (time.isNotEmpty()) {
                Text(
                    time,
                    style = TextStyle(fontSize = 10.sp, color = cp(cMeta)),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 1.dp)
                )
            }
        }
        // Blue "+" button: defaultWeight + height → flush top/bottom of widget (no margin).
        // No end margin → right edge of button = right edge of widget.
        Box(
            GlanceModifier
                .defaultWeight()
                .height(48.dp)
                .background(cp(cBlue))
                .cornerRadius(16.dp)
                .clickable(actionStartActivity(createNoteIntent(ctx)))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+",
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, color = cp(cWhite))
            )
        }
    }
}

private const val SNIPPET_LEN = 30
