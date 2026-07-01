package com.yy.writingwithai.core.note

import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef
import com.yy.writingwithai.core.note.impl.LocalNoteLinker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalNoteLinkerTest {

    private val noteDao: NoteDao = mockk()
    private val noteTagDao: NoteTagDao = mockk()
    private val linker = LocalNoteLinker(noteDao, noteTagDao)

    @Test
    fun `jaccard = 0_5`() = runTest {
        coEvery { noteDao.getById("A") } returns note("A", "t", "b")
        coEvery { noteDao.search(any()) } returns flowOf(emptyList())
        coEvery { noteTagDao.observeTagsFor("A") } returns flowOf(listOf("x", "y", "z"))
        coEvery { noteTagDao.observeAllCrossRefs() } returns flowOf(
            listOf(NoteTagCrossRef("B", "y"), NoteTagCrossRef("B", "z"), NoteTagCrossRef("B", "w"))
        )
        val r = linker.compute("A")
        val t = r.first { it.linkType == LinkType.TAG_OVERLAP }
        assertEquals(0.5f, t.weight, 0.01f)
    }

    @Test
    fun `jaccard = 1_0`() = runTest {
        coEvery { noteDao.getById("A") } returns note("A", "t", "b")
        coEvery { noteDao.search(any()) } returns flowOf(emptyList())
        coEvery { noteTagDao.observeTagsFor("A") } returns flowOf(listOf("x", "y"))
        coEvery { noteTagDao.observeAllCrossRefs() } returns flowOf(
            listOf(NoteTagCrossRef("B", "x"), NoteTagCrossRef("B", "y"))
        )
        val r = linker.compute("A")
        val t = r.first { it.linkType == LinkType.TAG_OVERLAP }
        assertEquals(1.0f, t.weight, 0.01f)
    }

    @Test
    fun `disjoint tags no candidate`() = runTest {
        coEvery { noteDao.getById("A") } returns note("A", "t", "b")
        coEvery { noteDao.search(any()) } returns flowOf(emptyList())
        coEvery { noteTagDao.observeTagsFor("A") } returns flowOf(listOf("x"))
        coEvery { noteTagDao.observeAllCrossRefs() } returns flowOf(listOf(NoteTagCrossRef("B", "z")))
        assertTrue(linker.compute("A").none { it.linkType == LinkType.TAG_OVERLAP })
    }

    @Test
    fun `keywordOverlapWeight`() {
        val w = LocalNoteLinker.keywordOverlapWeight("hello world", "hello there world today")
        assertEquals(1.0f, w, 0.01f)
    }

    @Test
    fun `keywordOverlapWeight partial`() {
        val w = LocalNoteLinker.keywordOverlapWeight("hello world kotlin", "hello there")
        assertTrue(w > 0f && w < 1f)
    }

    // -------- R3 fix M1:sanitizeForSearch 先 escape 后 take --------

    @Test
    fun `sanitizeForSearch escapes special LIKE chars before take`() {
        // 51 字符 + 末尾 `\` + 后续 escape `\\\\` 应在 take 前完成，不会因 take 切到 `\` 中间。
        val raw = "a".repeat(50) + "\\"
        val out = LocalNoteLinker.sanitizeForSearch(raw).take(LocalNoteLinker.LIKE_PREFIX_LEN)
        // take 之前的 escape 已把所有 `\` 变成 `\\`，所以 take(50) 切到任意位置，剩余部分仍是合法 LIKE 串。
        // 关键:不能让 `\` 在 50 边界悬空导致 `ESCAPE '\'` 解析错位。
        assertTrue(out.endsWith("a") || out.endsWith("\\"), "末尾必须是 a 或完整 \\，不应悬空")
    }

    @Test
    fun `sanitizeForSearch roundtrips special chars`() {
        // `\` `%` `_` → `\\` `\%` `\_`，配合 LIKE ESCAPE '\\' 必须能精确匹配。
        val raw = "100% off_coupon"
        val escaped = LocalNoteLinker.sanitizeForSearch(raw)
        assertEquals("100\\% off\\_coupon", escaped)
    }

    // -------- R3 fix M2:CJK unigram 覆盖短中文 --------

    @Test
    fun `tokenize includes CJK unigrams for single chars`() {
        val tokens = LocalNoteLinker.tokenize("人")
        assertTrue("人" in tokens, "单字 CJK 必须作为 unigram 出现，否则纯单字笔记 overlap=0")
    }

    @Test
    fun `tokenize emits both unigrams and bigrams for CJK runs`() {
        val tokens = LocalNoteLinker.tokenize("今天天气好")
        // unigram 全部出现
        assertTrue("今" in tokens)
        assertTrue("天" in tokens)
        assertTrue("好" in tokens)
        // bigram 出现
        assertTrue("今天" in tokens)
        assertTrue("天天" in tokens)
    }

    @Test
    fun `keywordOverlapWeight covers single-char CJK`() {
        // R3 fix M2 回归:之前纯单字 CJK 笔记 overlap=0，现在 ≥ 1(命中 unigram)。
        val w = LocalNoteLinker.keywordOverlapWeight("人", "人是什么")
        assertTrue(w > 0f, "单字 '人' 必须命中目标 '人是什么' 的 '人' unigram,w=$w")
    }

    private fun note(id: String, title: String, content: String) =
        NoteEntity(id = id, title = title, content = content, createdAt = 0L, updatedAt = 0L)
}
