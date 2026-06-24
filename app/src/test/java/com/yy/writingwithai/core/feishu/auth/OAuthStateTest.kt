package com.yy.writingwithai.core.feishu.auth

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OAuthStateTest {

    private val launcher = OAuthLauncher(authStore = mockk(relaxed = true))

    @Test
    fun `generateState returns UUID-format string`() {
        val s = launcher.generateState()
        // UUID v4: 8-4-4-4-12 hex chars
        val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue(uuid.matches(s), "expected UUID format, got: $s")
    }

    @Test
    fun `generateState returns unique values across calls`() {
        val a = launcher.generateState()
        val b = launcher.generateState()
        assertNotEquals(a, b)
    }

    @Test
    fun `buildAuthorizeUrl includes state parameter`() {
        val url = launcher.buildAuthorizeUrl("app123", "abc-state-xyz")
        assertTrue(url.contains("state=abc-state-xyz"), "expected state=abc-state-xyz in: $url")
        assertTrue(url.contains("app_id=app123"), "expected app_id=app123 in: $url")
        assertTrue(url.contains("redirect_uri="), "expected redirect_uri in: $url")
    }

    @Test
    fun `buildAuthorizeUrl URL-encodes special chars in state`() {
        val url = launcher.buildAuthorizeUrl("app1", "with space & special=chars")
        // space → +, & → %26, = → %3D
        assertTrue(
            url.contains("state=with+space+%26+special%3Dchars"),
            "expected URL-encoded state, got: $url"
        )
    }

    @Test
    fun `state equals no hardcoded placeholder`() {
        val url = launcher.buildAuthorizeUrl("app1", launcher.generateState())
        assertTrue(
            !url.contains("state=app_state"),
            "must not contain hardcoded 'app_state' placeholder, got: $url"
        )
    }
}
