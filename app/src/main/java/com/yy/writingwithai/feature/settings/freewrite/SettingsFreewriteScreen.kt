package com.yy.writingwithai.feature.settings.freewrite

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.WritingAppTheme
import com.yy.writingwithai.core.ui.AnimatedSwitch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * morning-freewrite §2.3:「设置 → 每日晨写」配置屏。
 *
 * 功能:
 * - Switch toggle:开 → 弹 POST_NOTIFICATIONS 权限 launcher(Android 13+);关 → 取消闹钟
 * - ListItem 时间:点击 → 弹 [TimePicker] dialog,改后调 [SettingsFreewriteViewModel.setTime]
 * - 权限被拒时显示「去系统设置」按钮(走 ACTION_APP_NOTIFICATION_SETTINGS intent)
 *
 * 设计要点:
 * - 权限检查走 Activity Result API(API 33+ 必需),SDK < 33 直接 toggle 无需请求
 * - TimePicker 用 Material3 内置(material3 BOM 2024.10+)
 * - **不**直接处理 AlarmManager,所有调度委托 [SettingsFreewriteViewModel]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFreewriteScreen(onBack: () -> Unit = {}, viewModel: SettingsFreewriteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    // Android 13+ POST_NOTIFICATIONS 权限 launcher;SDK < 33 直接拿到权限(API 23+ 旧版 manifest 即可用)。
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setEnabled(true)
        } else {
            // 拒绝 → 不开,用户仍可在「通知权限」被拒状态下使用「应用内」start,只是收不到闹钟
            // 用户可以从系统设置再次打开 → 显示下方"去系统设置"按钮
            // 这里不调 setEnabled(false),让用户偏好保持「想开但权限被拒」状态以便下次检查
            // (setEnabled 是幂等的,toggle on 失败也不污染 prefs)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.freewrite_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 总开关
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.freewrite_enable_label))
                },
                supportingContent = {
                    Text(stringResource(R.string.freewrite_enable_desc))
                },
                trailingContent = {
                    AnimatedSwitch(
                        checked = state.enabled,
                        onCheckedChange = { desired ->
                            if (desired) {
                                // 开 → 检查权限(SDK 33+ 才需要请求)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setEnabled(true)
                                }
                            } else {
                                viewModel.setEnabled(false)
                            }
                        }
                    )
                }
            )

            // 时间选择
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.freewrite_time_label))
                },
                supportingContent = {
                    Text(state.time.format(DateTimeFormatter.ofPattern("HH:mm")))
                },
                modifier = Modifier.clickable(enabled = state.enabled) {
                    showTimePicker = true
                }
            )

            // 权限引导(已开 + Android 13+ + 通知被拒场景)
            if (state.enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.freewrite_permission_denied_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.freewrite_permission_denied_desc))
                    },
                    modifier = Modifier.clickable {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.time.hour,
            initialMinute = state.time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(LocalTime.of(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            title = { Text(stringResource(R.string.freewrite_time_picker_title)) },
            text = {
                TimePicker(state = pickerState)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "SettingsFreewrite", showBackground = true)
@Composable
private fun SettingsFreewriteScreenPreview() {
    WritingAppTheme { SettingsFreewriteScreen() }
}
