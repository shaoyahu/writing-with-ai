package com.yy.writingwithai.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yy.writingwithai.core.prefs.UserPrefsStore
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * morning-freewrite · 闹钟触发接收器。
 *
 * AlarmManager 触发 → onReceive → 读 prefs(enabled + hour/minute)→
 * Notifier 弹当日通知 + Scheduler 安排次日(滚雪球)。
 *
 * 设计要点(design §3.2):
 * - `@AndroidEntryPoint` 注入 UserPrefsStore / Scheduler / Notifier。
 * - onReceive 不能挂起,用 `goAsync()` 异步跑协程 → handleAsyncResult。
 * - 不直接启 Activity(避免提前解锁屏),只弹通知。
 * - 通知里点 → MainActivity extra route=freewrite/{date} → 沉浸屏。
 */
@AndroidEntryPoint
class MorningFreewriteAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: UserPrefsStore

    @Inject lateinit var scheduler: MorningFreewriteScheduler

    @Inject lateinit var notifier: MorningFreewriteNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val enabled = prefs.morningFreewriteEnabledFlow.first()
                if (!enabled) {
                    return@launch
                }
                val time = prefs.morningFreewriteTimeFlow.first()
                val today = LocalDate.now()
                notifier.showDailyReminder(today)
                // 滚雪球:今天响完后再安排明天的闹钟
                scheduler.schedule(time.hour, time.minute, today)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
