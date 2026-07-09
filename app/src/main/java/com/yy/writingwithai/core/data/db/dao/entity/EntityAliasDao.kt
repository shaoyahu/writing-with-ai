package com.yy.writingwithai.core.data.db.dao.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.entity.EntityAliasRow
import com.yy.writingwithai.core.note.entity.EntityType

/** entity-extraction-association · entity_aliases DAO。 */
@Dao
interface EntityAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: EntityAliasRow)

    @Query("DELETE FROM entity_aliases WHERE entityType = :entityType AND aliasKey = :aliasKey")
    suspend fun deleteByAlias(entityType: EntityType, aliasKey: String)

    // fix M11 (full-review):删除实体时同时清 entity_aliases 别名行。
    // 之前 confirmDelete 只删 note_entities,留下 alias 孤儿行(指向不存在的 entity),
    // EntityBacklinker.findByAliasKeys 会拿到 stale canonicalKey,反向链接结果污染。
    @Query(
        "DELETE FROM entity_aliases WHERE entityType = :entityType AND " +
            "(aliasKey = :entityKey OR canonicalEntityKey = :entityKey)"
    )
    suspend fun deleteByEntityKey(entityType: EntityType, entityKey: String)

    @Query("SELECT * FROM entity_aliases WHERE aliasKey IN (:aliasKeys)")
    suspend fun findByAliasKeys(aliasKeys: List<String>): List<EntityAliasRow>

    @Query("SELECT * FROM entity_aliases ORDER BY entityType, aliasKey")
    suspend fun listAll(): List<EntityAliasRow>
}
