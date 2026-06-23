package com.yy.writingwithai.core.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * cloud-sync-foundation · 同步 DI Module。
 * B5b 对接实际后端时替换 FakeSyncEngine binding。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: FakeSyncEngine): SyncEngine
}
