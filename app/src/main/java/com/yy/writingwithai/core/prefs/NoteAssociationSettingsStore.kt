package com.yy.writingwithai.core.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 笔记关联设置。
 *
 * fix-review-r2-high H5:从 `feature/settings/NoteAssociationSettings` 迁到 `core/prefs/`,
 * 解除 `core/note/` 对 `feature/settings/` 的反向依赖(违反 CLAUDE.md "core 不依赖 feature" 硬规则)。
 *
 * entity-extraction-association §5.1:加 `association_threshold`(默认 0.10)+ `backfill_paused`(默认 false)。
 *
 * 存储走 SharedPreferences(prefs name `settings_note_association`),M6 polish 阶段统一迁 DataStore。
 */
interface NoteAssociationSettingsStore {
    fun isEnabled(): Boolean
    fun setEnabled(value: Boolean)
    fun observeEnabled(): Flow<Boolean>

    /** entity-extraction-association · 关联阈值(0.0..1.0)，默认 0.10。 */
    fun threshold(): Float
    fun setThreshold(value: Float)
    fun observeThreshold(): Flow<Float>

    /** entity-extraction-association · 回填是否暂停。 */
    fun pauseBackfill(): Boolean
    fun setPauseBackfill(value: Boolean)

    // entity-extraction-polish §4.1:新增 observe 流，设置页 slider / switch 双向绑定。
    fun observePauseBackfill(): Flow<Boolean>
}

@Singleton
class NoteAssociationSettingsStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NoteAssociationSettingsStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    override fun observeEnabled(): Flow<Boolean> = callbackFlow {
        trySend(isEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY_ENABLED) trySend(sp.getBoolean(KEY_ENABLED, false))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun threshold(): Float = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)

    override fun setThreshold(value: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, value.coerceIn(0f, 1f)).apply()
    }

    override fun observeThreshold(): Flow<Float> = callbackFlow {
        trySend(threshold())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY_THRESHOLD) trySend(sp.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun pauseBackfill(): Boolean = prefs.getBoolean(KEY_PAUSE, false)

    override fun setPauseBackfill(value: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE, value).apply()
    }

    override fun observePauseBackfill(): Flow<Boolean> = callbackFlow {
        trySend(pauseBackfill())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY_PAUSE) trySend(sp.getBoolean(KEY_PAUSE, false))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val PREFS_NAME = "settings_note_association"
        private const val KEY_ENABLED = "llm_extract_enabled"
        private const val KEY_THRESHOLD = "association_threshold"
        private const val KEY_PAUSE = "backfill_paused"

        // entity-extraction-polish §2.5:默认阈值从 0.25 收紧到 0.10，对齐 SQL 当前生产值。
        const val DEFAULT_THRESHOLD = 0.10f
    }
}

/**
 * H5 新增:NoteAssociationSettingsStore interface → Impl 绑定。
 * 放在 core/prefs/ 同文件内避免新增 module 文件(`core/` 下不应该有 di/ 子包，见 CLAUDE.md)。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoteAssociationSettingsModule {

    @Binds
    @Singleton
    abstract fun bindNoteAssociationSettingsStore(impl: NoteAssociationSettingsStoreImpl): NoteAssociationSettingsStore
}
