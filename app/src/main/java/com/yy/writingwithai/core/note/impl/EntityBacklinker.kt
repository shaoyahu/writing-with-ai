package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * entity-extraction-association · 实体命中反向链接(tasks §3.1, §4.2)。
 *
 * 流程:
 * 1. 取 [srcNoteId] 在 `note_entities` 里的全部 entityKey
 * 2. 用 [EntityAliasDao.findByAliasKeys] 把 alias 展成 canonical 全集(避免同义实体漏命中)
 * 3. 用 [NoteEntityDao.querySharedEntityHits] 查共享实体的所有 `note_entities` 行
 * 4. 按 dstNoteId 聚合共享实体集合
 * 5. 写 [NoteLinkEntity] linkType = ENTITY_HIT,evidence = `{"sharedEntities":[...]}`
 *
 * 不直接写 DB 副作用:由 [CompositeNoteLinker] 串行收口 + 通过 [NoteLinkCap] 2:1 截断。
 */
@Singleton
class EntityBacklinker
@Inject
constructor(
    private val entityDao: NoteEntityDao,
    private val aliasDao: EntityAliasDao
) {

    suspend fun compute(srcNoteId: String): List<NoteLinkEntity> {
        val selfRows = entityDao.getByNoteId(srcNoteId)
        if (selfRows.isEmpty()) return emptyList()

        // §4.2:用 alias 把全部 key 展开成 canonical，再 JOIN 查命中
        val rawKeys = selfRows.map { it.entityKey }
        val aliasRows = aliasDao.findByAliasKeys(rawKeys)
        val canonicalKeys = aliasRows.map { it.canonicalEntityKey }
        // 保留 raw + canonical 全集(alias 把 xiaom 指向 xiaoming，搜 xiaom 和 xiaoming 都该命中)
        val expandedKeys = (rawKeys + canonicalKeys).distinct()

        // 用 srcNoteId 拿 shared hits(由 NoteEntityDao 内部 JOIN)
        val hitRows = entityDao.querySharedEntityHits(srcNoteId, limit = MAX_HITS * 4)

        // 按 dstNoteId 聚合共享实体集合(过滤掉不在 expandedKeys 的)
        val byDst: MutableMap<String, MutableSet<String>> = HashMap()
        hitRows.forEach { row ->
            if (row.noteId == srcNoteId) return@forEach
            val key = row.entityKey
            if (key !in expandedKeys) return@forEach
            byDst.getOrPut(row.noteId) { mutableSetOf() }.add(key)
        }

        val now = System.currentTimeMillis()
        return byDst
            .filter { it.value.isNotEmpty() }
            .map { (dst, shared) ->
                NoteLinkEntity(
                    srcNoteId = srcNoteId,
                    dstNoteId = dst,
                    linkType = LinkType.ENTITY_HIT,
                    weight = 1.0f,
                    createdAt = now,
                    updatedAt = now,
                    evidence = buildEvidence(shared)
                )
            }
            .sortedByDescending { it.weight }
            .take(MAX_HITS)
    }

    private fun buildEvidence(shared: Set<String>): String {
        // fix M24 (full-review):用 kotlinx.serialization 替代手动 `"$it"` 拼接。
        // 之前 entity key 含 `"` / `\` / 控制字符会产出非法 JSON,sqlite 解析失败 → 整个
        // note_links 行被跳过，反向链接丢失。排序 + JSON encoder 一步到位。
        val sorted = shared.sorted()
        return """{"sharedEntities":${JSON.encodeToString(ListSerializer(String.serializer()), sorted)}}"""
    }

    companion object {
        const val MAX_HITS = 66

        // fix M24 (full-review):kotlinx Json 实例,buildEvidence 用。
        private val JSON = Json
    }
}
