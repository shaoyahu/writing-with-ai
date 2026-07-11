package com.yy.writingwithai.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * morning-freewrite · 闹钟调度器。
 *
 * 封装「每日 fixed time 提醒」三件 API:
 * - [schedule] 注册下一次闹钟(精确或 fallback)
 * - [cancel] 取消已注册的闹钟
 * - [nextTriggerAt] 纯函数:给定 hour/minute + now → 下一次触发时刻
 *
 * SCHEDULE_EXACT_ALARM 策略(design §3.2):
 * - API 31+:调 `canScheduleExactAlarms()`;true → `setExactAndAllowWhileIdle`;
 *   false → `setAndAllowWhileIdle`(DOZE 时可能晚几分钟,文档已写明)。
 * - API < 31:直接 `setExactAndAllowWhileIdle`(无需权限)。
 *
 * 不申请 SCHEDULE_EXACT_ALARM(Google Play 政策限制「日历/闹钟/提醒」以外类目),
 * fallback 路径在文档里写明「±15 分钟精度不保证」。
 */
@Singleton
class MorningFreewriteScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * 注册下一次闹钟。`date` 仅用于日历参考;实际触发时刻由 [nextTriggerAt] 计算。
     * 多次调用相同 requestCode 会自动覆盖前一个闹钟(PendingIntent.FLAG_UPDATE_CURRENT)。
     */
    fun schedule(hour: Int, minute: Int, date: LocalDate) {
        val am = alarmManager ?: return
        val pending = buildPendingIntent(hour, minute)
        val triggerAtMillis = nextTriggerAt(hour, minute, ZonedDateTime.now())
            .toInstant()
            .toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // fallback:无 SCHEDULE_EXACT_ALARM 权限 → 非精确,DOZE 时可能延迟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    /** 取消已注册的闹钟(走同一 PendingIntent)。 */
    fun cancel() {
        val am = alarmManager ?: return
        // 用 0/0 占位 schedule 一次只是为了拿等价 PendingIntent 来 cancel
        val pending = buildPendingIntent(0, 0)
        am.cancel(pending)
    }

    /**
     * 探测当前进程是否拥有 SCHEDULE_EXACT_ALARM 权限。
     * fix-review-r1 F8 4.6:Settings 屏调用此 API 决定是否弹"权限缺失"提示条 + 一键
     * 跳系统设置对话框(API 31+:Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
     * API < 31:恒返 true,不需要权限)。
     * 原版 schedule 只在内部静默 fallback,UI 完全无感,用户不知道为啥闹钟晚 15 分钟。
     */
    fun canScheduleExactAlarms(): Boolean {
        val am = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            // API < 31 不需要 SCHEDULE_EXACT_ALARM 权限
            true
        }
    }

    /**
     * 启动系统"允许精确闹钟"权限申请页(API 31+)。返回 Intent 让 caller 用
     * `rememberLauncherForActivityResult` 启动,成功后由 VM 重读 [canScheduleExactAlarms]。
     * API < 31 不需要此步骤,caller 应分支跳过。
     */
    fun buildExactAlarmPermissionIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }
    }

    /**
     * 纯函数(design §3.2):计算「下一次 hour:minute 触发」时刻(本地时区)。
     * - 当日 hour:minute 已过 → 返回次日
     * - 当日 hour:minute 未到 → 返回今日
     * - hour=23 / minute=59 等边界值走 java.time,不写死 UTC
     */
    fun nextTriggerAt(hour: Int, minute: Int, now: ZonedDateTime): ZonedDateTime {
        val zone: ZoneId = now.zone
        val targetToday = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        val targetTodayZoned = targetToday.atZone(zone)
        return if (now.toLocalTime().isBefore(LocalTime.of(hour, minute))) {
            targetTodayZoned
        } else {
            targetTodayZoned.plusDays(1)
        }
    }

    private fun buildPendingIntent(hour: Int, minute: Int): PendingIntent {
        val intent = Intent(context, MorningFreewriteAlarmReceiver::class.java).apply {
            // alarm receiver 走 BroadcastReceiver,不直接启 Activity(避免提前解锁到屏)。
            // receiver 内部走 Notifier 弹通知,再走 MainActivity extra route。
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        val flags =
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            flags
        )
    }

    companion object {
        private const val REQUEST_CODE_ALARM = 2001
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
    }
}
