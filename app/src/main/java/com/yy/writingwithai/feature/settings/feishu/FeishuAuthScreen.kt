package com.yy.writingwithai.feature.settings.feishu

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState

/**
 * feishu-user-oauth · 飞书 OAuth 授权屏(从 MyScreen "飞书同步" 入口进)。
 *
 * UI 三态:
 * - DISCONNECTED / FAILED:appId 输入 + "登录飞书"按钮
 * - CONNECTED / CONFIGURED:已授权,显示断开按钮
 * - KEYSTORE_UNAVAILABLE:加密 prefs 初始化失败,显示降级提示
 *
 * 流程(spec tasks 5.x):
 * 1. 用户输入 appId(从飞书开放后台拿到)
 * 2. 点"登录飞书" → [OAuthLauncher.launch] 拉系统浏览器 + CustomTabs
 * 3. 飞书回调 → [OAuthCodeReceiver] Activity 校验 state + 调 [UserTokenProvider.exchangeCode]
 * 4. 成功后 [FeishuAuthStore.authState] 切到 CONNECTED
 *
 * 安全:appSecret 不再由用户输入(飞书 OAuth 不需要);[OAuthCodeReceiver] 内
 * 一次性写入 EncryptedSharedPreferences。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeishuAuthScreen(onBack: () -> Unit, viewModel: FeishuAuthViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val oneShot by viewModel.oneShot.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var appIdInput by remember { mutableStateOf("") }

    // one-shot 事件消费
    LaunchedEffect(oneShot) {
        when (val ev = oneShot) {
            is FeishuAuthViewModel.OneShotEvent.OAuthFailed -> {
                Toast.makeText(ctx, ev.message, Toast.LENGTH_LONG).show()
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
                            contentDescription = null
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
                    Text(
                        text = stringResource(R.string.feishu_oauth_required_permissions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = appIdInput.isNotBlank() &&
                            authState != FeishuAuthState.TOKEN_FETCHING,
                        onClick = { viewModel.startOAuth(appIdInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.feishu_oauth_login_button))
                    }
                }
                FeishuAuthState.CONNECTED -> {
                    Text(
                        text = stringResource(R.string.feishu_oauth_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.feishu_oauth_disconnect_button))
                    }
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
