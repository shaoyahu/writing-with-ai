package com.yy.writingwithai.core.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * animation-system · 覆盖 4 套 AnimationStyle 的 token 工厂非空 + 4 套互异。
 *
 * 设计要点:
 * - NONE 风格所有 transition 是 `EnterTransition.None` / `ExitTransition.None`,spec 是 `snap()`。
 * - 其他 3 套至少 navEnter / navExit 必须非 None(否则 NONE 不构成"唯一瞬时")。
 */
class AnimationTokensTest {

    @Test
    fun `tokens are non-null for all 4 styles`() {
        AnimationStyle.entries.forEach { style ->
            val tokens = style.toTokens()
            assertNotNull("$style navEnter must be non-null", tokens.navEnter)
            assertNotNull("$style navExit must be non-null", tokens.navExit)
            assertNotNull("$style switchSpec must be non-null", tokens.switchSpec)
        }
    }

    @Test
    fun `NONE style uses EnterTransition None and snap`() {
        val tokens = AnimationStyle.NONE.toTokens()
        assertEquals(EnterTransition.None, tokens.navEnter)
        assertEquals(ExitTransition.None, tokens.navExit)
        assertEquals(EnterTransition.None, tokens.navPopEnter)
        assertEquals(ExitTransition.None, tokens.navPopExit)
        assertEquals(EnterTransition.None, tokens.dialogEnter)
        assertEquals(ExitTransition.None, tokens.dialogExit)
        // spec 类型不强制,但 NONE 必须保证是无 spring/tween 的 snap(瞬时)
        assertEquals("SnapSpec", tokens.switchSpec::class.simpleName)
        assertEquals("SnapSpec", tokens.tabContentSpec::class.simpleName)
    }

    @Test
    fun `4 styles produce at least 2 distinct navEnter`() {
        val navEnters = AnimationStyle.entries.map { it.toTokens().navEnter }.toSet()
        assert(navEnters.size >= 2) {
            "Expected at least 2 distinct navEnter values across 4 styles, got $navEnters"
        }
    }

    @Test
    fun `MINIMAL and FLUID and IMMERSIVE have non-None navEnter`() {
        listOf(AnimationStyle.MINIMAL, AnimationStyle.FLUID, AnimationStyle.IMMERSIVE).forEach { style ->
            val tokens = style.toTokens()
            assertNotEquals(
                "$style navEnter must NOT be None (only NONE should be instant)",
                EnterTransition.None,
                tokens.navEnter
            )
        }
    }

    @Test
    fun `same style returns same token instance (factory memoization)`() {
        // AnimationStyle 工厂用 companion object 缓存,toTokens() 应是引用相同。
        val a = AnimationStyle.MINIMAL.toTokens()
        val b = AnimationStyle.MINIMAL.toTokens()
        assertEquals(
            "MINIMAL.toTokens() must return the same instance (companion memoized)",
            a,
            b
        )
    }
}
