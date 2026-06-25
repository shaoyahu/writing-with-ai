@file:Suppress("MagicNumber")

package com.yy.writingwithai.core.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
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

class QuickNoteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val notes = QuickNoteWidgetHiltBridge.repository?.observeRecent(LIMIT)?.first() ?: emptyList()
        val noteIndex = WidgetStateStore.current(context).currentNoteIndex
        provideContent {
            GlanceTheme { WidgetContent(notes = notes, noteIndex = noteIndex, context = context) }
        }
    }
    private companion object {
        const val LIMIT = 3
    }
}

@Composable
private fun WidgetContent(notes: List<Note>, noteIndex: Int, context: Context) {
    val colors = widgetColors()
    if (LocalSize.current.width <= 160.dp) {
        WidgetSmall(notes, noteIndex, context, colors)
    } else {
        WidgetWide(notes, context, colors)
    }
}

@Composable
private fun AddButton(context: Context, colors: WidgetColors) {
    Box(
        GlanceModifier
            .background(colors.widgetPrimary)
            .clickable(actionStartActivity(createNoteIntent(context)))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "+",
            style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, color = colors.widgetOnBackground)
        )
    }
}

// ============================================================
// Small(2x2)
// ============================================================
@Composable
private fun WidgetSmall(notes: List<Note>, noteIndex: Int, context: Context, colors: WidgetColors) {
    Column(GlanceModifier.fillMaxSize().background(colors.widgetBackground)) {
        // Header: title(left) + button(right, touches edges), no outer padding
        Row(
            GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                context.getString(R.string.widget_2x2_title),
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.widgetOnSurfaceVariant
                ),
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp)
            )
            AddButton(context, colors)
        }
        // Content with padding
        // review r2 修:notes 为空时 `noteIndex % notes.size` 即 `0 % 0` 抛 ArithmeticException,
        // 导致 widget 崩溃。先判空,为空时直接走 EmptyState。
        val note = if (notes.isNotEmpty()) notes.getOrNull(noteIndex % notes.size) ?: notes.firstOrNull() else null
        if (note != null) {
            val params = actionParametersOf(OpenNoteAction.KEY_NOTE_ID to note.id)
            Column(
                GlanceModifier.defaultWeight().fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .background(colors.widgetBackground)
                    .clickable(actionRunCallback<OpenNoteAction>(params))
                    .padding(10.dp)
            ) {
                Text(
                    note.title.ifBlank { note.content.take(SNIPPET_LEN) },
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = colors.widgetOnBackground
                    ),
                    maxLines = 1
                )
                Text(
                    note.content.replace("\n", " ").take(50),
                    style = TextStyle(fontSize = 11.sp, color = colors.widgetOnSurfaceVariant),
                    maxLines = 2,
                    modifier = GlanceModifier.padding(top = 2.dp)
                )
                Text(
                    formatRelativeTime(context, note.updatedAt),
                    style = TextStyle(fontSize = 10.sp, color = colors.widgetOnSurfaceVariant),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
        } else {
            EmptyState(colors, context)
        }
        // bottom spacer to keep content area balanced
        Box(GlanceModifier.fillMaxWidth().padding(bottom = 10.dp)) {}
    }
}

// ============================================================
// Wide(4x2)
// ============================================================
@Composable
private fun WidgetWide(notes: List<Note>, context: Context, colors: WidgetColors) {
    Column(GlanceModifier.fillMaxSize().background(colors.widgetBackground)) {
        Row(
            GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                context.getString(R.string.widget_4x2_title),
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.widgetOnSurfaceVariant
                ),
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp)
            )
            AddButton(context, colors)
        }
        if (notes.isEmpty()) {
            EmptyState(colors, context)
        } else {
            Column(GlanceModifier.fillMaxSize()) {
                notes.take(3).forEachIndexed { i, note ->
                    val title = note.title.ifBlank { note.content.take(SNIPPET_LEN) }
                    val body = note.content.replace("\n", " ").take(40)
                    val params = actionParametersOf(OpenNoteAction.KEY_NOTE_ID to note.id)
                    Column(
                        GlanceModifier.fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .background(colors.widgetBackground)
                            .clickable(actionRunCallback<OpenNoteAction>(params))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                style = TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = colors.widgetOnBackground
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                formatRelativeTimeCompact(context, note.updatedAt),
                                style = TextStyle(fontSize = 10.sp, color = colors.widgetOnSurfaceVariant),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(start = 4.dp)
                            )
                        }
                        if (body.isNotBlank()) {
                            Text(
                                body,
                                style = TextStyle(fontSize = 10.sp, color = colors.widgetOnSurfaceVariant),
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

// ============================================================
// Empty state + ROM hint(国产 ROM widget-rome-compat)
// ============================================================
@Composable
private fun EmptyState(colors: WidgetColors, context: Context) {
    val romHint = when (RomDetector.current()) {
        RomVendor.MIUI -> context.getString(R.string.widget_rom_miui_hint)
        RomVendor.EMUI -> context.getString(R.string.widget_rom_emui_hint)
        RomVendor.COLOROS -> context.getString(R.string.widget_rom_coloros_hint)
        RomVendor.ORIGINOS -> context.getString(R.string.widget_rom_originos_hint)
        RomVendor.AOSP -> null
    }
    Box(GlanceModifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                context.getString(R.string.widget_empty),
                style = TextStyle(fontSize = 11.sp, color = colors.widgetOnSurfaceVariant)
            )
            if (romHint != null) {
                Text(
                    romHint,
                    style = TextStyle(fontSize = 10.sp, color = colors.widgetOnSurfaceVariant),
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ── Helpers ──

internal fun formatRelativeTime(context: Context, epochMs: Long): String = DateUtils.getRelativeTimeSpanString(
    epochMs,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE
).toString()

internal fun formatRelativeTimeCompact(context: Context, epochMs: Long): String = DateUtils.getRelativeTimeSpanString(
    epochMs,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE or DateUtils.FORMAT_ABBREV_MONTH
).toString()

private const val SNIPPET_LEN = 30

/**
 * Returns Intent with route extra — Glance fires it on click, no premature launch.
 *
 * L3 修:去掉 [Intent.FLAG_ACTIVITY_CLEAR_TASK] —— CLEAR_TASK 会清空 launcher 任务栈,
 * 进而杀掉 consent 闸门 Activity(若用户已同意但系统重新创建 task)造成流程旁路。
 * 对照 `WidgetIntentHelpers.launchWithTaskStack` 的 flag 选择 — 那边走
 * [androidx.core.app.TaskStackBuilder] 显式构造回退栈,本路径只设 NEW_TASK。
 */
internal fun createNoteIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    .putExtra("route", "quicknote/edit?prefillFocus=true")
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
