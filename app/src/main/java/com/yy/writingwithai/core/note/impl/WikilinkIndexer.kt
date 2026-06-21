package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.note.wikilink.WikilinkParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikilinkIndexer @Inject constructor(
    private val noteDao: NoteDao
) {
    suspend fun index(srcNoteId: String): List<NoteLinkEntity> {
        val src = noteDao.getById(srcNoteId) ?: return emptyList()
        val titles = WikilinkParser.parse(src.content)
        if (titles.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return titles.mapNotNull { title ->
            val dst = noteDao.resolveByTitle(title) ?: return@mapNotNull null
            NoteLinkEntity(
                srcNoteId = srcNoteId,
                dstNoteId = dst.id,
                linkType = LinkType.WIKILINK,
                weight = 1.0f,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    suspend fun resolveTitles(titles: List<String>): Map<String, NoteEntity?> {
        val result = mutableMapOf<String, NoteEntity?>()
        titles.forEach { title -> result[title] = noteDao.resolveByTitle(title) }
        return result
    }
}
