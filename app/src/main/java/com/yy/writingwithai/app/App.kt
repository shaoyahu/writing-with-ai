@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.yy.writingwithai.app.ui.theme.WritingAppTheme
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.core.widget.WidgetLaunchRoute
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * writing-with-ai · 应用根 Composable。
 *
 * 由 [MainActivity.onCreate] 通过 `setContent { App() }` 触发。
 * 整棵 Compose 树从这里进入:[WritingAppTheme] → [Surface] → [AppNav] → 各路由。
 *
 * M4-4 改动(r1 H1 修):新增 `widgetPendingRoute: MutableState<String?>` 参数，
 * `MainActivity` 写入，`AppNav.LaunchedEffect(consentState.accepted)` 在同意后
 * 读 + navigate 该 route + 清栈(防 back 回 onboarding)。
 */
@Composable
fun App(
    initialRoute: WidgetLaunchRoute? = null,
    // fix-full-review:默认参数 mutableStateOf(null) 在 Composable 函数外求值，
    // 所有使用默认值的调用点共享同一个 MutableState 实例。改为 remember { mutableStateOf(null) }
    // 确保每个调用点获得独立实例(MainActivity 显式传入，不受影响)。
    widgetPendingRoute: MutableState<WidgetLaunchRoute?> = remember { mutableStateOf(null) },
    onNavControllerReady: (NavController) -> Unit = {}
) {
    val userPrefsStore = rememberUserPrefsStore()
    WritingAppTheme(userPrefsStore = userPrefsStore) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNav(
                initialRoute = initialRoute,
                widgetPendingRoute = widgetPendingRoute,
                onNavControllerReady = onNavControllerReady
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    WritingAppTheme {
        App()
    }
}

/** Hilt EntryPoint 用于在 @Composable 中获取 [UserPrefsStore](Theme 不反向依赖 core/prefs 构造参数)。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUserPrefsStoreEntryPoint {
    fun userPrefsStore(): UserPrefsStore
}

/** 从 Application context 通过 Hilt EntryPoint 获取 [UserPrefsStore]。 */
@Composable
private fun rememberUserPrefsStore(): UserPrefsStore? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AppUserPrefsStoreEntryPoint::class.java
            ).userPrefsStore()
        }.getOrNull()
    }
}
