package com.yy.writingwithai.feature.my.devmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.prefs.DeveloperModeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DeveloperModeViewModel @Inject constructor(
    private val store: DeveloperModeStore
) : ViewModel() {

    val isEnabled: StateFlow<Boolean> = store.isEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = false
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setEnabled(enabled) }
    }
}
