package com.yy.writingwithai.feature.settings.feishu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * feishu-oauth-flow · 设置页「飞书授权」UI(design D5)。
 *
 * spec: openspec/changes/feishu-oauth-flow/tasks.md §6.1
 *
 * app-bottom-tab-bar 增量 · 加 Scaffold + TopAppBar(标题 + ArrowBack)与其他二级页面一致;
 * `innerPadding` 由 Scaffold 提供,自动处理顶部状态栏/摄像头挖孔空间(WindowInsets)。
 *
 * 反馈(2026-06-23):飞书同步日志迁到页面底部(auth 内容下方),加权限提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeishuAuthScreen(onBack: () -> Unit, viewModel: FeishuAuthViewModel = hiltViewModel()) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val appIdInput by viewModel.appIdInput.collectAsStateWithLifecycle()
    val appSecretInput by viewModel.appSecretInput.collectAsStateWithLifecycle()
    val savedAppId by viewModel.savedAppId.collectAsStateWithLifecycle()
    val folderTokenInput by viewModel.folderTokenInput.collectAsStateWithLifecycle()
    val savedFolderToken by viewModel.savedFolderToken.collectAsStateWithLifecycle()

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var secretRevealed by remember { mutableStateOf(false) }
    var seedDone by remember { mutableStateOf(false) }

    // 首次进入 DISCONNECTED:把已存值填入 input,避免按钮始终灰色
    LaunchedEffect(state, savedAppId, savedFolderToken) {
        if (state == FeishuAuthState.DISCONNECTED && !seedDone) {
            val sa = savedAppId
            val sf = savedFolderToken
            if (appIdInput.isBlank() && sa != null) viewModel.onAppIdChange(sa)
            if (folderTokenInput.isBlank() && sf != null) viewModel.onFolderTokenChange(sf)
            seedDone = true
        }
        if (state != FeishuAuthState.DISCONNECTED) seedDone = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feishu_auth_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.feishu_oauth_authorization_intro),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.feishu_oauth_required_permissions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            val cbUrl = "https://xiaozha.nananxue.cn/callback"
            val cm = LocalClipboardManager.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "回调地址: $cbUrl",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    cm.setText(androidx.compose.ui.text.AnnotatedString(cbUrl))
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                "飞书开放后台 → 安全设置 → 重定向 URL 粘贴上述地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            when (state) {
                FeishuAuthState.DISCONNECTED -> {
                    CredentialInputs(
                        savedAppId = savedAppId,
                        appIdInput = appIdInput,
                        onAppIdChange = viewModel::onAppIdChange,
                        appSecretInput = appSecretInput,
                        onAppSecretChange = viewModel::onAppSecretChange,
                        folderTokenInput = folderTokenInput,
                        savedFolderToken = savedFolderToken,
                        onFolderTokenChange = viewModel::onFolderTokenChange,
                        secretRevealed = secretRevealed,
                        onToggleSecret = { secretRevealed = !secretRevealed }
                    )
                    val hasId = appIdInput.isNotBlank() || savedAppId?.isNotBlank() == true
                    val canLogin = hasId && appSecretInput.isNotBlank()
                    Button(
                        onClick = viewModel::startOAuth,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canLogin
                    ) { Text("登录飞书") }
                }

                FeishuAuthState.CONFIGURED,
                FeishuAuthState.TOKEN_FETCHING,
                FeishuAuthState.FAILED -> {
                    Text("已配置 app_id = ${savedAppId ?: "(未知)"}", style = MaterialTheme.typography.bodyMedium)
                    if (state == FeishuAuthState.TOKEN_FETCHING) {
                        Text("等待飞书授权页面...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "已配置 app_id = ${savedAppId ?: "(未知)"},请回到浏览器完成飞书授权。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = viewModel::startOAuth,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("重新登录飞书") }
                    }
                    if (state == FeishuAuthState.FAILED && errorMessage != null) {
                        Text("错误: $errorMessage", color = MaterialTheme.colorScheme.error)
                    }
                }

                FeishuAuthState.KEYSTORE_UNAVAILABLE -> {
                    Text(
                        "Android Keystore 不可用,无法安全存储飞书授权。请检查系统加密服务后重试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                FeishuAuthState.CONNECTED -> {
                    Text("已连接", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { showDisconnectDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("断开飞书") }
                }
            }

            // 飞书同步日志:auth 内容下方,半透明分隔线 + 日志列表。
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val eventDao = remember(ctx) {
                EntryPoints.get(
                    ctx.applicationContext,
                    FeishuAuthEntryPoint::class.java
                ).feishuSyncEventDao()
            }
            FeishuSyncLogSection(eventDao = eventDao)
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

/** 飞书授权屏专用 Hilt EntryPoint:从 ApplicationContext 拿 FeishuSyncEventDao 给同步日志。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeishuAuthEntryPoint {
    fun feishuSyncEventDao(): com.yy.writingwithai.core.feishu.sync.FeishuSyncEventDao
}

@Composable
private fun CredentialInputs(
    savedAppId: String?,
    appIdInput: String,
    onAppIdChange: (String) -> Unit,
    appSecretInput: String,
    onAppSecretChange: (String) -> Unit,
    folderTokenInput: String,
    savedFolderToken: String?,
    onFolderTokenChange: (String) -> Unit,
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
    // 文件夹 token(可选):直接展示输入值,不用fallback到saved(避免删不掉)
    OutlinedTextField(
        value = folderTokenInput,
        onValueChange = onFolderTokenChange,
        label = { Text("文件夹 token(可选)") },
        supportingText = { Text("不填则建在云空间根目录;从飞书文件夹 URL 末段提取") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
