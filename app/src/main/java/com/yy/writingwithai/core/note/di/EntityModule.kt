package com.yy.writingwithai.core.note.di

import com.yy.writingwithai.core.note.entity.EntityExtractor
import com.yy.writingwithai.core.note.entity.LlmEntityExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** entity-extraction-association · EntityExtractor → LlmEntityExtractor Hilt binding(tasks §2.5)。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EntityModule {

    @Binds
    @Singleton
    abstract fun bindEntityExtractor(impl: LlmEntityExtractor): EntityExtractor
}
