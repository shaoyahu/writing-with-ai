package com.yy.writingwithai.feature.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteAssociationSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var llmExtractEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun isEnabled(): Boolean = llmExtractEnabled

    companion object {
        private const val PREFS_NAME = "settings_note_association"
        private const val KEY_ENABLED = "llm_extract_enabled"
    }
}
