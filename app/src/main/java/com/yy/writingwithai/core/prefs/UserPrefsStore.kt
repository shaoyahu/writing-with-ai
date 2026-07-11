package com.yy.writingwithai.core.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.ui.animation.AnimationStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * onboarding-apikey-prompt · 用户偏好(非敏感)。
 *
 * 与 [ConsentStore] 区别:这里存「轻量 UI 偏好」，不存法律 / 安全敏感数据。
 * 走普通 DataStore Preferences，不进 EncryptedSharedPreferences。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "Apikey prompt screen shown after consent" + "AI capability guard on first use"
 *
 * animation-system 扩展(spec §animation-system REQ 1):
 * - 增加 `animation_style_v1`(枚举名 String，未知值回退 MINIMAL + LOG warn)。
 */
interface UserPrefsStore {
    /** 用户是否已阅读并确认 apikey 教育页(读侧)。 */
    val ackApikeyPromptFlow: Flow<Boolean>

    /** 同步取当前值(测试 / 拦截用)。 */
    suspend fun isApikeyPromptAcked(): Boolean

    /** 设置 ack 状态。`true` = 已确认，`false` = 需重弹。 */
    suspend fun setAckApikeyPrompt(ack: Boolean)

    /**
     * 当前动画风格;未设置或解析失败回退 [AnimationStyle.MINIMAL](spec §REQ 1 + §REQ 3)。
     */
    val animationStyleFlow: Flow<AnimationStyle>

    /** 持久化用户选择的动画风格(spec D2:存 enum name String)。 */
    suspend fun setAnimationStyle(style: AnimationStyle)

    /**
     * animation-switch-redesign · 「导航动画」总开关(animation-system spec ADDED REQ 1)。
     * 未设置或解析失败回退 `true`(尊重现状,reduce-motion 走 NONE 仍保 fallback)。
     */
    val navAnimationsEnabledFlow: Flow<Boolean>

    /** 持久化「导航动画」开关。`false` 时 nav 切换立即退化为无动画。 */
    suspend fun setNavAnimationsEnabled(enabled: Boolean)

    /**
     * animation-switch-redesign · 「标签动画」总开关(animation-system spec ADDED REQ 2)。
     * 未设置或解析失败回退 `true`。
     */
    val tabAnimationsEnabledFlow: Flow<Boolean>

    /** 持久化「标签动画」开关。`false` 时 tab 内容切换立即退化为 `snap()`。 */
    suspend fun setTabAnimationsEnabled(enabled: Boolean)

    // ---- morning-freewrite ----

    /** 「每日晨写」总开关(design §3.2)。未设置默认 `false`(用户主动开)。 */
    val morningFreewriteEnabledFlow: Flow<Boolean>

    /** 持久化晨写开关。 */
    suspend fun setMorningFreewriteEnabled(enabled: Boolean)

    /** 「每日晨写」触发时刻(design §3.2)。未设置默认 `08:00`。 */
    val morningFreewriteTimeFlow: Flow<LocalTime>

    /** 持久化晨写时刻(hour + minute 分开存,LocalTime 不直接序列化进 DataStore)。 */
    suspend fun setMorningFreewriteTime(time: LocalTime)
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

    override val animationStyleFlow: Flow<AnimationStyle> =
        store.data.map { prefs ->
            parseAnimationStyleOrNull(prefs[KEY_ANIMATION_STYLE_V1]) ?: AnimationStyle.IMMERSIVE
        }

    override suspend fun setAnimationStyle(style: AnimationStyle) {
        store.edit { it[KEY_ANIMATION_STYLE_V1] = style.name }
    }

    override val navAnimationsEnabledFlow: Flow<Boolean> =
        store.data.map { prefs ->
            prefs[KEY_NAV_ANIMATIONS_ENABLED_V1] ?: DEFAULT_ANIMATIONS_ENABLED
        }

    override suspend fun setNavAnimationsEnabled(enabled: Boolean) {
        store.edit { it[KEY_NAV_ANIMATIONS_ENABLED_V1] = enabled }
    }

    override val tabAnimationsEnabledFlow: Flow<Boolean> =
        store.data.map { prefs ->
            prefs[KEY_TAB_ANIMATIONS_ENABLED_V1] ?: DEFAULT_ANIMATIONS_ENABLED
        }

    override suspend fun setTabAnimationsEnabled(enabled: Boolean) {
        store.edit { it[KEY_TAB_ANIMATIONS_ENABLED_V1] = enabled }
    }

    override val morningFreewriteEnabledFlow: Flow<Boolean> =
        store.data.map { prefs ->
            prefs[KEY_MORNING_FREEWRITE_ENABLED_V1] ?: DEFAULT_MORNING_FREEWRITE_ENABLED
        }

