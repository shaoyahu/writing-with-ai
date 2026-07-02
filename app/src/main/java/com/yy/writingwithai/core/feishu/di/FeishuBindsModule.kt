package com.yy.writingwithai.core.feishu.di

import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuApiClientImpl
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStoreImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * review-2026-07-02 DI-005:从原 [FeishuModule] 拆分 @Binds 专用模块。
 * 与 [FeishuModule] 同级,与项目其他模块(prefs / data / sync / editor)
 * 「单文件单 module」惯例对齐。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeishuBindsModule {

    @Binds
    @Singleton
    abstract fun bindFeishuAuthStore(impl: FeishuAuthStoreImpl): FeishuAuthStore

    @Binds
    @Singleton
    abstract fun bindFeishuApiClient(impl: FeishuApiClientImpl): FeishuApiClient
}
