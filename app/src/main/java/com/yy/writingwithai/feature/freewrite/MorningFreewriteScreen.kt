package com.yy.writingwithai.feature.freewrite

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.feature.freewrite.FreewriteEntry.rememberMorningFreewriteViewModel

/**
 * morning-freewrite §3.1:沉浸晨写屏。
 *
 * 视觉:
 * - 全屏沉浸(WindowInsetsControllerCompat.hide(systemBars),屏 destroy 自动还原)
 * - 顶部右上角倒计时 `MM:SS`(≤30s tertiary 色,≤10s error 色)
 * - 中部 weight=1f 的无边框 BasicTextField(headlineMedium typography)
 * - 底部「完成」「跳过」两按钮(前 30s 跳过按钮隐藏避免误触)
 *
 * 状态机屏侧渲染:
 * - Writing → 显示编辑器
 * - Polishing / Organizing → 显示「AI 整理中…」spinner
 * - Saved → Snackbar(`fallback` 决定文案) + 关闭屏
 * - Failed → 显示重试文案
 * - NoProvider → 显示「请先配置 AI provider」+ 去设置按钮
 */
@Composable
fun MorningFreewriteScreen(
    date: String,
    onDismiss: () -> Unit,
    onNavigateToModelManagement: () -> Unit = onDismiss,
    viewModel: MorningFreewriteViewModel = rememberMorningFreewriteViewModel(date)
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    // 全屏沉浸(design §3.1):屏渲染期间 hide system bars,屏离开自动还原。
    val window = remember(context) { (context as? Activity)?.window }
    DisposableEffect(window) {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 监听 saveEvent → 弹 Snackbar 后关闭屏
    LaunchedEffect(Unit) {
        viewModel.saveEvents.collect { event ->
            when (event) {
                is SaveEvent.Saved -> {
                    val msg = if (event.fallback) {
                        context.getString(R.string.freewrite_fallback_toast)
                    } else {
                        context.getString(R.string.freewrite_success_toast)
                    }
                    snackbarHostState.showSnackbar(msg)
                    onDismiss()
                }
            }
        }
    }

    // fix-review-r1 F8 4.5:BackHandler 三态分流。
    //   - Writing → 弹"确认退出"对话框(原有,避免误触丢草稿)
    //   - Polishing / Organizing → 弹"中断 AI 任务"对话框(原版直接 onDismiss,
    //     中途打断会让 ViewModel 协程被 cancel 且留半个 ai_history 行;改为先 cancel VM
    //     任务再导航,既不丢 db 行也不残留 spinner)
    //   - Saved / Failed / NoProvider → 直接退出(终态,无副作用)
    BackHandler {
        when (state) {
            is FreewriteUiState.Writing -> showExitDialog = true
            is FreewriteUiState.Polishing,
            is FreewriteUiState.Organizing -> showExitDialog = true
            else -> onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            // 顶部:日期 + 倒计时(右上)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TimerText(secondsLeft = secondsLeft)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 中部:编辑器 / spinner / NoProvider / Failed / Saved
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (val current = state) {
                    is FreewriteUiState.Writing -> {
                        BasicTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                viewModel.setContent(it)
                            },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxSize(),
                            decorationBox = { innerTextField ->
                                if (text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.freewrite_hint),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        // 显式 suppress 未用分支变量 current
                        @Suppress("UNUSED_EXPRESSION")
                        current
                    }
                    FreewriteUiState.Polishing,
                    FreewriteUiState.Organizing -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (state is FreewriteUiState.Polishing) {
                                    stringResource(R.string.freewrite_polishing)
                                } else {
                                    stringResource(R.string.freewrite_organizing)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.freewrite_please_wait),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is FreewriteUiState.Saved -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (current.fallback) {
                                    stringResource(R.string.freewrite_fallback_toast)
                                } else {
                                    stringResource(R.string.freewrite_success_toast)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    FreewriteUiState.Failed -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.freewrite_failed),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    FreewriteUiState.NoProvider -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.freewrite_no_provider_hint),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onNavigateToModelManagement) {
                                Text(stringResource(R.string.freewrite_no_provider_action))
                            }
                        }
                    }
                }
            }

            // 底部:Writing 态显按钮,其它态全部隐藏
            if (state is FreewriteUiState.Writing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    // 跳过按钮:前 30s 隐藏(spec §3.1 防误触)
                    if (secondsLeft <= 270) {
                        TextButton(onClick = { viewModel.skip() }) {
                            Text(stringResource(R.string.freewrite_skip))
                        }
                    }
                    Button(onClick = { viewModel.finish() }) {
                        Text(stringResource(R.string.freewrite_finish))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.freewrite_exit_dialog_title)) },
            text = { Text(stringResource(R.string.freewrite_exit_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.skip()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.freewrite_exit_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 倒计时 `MM:SS` 渲染:≤30s tertiary 色,≤10s error 色,其它 onSurface。 */
@Composable
private fun TimerText(secondsLeft: Int) {
    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    val color = when {
        secondsLeft <= 10 -> MaterialTheme.colorScheme.error
        secondsLeft <= 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "%02d:%02d".format(minutes, seconds),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = 32.sp,
            color = color
        )
    )
}
