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
 * morning-freewrite · 开机 / 替换广播接收器。
 *
 * 设备重启或 APK 自更新后,所有 AlarmManager 闹钟会被系统清空。
 * 监听 BOOT_COMPLETED + MY_PACKAGE_REPLACED,重排闹钟。
 *
 * 设计要点(design §3.3):
 * - `@AndroidEntryPoint` 注入 UserPrefsStore + Scheduler。
 * - enabled=false 时不调度(用户主动关)。
 * - 用 goAsync() 让 onReceive 异步跑,挂起函数能 await Flow.first()。
 * - manifest 配 exported=true(系统广播 receiver 强制要求)。
 */
@AndroidEntryPoint
class MorningFreewriteBootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: UserPrefsStore

    @Inject lateinit var scheduler: MorningFreewriteScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val enabled = prefs.morningFreewriteEnabledFlow.first()
                        val time = prefs.morningFreewriteTimeFlow.first()
                        if (!enabled) {
                            // fix-review-r1 F8 4.4:用户已禁用 → 显式 cancel 一次,
                            // 兜底覆盖"BOOT 之后某些 ROM(尤其定制 Android 厂商)
                            // 把 BOOT_COMPLETED 派到 receiver 之前,闹钟被系统残留重启"
                            // 的边界场景。cancel 是幂等的(no-op if not scheduled),
                            // 不会抛错也不会更糟。MY_PACKAGE_REPLACED 同理:用户
                            // 在更新前如果关掉,替换后理应不响。
                            scheduler.cancel()
                            return@launch
                        }
                        scheduler.schedule(time.hour, time.minute, LocalDate.now())
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            // 其他 action 静默忽略
        }
    }
}
