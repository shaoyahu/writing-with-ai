@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
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
 * 实现，route 是 NavController 字符串标识。`composable<AppShell> { AppShell(...) }` block
 * 把它们绑在一起。review r1 L3 注解。
 *
 * 职责:
 * - 渲染自定义 `Surface` tab 栏 + 中央凸起 FAB(全局新建笔记入口)
 * - 内部嵌入子 `NavHost`,startDestination = Notes，渲染 `Notes` / `Me` 两个 tab 根屏
 * - tab 切换走**子 NavController**;FAB / 详情 / 编辑器 / 设置走**根 NavController**
 *   (详情/编辑器跨 tab 共享 root 栈，tab bar 选中态保留)
 *
 * 不持有独立 `selectedTab` state;当前选中态通过 `currentBackStackEntryAsState()` +
 * `NavDestination.hasRoute<T>()`(review r1 M5 修)推导。
 *
 * popUpTo 锚点用 inner NavController 的 startDestination(`Notes::class`)而非外层 `AppShell`
 * ——`AppShell` 不在 inner graph，会抛 IllegalArgumentException。review r1 H1 修。
 */
@Composable
fun AppShell(rootNavController: NavHostController, onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    val innerNavController = rememberNavController()
    val currentEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 反馈(2026-06-23):外层 Scaffold 只有 bottomBar，不应给 inner 内容加 status bar inset。
        // contentWindowInsets = WindowInsets(0) 让 inner 各 Screen 自行处理 insets,
        // 避免 enableEdgeToEdge() 后 inner Scaffold 的 TopAppBar 出现 double-pad。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppTabBar(
                currentDestination = currentEntry?.destination,
                onSelectTab = { route ->
                    // tab 切换:传 route 实例(Notes / Me)，不用 KClass —— NavController 用 serializer
                    // 把 route 转 String,KClass 没有 @Serializable serializer 会抛
                    // "Serializer for class 'ClassReference' is not found"(真机崩)。
                    // popUpTo<Notes>() 是 typed 扩展，在 NavController graph 内按 KClass 找 ID,
                    // 不走 serializer，所以安全。
                    innerNavController.navigate(route) {
                        // review r1 H1 修:popUpTo 锚点传 route 实例(Notes)而非 KClass。
                        // popUpTo(Notes) 内部用 serializer 把 Notes 转 ID，在 graph 找匹配 destination。
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
        // NavHost transition lambda 不是 @Composable，需提前读 token 再 lambda 内引用。
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
                // review r1 M2 修:onNavigate 改成 typed (MeTabTarget) -> Unit，编译期捕获拼写错。
                // app-bottom-tab-bar spec 4 Decision 4:5 条入口，飞书同步 → Settings(已有
                // FeishuSyncLogSection)，关于条目纯展示不 navigate，不在 MeTabTarget 中。
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
                            // animation-switch-redesign-followup §6.3:SettingsAnimationDetail → AnimationDetailScreen(nav/tab 细分开关)
                            MeTabTarget.SettingsAnimationDetail -> rootNavController.navigate(SettingsAnimationDetail)
                            // ux-2026-06-28 P6:飞书授权页专属路由
                            MeTabTarget.SettingsFeishu -> rootNavController.navigate(SettingsFeishu)
                            // ux-2026-06-28 P6:笔记关联设置专属路由
                            MeTabTarget.SettingsNoteAssociation -> rootNavController.navigate(SettingsNoteAssociation)
                            MeTabTarget.SettingsLanguage -> rootNavController.navigate(SettingsLanguage)
                        }
                    }
                )
            }
        }
    }
}

/**
 * app-tab-bar-redesign v4 · 底部 tab 栏(三槽内嵌布局，全宽铺满)。
 *
 * 视觉对齐【我的】tab(MyScreen.kt)的 SectionCard 圆角 + primary tint icon + surfaceVariant
 * 容器:外层 1 个 surfaceVariant Surface(**全宽无圆角**，顶部 HorizontalDivider 1dp 分隔),
 * 内部 `Row` 内嵌 3 个 16dp 圆角 Surface 子卡，均匀分布(spacedBy 8dp)，完全 inline 无凸起:
 *
 * - 槽位 1(笔记):`TabCard`,selected = primary 容器色
 * - 槽位 2(中央创建):`CenterCreateCard`,**始终** primaryContainer 高亮，含"+ 新建" label
 * - 槽位 3(我的):`TabCard`,selected = primary 容器色
 *
 * 全宽铺满避免圆角矩形在屏幕底部两侧露出底色(v4 修订:原 24dp 圆角在 MyScreen
 * surfaceVariant 背景下可见白色间隙)。
 *
 * 手势导航设备:用 `WindowInsets.systemBars.only(Bottom)` 保证 bar 不被裁(挂在外层 Surface 上)。
 */
@Composable
private fun AppTabBar(
    currentDestination: androidx.navigation.NavDestination?,
    onSelectTab: (Any) -> Unit,
    onCenterFabClick: () -> Unit
) {
    // 外层 surfaceVariant 全宽容器(无圆角，顶部 HorizontalDivider 分隔)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
            )
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                // ux-2026-06-30:CenterCreateCard 宽度收窄到 0.85，小于两侧 TabCard(weight 1f),
                // 避免 primaryContainer 高亮 + "+ 新建" label 字数多造成视觉过重;两侧仍是主焦点。
                CenterCreateCard(
                    onClick = onCenterFabClick,
                    modifier = Modifier.weight(0.85f)
                )
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
    }
}

/**
 * app-bottom-tab-bar · 笔记 / 我的 tab 子卡(16dp 圆角 Surface)。
 * selected = primary 容器 + onPrimary 内容;unselected = 透明容器 + onSurfaceVariant 内容。
 * 整卡 clickable，触控目标 ≥ 56dp(icon 24dp + 上下 padding 12dp × 2)。
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

/**
 * app-tab-bar-redesign v4 · 中央「+ 新建」创建入口子卡(16dp 圆角 Surface)。
 *
 * **始终** primaryContainer 高亮(无选中 / 未选中态切换)，作为常驻「创建」主焦点。
 * primaryContainer 色调比 primary 更柔和，视觉上不"笨重"，同时仍传达"可创建"。
 * 含 `Add` icon + "+ 新建" label，结构跟两侧 `TabCard` 对称(icon + spacer + label),
 * 让用户一眼看出"所有位置都可以新建"。
 * 无 elevation，无凸起，跟两侧 `TabCard` 同 baseline 完全 inline。
 * 表面高度 = icon 24dp + spacer 4dp + label ~16dp + 上下 padding 12dp × 2 = 68dp,
 * 跟 `TabCard` 等高，三 Surface 在 Row 内视觉基线对齐。
 * 触控高度 = Surface 整体高度 = 68dp ≥ M3 触控目标 56dp。
 */
@Composable
private fun CenterCreateCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.tab_new_note_cd)
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.tab_new_note),
                style = MaterialTheme.typography.labelSmall
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
