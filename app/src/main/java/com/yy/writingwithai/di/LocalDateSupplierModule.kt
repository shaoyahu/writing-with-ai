package com.yy.writingwithai.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.util.function.Supplier
import javax.inject.Singleton

/**
 * morning-freewrite · DI provider:把"当前日期"包装成 [Supplier],给
 * [com.yy.writingwithai.feature.freewrite.MorningFreewriteViewModel] 注入。
 *
 * 用 Supplier 而不是 `() -> LocalDate`(Kotlin 函数类型)的原因:Hilt 走 Java 反射生成
 * 注入代码,Kotlin Function0 / Function1 不能直接被 javax.inject 识别为合法依赖。
 * 单测里用 [TestLocalDateSupplier] 替换即可。
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalDateSupplierModule {
    @Provides
    @Singleton
    fun provideLocalDateSupplier(): Supplier<LocalDate> = Supplier { LocalDate.now() }
}