    override suspend fun setMorningFreewriteEnabled(enabled: Boolean) {
        store.edit { it[KEY_MORNING_FREEWRITE_ENABLED_V1] = enabled }
    }

    override val morningFreewriteTimeFlow: Flow<LocalTime> =
        store.data.map { prefs ->
            val hour = prefs[KEY_MORNING_FREEWRITE_HOUR_V1] ?: DEFAULT_MORNING_FREEWRITE_HOUR
            val minute = prefs[KEY_MORNING_FREEWRITE_MINUTE_V1] ?: DEFAULT_MORNING_FREEWRITE_MINUTE
            LocalTime.of(hour, minute)
        }

    override suspend fun setMorningFreewriteTime(time: LocalTime) {
        store.edit { prefs ->
            prefs[KEY_MORNING_FREEWRITE_HOUR_V1] = time.hour
            prefs[KEY_MORNING_FREEWRITE_MINUTE_V1] = time.minute
        }
    }

    companion object {
        private val KEY_ACK_APIKEY_PROMPT = booleanPreferencesKey("ack_apikey_prompt_v1")

        /**
         * DataStore key;公开给 [FakeUserPrefsStore] 复用(走同一字符串保证 fake / real 一致)。
         */
        @Suppress("MemberVisibilityCanBePrivate")
        val KEY_ANIMATION_STYLE_V1 = stringPreferencesKey("animation_style_v1")

        /**
         * animation-switch-redesign · DataStore key:「导航动画」总开关。
         * 公开给 [FakeUserPrefsStore] 复用。
         */
        @Suppress("MemberVisibilityCanBePrivate")
        val KEY_NAV_ANIMATIONS_ENABLED_V1 = booleanPreferencesKey("nav_animations_enabled_v1")

        /**
         * animation-switch-redesign · DataStore key:「标签动画」总开关。
         * 公开给 [FakeUserPrefsStore] 复用。
         */
        @Suppress("MemberVisibilityCanBePrivate")
        val KEY_TAB_ANIMATIONS_ENABLED_V1 = booleanPreferencesKey("tab_animations_enabled_v1")

        /**
         * 2 个动画开关的默认值;缺失或解析失败走 `true`(尊重现状)。
         * spec ADDED REQ 1 / REQ 2:「Default SHALL be `true`」。
         */
        private const val DEFAULT_ANIMATIONS_ENABLED = true

        // ===== morning-freewrite · 每日晨写默认 + DataStore key =====
        /**
         * 「开启每日提醒」默认 `false`(v1 内测,关闭状态起步;用户进设置页手动开启)。
         * 公开给 [FakeUserPrefsStore] 复用 fake 与 real 默认一致。
         */
        @Suppress("MemberVisibilityCanBePrivate")
        const val DEFAULT_MORNING_FREEWRITE_ENABLED = false

        @Suppress("MemberVisibilityCanBePrivate")
        val KEY_MORNING_FREEWRITE_ENABLED_V1 = booleanPreferencesKey("morning_freewrite_enabled_v1")

        /** 默认提醒时间 08:00;v1 内测取早 8 点,符合"上班前写"心智模型。 */
        const val DEFAULT_MORNING_FREEWRITE_HOUR = 8
        const val DEFAULT_MORNING_FREEWRITE_MINUTE = 0

        @Suppress("MemberVisibilityCanBePrivate")
        val KEY_MORNING_FREEWRITE_HOUR_V1 = intPreferencesKey("morning_freewrite_hour_v1")

        @Suppress("MemberVisibilityCanBePrivate")
        val KEY_MORNING_FREEWRITE_MINUTE_V1 = intPreferencesKey("morning_freewrite_minute_v1")

        /**
         * 把 DataStore 存的 String 还原为 [AnimationStyle];未知 / null / 解析失败返回 null
         * (由 caller 决定回退值，默认 [AnimationStyle.IMMERSIVE]，同时 LOG warn 一次性)。
         */
        internal fun parseAnimationStyleOrNull(raw: String?): AnimationStyle? {
            if (raw == null) return null
            return runCatching { AnimationStyle.valueOf(raw) }
                .onFailure {
                    // Release 构建不写 logcat(避免污染 + 避免意外数据外泄;规格 R5-4/R6-1)
                    if (BuildConfig.DEBUG) {
                        Log.w(
                            "UserPrefsStore",
                            "Unknown animation_style_v1 value '$raw', falling back to IMMERSIVE"
                        )
                    }
                }
                .getOrNull()
        }
    }
}
