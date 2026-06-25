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

    /**
     * fix-2026-06-24-review-r1-critical:仅在 `BuildConfig.DEBUG` 时注册 `FakeAiProvider`,
     * release build 的 provider map 不含 `"fake"` key,真实用户不会误走 mock 输出。
     */
    @Provides
    @Singleton
    fun provideFakeAiProvider(): FakeAiProvider? =
        if (com.yy.writingwithai.BuildConfig.DEBUG) FakeAiProvider() else null

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
        fake: FakeAiProvider?,
        @Named("deepseek") deepseek: AnthropicCompatibleAdapter,
        @Named("minimax") minimax: AnthropicCompatibleAdapter,
        @Named("mimo") mimo: AnthropicCompatibleAdapter
    ): Map<String, @JvmSuppressWildcards AiProvider> = buildMap {
        put("deepseek", deepseek)
        put("minimax", minimax)
        put("mimo", mimo)
        if (fake != null) put("fake", fake)
    }.toMap()

    @Provides
    @Singleton
    fun bindAiGateway(gateway: CoreAiGateway): AiGateway = gateway
}
