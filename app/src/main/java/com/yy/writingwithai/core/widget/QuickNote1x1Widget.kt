package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yy.writingwithai.R

/**
 * 1x1 快速记笔记纯按钮 widget。
 * 点击跳转编辑页(新建笔记,prefillFocus=true)。
 */
class QuickNote1x1Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Widget1x1Content() }
        }
    }
}

@Composable
private fun Widget1x1Content() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primary)
            .cornerRadius(16.dp)
            .clickable(
                onClick = actionRunCallback<CreateNoteFromWidgetAction>()
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(R.string.widget_1x1_label),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
