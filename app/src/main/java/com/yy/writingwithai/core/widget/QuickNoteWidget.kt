@file:Suppress("MagicNumber")

package com.yy.writingwithai.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.MainActivity
import com.yy.writingwithai.core.data.model.Note
import kotlinx.coroutines.flow.first

internal val cBlue = Color(0xFF3B82F6)
internal val cWhite = Color(0xFFFFFFFF)
internal val cBg = Color(0xFFF0F2F5)
internal val cTitle = Color(0xFF111827)
internal val cBody = Color(0xFF6B7280)
internal val cMeta = Color(0xFF9CA3AF)
internal fun cp(c: Color) = ColorProvider(c, c)

class QuickNoteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val notes = QuickNoteWidgetHiltBridge.repository?.observeRecent(LIMIT)?.first() ?: emptyList()
        provideContent { GlanceTheme { WidgetContent(notes = notes, context = context) } }
    }
    private companion object {
        const val LIMIT = 3
    }
}

@Composable
private fun WidgetContent(notes: List<Note>, context: Context) {
    if (LocalSize.current.width <= 160.dp) {
        WidgetSmall(notes, context)
    } else {
        WidgetWide(notes, context)
    }
}

@Composable
private fun AddButton(context: Context) {
    Box(
        GlanceModifier
            .background(cp(cBlue))
            .clickable(actionStartActivity(createNoteIntent(context)))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("+", style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, color = cp(cWhite)))
    }
}

// ============================================================
// Small(2x2)
// ============================================================
@Composable
private fun WidgetSmall(notes: List<Note>, context: Context) {
    Column(GlanceModifier.fillMaxSize().background(cp(cBg))) {
        // Header: title(left) + button(right, touches edges), no outer padding
        Row(
            GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                context.getString(R.string.widget_2x2_title),
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = cp(cBody)),
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp)
            )
            AddButton(context)
        }
        // Content with padding
        val note = notes.firstOrNull()
        if (note != null) {
            val params = actionParametersOf(OpenNoteAction.KEY_NOTE_ID to note.id)
            Column(
                GlanceModifier.defaultWeight().fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .background(cp(cWhite))
                    .clickable(actionRunCallback<OpenNoteAction>(params))
                    .padding(10.dp)
            ) {
                Text(
                    note.title.ifBlank { note.content.take(SNIPPET_LEN) },
                    style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = cp(cTitle)),
                    maxLines = 1
                )
                Text(
                    note.content.replace("\n", " ").take(50),
                    style = TextStyle(fontSize = 11.sp, color = cp(cBody)),
                    maxLines = 2,
                    modifier = GlanceModifier.padding(top = 2.dp)
                )
                Text(
                    formatRelativeTime(context, note.updatedAt),
                    style = TextStyle(fontSize = 10.sp, color = cp(cMeta)),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
        } else {
            Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(context.getString(R.string.widget_empty), style = TextStyle(fontSize = 11.sp, color = cp(cMeta)))
            }
        }
        // bottom spacer to keep content area balanced
        Box(GlanceModifier.fillMaxWidth().padding(bottom = 10.dp)) {}
    }
}

// ============================================================
// Wide(4x2)
// ============================================================
@Composable
private fun WidgetWide(notes: List<Note>, context: Context) {
    Column(GlanceModifier.fillMaxSize().background(cp(cBg))) {
        Row(
            GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                context.getString(R.string.widget_4x2_title),
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = cp(cBody)),
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp)
            )
            AddButton(context)
        }
        if (notes.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(context.getString(R.string.widget_empty), style = TextStyle(fontSize = 11.sp, color = cp(cMeta)))
            }
        } else {
            Column(GlanceModifier.fillMaxSize()) {
                notes.take(3).forEachIndexed { i, note ->
                    val title = note.title.ifBlank { note.content.take(SNIPPET_LEN) }
                    val body = note.content.replace("\n", " ").take(40)
                    val params = actionParametersOf(OpenNoteAction.KEY_NOTE_ID to note.id)
                    Column(
                        GlanceModifier.fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .background(cp(cWhite))
                            .clickable(actionRunCallback<OpenNoteAction>(params))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cp(cTitle)),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                formatRelativeTimeCompact(context, note.updatedAt),
                                style = TextStyle(fontSize = 10.sp, color = cp(cMeta)),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(start = 4.dp)
                            )
                        }
                        if (body.isNotBlank()) {
                            Text(
                                body,
                                style = TextStyle(fontSize = 10.sp, color = cp(cBody)),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(top = 1.dp)
                            )
                        }
                    }
                    if (i < notes.size - 1 && i < 2) Box(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {}
                }
            }
        }
    }
}

// ── Helpers ──

internal fun formatRelativeTime(context: Context, epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val m = 60_000L
    val h = 60 * m
    val d = 24 * h
    return when {
        diff < m -> context.getString(R.string.widget_time_just_now)
        diff < h -> "${diff / m} ${context.getString(R.string.widget_time_minute_ago)}"
        diff < d -> "${diff / h} ${context.getString(R.string.widget_time_hour_ago)}"
        diff < 7 * d -> "${diff / d} ${context.getString(R.string.widget_time_day_ago)}"
        else -> "${diff / (7 * d)} ${context.getString(R.string.widget_time_week_ago)}"
    }
}

internal fun formatRelativeTimeCompact(context: Context, epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val m = 60_000L
    val h = 60 * m
    val d = 24 * h
    return when {
        diff < m -> context.getString(R.string.widget_time_just_now)
        diff < h -> "${diff / m}m"
        diff < d -> "${diff / h}h"
        diff < 7 * d -> "${diff / d}d"
        else -> "${diff / (7 * d)}w"
    }
}

private const val SNIPPET_LEN = 30

/** Returns Intent with route extra — Glance fires it on click, no premature launch. */
internal fun createNoteIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    .putExtra("route", "quicknote/edit?prefillFocus=true")
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
