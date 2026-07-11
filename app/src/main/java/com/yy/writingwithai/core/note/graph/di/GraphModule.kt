package com.yy.writingwithai.core.note.graph.di

import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.graph.CircularLayout
import com.yy.writingwithai.core.note.graph.ForceLayout
import com.yy.writingwithai.core.note.graph.GraphDataLoader
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * note-graph-view · Hilt SPI 注入入口。
 *
 * - [GraphDataLoader]:由 [NoteLinker] / [NoteDao] / [NoteLinkDao] / [NoteEntityDao] /
 *   [NoteAssociationSettingsStore] 已有 @Singleton 装配,本模块用 @Provides 显式提供(@Inject 已经
 *   能自动注入 @Singleton 类,这里明写为契约入口 + 让 v2+ 加 SPI 时同步收敛)。
 * - [ForceLayout] / [CircularLayout]:无状态算法工具,本模块提供 @Provides 单例,避免每次 ViewModel 重建
 *   重新实例化 ~200 行的算法对象。
 * - [com.yy.writingwithai.core.note.graph.LayoutCache] 走 `@Inject constructor` 自动注入(已在自己的
 *   文件里 `@Singleton`),不需要显式 @Provides。
 */
@Module
@InstallIn(SingletonComponent::class)
object GraphModule {

    @Provides
    @Singleton
    fun provideGraphDataLoader(
        noteLinker: NoteLinker,
        noteLinkDao: NoteLinkDao,
        noteDao: NoteDao,
        noteEntityDao: NoteEntityDao,
        assocSettings: NoteAssociationSettingsStore
    ): GraphDataLoader = GraphDataLoader(
        noteLinker = noteLinker,
        noteLinkDao = noteLinkDao,
        noteDao = noteDao,
        noteEntityDao = noteEntityDao,
        assocSettings = assocSettings
    )

    @Provides
    @Singleton
    fun provideForceLayout(): ForceLayout = ForceLayout()

    @Provides
    @Singleton
    fun provideCircularLayout(): CircularLayout = CircularLayout()
}
