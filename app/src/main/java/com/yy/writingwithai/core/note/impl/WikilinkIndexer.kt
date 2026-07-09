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
        val matches = WikilinkParser.parse(src.content)
        if (matches.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        // fix M26 (full-review):批量解析 target title,避免 N+1 SQL。
        // 之前每条 match 单独 noteDao.resolveByTitle(title),每次一条 SQL + IO 调度。
        // 改用单条 IN 查询,内存里按 LOWER(title) 取最新一条(updatedAt DESC LIMIT 1 已对齐)。
        val uniqueLower = matches.map { it.target.lowercase() }.distinct()
        val resolved = noteDao.resolveByTitles(uniqueLower).associateBy { it.title.lowercase() }
        return matches.mapNotNull { match ->
            val dst = resolved[match.target.lowercase()] ?: return@mapNotNull null
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
        if (titles.isEmpty()) return emptyMap()
        // fix M26 (full-review):同样改走批量查询,之前 N 次单条。
        val uniqueLower = titles.map { it.lowercase() }.distinct()
        val byLower = noteDao.resolveByTitles(uniqueLower).associateBy { it.title.lowercase() }
        return titles.associateWith { byLower[it.lowercase()] }
    }
}
