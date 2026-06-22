package com.yy.writingwithai.core.data.db.entity.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.note.entity.EntityType

/** entity-extraction-association · note_entities 表。 */
@Entity(
    tableName = "note_entities",
    primaryKeys = ["noteId", "entityKey"],
    indices = [Index("noteId"), Index("entityType"), Index("entityKey")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteEntityRow(
    val noteId: String,
    val entityType: EntityType,
    val entityKey: String,
    val surfaceForm: String,
    val spanStart: Int,
    val spanEnd: Int,
    val lastExtractedAt: Long
)

/** entity-extraction-association · entity_aliases 表。 */
@Entity(
    tableName = "entity_aliases",
    primaryKeys = ["entityType", "aliasKey"],
    indices = [Index("canonicalEntityKey")]
)
data class EntityAliasRow(
    val entityType: EntityType,
    val aliasKey: String,
    val canonicalEntityKey: String
)
