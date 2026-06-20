package com.yy.writingwithai.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.widget.OpenNoteAction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * writing-with-ai · 单 Activity 入口。
 *
 * 整个应用的 Compose 树根是 [App];所有路由都由 [AppNav] 承载。
 *
 * M4-1 改动:从 widget `PendingIntent` 启动时,`Intent.extra[OpenNoteAction.EXTRA_ROUTE]`
 * 携带路由字符串(如 `"quicknote/edit?prefillFocus=true"`),`App(initialRoute=...)` 解析并跳。
 *
 * M4-4 改动(r1 H1 + M1 修):
 * - 解析 `intent.getStringExtra("route")` 前同步查 ConsentStore.isConsented();
 *   未同意 → 把 route 写到 `widgetPendingRoute` MutableState(由 `AppNav` 在同意后 navigate)
 * - `onNewIntent`(r1 M1 修)同样处理:未同意 → 改 widgetPendingRoute;已同意 → 立刻
 *   setContent 把 route 传给 `App`(走 initialRoute 路径,AppNav 启动 LaunchedEffect 解析)
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface MainActivityEntryPoint {
    fun consentStore(): ConsentStore
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** M4-4:widget 启动但未同意时暂存 route,同意后回放。Compose state 由 App 持,跨 Activity 重建。 */
    private val widgetPendingRoute = mutableStateOf<String?>(null)
    private var lastInitialRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawRoute = intent?.getStringExtra(OpenNoteAction.EXTRA_ROUTE)
        handleRawRoute(rawRoute)
        setContent {
            App(
                initialRoute = lastInitialRoute,
                widgetPendingRoute = widgetPendingRoute
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val rawRoute = intent.getStringExtra(OpenNoteAction.EXTRA_ROUTE)
        handleRawRoute(rawRoute)
    }

    /**
     * M5:consent 检查移入 [Dispatchers.IO] 协程,主线程不 block。
     * 已同意 → 更新 [lastInitialRoute];未同意 → 写 [widgetPendingRoute] state。
     * fire-and-forget;AppNav LaunchedEffect(consentState) 异步处理 route 回放。
     */
    private fun handleRawRoute(rawRoute: String?) {
        if (rawRoute == null) return
        val consentStore =
            EntryPointAccessors.fromActivity(this, MainActivityEntryPoint::class.java).consentStore()
        lifecycleScope.launch(Dispatchers.IO) {
            val consented = consentStore.isConsented(BuildConfig.CONSENT_VERSION)
            withContext(Dispatchers.Main) {
                if (consented) {
                    lastInitialRoute = rawRoute
                } else {
                    widgetPendingRoute.value = rawRoute
                }
            }
        }
    }
}
