package com.yy.writingwithai.core.feishu.di

import androidx.room.withTransaction
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.feishu.api.AuthInterceptor
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuApiClientImpl
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStoreImpl
import com.yy.writingwithai.core.feishu.sync.TransactionExecutor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
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

    // H10 修:显式 @Named("feishu") 限定,避免与 AiModule 的 @Named("ai") 串包。
    @Provides
    @Singleton
    @Named("feishu")
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return client
    }

    // M3:prod 事务执行器走 Room withTransaction。test 通过构造器注入 passthrough。
    @Provides
    @Singleton
    fun provideTransactionExecutor(db: AppDatabase): TransactionExecutor = object : TransactionExecutor {
        override suspend fun <R> execute(block: suspend () -> R): R = db.withTransaction { block() }
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

    // feishu-bidir-sync providers:FeishuSyncService + FeishuConflictResolver 自身有 @Inject ctor,
    // Hilt 可直接发现,但显式通过 Module 提供以保持一致性(claude.md 约束)
}
