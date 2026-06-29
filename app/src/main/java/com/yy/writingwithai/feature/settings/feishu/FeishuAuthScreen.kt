package com.yy.writingwithai.feature.settings.feishu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import com.yy.writingwithai.core.feishu.auth.OAuthLauncher

/**
 * feishu-user-oauth · 飞书 OAuth 授权屏(从 MyScreen "飞书同步" 入口进)。
 *
 * UI 三态:
 * - DISCONNECTED / FAILED / CONFIGURED / TOKEN_FETCHING:appId + appSecret 输入 +
 *   回调地址展示 + 飞书开放后台配置步骤 + "登录飞书"按钮
 * - CONNECTED:已授权,显示断开按钮 + 同步日志
 * - KEYSTORE_UNAVAILABLE:加密 prefs 初始化失败,显示降级提示
 *
 * 流程(spec tasks 5.x):
 * 1. 用户输入 appId + appSecret(从飞书开放后台「应用凭证」页拿到)
 * 2. 点"登录飞书" → [OAuthLauncher.launch] 先持久化 appId + appSecret,再拉 Custom Tabs
 * 3. 飞书回调 → [OAuthCodeReceiver] 校验 state + 读 appId/appSecret + 调 [UserTokenProvider.exchangeCode]
 * 4. 成功后 [FeishuAuthStore.authState] 切到 CONNECTED
 *
 * ux-2026-06-28:补 appSecret 输入(原本只有 appId,exchangeCode 报 NotAuthorized);
 * 补回调地址展示 + 5 步飞书开放后台配置说明(用户无从知道在飞书后台该填什么 URL)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeishuAuthScreen(onBack: () -> Unit, viewModel: FeishuAuthViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val oneShot by viewModel.oneShot.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var appIdInput by remember { mutableStateOf("") }
    var appSecretInput by remember { mutableStateOf("") }
    var revealSecret by remember { mutableStateOf(false) }

    // one-shot 事件消费
    LaunchedEffect(oneShot) {
        when (val ev = oneShot) {
            is FeishuAuthViewModel.OneShotEvent.OAuthFailed -> {
                Toast.makeText(ctx, ctx.getString(ev.messageRes), Toast.LENGTH_LONG).show()
                viewModel.consumeOneShot()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feishu_oauth_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (authState) {
                FeishuAuthState.DISCONNECTED,
                FeishuAuthState.FAILED,
                FeishuAuthState.CONFIGURED,
                FeishuAuthState.TOKEN_FETCHING -> {
                    Text(
                        text = stringResource(R.string.feishu_oauth_authorization_intro),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = appIdInput,
                        onValueChange = { appIdInput = it.trim() },
                        label = { Text(stringResource(R.string.feishu_oauth_app_id_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    // ux-2026-06-28:补 secret 输入框(token exchange 必需)
                    OutlinedTextField(
                        value = appSecretInput,
                        onValueChange = { appSecretInput = it.trim() },
                        label = { Text(stringResource(R.string.feishu_oauth_app_secret_label)) },
                        singleLine = true,
                        visualTransformation =
                        if (revealSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { revealSecret = !revealSecret }) {
                                Icon(
                                    imageVector =
                                    if (revealSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.model_provider_detail_show_key)
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                stringResource(R.string.feishu_oauth_secret_helper),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.feishu_oauth_required_permissions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = appIdInput.isNotBlank() && appSecretInput.isNotBlank() &&
                            authState != FeishuAuthState.TOKEN_FETCHING,
                        onClick = { viewModel.startOAuth(appIdInput, appSecretInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.feishu_oauth_login_button))
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    SetupStepsSection()
                }
                FeishuAuthState.CONNECTED -> {
                    val events by viewModel.events.collectAsStateWithLifecycle()
                    val currentFolderToken by viewModel.folderToken.collectAsStateWithLifecycle()
                    var folderTokenInput by remember(currentFolderToken) {
                        mutableStateOf(currentFolderToken.orEmpty())
                    }
                    Text(
                        text = stringResource(R.string.feishu_oauth_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    // 文件夹 token 输入:指定同步目标文件夹
                    OutlinedTextField(
                        value = folderTokenInput,
                        onValueChange = { folderTokenInput = it.trim() },
                        label = { Text(stringResource(R.string.feishu_folder_token_label)) },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.feishu_folder_token_placeholder)) },
                        supportingText = {
                            Text(
                                stringResource(R.string.feishu_folder_token_helper),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = folderTokenInput != (currentFolderToken.orEmpty()),
                        onClick = { viewModel.setFolderToken(folderTokenInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.feishu_folder_token_save))
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.feishu_oauth_disconnect_button))
                    }
                    // feishu-sync-end-to-end · 已连接时挂载同步日志 section
                    Spacer(Modifier.height(16.dp))
                    FeishuSyncLogSection(events = events)
                }
                FeishuAuthState.KEYSTORE_UNAVAILABLE -> {
                    Text(
                        text = stringResource(R.string.feishu_oauth_keystore_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * ux-2026-06-28:飞书开放后台配置步骤 + 回调 URL(点击复制到剪贴板)。
 */
@Composable
private fun SetupStepsSection() {
    // M4 fix:Composable 不应接收 Context 参数,内部用 LocalContext.current。
    val ctx = LocalContext.current
    val redirectUri = OAuthLauncher.REDIRECT_URI
    val steps = listOf(
        stringResource(R.string.feishu_oauth_setup_step1),
        stringResource(R.string.feishu_oauth_setup_step2),
        stringResource(R.string.feishu_oauth_setup_step3),
        stringResource(R.string.feishu_oauth_setup_step4),
        stringResource(R.string.feishu_oauth_setup_step5)
    )
    Text(
        text = stringResource(R.string.feishu_oauth_setup_steps_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    steps.forEach { step ->
        Text(
            text = step,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.feishu_oauth_redirect_uri_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ClickableText(
                text = AnnotatedString(redirectUri),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                ),
                onClick = {
                    copyToClipboard(ctx, redirectUri)
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.feishu_oauth_redirect_uri_helper),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(stringResource(R.string.feishu_oauth_redirect_uri_helper))
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** ux-2026-06-28:回调 URL 复制到系统剪贴板,方便用户粘到飞书开放后台。 */
private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("feishu_redirect_uri", text))
}
