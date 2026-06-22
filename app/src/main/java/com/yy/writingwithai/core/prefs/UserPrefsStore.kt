package com.yy.writingwithai.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * onboarding-apikey-prompt · 用户偏好(非敏感)。
 *
 * 与 [ConsentStore] 区别:这里存「轻量 UI 偏好」,不存法律 / 安全敏感数据。
 * 走普通 DataStore Preferences,不进 EncryptedSharedPreferences。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "Apikey prompt screen shown after consent" + "AI capability guard on first use"
 */
interface UserPrefsStore {
    /** 用户是否已阅读并确认 apikey 教育页(读侧)。 */
    val ackApikeyPromptFlow: Flow<Boolean>

    /** 同步取当前值(测试 / 拦截用)。 */
    suspend fun isApikeyPromptAcked(): Boolean

    /** 设置 ack 状态。`true` = 已确认,`false` = 需重弹。 */
    suspend fun setAckApikeyPrompt(ack: Boolean)
}

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs_store")

@Singleton
class UserPrefsStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : UserPrefsStore {
    private val store = context.userPrefsDataStore

    override val ackApikeyPromptFlow: Flow<Boolean> =
        store.data.map { it[KEY_ACK_APIKEY_PROMPT] ?: false }

    override suspend fun isApikeyPromptAcked(): Boolean = ackApikeyPromptFlow.first()

    override suspend fun setAckApikeyPrompt(ack: Boolean) {
        store.edit { it[KEY_ACK_APIKEY_PROMPT] = ack }
    }

    companion object {
        private val KEY_ACK_APIKEY_PROMPT = booleanPreferencesKey("ack_apikey_prompt_v1")
    }
}
