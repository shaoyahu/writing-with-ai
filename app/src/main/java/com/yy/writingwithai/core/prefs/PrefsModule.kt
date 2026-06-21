package com.yy.writingwithai.core.prefs

import android.content.Context
import com.yy.writingwithai.core.ai.provider.CustomProviderStore
import com.yy.writingwithai.core.ai.provider.CustomProviderStoreImpl
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStoreImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * M4-4 onboarding-consent · core/prefs/ Hilt 模块。
 *
 * spec: openspec/changes/onboarding-consent/specs/secure-prefs/spec.md
 * "SecurePrefsModule provides Hilt singleton"
 *
 * 注:ConsentStore / SecureApiKeyStore 实现类都标了 @Inject constructor,
 * 本身可被 Hilt 直接发现;本 module 显式 @Provides 是为了"UI 层不直接 import
 * 实现类"约束(spec 末尾)集中到一处。
 */
@Module
@InstallIn(SingletonComponent::class)
object PrefsModule {
    @Provides
    @Singleton
    fun provideConsentStore(@ApplicationContext context: Context): ConsentStore = ConsentStoreImpl(context)

    @Provides
    @Singleton
    fun provideSecureApiKeyStore(@ApplicationContext context: Context): SecureApiKeyStore =
        SecureApiKeyStoreImpl(context)

    @Provides
    @Singleton
    fun providePromptTemplateStore(@ApplicationContext context: Context): PromptTemplateStore =
        PromptTemplateStoreImpl(context)

    @Provides
    @Singleton
    fun provideProviderPrefsStore(@ApplicationContext context: Context): ProviderPrefsStore =
        ProviderPrefsStoreImpl(context)

    @Provides
    @Singleton
    fun provideCustomProviderStore(@ApplicationContext context: Context): CustomProviderStore =
        CustomProviderStoreImpl(context)
}
