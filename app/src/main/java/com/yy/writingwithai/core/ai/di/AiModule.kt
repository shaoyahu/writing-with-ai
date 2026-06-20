package com.yy.writingwithai.core.ai.di

import com.yy.writingwithai.core.ai.CoreAiGateway
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiProvider
import com.yy.writingwithai.core.ai.fake.FakeAiProvider
import com.yy.writingwithai.core.ai.provider.AnthropicCompatibleAdapter
import com.yy.writingwithai.core.ai.provider.deepseek.DeepseekConfig
import com.yy.writingwithai.core.ai.provider.mimo.MimoConfig
import com.yy.writingwithai.core.ai.provider.minimax.MinimaxConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    @Named("ai")
    fun provideAiOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideFakeAiProvider(): FakeAiProvider = FakeAiProvider()

    @Provides
    @Singleton
    @Named("deepseek")
    fun provideDeepseekAdapter(@Named("ai") client: OkHttpClient): AnthropicCompatibleAdapter =
        AnthropicCompatibleAdapter(DeepseekConfig.config, client)

    @Provides
    @Singleton
    @Named("minimax")
    fun provideMinimaxAdapter(@Named("ai") client: OkHttpClient): AnthropicCompatibleAdapter =
        AnthropicCompatibleAdapter(MinimaxConfig.config, client)

    @Provides
    @Singleton
    @Named("mimo")
    fun provideMimoAdapter(@Named("ai") client: OkHttpClient): AnthropicCompatibleAdapter =
        AnthropicCompatibleAdapter(MimoConfig.config, client)

    @Provides
    @Singleton
    fun provideAiProviders(
        fake: FakeAiProvider,
        @Named("deepseek") deepseek: AnthropicCompatibleAdapter,
        @Named("minimax") minimax: AnthropicCompatibleAdapter,
        @Named("mimo") mimo: AnthropicCompatibleAdapter
    ): Map<String, @JvmSuppressWildcards AiProvider> = mapOf(
        "fake" to fake,
        "deepseek" to deepseek,
        "minimax" to minimax,
        "mimo" to mimo
    )

    @Provides
    @Singleton
    fun bindAiGateway(gateway: CoreAiGateway): AiGateway = gateway
}
