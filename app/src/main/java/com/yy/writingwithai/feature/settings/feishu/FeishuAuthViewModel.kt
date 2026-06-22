package com.yy.writingwithai.feature.settings.feishu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.TenantTokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * feishu-oauth-flow · 设置页「飞书授权」ViewModel。
 *
 * spec: openspec/changes/feishu-oauth-flow/tasks.md §6.2
 */
@HiltViewModel
class FeishuAuthViewModel
@Inject
constructor(
    private val store: FeishuAuthStore,
    private val tokenProvider: TenantTokenProvider
) : ViewModel() {

    val authState: StateFlow<FeishuAuthState> = store.authState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _appIdInput = MutableStateFlow("")
    val appIdInput: StateFlow<String> = _appIdInput.asStateFlow()

    private val _appSecretInput = MutableStateFlow("")
    val appSecretInput: StateFlow<String> = _appSecretInput.asStateFlow()

    val savedAppId: StateFlow<String?> = store.appId.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    fun onAppIdChange(value: String) {
        _appIdInput.value = value
    }

    fun onAppSecretChange(value: String) {
        _appSecretInput.value = value
    }

    /** 保存凭证但不立即取 token;state → CONFIGURED */
    fun saveCredentials() {
        viewModelScope.launch {
            store.setCredentials(_appIdInput.value.trim(), _appSecretInput.value.trim())
            _appSecretInput.value = "" // 清空 UI 输入框;secret 不再保留
        }
    }

    /** 取 token(design:从已存的 credentials POST) */
    fun connect() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                tokenProvider.getToken() // 触发 reentrantFetch,失败抛 FeishuError
            } catch (e: FeishuError) {
                _errorMessage.value = e.message
            } catch (e: Throwable) {
                _errorMessage.value = e.javaClass.simpleName + ": " + (e.message ?: "")
            }
        }
    }

    /** 断开 → 清所有 + 跳 DISCONNECTED */
    fun disconnect() {
        viewModelScope.launch {
            tokenProvider.invalidate()
            store.clearAll()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
