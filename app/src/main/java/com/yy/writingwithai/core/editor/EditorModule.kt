package com.yy.writingwithai.core.editor

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EditorModule {
    @Binds
    @Singleton
    abstract fun bindMarkdownEditor(impl: SimpleMarkdownEditor): MarkdownEditor
}
