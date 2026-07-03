package com.yy.writingwithai.core.prefs

import com.yy.writingwithai.core.ui.animation.AnimationStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * animation-system · 覆盖 [FakeUserPrefsStore] 的 7 个 enum 值 round-trip +
 * 未知 String 回退 MINIMAL + 默认值(spec §REQ 1 + §REQ 3 + §REQ 4)。
 */
class FakeUserPrefsStoreTest {

    @Test
    fun `default animation style is IMMERSIVE when nothing seeded`() = runTest {
        val fake = FakeUserPrefsStore()
        assertEquals(AnimationStyle.IMMERSIVE, fake.animationStyleFlow.first())
    }

    @Test
    fun `seedAnimationStyle with null falls back to IMMERSIVE`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.seedAnimationStyle(null)
        assertEquals(AnimationStyle.IMMERSIVE, fake.animationStyleFlow.first())
    }

    @Test
    fun `seedAnimationStyle with unknown string falls back to IMMERSIVE`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.seedAnimationStyle("MAGIC_GLOW")
        assertEquals(AnimationStyle.IMMERSIVE, fake.animationStyleFlow.first())
    }

    @Test
    fun `setAnimationStyle round-trips MINIMAL`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.setAnimationStyle(AnimationStyle.MINIMAL)
        assertEquals(AnimationStyle.MINIMAL, fake.animationStyleFlow.first())
    }

    @Test
    fun `setAnimationStyle round-trips FLUID`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.setAnimationStyle(AnimationStyle.FLUID)
        assertEquals(AnimationStyle.FLUID, fake.animationStyleFlow.first())
    }

    @Test
    fun `setAnimationStyle round-trips IMMERSIVE`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.setAnimationStyle(AnimationStyle.IMMERSIVE)
        assertEquals(AnimationStyle.IMMERSIVE, fake.animationStyleFlow.first())
    }

    @Test
    fun `setAnimationStyle round-trips NONE`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.setAnimationStyle(AnimationStyle.NONE)
        assertEquals(AnimationStyle.NONE, fake.animationStyleFlow.first())
    }

    @Test
    fun `setAnimationStyle round-trips CROSSFADE`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.setAnimationStyle(AnimationStyle.CROSSFADE)
        assertEquals(AnimationStyle.CROSSFADE, fake.animationStyleFlow.first())
    }

    @Test
    fun `setAnimationStyle round-trips SCALE`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.setAnimationStyle(AnimationStyle.SCALE)
        assertEquals(AnimationStyle.SCALE, fake.animationStyleFlow.first())
    }

    @Test
    fun `setAnimationStyle round-trips SLIDE_UP`() = runTest {
        val fake = FakeUserPrefsStore()
        fake.setAnimationStyle(AnimationStyle.SLIDE_UP)
        assertEquals(AnimationStyle.SLIDE_UP, fake.animationStyleFlow.first())
    }

    @Test
    fun `seedAnimationStyle with valid enum names resolves to correct style`() = runTest {
        val fake = FakeUserPrefsStore()
        listOf(
            "MINIMAL" to AnimationStyle.MINIMAL,
            "FLUID" to AnimationStyle.FLUID,
            "IMMERSIVE" to AnimationStyle.IMMERSIVE,
            "CROSSFADE" to AnimationStyle.CROSSFADE,
            "SCALE" to AnimationStyle.SCALE,
            "SLIDE_UP" to AnimationStyle.SLIDE_UP,
            "NONE" to AnimationStyle.NONE
        ).forEach { (raw, expected) ->
            fake.seedAnimationStyle(raw)
            assertEquals("seed '$raw' should resolve to $expected", expected, fake.animationStyleFlow.first())
        }
    }

    @Test
    fun `ackApikeyPrompt API unchanged and works`() = runTest {
        val fake = FakeUserPrefsStore()
        assertEquals(false, fake.isApikeyPromptAcked())
        fake.seed(true)
        assertEquals(true, fake.isApikeyPromptAcked())
        fake.setAckApikeyPrompt(false)
        assertEquals(false, fake.isApikeyPromptAcked())
    }
}
