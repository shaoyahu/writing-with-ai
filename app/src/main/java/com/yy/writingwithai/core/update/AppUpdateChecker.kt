package com.yy.writingwithai.core.update

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * app-self-hosted-update · 拉版本 manifest。
 *
 * 失败分类:网络异常 → UpdateError.Network;HTTP 4xx/5xx → UpdateError.Http;
 * 解析失败 → UpdateError.Parse。
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    @UpdateHttp private val httpClient: OkHttpClient,
    private val json: Json
) {

    suspend fun fetch(): Result<AppUpdateManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(MANIFEST_URL)
                .get()
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw UpdateError.Http(resp.code)
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) throw UpdateError.Parse()
                try {
                    json.decodeFromString(AppUpdateManifest.serializer(), body)
                } catch (e: Throwable) {
                    Log.w(TAG, "manifest parse failed: ${e.message}")
                    throw UpdateError.Parse(e)
                }
            }
        }.recoverCatching { e ->
            // 网络层错误(HttpException、IOException、SSLException 等)→ Network
            if (e is UpdateError) throw e
            Log.w(TAG, "manifest fetch failed: ${e.javaClass.simpleName}: ${e.message}")
            throw UpdateError.Network(e)
        }
    }

    companion object {
        private const val TAG = "AppUpdateChecker"

        // spec/app-update: HTTPS-only transport。
        // debug 用 BuildConfig.UPDATE_MANIFEST_URL(本地 mockwebserver / 10.0.2.2 走主机),
        // release 走生产 URL。两者都从 app/build.gradle.kts 注入。
        internal val MANIFEST_URL: String = com.yy.writingwithai.BuildConfig.UPDATE_MANIFEST_URL
    }
}
