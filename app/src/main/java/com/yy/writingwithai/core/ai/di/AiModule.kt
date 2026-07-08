package com.yy.writingwithai.core.ai.di

import com.yy.writingwithai.core.ai.CoreAiGateway
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiProvider
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
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    // review-2026-07-02 SEC-007:对三家内置 provider 配 TLS pin,防 MITM 替换 root CA 后
    // 截获 apikey / prompt。pin 集合是 SPKI SHA-256 base64;若 provider 轮换证书,
    // 需要同步更新这里,否则 OkHttp 抛 SSLPeerUnverifiedException。
    //
    // 自定义 provider 由用户自配 baseUrl,不在此 pin 范围(走系统 trust store)。
    //
    // 当前不在此处预配真实 pin(防止 placeholder pin 阻断真请求),改在 `res/xml/network_security_config.xml`
    // 配 `<pin-set>` 块 + 走 BuildConfig.TLS_PINNING_ENABLED 开关;开关关闭时走系统 trust store,
    // 开启时严格 pin。release 前由 ops 用 openssl s_client 取各家 leaf/intermediate SPKI 写入。
    private val aiCertificatePinner: CertificatePinner? = null
        .takeIf { false } // 占位:开启时改 `false` → `BuildConfig.TLS_PINNING_ENABLED`

    @Provides
    @Singleton
    @Named("ai")
    fun provideAiOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        aiCertificatePinner?.let { builder.certificatePinner(it) }
        return builder.build()
    }

    /**
     * remove-debug-fake-fallback §2.1-2.4:FakeAiProvider 类保留在 `core/ai/fake/`(JVM 单测用),
     * 但 main DI 图不再注册它。debug 与 release 行为一致:无 provider apikey 走「请先配置 AI 模型」错误。
     * 单测通过 `@TestInstallIn` 或直接 `new FakeAiProvider()` 拿实例,不走 Hilt main module。
     */

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
        @Named("deepseek") deepseek: AnthropicCompatibleAdapter,
        @Named("minimax") minimax: AnthropicCompatibleAdapter,
        @Named("mimo") mimo: AnthropicCompatibleAdapter
    ): Map<String, @JvmSuppressWildcards AiProvider> = mapOf(
        "deepseek" to deepseek,
        "minimax" to minimax,
        "mimo" to mimo
    )

    @Provides
    @Singleton
    fun bindAiGateway(gateway: CoreAiGateway): AiGateway = gateway
}
