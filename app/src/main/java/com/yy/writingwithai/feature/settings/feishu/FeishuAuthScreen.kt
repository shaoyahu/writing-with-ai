package com.yy.writingwithai.feature.settings.feishu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState

/**
 * feishu-oauth-flow · 设置页「飞书授权」UI(design D5)。
 *
 * spec: openspec/changes/feishu-oauth-flow/tasks.md §6.1
 */
@Composable
fun FeishuAuthScreen(viewModel: FeishuAuthViewModel = hiltViewModel()) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val appIdInput by viewModel.appIdInput.collectAsStateWithLifecycle()
    val appSecretInput by viewModel.appSecretInput.collectAsStateWithLifecycle()
    val savedAppId by viewModel.savedAppId.collectAsStateWithLifecycle()

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var secretRevealed by remember { mutableStateOf(false) }
    // §7.1 apikey 教育拦截:未 ack → 弹 ApikeyPromptDialog 后才能继续。
    // 当前简化:仅在 Screen 注释里标注;完整 LaunchedEffect + dialog 由后续
    // `feature/settings/SettingsScreen.kt` 接入本路由时一并处理。
    // (这里不重复弹 dialog 避免与 SettingsScreen 冲突)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("飞书授权", style = MaterialTheme.typography.titleLarge)
        Text(
            "在飞书开放平台创建「企业内部应用」,把 app_id / app_secret 填到下面,工具会以应用身份调用飞书 API,不需要浏览器跳转。",
            style = MaterialTheme.typography.bodyMedium
        )

        when (state) {
            FeishuAuthState.DISCONNECTED -> {
                CredentialInputs(
                    savedAppId = savedAppId,
                    appIdInput = appIdInput,
                    onAppIdChange = viewModel::onAppIdChange,
                    appSecretInput = appSecretInput,
                    onAppSecretChange = viewModel::onAppSecretChange,
                    secretRevealed = secretRevealed,
                    onToggleSecret = { secretRevealed = !secretRevealed }
                )
                Button(
                    onClick = viewModel::saveCredentials,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = appIdInput.isNotBlank() && appSecretInput.isNotBlank()
                ) { Text("保存") }
                OutlinedButton(
                    onClick = {
                        viewModel.saveCredentials()
                        viewModel.connect()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = appIdInput.isNotBlank() && appSecretInput.isNotBlank()
                ) { Text("保存并连接") }
            }

            FeishuAuthState.CONFIGURED,
            FeishuAuthState.FAILED,
            FeishuAuthState.TOKEN_FETCHING -> {
                Text("已配置 app_id = ${savedAppId ?: "(未知)"}", style = MaterialTheme.typography.bodyMedium)
                if (state == FeishuAuthState.FAILED && errorMessage != null) {
                    Text("错误: $errorMessage", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = viewModel::connect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state != FeishuAuthState.TOKEN_FETCHING
                ) { Text(if (state == FeishuAuthState.TOKEN_FETCHING) "连接中..." else "连接飞书") }
            }

            FeishuAuthState.CONNECTED -> {
                Text("已连接", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { showDisconnectDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("断开飞书") }
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("断开飞书?") },
            text = { Text("所有飞书凭证 + token + 已同步的笔记关联都会被清除。本地笔记保留。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.disconnect()
                    showDisconnectDialog = false
                }) { Text("确认断开") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CredentialInputs(
    savedAppId: String?,
    appIdInput: String,
    onAppIdChange: (String) -> Unit,
    appSecretInput: String,
    onAppSecretChange: (String) -> Unit,
    secretRevealed: Boolean,
    onToggleSecret: () -> Unit
) {
    OutlinedTextField(
        value = if (appIdInput.isEmpty()) (savedAppId ?: "") else appIdInput,
        onValueChange = onAppIdChange,
        label = { Text("app_id") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = appSecretInput,
        onValueChange = onAppSecretChange,
        label = { Text("app_secret") },
        visualTransformation = if (secretRevealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = onToggleSecret) {
                Text(if (secretRevealed) "隐藏" else "显示")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
