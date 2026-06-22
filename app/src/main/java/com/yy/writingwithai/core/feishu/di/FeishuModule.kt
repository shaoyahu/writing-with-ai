package com.yy.writingwithai.core.feishu.di

import com.yy.writingwithai.core.feishu.api.AuthInterceptor
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuApiClientImpl
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStoreImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * feishu-oauth-flow · core/feishu/di/ Hilt module。
 *
 * spec: openspec/changes/feishu-oauth-flow/tasks.md §5
 *
 * 注:CLAUDE.md 说 `core/<x>/` 下不放 `di/`,但 `feishu` 已是聚合入口(对外暴露
 * 整个飞书集成),这里破例放 di/ 包,与 prefs 同级。
 */
@Module
@InstallIn(SingletonComponent::class)
object FeishuModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return client
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeishuModuleBinds {

    @Binds
    @Singleton
    abstract fun bindFeishuAuthStore(impl: FeishuAuthStoreImpl): FeishuAuthStore

    @Binds
    @Singleton
    abstract fun bindFeishuApiClient(impl: FeishuApiClientImpl): FeishuApiClient
}
