package com.yy.writingwithai.core.i18n

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * language-switcher · 持久化用户在「我的 → 设置 → 语言」选中的 locale 偏好。
 *
 * fix-r1:用 **SharedPreferences** 替代 DataStore。原因:DataStore 的
 * `preferencesDataStore` extension 强依赖 `applicationContext`，但 Application.attachBaseContext
 * 早于 super.onCreate,framework 还没把 base 绑到 Application → `applicationContext` 为 null
 * → NPE("applicationContext must not be null")→ 整个 APP 启动即闪退。
 *
 * SharedPreferences 同步 IO，可在 attachBaseContext 直接调，无需 runBlocking。
 * UI 实时刷新用 [StateFlow](同步监听 prefs 变化)代替 DataStore Flow。
 */
@Singleton
class LocaleStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 内存镜像 + prefs 监听同步，UI 层用 StateFlow.collectAsStateWithLifecycle 实时刷新。
    // 初始化从 prefs 读当前值(同步)。
    private val _state = MutableStateFlow(LocaleSelection.fromKey(prefs.getString(KEY_SELECTION, null)))
    val observe: StateFlow<LocaleSelection> = _state.asStateFlow()

    @Volatile
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    init {
        // 注册 prefs 监听(多进程 / 多实例下也能同步，虽然本项目单进程为主，稳)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SELECTION) {
                _state.value = LocaleSelection.fromKey(prefs.getString(KEY_SELECTION, null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        listener = l
    }

    fun set(selection: LocaleSelection) {
        // 同步 commit:保证 attachBaseContext 之后任何代码读 prefs 都能拿到最新值
        prefs.edit().putString(KEY_SELECTION, selection.key).commit()
        _state.value = selection
    }

    companion object {
        private const val PREFS_NAME = "writingwithai_locale"
        private const val KEY_SELECTION = "language_selection"

        // fix-r1:Application.attachBaseContext 在 super.onCreate 之前调，Hilt @Inject
        // 还没 ready 且 DataStore extension 报 NPE。改 SharedPreferences 后可以同步直读。
        // 任何 Context(包含 attachBaseContext 传进来的 base)都能调。
        @JvmStatic
        fun readOnceBlocking(context: Context): LocaleSelection {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return LocaleSelection.fromKey(prefs.getString(KEY_SELECTION, null))
        }
    }
}
