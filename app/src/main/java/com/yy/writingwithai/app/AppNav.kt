@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * writing-with-ai · 应用 NavHost。
 *
 * M0 仅含一个占位路由 `home`,后续 change(`quick-note-feature` / `aiwriting` 等)按需加 destination。
 * 路由 id 用裸字符串便于 v1 简单维护;v2+ 可换类型安全路由。
 */
@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
    ) {
        composable(HOME_ROUTE) {
            HomePlaceholder()
        }
    }
}

private const val HOME_ROUTE = "home"

@Composable
private fun HomePlaceholder() {
    Text(
        text = stringResource(R.string.placeholder_greeting),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(LocalSpacing.current.lg),
    )
}
