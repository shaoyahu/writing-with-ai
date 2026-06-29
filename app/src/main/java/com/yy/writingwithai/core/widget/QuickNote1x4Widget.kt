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
 *
 * widget-rome-compat · 颜色 token 化(从 Material 3 colorScheme 派生,跟随系统暗色 / 亮色 /
 * Material You),不再用 QuickNoteWidget.kt 已删的 cBlue / cWhite / cTitle / cMeta hex。
 */
class QuickNote1x4Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = QuickNoteWidgetHiltBridge.resolveRepository(context) ?: return
        val notes = repository.observeRecent(LIMIT).first()
        provideContent { GlanceTheme { Widget1x4Content(notes = notes) } }
    }

    private companion object {
        const val LIMIT = 1
    }
}

@Composable
private fun Widget1x4Content(notes: List<Note>) {
    val ctx = LocalContext.current
    val colors = widgetColors()
    val note = notes.firstOrNull()
    val title = note?.title?.ifBlank { note.content.take(SNIPPET_LEN) } ?: ctx.getString(R.string.widget_empty)
    val time = note?.let { formatRelativeTimeCompact(ctx, it.updatedAt) }.orEmpty()
    val params = note?.let { actionParametersOf(OpenNoteAction.KEY_NOTE_ID to it.id) }

    // Widget card surface from ColorScheme.surface.
    // Left: note title + time. Right: primary "+" button
    // fills full widget height and is flush to the right edge (zero right margin).
    Row(
        GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
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
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.widgetOnBackground),
                maxLines = 1
            )
            if (time.isNotEmpty()) {
                Text(
                    time,
                    style = TextStyle(fontSize = 10.sp, color = colors.widgetOnSurfaceVariant),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 1.dp)
                )
            }
        }
        // "+" button: defaultWeight + height → flush top/bottom of widget (no margin).
        // No end margin → right edge of button = right edge of widget.
        Box(
            GlanceModifier
                .defaultWeight()
                .height(48.dp)
                .background(colors.widgetPrimary)
                .cornerRadius(16.dp)
                // R3 C5 fix:与 AddButton 走同一条 launcher→MainActivity 回退栈。
                .clickable { ctx.launchWithTaskStack(WidgetLaunchRoute.NewNote) }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+",
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, color = colors.widgetOnBackground)
            )
        }
    }
}

private const val SNIPPET_LEN = 30
