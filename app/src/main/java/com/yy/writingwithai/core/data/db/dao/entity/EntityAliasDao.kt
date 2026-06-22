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

    @Query("SELECT * FROM entity_aliases WHERE aliasKey IN (:aliasKeys)")
    suspend fun findByAliasKeys(aliasKeys: List<String>): List<EntityAliasRow>

    @Query("SELECT * FROM entity_aliases ORDER BY entityType, aliasKey")
    suspend fun listAll(): List<EntityAliasRow>
}
