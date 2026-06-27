package com.yy.writingwithai.core.data.db.dao.entity

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.entity.entity.EntityAliasRow
import com.yy.writingwithai.core.note.entity.EntityType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * entity-extraction-polish §6.2:EntityAliasDao 实跑 Robolectric in-memory Room 测试。
 *
 * 覆盖:
 * - upsert REPLACE(同 (entityType, aliasKey) 覆盖)
 * - deleteByAlias 精确删除
 * - findByAliasKeys(List<String>) 批量查
 */
@RunWith(RobolectricTestRunner::class)
class EntityAliasDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EntityAliasDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.entityAliasDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alias(type: EntityType, alias: String, canonical: String) =
        EntityAliasRow(entityType = type, aliasKey = alias, canonicalEntityKey = canonical)

    @Test
    fun upsert_REPLACE_overwrites_same_composite_key() = runBlocking {
        dao.upsert(alias(EntityType.PERSON, "person::x", "person::x_canonical"))
        dao.upsert(alias(EntityType.PERSON, "person::x", "person::x_v2"))

        val found = dao.findByAliasKeys(listOf("person::x"))
        assertEquals(1, found.size)
        assertEquals("person::x_v2", found[0].canonicalEntityKey)
    }

    @Test
    fun deleteByAlias_removes_only_the_targeted_alias() = runBlocking {
        dao.upsert(alias(EntityType.PERSON, "person::a", "person::a_c"))
        dao.upsert(alias(EntityType.PERSON, "person::b", "person::b_c"))

        dao.deleteByAlias(EntityType.PERSON, "person::a")

        val remaining = dao.listAll()
        assertEquals(1, remaining.size)
        assertEquals("person::b", remaining[0].aliasKey)
    }

    @Test
    fun findByAliasKeys_batch_query_returns_matching_rows() = runBlocking {
        dao.upsert(alias(EntityType.PERSON, "person::a", "person::a_c"))
        dao.upsert(alias(EntityType.PERSON, "person::b", "person::b_c"))
        dao.upsert(alias(EntityType.WORK, "work::x", "work::x_c"))

        val found = dao.findByAliasKeys(listOf("person::a", "work::x", "person::missing"))
        assertEquals(2, found.size)
        val keys = found.map { it.aliasKey }.toSet()
        assertTrue(keys.containsAll(listOf("person::a", "work::x")))
    }
}
