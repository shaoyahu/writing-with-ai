package com.yy.writingwithai.core.ai.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * M5 polish · provider-real-integration · 持久化用户选定的 AI provider id。
 *
 * spec: openspec/changes/provider-real-integration/specs/model-management/spec.md
 * "ProviderPrefsStore 持久化 selected provider id"
 *
 * 单 string key,默认 `"fake"`(让老用户平滑过渡,`AiActionViewModel.resolveProviderId`
 * 仍走 FakeProvider 直到用户在设置 → 模型管理改)。
 *
 * apikey 不在本 store —— 走 [com.yy.writingwithai.core.prefs.SecureApiKeyStore] 加密存。
 * 本 store 仅存"哪个 provider"的明文 id,无敏感数据。
 */
interface ProviderPrefsStore {
    /** 当前选定的 providerId;默认 `"fake"`。suspend 因 DataStore IO。 */
    suspend fun getSelectedProviderId(): String

    suspend fun setSelectedProviderId(providerId: String)

    /** Flow,UI 可订阅实时刷新。 */
    fun observeSelectedProviderId(): Flow<String>
}

private val Context.providerPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "writingwithai_provider_prefs"
)

class ProviderPrefsStoreImpl(
    private val context: Context
) : ProviderPrefsStore {
    override suspend fun getSelectedProviderId(): String = context.providerPrefsDataStore.data
        .map { it[KEY_SELECTED_PROVIDER_ID] ?: DEFAULT_PROVIDER_ID }
        .first()

    override suspend fun setSelectedProviderId(providerId: String) {
        context.providerPrefsDataStore.edit { it[KEY_SELECTED_PROVIDER_ID] = providerId }
    }

    override fun observeSelectedProviderId(): Flow<String> = context.providerPrefsDataStore.data
        .map { it[KEY_SELECTED_PROVIDER_ID] ?: DEFAULT_PROVIDER_ID }

    companion object {
        const val DEFAULT_PROVIDER_ID = "fake"
        private val KEY_SELECTED_PROVIDER_ID = stringPreferencesKey("selected_provider_id")
    }
}
