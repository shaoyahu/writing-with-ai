package com.yy.writingwithai.core.feishu.auth

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yy.writingwithai.core.feishu.api.FeishuError
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * feishu-user-oauth · OAuth 回调接收 Activity。
 *
 * 飞书重定向到 com.yy.writingwithai://feishu/callback?code=xxx,
 * 此 Activity 接 code 并触发 token 交换,完成后 finish。
 */
@AndroidEntryPoint
class OAuthCodeReceiver : ComponentActivity() {

    @Inject
    lateinit var tokenProvider: UserTokenProvider

    @Inject
    lateinit var authStore: FeishuAuthStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        val code = data?.getQueryParameter("code")
        val errCode = data?.getQueryParameter("error")
        if (errCode != null) {
            Log.w(TAG, "OAuth error from feishu: $errCode")
            finish()
            return
        }
        if (code == null) {
            Log.w(TAG, "OAuth callback missing code: $data")
            finish()
            return
        }

        // appId 不在 redirect URL 中(飞书只回传 code),从 store 读
        val appId = authStore.getAppIdAndRefreshToken()?.first
        if (appId == null) {
            Log.w(TAG, "OAuth callback but no appId in store")
            finish()
            return
        }

        val appSecret = authStore.getAppSecretSnapshot()
        if (appSecret == null) {
            Log.w(TAG, "OAuth callback but appSecret missing from store")
            finish()
            return
        }
        lifecycleScope.launch {
            val ctx = this@OAuthCodeReceiver
            try {
                tokenProvider.exchangeCode(
                    appId = appId,
                    appSecret = appSecret,
                    code = code
                )
                // appSecret 清理由 exchangeCode 内部完成(见 UserTokenProvider.persistUserToken)
                android.widget.Toast.makeText(ctx, "飞书授权成功", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: FeishuError) {
                Log.e(TAG, "OAuth exchange failed: ${e.message}", e)
                android.widget.Toast.makeText(
                    ctx,
                    "授权失败: ${e.message ?: "未知错误"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: java.io.IOException) {
                Log.e(TAG, "OAuth exchange network error", e)
                android.widget.Toast.makeText(
                    ctx,
                    "授权失败: 网络异常 ${e.message ?: ""}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } finally {
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "OAuthCodeReceiver"
    }
}
