package com.yy.writingwithai.core.note.entity

/** entity-extraction-association · 实体抽取 SPI。 */
interface EntityExtractor {
    /**
     * 抽取笔记实体并持久化到 note_entities。
     * @param noteId 笔记 ID
     * @param bypassRateLimit 是否跳过 LLM 限频(用户手动触发为 true)
     * @return 抽取到的实体数
     */
    suspend fun extractAndPersist(noteId: String, bypassRateLimit: Boolean = false): Int
}
