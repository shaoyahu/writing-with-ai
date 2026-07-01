package com.yy.writingwithai.core.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * M5 widget-1x4-compact · 1x4 compact Glance widget receiver。
 *
 * 注册在 `AndroidManifest.xml`(`android:name=".core.widget.QuickNote1x4WidgetReceiver"` +
 * `android.appwidget.action.APPWIDGET_UPDATE` intent-filter)，桥接 widget host 与
 * [QuickNote1x4Widget]。
 *
 * 与 [QuickNoteWidgetReceiver] 独立 — 一个 AOSP launcher 同 package 多个 AppWidget receiver
 * 共存，各自 label + widget_info 元数据让 launcher widget picker 同时展示"随手记"1x4 紧凑选项。
 *
 * widget-rome-compat · 状态持久化走 [WidgetStateStore](Glance 1.1.x 不支持 override stateDefinition)。
 */
class QuickNote1x4WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNote1x4Widget()
}
