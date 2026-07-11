package com.yy.writingwithai.app

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.i18n.LocaleHelper
import com.yy.writingwithai.core.i18n.LocaleStore
import com.yy.writingwithai.core.note.backfill.BackfillScheduler
import com.yy.writingwithai.core.notification.MorningFreewriteNotifier
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetWorker
import com.yy.writingwithai.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * writing-with-ai · Application 入口。
 *
 * 由 `@HiltAndroidApp` 触发 Hilt 组件编译期生成;
 * M2 起在 onCreate 里完成 Room / DataStore / OkHttp 初始化，M0 仅做 Hilt 接入验证。
 *
 * M4-1:用 [WorkManager.enqueueUniquePeriodicWork] 注册 widget 兜底 15min 周期任务。
 *
 * hardening-sse-and-widget-init H-3:删除 [QuickNoteWidgetHiltBridge.repository] 的赋值。
 * 旧实现依赖 `Application.onCreate` 写入全局 mutable 字段供 Glance widget host process 读;
 * 新实现走 `EntryPointAccessors.fromApplication` 动态解析，不依赖 onCreate 完成时机。
 *
 * M4-4:r1 H3 修 — `BuildConfig.CONSENT_GATE_ENABLED=false` 时同步写默认 consent 到 DataStore
 * (一次性，卸载重装会重置，等同于"回滚到 M4-3 行为")。这样 App 冷启时 `AppNav` 看到
 * `accepted=true` 直接跳过 onboarding 屏，不会死锁。
 */
@HiltAndroidApp
class WritingApp : Application() {
    @Inject
    lateinit var consentStore: ConsentStore

    @Inject
    lateinit var backfillScheduler: BackfillScheduler

    @Inject
    lateinit var localeStore: LocaleStore

    // morning-freewrite · 通知 channel 在 App 启动时建一次(SDK 26+ 幂等)。
    @Inject
    lateinit var morningFreewriteNotifier: MorningFreewriteNotifier

    // fix H19:改用 Hilt 注入的 @ApplicationScope CoroutineScope,不再自管 SupervisorJob。
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    // language-switcher:在 super.onCreate 之前注入 locale(attachBaseContext 早于 onCreate,
    // Hilt @Inject 还没 ready — 用 LocaleStore.readOnceBlocking 走 raw DataStore 读，
    // 不依赖 Hilt 注入)。所有后续 Activity / Composable 拿资源时自动走正确 locale。
    override fun attachBaseContext(base: Context) {
        val selection = LocaleStore.readOnceBlocking(base)
        val systemLocale = base.resources.configuration.locales[0]
            ?: java.util.Locale.getDefault()
        val locale = LocaleHelper.resolveLocale(selection, systemLocale)
        super.attachBaseContext(LocaleHelper.wrap(base, locale))
    }

    override fun onCreate() {
        super.onCreate()

        // r1 H3 修:CONSENT_GATE_ENABLED=false 回滚逃生口 — 同步写默认 consent
        // (卸载重装即重置，避免永久卡 onboarding 屏)
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

        // 3) note-association 首次启动 backfill(延后 5s，一次性)
        backfillScheduler.scheduleIfNeeded()

        // 4) entity-extraction-association §7.2:DB 升级后 enqueue 实体抽取回填(5s 延后)
        backfillScheduler.scheduleEntityBackfillIfNeeded()

        // 5) morning-freewrite · 启动时建 channel(SDK < 26 空走,SDK >= 26 幂等)
        morningFreewriteNotifier.createChannel()
    }
}
