package com.yy.writingwithai.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.note.backfill.BackfillScheduler
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * writing-with-ai · Application 入口。
 *
 * 由 `@HiltAndroidApp` 触发 Hilt 组件编译期生成;
 * M2 起在 onCreate 里完成 Room / DataStore / OkHttp 初始化,M0 仅做 Hilt 接入验证。
 *
 * M4-1:用 [WorkManager.enqueueUniquePeriodicWork] 注册 widget 兜底 15min 周期任务。
 *
 * hardening-sse-and-widget-init H-3:删除 [QuickNoteWidgetHiltBridge.repository] 的赋值。
 * 旧实现依赖 `Application.onCreate` 写入全局 mutable 字段供 Glance widget host process 读;
 * 新实现走 `EntryPointAccessors.fromApplication` 动态解析,不依赖 onCreate 完成时机。
 *
 * M4-4:r1 H3 修 — `BuildConfig.CONSENT_GATE_ENABLED=false` 时同步写默认 consent 到 DataStore
 * (一次性,卸载重装会重置,等同于"回滚到 M4-3 行为")。这样 App 冷启时 `AppNav` 看到
 * `accepted=true` 直接跳过 onboarding 屏,不会死锁。
 */
@HiltAndroidApp
class WritingApp : Application() {
    @Inject
    lateinit var consentStore: ConsentStore

    @Inject
    lateinit var backfillScheduler: BackfillScheduler

    // fix-2026-06-24-review-r1-high H23:异步 IO scope,不阻塞 Application.onCreate
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // r1 H3 修:CONSENT_GATE_ENABLED=false 回滚逃生口 — 同步写默认 consent
        // (卸载重装即重置,避免永久卡 onboarding 屏)
        if (!BuildConfig.CONSENT_GATE_ENABLED) {
            appScope.launch {
                consentStore.setAccepted(
                    version = BuildConfig.CONSENT_VERSION,
                    at = System.currentTimeMillis()
                )
            }
        }

        // 2) 注册 widget 兜底周期任务(15 min,KEEP 保证 App 重启不重置下次执行时间)
        val periodic = PeriodicWorkRequestBuilder<QuickNoteWidgetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "quicknote-widget-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        // 3) note-association 首次启动 backfill(延后 5s,一次性)
        backfillScheduler.scheduleIfNeeded()

        // 4) entity-extraction-association §7.2:DB 升级后 enqueue 实体抽取回填(5s 延后)
        backfillScheduler.scheduleEntityBackfillIfNeeded()
    }
}
