@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yy.writingwithai.app.ui.theme.WritingAppTheme

/**
 * writing-with-ai · 应用根 Composable。
 *
 * 由 [MainActivity.onCreate] 通过 `setContent { App() }` 触发。
 * 整棵 Compose 树从这里进入:[WritingAppTheme] → [Surface] → [AppNav] → 各路由。
 *
 * M4-4 改动(r1 H1 修):新增 `widgetPendingRoute: MutableState<String?>` 参数,
 * `MainActivity` 写入,`AppNav.LaunchedEffect(consentState.accepted)` 在同意后
 * 读 + navigate 该 route + 清栈(防 back 回 onboarding)。
 */
@Composable
fun App(initialRoute: String? = null, widgetPendingRoute: MutableState<String?> = mutableStateOf(null)) {
    WritingAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNav(
                initialRoute = initialRoute,
                widgetPendingRoute = widgetPendingRoute
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
