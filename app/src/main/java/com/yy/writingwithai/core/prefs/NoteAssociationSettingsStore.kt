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
 * 笔记关联 LLM 抽取开关。
 *
 * fix-review-r2-high H5:从 `feature/settings/NoteAssociationSettings` 迁到 `core/prefs/`,
 * 解除 `core/note/` 对 `feature/settings/` 的反向依赖(违反 CLAUDE.md "core 不依赖 feature" 硬规则)。
 *
 * 存储仍走 SharedPreferences(prefs name `settings_note_association`,key `llm_extract_enabled`),
 * 保留与老用户二进制兼容;M6 polish 阶段统一迁 DataStore。
 */
interface NoteAssociationSettingsStore {
    fun isEnabled(): Boolean
    fun setEnabled(value: Boolean)
    fun observeEnabled(): Flow<Boolean>
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

    companion object {
        private const val PREFS_NAME = "settings_note_association"
        private const val KEY_ENABLED = "llm_extract_enabled"
    }
}

/**
 * H5 新增:NoteAssociationSettingsStore interface → Impl 绑定。
 * 放在 core/prefs/ 同文件内避免新增 module 文件(`core/` 下不应该有 di/ 子包,见 CLAUDE.md)。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoteAssociationSettingsModule {

    @Binds
    @Singleton
    abstract fun bindNoteAssociationSettingsStore(impl: NoteAssociationSettingsStoreImpl): NoteAssociationSettingsStore
}
