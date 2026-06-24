package com.yy.writingwithai.feature.settings.feishu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.OAuthLauncher
import com.yy.writingwithai.core.feishu.auth.UserTokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FeishuAuthViewModel
@Inject
constructor(
    application: Application,
    private val store: FeishuAuthStore,
    private val tokenProvider: UserTokenProvider,
    private val oauthLauncher: OAuthLauncher
) : AndroidViewModel(application) {

    val authState: StateFlow<FeishuAuthState> = store.authState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _appIdInput = MutableStateFlow("")
    val appIdInput: StateFlow<String> = _appIdInput.asStateFlow()

    private val _appSecretInput = MutableStateFlow("")
    val appSecretInput: StateFlow<String> = _appSecretInput.asStateFlow()

    private val _folderTokenInput = MutableStateFlow("")
    val folderTokenInput: StateFlow<String> = _folderTokenInput.asStateFlow()

    val savedAppId: StateFlow<String?> = store.appId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val savedFolderToken: StateFlow<String?> = store.folderToken.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val sa = store.appId.first()
            if (_appIdInput.value.isBlank() && sa != null) _appIdInput.value = sa
            val sf = store.folderToken.first()
            if (_folderTokenInput.value.isBlank() && sf != null) _folderTokenInput.value = sf
        }
    }

    fun onAppIdChange(value: String) {
        _appIdInput.value = value
    }
    fun onAppSecretChange(value: String) {
        _appSecretInput.value = value
    }
    fun onFolderTokenChange(value: String) {
        _folderTokenInput.value = value
    }

    fun startOAuth() {
        val appId = _appIdInput.value.trim()
        val appSecret = _appSecretInput.value.trim()
        val folderToken = _folderTokenInput.value.trim().ifBlank { null }
        if (appId.isBlank() || appSecret.isBlank()) {
            _errorMessage.value = "请填写 app_id 和 app_secret"
            return
        }
        viewModelScope.launch {
            // 持久化 app_id + app_secret:浏览器打开期间进程可能被杀
            store.setOAuthCredentials(appId, "", "", 0L)
            store.persistAppSecret(appSecret)
            store.setAuthState(FeishuAuthState.TOKEN_FETCHING)
            oauthLauncher.launch(getApplication(), appId)
        }
    }

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
