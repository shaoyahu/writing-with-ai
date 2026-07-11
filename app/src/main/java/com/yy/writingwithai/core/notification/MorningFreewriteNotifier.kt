package com.yy.writingwithai.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.yy.writingwithai.R
import com.yy.writingwithai.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * morning-freewrite · 通知发送器。
 *
 * 封装「每日晨写」通知的 channel 创建 + PendingIntent 构造 + 发送。channel
 * id = [CHANNEL_ID],重要性 `IMPORTANCE_DEFAULT`(有声音 + 不弹全屏)。
 *
 * 设计要点(spec §3.1):
 * - PendingIntent 走 `TaskStackBuilder` + MainActivity extra `route=freewrite/{date}`
 *   复用 M4-1 widget helper 同款"back 回 launcher"行为;
 * - flags: `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`(API 31+ 强制 immutable);
 * - `createChannel()` 幂等(SDK >= 26 create 一次,SDK < 26 无 channel 也调一次无害)。
 */
@Singleton
class MorningFreewriteNotifier
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private val nm: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        createChannel()
    }

    /**
     * spec §3.1 + design §3.1:幂等创建 channel。SDK >= 26 走 NotificationManager API;
     * < 26 NotificationChannel 不存在,空走。
     */
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = nm ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.freewrite_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            // fix-review-r1 F8 4.7:channel description 不再用 per-notification body。
            // 系统设置 → 应用通知 → 频道详情页读这里,跟通知栏正文解耦。
            description = context.getString(R.string.freewrite_notification_channel_description)
        }
        mgr.createNotificationChannel(channel)
    }

    /**
     * 发"今日晨写"通知。点击 → MainActivity extra `route=freewrite/{date.toString()}`。
     */
    fun showDailyReminder(date: LocalDate) {
        val mgr = nm ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ROUTE, "freewrite/$date")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(
                REQUEST_CODE_DAILY,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ai_24dp)
            .setContentTitle(context.getString(R.string.freewrite_notification_title))
            .setContentText(context.getString(R.string.freewrite_notification_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        mgr.notify(NOTIFICATION_ID_DAILY, notification)
    }

    companion object {
        const val CHANNEL_ID = "morning_freewrite"
        const val EXTRA_ROUTE = "route"
        private const val REQUEST_CODE_DAILY = 1001
        private const val NOTIFICATION_ID_DAILY = 1001
    }
}
