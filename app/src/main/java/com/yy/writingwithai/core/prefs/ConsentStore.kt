package com.yy.writingwithai.core.prefs

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * M4-4 onboarding-consent · 同意状态。
 *
 * 持久化在 DataStore Preferences(M4-4 design D1 拍板);key 集合:
 * - `consent_accepted: Boolean`
 * - `consent_accepted_at: Long`(epoch millis)
 * - `consent_version: Int`
 *
 * spec: openspec/changes/onboarding-consent/specs/onboarding-consent/spec.md
 * "ConsentStore persists consent state via DataStore"
 */
@Immutable
data class ConsentState(
    val accepted: Boolean,
    val acceptedAt: Long,
    val version: Int
) {
    companion object {
        /** 未写入任何 key 时的初值(对应未同意用户)。 */
        val EMPTY = ConsentState(accepted = false, acceptedAt = 0L, version = 0)
    }
}

interface ConsentStore {
    val consentFlow: StateFlow<ConsentState>

    suspend fun setAccepted(version: Int, at: Long)

    suspend fun isConsented(currentVersion: Int): Boolean
}

private val Context.consentDataStore: DataStore<Preferences> by preferencesDataStore(name = "consent_store")

@Singleton
class ConsentStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : ConsentStore {
    private val store = context.consentDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val consentFlow: StateFlow<ConsentState> =
        combine(
            store.data.map { it[KEY_ACCEPTED] ?: false },
            store.data.map { it[KEY_ACCEPTED_AT] ?: 0L },
            store.data.map { it[KEY_VERSION] ?: 0 }
        ) { accepted, at, version ->
            ConsentState(accepted = accepted, acceptedAt = at, version = version)
        }.stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = ConsentState.EMPTY
        )

    override suspend fun setAccepted(version: Int, at: Long) {
        store.edit {
            it[KEY_ACCEPTED] = true
            it[KEY_ACCEPTED_AT] = at
            it[KEY_VERSION] = version
        }
    }

    override suspend fun isConsented(currentVersion: Int): Boolean {
        val state = consentFlow.first()
        return state.accepted && state.version >= currentVersion
    }

    companion object {
        private val KEY_ACCEPTED = booleanPreferencesKey("consent_accepted")
        private val KEY_ACCEPTED_AT = longPreferencesKey("consent_accepted_at")
        private val KEY_VERSION = intPreferencesKey("consent_version")
    }
}
