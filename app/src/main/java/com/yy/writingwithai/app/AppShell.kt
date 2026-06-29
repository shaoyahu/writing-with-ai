@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.yy.writingwithai.core.ui.animation.LocalAnimationTokens
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
 * - 渲染自定义 `Surface` tab 栏 + 中央凸起 FAB(全局新建笔记入口)
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
        // animation-system · tab 切换也走 token(spec §REQ 4 AppShell 部分)。
        // NavHost transition lambda 不是 @Composable,需提前读 token 再 lambda 内引用。
        val navTokens = LocalAnimationTokens.current
        NavHost(
            navController = innerNavController,
            startDestination = Notes::class,
            enterTransition = { navTokens.navEnter },
            exitTransition = { navTokens.navExit },
            popEnterTransition = { navTokens.navPopEnter },
            popExitTransition = { navTokens.navPopExit },
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
                // app-bottom-tab-bar spec 4 Decision 4:5 条入口,飞书同步 → Settings(已有
                // FeishuSyncLogSection),关于条目纯展示不 navigate,不在 MeTabTarget 中。
                MyScreen(
                    onNavigate = { target ->
                        when (target) {
                            MeTabTarget.SettingsData -> rootNavController.navigate(SettingsData)
                            MeTabTarget.SettingsModelManagement -> rootNavController.navigate(SettingsModelManagement)
                            MeTabTarget.SettingsPromptTemplate -> rootNavController.navigate(SettingsPromptTemplate)
                            MeTabTarget.SettingsAliasManagement -> rootNavController.navigate(SettingsAliasManagement)
                            MeTabTarget.Settings -> rootNavController.navigate(Settings)
                            // animation-system-and-consent-redesign §11.3:MyScreen when 分支接
                            // SettingsAnimationStyle → 根 NavController navigate 走 AnimationStylePreviewScreen
                            MeTabTarget.SettingsAnimationStyle -> rootNavController.navigate(SettingsAnimationStyle)
                            // ux-2026-06-28 P6:飞书授权页专属路由
                            MeTabTarget.SettingsFeishu -> rootNavController.navigate(SettingsFeishu)
                            // ux-2026-06-28 P6:笔记关联设置专属路由
                            MeTabTarget.SettingsNoteAssociation -> rootNavController.navigate(SettingsNoteAssociation)
                        }
                    },
                    onBack = { innerNavController.popBackStack(Notes, inclusive = false) }
                )
            }
        }
    }
}

/**
 * app-bottom-tab-bar · 底部 tab 栏 + 中央凸起圆形 FAB。
 *
 * 视觉对齐【我的】tab(MyScreen.kt)的 SectionCard 圆角 + primary tint icon + surfaceVariant
 * 容器:外层 1 个 surfaceVariant Surface(顶部 24dp 圆角 + 1dp tonalElevation),
 * 2 个 Surface 子卡(16dp 圆角)分别装 Notes / Me(selected = primary 容器色)。
 *
 * 槽位 1:笔记(Surface 子卡)
 * 槽位 2:中央 FAB(Spacer 占位 + Box overlay 抬出)
 * 槽位 3:我的(Surface 子卡)
 *
 * 手势导航设备:用 `WindowInsets.systemBars.only(Bottom)` 保证 FAB / bar 不被裁。
 *
 * internal 暴露给 `app/src/test/.../app/AppTabBarTest` 验证视觉层级(spec 4.5 替代品 — 真机离线
 * 场景下用 Robolectric + Compose UI Test 渲染断言)。
 */
@Composable
private fun AppTabBar(
    currentDestination: androidx.navigation.NavDestination?,
    onSelectTab: (Any) -> Unit,
    onCenterFabClick: () -> Unit
) {
    // 反馈(2026-06-23):FAB 用 Box overlay 绝对定位,不再塞进 bar 内部。
    // 之前 FAB 在 Row slot 内 + offset(-16),顶部被 bar Surface 遮挡成横线。
    // 现在外层 Surface 正常渲染 2 个 tab 子卡 + 中央 Spacer 间隔,FAB 作为兄弟层叠在上方,
    // 用 align(TopCenter).offset 抬出 bar 上沿,完整圆形可见。
    Box(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
        )
    ) {
        // 外层 surfaceVariant 卡片(跟【我的】tab 的 SectionCard 同源视觉)
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val notesSelected = currentDestination?.hasRoute<Notes>() == true
                TabCard(
                    selected = notesSelected,
                    icon = if (notesSelected) {
                        Icons.AutoMirrored.Filled.Notes
                    } else {
                        Icons.AutoMirrored.Outlined.Notes
                    },
                    label = stringResource(R.string.tab_notes),
                    onClick = { onSelectTab(Notes) },
                    modifier = Modifier.weight(1f)
                )
                // 槽 2 — 中央占位(56dp,给 FAB 抬出留空间)
                Spacer(Modifier.size(56.dp))
                val meSelected = currentDestination?.hasRoute<Me>() == true
                TabCard(
                    selected = meSelected,
                    icon = if (meSelected) {
                        Icons.Filled.Person
                    } else {
                        Icons.Outlined.Person
                    },
                    label = stringResource(R.string.tab_my),
                    onClick = { onSelectTab(Me) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // 中央 FAB overlay:绝对定位在 Surface 顶部中央,offset 抬出 bar 上沿。
        // 容器色从 secondary 改 primary,跟 selected tab 视觉呼应。
        FloatingActionButton(
            onClick = onCenterFabClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 12.dp,
                pressedElevation = 16.dp
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

/**
 * app-bottom-tab-bar · 笔记 / 我的 tab 子卡(16dp 圆角 Surface)。
 * selected = primary 容器 + onPrimary 内容;unselected = 透明容器 + onSurfaceVariant 内容。
 * 整卡 clickable,触控目标 ≥ 48dp。
 */
@Composable
private fun TabCard(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.size(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
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
