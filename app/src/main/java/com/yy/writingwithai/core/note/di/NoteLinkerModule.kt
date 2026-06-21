package com.yy.writingwithai.core.note.di

import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.impl.CompositeNoteLinker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NoteLinkerModule {

    @Binds
    @Singleton
    abstract fun bindNoteLinker(impl: CompositeNoteLinker): NoteLinker
}
