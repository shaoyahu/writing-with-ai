package com.yy.writingwithai.core.feishu.converter

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * markdown-docx-converter · 注入两个 converter。
 *
 * 注意:`core/` 下不放 `di/` 子包(见 CLAUDE.md),Module 文件就放在 converter 同包。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeishuConverterModule {

    @Binds
    @Singleton
    abstract fun bindMarkdownToDocxConverter(impl: MarkdownToDocxConverterImpl): MarkdownToDocxConverter

    @Binds
    @Singleton
    abstract fun bindDocxToMarkdownConverter(impl: DocxToMarkdownConverterImpl): DocxToMarkdownConverter
}
