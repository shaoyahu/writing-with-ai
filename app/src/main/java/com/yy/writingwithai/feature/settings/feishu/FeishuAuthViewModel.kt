package com.yy.writingwithai.feature.settings.feishu

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.OAuthLauncher
import com.yy.writingwithai.core.feishu.auth.OAuthLauncher.OAuthLaunchException
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventDao
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * feishu-user-oauth · 飞书授权 ViewModel。
 *
 * 状态机(由 [FeishuAuthStore.authState] 驱动):
 * - DISCONNECTED:无 appId / 无 refreshToken,显示"登录飞书"按钮
 * - CONFIGURED:有 appId 但 token 未取过
 * - TOKEN_FETCHING:正在 POST 取 token
 * - CONNECTED:token 有效
 * - FAILED:最近一次取 token 失败
 * - KEYSTORE_UNAVAILABLE:EncryptedSharedPreferences 初始化失败
 *
 * 删除原 appSecret / folderToken 输入框(走 OAuth 隐式收集凭证);
 * [startOAuth] 触发 [OAuthLauncher] 跳系统浏览器。
 */
@HiltViewModel
class FeishuAuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authStore: FeishuAuthStore,
    private val oauthLauncher: OAuthLauncher,
    private val eventDao: FeishuSyncEventDao
) : ViewModel() {

    val authState: StateFlow<FeishuAuthState> = authStore.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeishuAuthState.DISCONNECTED
        )

    /**
     * feishu-sync-end-to-end · 设置页同步日志 reactive 投影。
     *
     * 暴露给 Composable 走 `collectAsStateWithLifecycle`,由 [FeishuAuthScreen] 在 `CONNECTED`
     * 状态时挂载 [FeishuSyncLogSection] 渲染。
     */
    val events: StateFlow<List<FeishuSyncEventEntity>> = eventDao.observeLast(20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 一次性操作状态(startOAuth 失败 / pending exchange 状态) */
    private val _oneShot = MutableStateFlow<OneShotEvent?>(null)
    val oneShot: StateFlow<OneShotEvent?> = _oneShot.asStateFlow()

    /**
     * 启动飞书 OAuth 流程。
     *
     * 流程:
     * 1. 落 appId + appSecret 到 EncryptedSharedPreferences(供 OAuthCodeReceiver 回调时取回)
     * 2. 生成随机 state(CSRF 防护) → 落 EncryptedSharedPreferences
     * 3. 构造飞书授权 URL + Custom Tabs 启动
     * 4. 用户在浏览器同意 → 飞书重定向到 [OAuthCodeReceiver]
     * 5. Receiver 校验 state → 调 [com.yy.writingwithai.core.feishu.auth.UserTokenProvider.exchangeCode]
     *
     * 失败:设置 [OneShotEvent.OAuthFailed] 让 UI 显示 toast。
     */
    fun startOAuth(appId: String, appSecret: String) {
        if (appId.isBlank()) {
            _oneShot.value = OneShotEvent.OAuthFailed(R.string.feishu_oauth_error_appid_empty)
            return
        }
        if (appSecret.isBlank()) {
            _oneShot.value = OneShotEvent.OAuthFailed(R.string.feishu_oauth_error_secret_empty)
            return
        }
        viewModelScope.launch {
            try {
                oauthLauncher.launch(context, appId, appSecret)
            } catch (e: OAuthLaunchException.KeystoreUnavailable) {
                Log.e(TAG, "OAuth launch failed: Android Keystore unavailable", e)
                _oneShot.value = OneShotEvent.OAuthFailed(R.string.feishu_oauth_error_keystore_unavailable)
            } catch (e: OAuthLaunchException.LaunchFailed) {
                Log.e(TAG, "OAuth launch failed: browser launch failed", e)
                _oneShot.value = OneShotEvent.OAuthFailed(R.string.feishu_oauth_error_browser_unavailable)
            } catch (e: Throwable) {
                Log.e(TAG, "OAuth launch failed: ${e.message}", e)
                _oneShot.value = OneShotEvent.OAuthFailed(R.string.feishu_oauth_error_launch_failed)
            }
        }
    }

    /** 清除 token + 重置到 DISCONNECTED。 */
    fun disconnect() {
        viewModelScope.launch {
            authStore.clearAll()
        }
    }

    fun consumeOneShot() {
        _oneShot.value = null
    }

    sealed interface OneShotEvent {
        data class OAuthFailed(@StringRes val messageRes: Int) : OneShotEvent
    }

    companion object {
        private const val TAG = "FeishuAuthVM"
    }
}
