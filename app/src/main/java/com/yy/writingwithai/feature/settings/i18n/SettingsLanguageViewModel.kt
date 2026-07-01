package com.yy.writingwithai.feature.settings.i18n

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.i18n.LocaleSelection
import com.yy.writingwithai.core.i18n.LocaleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * language-switcher · 「我的 → 设置 → 语言」屏 VM。
 *
 * 暴露当前 [LocaleSelection] 给 UI，点击选项调 [LocaleStore.set] 写 DataStore,
 * 再 `activity.recreate()` 让 MainActivity 重新走 attachBaseContext → 拉新 locale →
 * 整个 UI 走新 values/ 资源。
 *
 * Activity-level recreate 由 caller 持有 Activity context 触发(VM 拿不到 Activity
 * 引用，避免内存泄漏);屏 Composable 用 `LocalContext.current as Activity` 调 recreate。
 */
@HiltViewModel
class SettingsLanguageViewModel @Inject constructor(
    private val localeStore: LocaleStore
) : ViewModel() {

    val current: StateFlow<LocaleSelection> = localeStore.observe
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocaleSelection.SYSTEM)

    fun select(selection: LocaleSelection, activity: Activity) {
        viewModelScope.launch {
            localeStore.set(selection)
            activity.recreate()
        }
    }
}
