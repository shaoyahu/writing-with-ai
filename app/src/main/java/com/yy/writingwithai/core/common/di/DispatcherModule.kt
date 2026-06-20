package com.yy.writingwithai.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 应用级 coroutine dispatcher 注入。
 *
 * 业务侧 ViewModel / Repository 不要直接 import `kotlinx.coroutines.Dispatchers`,
 * 一律通过 [@IoDispatcher] 注入,test 环境可以传 `UnconfinedTestDispatcher`。
 *
 * 见 [openspec.changes.data-export-import.specs.data-export-import.spec]
 * §"SettingsDataViewModel 用 viewModelScope.launch + Dispatchers.IO" 场景。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
