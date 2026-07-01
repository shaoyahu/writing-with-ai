package com.yy.writingwithai.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * hardening-sse-and-widget-init H-5:进程级 CoroutineScope 限定符。
 *
 * 旧实现:`NoteRepository` 在 `@Singleton` 内自管 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`,
 * Application 退出时无法取消(Android `onTerminate` 不可靠),`recomputeFlow` 协程长期 leak。
 *
 * 新实现:
 * - `@ApplicationScope` 限定符在编译期防误用其他 scope
 * - Hilt `@Provides @Singleton` 注入同一实例到所有需要进程级常驻任务的类
 * - Application 退出时由进程死亡隐式 cancel(Android 进程级 CoroutineScope 标准模式)
 *
 * KDoc 警告:只用于进程级 fire-and-forget 任务(后端轮询 / 缓存预热 / 重组计算等),
 * **不**用于用户可见的 UI 任务(用 viewModelScope)，也**不**用于 IO 密集短任务(用 withContext(Dispatchers.IO))。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
