@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.WritingAppTheme
import com.yy.writingwithai.feature.my.MeTabTarget
import com.yy.writingwithai.feature.my.MyScreen
import com.yy.writingwithai.feature.quicknote.list.QuickNoteListScreen

/**
 * app-bottom-tab-bar · 应用底部 3 槽 tab 容器 Composable。
 *
 * 与同 package 的 `data object AppShell`(@Serializable route)同名不同物:本 Composable 是 UI
 * 实现,route 是 NavController 字符串标识。`composable<AppShell> { AppShell(...) }` block
 * 把它们绑在一起。review r1 L3 注解。
 *
 * 职责:
 * - 渲染 `NavigationBar` + 中央凸起 FAB(全局新建笔记入口)
 * - 内部嵌入子 `NavHost`,startDestination = Notes,渲染 `Notes` / `Me` 两个 tab 根屏
 * - tab 切换走**子 NavController**;FAB / 详情 / 编辑器 / 设置走**根 NavController**
 *   (详情/编辑器跨 tab 共享 root 栈,tab bar 选中态保留)
 *
 * 不持有独立 `selectedTab` state;当前选中态通过 `currentBackStackEntryAsState()` +
 * `NavDestination.hasRoute<T>()`(review r1 M5 修)推导。
 *
 * popUpTo 锚点用 inner NavController 的 startDestination(`Notes::class`)而非外层 `AppShell`
 * ——`AppShell` 不在 inner graph,会抛 IllegalArgumentException。review r1 H1 修。
 */
@Composable
fun AppShell(rootNavController: NavHostController, onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    val innerNavController = rememberNavController()
    val currentEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 反馈(2026-06-23):外层 Scaffold 只有 bottomBar,不应给 inner 内容加 status bar inset。
        // contentWindowInsets = WindowInsets(0) 让 inner 各 Screen 自行处理 insets,
        // 避免 enableEdgeToEdge() 后 inner Scaffold 的 TopAppBar 出现 double-pad。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppTabBar(
                currentDestination = currentEntry?.destination,
                onSelectTab = { route ->
                    // tab 切换:传 route 实例(Notes / Me),不用 KClass —— NavController 用 serializer
                    // 把 route 转 String,KClass 没有 @Serializable serializer 会抛
                    // "Serializer for class 'ClassReference' is not found"(真机崩)。
                    // popUpTo<Notes>() 是 typed 扩展,在 NavController graph 内按 KClass 找 ID,
                    // 不走 serializer,所以安全。
                    innerNavController.navigate(route) {
                        // review r1 H1 修:popUpTo 锚点传 route 实例(Notes)而非 KClass。
                        // popUpTo(Notes) 内部用 serializer 把 Notes 转 ID,在 graph 找匹配 destination。
                        // 之前用 popUpTo(Notes::class) / popUpTo<Notes>() 都会因 KClass 没 @Serializable
                        // 而崩("Serializer for class 'ClassReference' is not found")。
                        popUpTo(Notes) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onCenterFabClick = onCreateClick
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = Notes::class,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // tab 根屏:Notes / Me。QuickNoteListScreen 不再含 overflow / FAB(由本 shell 接管)。
            composable<Notes> {
                QuickNoteListScreen(
                    onNoteClick = { id -> rootNavController.navigate(QuicknoteDetail(id)) },
                    onCreateClick = onCreateClick
                )
            }
            composable<Me> {
                // review r1 M2 修:onNavigate 改成 typed (MeTabTarget) -> Unit,编译期捕获拼写错。
                MyScreen(
                    onNavigate = { target ->
                        when (target) {
                            MeTabTarget.SettingsData -> rootNavController.navigate(SettingsData)
                            MeTabTarget.SettingsModelManagement -> rootNavController.navigate(SettingsModelManagement)
                            MeTabTarget.SettingsPromptTemplate -> rootNavController.navigate(SettingsPromptTemplate)
                            MeTabTarget.SettingsAliasManagement -> rootNavController.navigate(SettingsAliasManagement)
                            MeTabTarget.Settings -> rootNavController.navigate(Settings)
                            MeTabTarget.FeishuAuth -> rootNavController.navigate(FeishuAuth)
                            MeTabTarget.About -> rootNavController.navigate(About)
                        }
                    }
                )
            }
        }
    }
}

/**
 * app-bottom-tab-bar · 底部 3 槽 tab 栏 + 中央凸起圆形 FAB。
 *
 * 槽位 1:笔记(NavigationBarItem)
 * 槽位 2:中央 FAB(Box 占位 + Modifier.offset 抬出)
 * 槽位 3:我的(NavigationBarItem)
 *
 * 手势导航设备:用 `WindowInsets.systemBars.only(Bottom)` 保证 FAB / bar 不被裁。
 *
 * review r1 修:
 * - M5:`NavDestination.hasRoute<T>()` 替 substring 匹配
 * - M4:删 FAB `Modifier.shadow`(FAB 自带 elevation 已投影)
 * - L2:`onSelectTab` 入参类型改为 `KClass<*>`
 * - L5:NavigationBarItem 内 `Icon.contentDescription = null`(避免 a11y 双重 label)
 */
@Composable
private fun AppTabBar(
    currentDestination: androidx.navigation.NavDestination?,
    onSelectTab: (Any) -> Unit,
    onCenterFabClick: () -> Unit
) {
    // 反馈(2026-06-23):FAB 改用 Box overlay 绝对定位,不再塞进 NavigationBar 内部。
    // 之前 FAB 在 NavigationBar 的 Row slot 内 + offset(-16),顶部被 bar Surface 遮挡成横线。
    // 现在 NavigationBar 正常渲染 3 槽(左 notes / 中占位 / 右 me),FAB 作为兄弟层叠在上方,
    // 用 align(TopCenter).offset 抬出 bar 上沿,完整圆形可见。
    Box(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
        )
    ) {
        NavigationBar {
            val notesSelected = currentDestination?.hasRoute<Notes>() == true
            NavigationBarItem(
                selected = notesSelected,
                onClick = { onSelectTab(Notes) },
                icon = {
                    Icon(
                        imageVector = if (notesSelected) {
                            Icons.AutoMirrored.Filled.Notes
                        } else {
                            Icons.AutoMirrored.Outlined.Notes
                        },
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.tab_notes)) }
            )
            // 槽 2 — 中央占位(与右侧槽等宽)
            Spacer(Modifier.size(56.dp))
            val meSelected = currentDestination?.hasRoute<Me>() == true
            NavigationBarItem(
                selected = meSelected,
                onClick = { onSelectTab(Me) },
                icon = {
                    Icon(
                        imageVector = if (meSelected) {
                            Icons.Filled.Person
                        } else {
                            Icons.Outlined.Person
                        },
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.tab_my)) }
            )
        }
        // 中央 FAB overlay:绝对定位在 NavigationBar 顶部中央,offset 抬出 bar 上沿。
        FloatingActionButton(
            onClick = onCenterFabClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
                .size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.tab_new_note_cd)
            )
        }
    }
}

@Preview(name = "AppShell", showBackground = true)
@Composable
private fun AppShellPreview() {
    WritingAppTheme {
        AppShell(
            rootNavController = rememberNavController(),
            onCreateClick = {}
        )
    }
}
