package com.yy.writingwithai.core.update

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * app-self-hosted-update · Hilt module for update check infrastructure.
 *
 * 独立的 OkHttpClient(短超时，manifest 轻量)，不复用 feishu 那个(30s timeout + AuthInterceptor)。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateHttp

@Module
@InstallIn(SingletonComponent::class)
object UpdateDeps {

    @Provides
    @Singleton
    @UpdateHttp
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // 服务端字段未来加，客户端不崩
        explicitNulls = false
    }
}
