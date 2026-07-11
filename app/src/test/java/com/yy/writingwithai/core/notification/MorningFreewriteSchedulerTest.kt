package com.yy.writingwithai.core.notification

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * morning-freewrite · [MorningFreewriteScheduler.nextTriggerAt] 单测。
 *
 * 纯函数测试,不走 Android 框架(纯 JVM);JVM 默认时区由环境决定,
 * 所有 case 用 [ZoneId.systemDefault] 显式拿,避免 hardcoded UTC 漂移。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MorningFreewriteSchedulerTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val scheduler by lazy {
        MorningFreewriteScheduler(ApplicationProvider.getApplicationContext())
    }

    private fun at(date: String, time: String): ZonedDateTime =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone)

    @Test
    fun `target time later today returns today`() {
        val now = at("2026-07-11", "08:00")
        val result = scheduler.nextTriggerAt(9, 30, now)
        assertEquals(at("2026-07-11", "09:30"), result)
    }

    @Test
    fun `target time earlier today returns tomorrow`() {
        val now = at("2026-07-11", "08:00")
        val result = scheduler.nextTriggerAt(7, 0, now)
        assertEquals(at("2026-07-12", "07:00"), result)
    }

    @Test
    fun `target time equal to now returns tomorrow`() {
        // 边界:now == target → 不算"未来",走次日(design:严格按 isBefore)
        val now = at("2026-07-11", "08:00")
        val result = scheduler.nextTriggerAt(8, 0, now)
        assertEquals(at("2026-07-12", "08:00"), result)
    }

    @Test
    fun `hour 23 minute 59 cross midnight`() {
        val now = at("2026-07-11", "22:00")
        val result = scheduler.nextTriggerAt(23, 59, now)
        assertEquals(at("2026-07-11", "23:59"), result)
    }

    @Test
    fun `hour 0 minute 0 from afternoon returns next day midnight`() {
        val now = at("2026-07-11", "13:00")
        val result = scheduler.nextTriggerAt(0, 0, now)
        assertEquals(at("2026-07-12", "00:00"), result)
    }

    @Test
    fun `month boundary - target earlier in next month`() {
        // 跨月底:7 月 31 23:00 → 8 月 1 日 06:00
        val now = at("2026-07-31", "23:00")
        val result = scheduler.nextTriggerAt(6, 0, now)
        assertEquals(at("2026-08-01", "06:00"), result)
    }

    @Test
    fun `year boundary - target on Jan 1 next year`() {
        val now = at("2026-12-31", "23:00")
        val result = scheduler.nextTriggerAt(0, 0, now)
        assertEquals(at("2027-01-01", "00:00"), result)
    }

    @Test
    fun `result preserves source zone`() {
        val now = at("2026-07-11", "08:00")
        val result = scheduler.nextTriggerAt(9, 0, now)
        assertEquals(zone, result.zone)
        assertNotEquals(ZoneId.of("UTC"), result.zone) // 防御:不能写成 hardcoded UTC
    }

    @Test
    fun `target 1 minute in the future returns same minute`() {
        val now = at("2026-07-11", "08:29:59")
        val result = scheduler.nextTriggerAt(8, 30, now)
        // 任何精度对齐到 minute,result 落在 08:30 同一分钟
        assertTrue(result.isAfter(now))
        assertEquals(8, result.hour)
        assertEquals(30, result.minute)
    }
}
