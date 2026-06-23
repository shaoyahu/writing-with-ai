package com.yy.writingwithai.feature.settings.alias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
            _message.value = "已合并别名"
            refresh()
        }
    }

    fun unmerge(entityType: EntityType, aliasKey: String) {
        viewModelScope.launch {
            aliasDao.deleteByAlias(entityType, aliasKey)
            _message.value = "已取消别名"
            refresh()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
