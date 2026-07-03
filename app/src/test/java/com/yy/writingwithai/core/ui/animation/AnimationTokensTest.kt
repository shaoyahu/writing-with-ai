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
    fun `tokens are non-null for all 7 styles`() {
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
        // spec 类型不强制，但 NONE 必须保证是无 spring/tween 的 snap(瞬时)
        assertEquals("SnapSpec", tokens.switchSpec::class.simpleName)
        assertEquals("SnapSpec", tokens.tabContentSpec::class.simpleName)
    }

    @Test
    fun `7 styles produce at least 2 distinct navEnter`() {
        val navEnters = AnimationStyle.entries.map { it.toTokens().navEnter }.toSet()
        assert(navEnters.size >= 2) {
            "Expected at least 2 distinct navEnter values across 7 styles, got $navEnters"
        }
    }

    @Test
    fun `all non-NONE styles have non-None navEnter`() {
        AnimationStyle.entries.filter { it != AnimationStyle.NONE }.forEach { style ->
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
        // AnimationStyle 工厂用 companion object 缓存，toTokens() 应是引用相同。
        val a = AnimationStyle.MINIMAL.toTokens()
        val b = AnimationStyle.MINIMAL.toTokens()
        assertEquals(
            "MINIMAL.toTokens() must return the same instance (companion memoized)",
            a,
            b
        )
    }

    // animation-switch-redesign §5.2:覆盖 tokensFor(style, navEnabled, tabEnabled)。

    @Test
    fun `tokensFor with both toggles disabled zeros out nav transitions and snaps tab`() {
        // IMMERSIVE 风格基线:nav 一定有非 None 动画;tokensFor 关掉后必须全部退化为 None / snap。
        val base = AnimationStyle.IMMERSIVE.toTokens()
        val overridden = tokensFor(AnimationStyle.IMMERSIVE, navEnabled = false, tabEnabled = false)

        // 5 个被覆盖的字段(spec ADDED REQ 3)
        assertEquals(EnterTransition.None, overridden.navEnter)
        assertEquals(ExitTransition.None, overridden.navExit)
        assertEquals(EnterTransition.None, overridden.navPopEnter)
        assertEquals(ExitTransition.None, overridden.navPopExit)
        assertEquals("SnapSpec", overridden.tabContentSpec::class.simpleName)

        // 其它字段透传(开关不影响 dialog / expand / collapse / switchSpec / listItemSpec)
        assertEquals(base.switchSpec, overridden.switchSpec)
        assertEquals(base.dialogEnter, overridden.dialogEnter)
        assertEquals(base.dialogExit, overridden.dialogExit)
        assertEquals(base.expandSpec, overridden.expandSpec)
        assertEquals(base.collapseSpec, overridden.collapseSpec)
        assertEquals(base.listItemSpec, overridden.listItemSpec)
    }

    @Test
    fun `tokensFor with both toggles enabled matches style baseline`() {
        // OPEN Q4:navEnabled = tabEnabled = true 时,行为完全等同 style.toTokens()(spec Decision 1)。
        AnimationStyle.entries.forEach { style ->
            val base = style.toTokens()
            val overridden = tokensFor(style, navEnabled = true, tabEnabled = true)
            assertEquals(
                "$style tokensFor(true, true) must equal style.toTokens()",
                base,
                overridden
            )
        }
    }

    @Test
    fun `tokensFor navEnabled false but tabEnabled true preserves tab spec`() {
        // nav 关 + tab 开:nav 退化 None,tab 保留基线 spec。
        val base = AnimationStyle.FLUID.toTokens()
        val overridden = tokensFor(AnimationStyle.FLUID, navEnabled = false, tabEnabled = true)

        assertEquals(EnterTransition.None, overridden.navEnter)
        assertEquals(ExitTransition.None, overridden.navExit)
        assertEquals(EnterTransition.None, overridden.navPopEnter)
        assertEquals(ExitTransition.None, overridden.navPopExit)
        // tab 透传 — FLUID 的 tabContentSpec 不是 snap(否则这个断言会失败)
        assertEquals(base.tabContentSpec, overridden.tabContentSpec)
        assertNotEquals(
            "FLUID tabContentSpec should NOT be SnapSpec (animation enabled)",
            "SnapSpec",
            overridden.tabContentSpec::class.simpleName
        )
    }

    @Test
    fun `tokensFor navEnabled true but tabEnabled false snaps tab only`() {
        // nav 开 + tab 关:nav 保持基线,tab snap。
        val base = AnimationStyle.FLUID.toTokens()
        val overridden = tokensFor(AnimationStyle.FLUID, navEnabled = true, tabEnabled = false)

        // nav 透传
        assertEquals(base.navEnter, overridden.navEnter)
        assertEquals(base.navExit, overridden.navExit)
        assertEquals(base.navPopEnter, overridden.navPopEnter)
        assertEquals(base.navPopExit, overridden.navPopExit)
        // tab snap
        assertEquals("SnapSpec", overridden.tabContentSpec::class.simpleName)
    }
}
