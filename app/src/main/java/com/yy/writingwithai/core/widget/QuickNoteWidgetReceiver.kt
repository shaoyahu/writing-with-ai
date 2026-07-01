package com.yy.writingwithai.core.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * M4-1 · Glance 桌面 widget receiver。
 *
 * 在 `AndroidManifest.xml` 注册(`android:name=".core.widget.QuickNoteWidgetReceiver"` +
 * `android.appwidget.action.APPWIDGET_UPDATE` intent-filter)，桥接 widget host 与
 * [QuickNoteWidget]。
 *
 * widget-rome-compat · widget 状态持久化由 [WidgetStateStore] (Application-scoped DataStore)
 * 承担，不走 `GlanceAppWidget.stateDefinition`(Glance 1.1.x final 不可 override)。
 */
class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()
}
