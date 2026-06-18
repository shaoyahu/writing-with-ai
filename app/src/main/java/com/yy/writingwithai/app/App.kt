@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yy.writingwithai.app.ui.theme.WritingAppTheme

/**
 * writing-with-ai · 应用根 Composable。
 *
 * 由 [MainActivity.onCreate] 通过 `setContent { App() }` 触发。
 * 整棵 Compose 树从这里进入:[WritingAppTheme] → [Surface] → [AppNav] → 各路由。
 */
@Composable
fun App() {
    WritingAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNav()
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
