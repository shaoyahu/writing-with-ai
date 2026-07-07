package com.yy.writingwithai.feature.my.devmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.CustomPromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PromptEditorViewModel @Inject constructor(
    private val repo: CustomPromptRepository
) : ViewModel() {

    data class UiState(
        val content: String = "",
        val defaultContent: String = "",
        val isCustom: Boolean = false,
        val loading: Boolean = true,
        val saved: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val custom = withContext(Dispatchers.IO) { repo.getCustomContent() }
            val effective = withContext(Dispatchers.IO) { repo.getEffectiveContent() }
            val default = withContext(Dispatchers.IO) { repo.getDefaultContent() }
            _uiState.value = UiState(
                content = effective,
                defaultContent = default,
                isCustom = custom != null,
                loading = false,
                saved = false
            )
        }
    }

    fun updateContent(value: String) {
        _uiState.value = _uiState.value.copy(content = value, saved = false)
    }

    fun save() {
        val current = _uiState.value.content
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveCustom(current) }
            _uiState.value = _uiState.value.copy(isCustom = true, saved = true)
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.resetToDefault() }
            val default = withContext(Dispatchers.IO) { repo.getDefaultContent() }
            _uiState.value = _uiState.value.copy(
                content = default,
                defaultContent = default,
                isCustom = false,
                saved = true
            )
        }
    }
}
