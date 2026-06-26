package com.yy.writingwithai.feature.settings.feishu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.OAuthLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
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
    application: Application,
    private val authStore: FeishuAuthStore,
    private val oauthLauncher: OAuthLauncher
) : AndroidViewModel(application) {

    val authState: StateFlow<FeishuAuthState> = authStore.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeishuAuthState.DISCONNECTED
        )

    /** 一次性操作状态(startOAuth 失败 / pending exchange 状态) */
    private val _oneShot = MutableStateFlow<OneShotEvent?>(null)
    val oneShot: StateFlow<OneShotEvent?> = _oneShot.asStateFlow()

    /**
     * 启动飞书 OAuth 流程。
     *
     * 流程:
     * 1. 生成随机 state(CSRF 防护) → 落 EncryptedSharedPreferences
     * 2. 构造飞书授权 URL + Custom Tabs 启动
     * 3. 用户在浏览器同意 → 飞书重定向到 [OAuthCodeReceiver]
     * 4. Receiver 校验 state → 调 [com.yy.writingwithai.core.feishu.auth.UserTokenProvider.exchangeCode]
     *
     * 失败:设置 [OneShotEvent.OAuthFailed] 让 UI 显示 toast。
     */
    fun startOAuth(appId: String) {
        if (appId.isBlank()) {
            _oneShot.value = OneShotEvent.OAuthFailed("appId 不能为空")
            return
        }
        viewModelScope.launch {
            try {
                oauthLauncher.launch(getApplication(), appId)
            } catch (e: Throwable) {
                _oneShot.value = OneShotEvent.OAuthFailed(e.message ?: "启动飞书授权失败")
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
        data class OAuthFailed(val message: String) : OneShotEvent
    }
}
