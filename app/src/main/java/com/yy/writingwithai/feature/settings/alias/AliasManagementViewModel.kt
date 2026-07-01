package com.yy.writingwithai.feature.settings.alias

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
import com.yy.writingwithai.core.data.db.entity.entity.EntityAliasRow
import com.yy.writingwithai.core.note.entity.EntityType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** entity-extraction-association · 别名管理 ViewModel(tasks §4.4)。 */
@HiltViewModel
class AliasManagementViewModel
@Inject
constructor(
    private val aliasDao: EntityAliasDao
) : ViewModel() {
    private val _aliases = MutableStateFlow<List<EntityAliasRow>>(emptyList())
    val aliases: StateFlow<List<EntityAliasRow>> = _aliases.asStateFlow()

    // H8 修:sealed class 携带 @StringRes,Composable 端 stringResource 渲染，避免 VM 硬编码中文。
    private val _message = MutableStateFlow<AliasMessage?>(null)
    val message: StateFlow<AliasMessage?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _aliases.value = aliasDao.listAll()
        }
    }

    fun merge(entityType: EntityType, aliasKey: String, canonicalKey: String) {
        viewModelScope.launch {
            val alias = EntityType.normalizeKey(entityType, aliasKey)
            val canonical = EntityType.normalizeKey(entityType, canonicalKey)
            aliasDao.upsert(EntityAliasRow(entityType, alias, canonical))
            _message.value = AliasMessage.Merged
            refresh()
        }
    }

    fun unmerge(entityType: EntityType, aliasKey: String) {
        viewModelScope.launch {
            aliasDao.deleteByAlias(entityType, aliasKey)
            _message.value = AliasMessage.Unmerged
            refresh()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** H8 修:VM 状态携带 string resource id,UI 层用 stringResource 渲染。 */
sealed class AliasMessage(
    @StringRes val messageRes: Int
) {
    data object Merged : AliasMessage(R.string.entity_alias_merged_toast)
    data object Unmerged : AliasMessage(R.string.entity_alias_unmerged_toast)
}
