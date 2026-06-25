package com.yy.writingwithai.core.security

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PathSafetyTest {

    // ----- safeName -----

    @Test
    fun `safeName accepts alphanumeric dash underscore`() {
        // fix-2026-06-25-review-r1 M7:SAFE_NAME 不再允许 `.`,
        // 故更新测试用例只覆盖字母数字 + dash/underscore。
        assertEquals("writing-with-ai", PathSafety.safeName("writing-with-ai"))
        assertEquals("update", PathSafety.safeName("update"))
        assertEquals("a_b-c", PathSafety.safeName("a_b-c"))
    }

    @Test
    fun `safeName rejects names containing dot`() {
        // fix-2026-06-25-review-r1 M7:dot 不再属于 SAFE_NAME 安全字符,
        // `update.apk` 这种应改走 stripExt 路径或 fallback。
        assertEquals("default", PathSafety.safeName("update.apk"))
        assertEquals("default", PathSafety.safeName("writing-with-ai-1.2.3.apk"))
        assertEquals("default", PathSafety.safeName("a.b"))
    }

    @Test
    fun `safeName rejects leading dot`() {
        // fix-2026-06-25-review-r1 M7:`.bashrc` / `.htaccess` 隐藏文件应 fallback。
        assertEquals("default", PathSafety.safeName(".bashrc"))
        assertEquals("default", PathSafety.safeName(".env"))
    }

    @Test
    fun `safeName rejects consecutive dotdot`() {
        // fix-2026-06-25-review-r1 M7:子串 `..` 一律拒,即便前后无 `/`。
        assertEquals("default", PathSafety.safeName("a..b"))
        assertEquals("default", PathSafety.safeName("name..tar"))
    }

    @Test
    fun `safeName rejects path traversal`() {
        assertEquals("default", PathSafety.safeName("../../../etc/passwd"))
        assertEquals("default", PathSafety.safeName("..\\..\\evil.exe"))
        assertEquals("default", PathSafety.safeName("/etc/passwd"))
    }

    @Test
    fun `safeName rejects shell metachars`() {
        assertEquals("default", PathSafety.safeName("foo;rm -rf /"))
        assertEquals("default", PathSafety.safeName("foo\$(whoami).apk"))
        assertEquals("default", PathSafety.safeName("foo`bar`.apk"))
    }

    @Test
    fun `safeName rejects null and empty`() {
        assertEquals("default", PathSafety.safeName(null))
        assertEquals("default", PathSafety.safeName(""))
    }

    @Test
    fun `safeName respects fallback`() {
        assertEquals("fallback-x", PathSafety.safeName("../bad", fallback = "fallback-x"))
    }

    @Test
    fun `safeName rejects overlong strings`() {
        val tooLong = "a".repeat(129)
        assertEquals("default", PathSafety.safeName(tooLong))
    }

    // ----- requireSafeId -----

    @Test
    fun `requireSafeId accepts uuid-like`() {
        PathSafety.requireSafeId("abc-123", "noteId")
        PathSafety.requireSafeId("n_1", "noteId")
        PathSafety.requireSafeId("ABCdef", "noteId")
    }

    @Test
    fun `requireSafeId rejects slash dot dotdot`() {
        assertThrows(IllegalArgumentException::class.java) {
            PathSafety.requireSafeId("../etc", "noteId")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PathSafety.requireSafeId("a/b", "noteId")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PathSafety.requireSafeId("a.b", "noteId")
        }
    }

    // ----- requireSafeExt -----

    @Test
    fun `requireSafeExt accepts short alpha`() {
        PathSafety.requireSafeExt("jpg")
        PathSafety.requireSafeExt("md")
        PathSafety.requireSafeExt("png123")
    }

    @Test
    fun `requireSafeExt rejects long or nonalpha`() {
        assertThrows(IllegalArgumentException::class.java) { PathSafety.requireSafeExt("jp eg") }
        assertThrows(IllegalArgumentException::class.java) { PathSafety.requireSafeExt("toolongext") }
        assertThrows(IllegalArgumentException::class.java) { PathSafety.requireSafeExt("jpg/") }
    }

    // ----- assertContainedUnder -----

    @Test
    fun `assertContainedUnder accepts child inside root`() {
        val root = File("/tmp/root").also { it.mkdirs() }
        val child = File(root, "sub/file.txt").also { it.parentFile?.mkdirs() }
        PathSafety.assertContainedUnder(child, root)
    }

    @Test
    fun `assertContainedUnder rejects sibling escape`() {
        val root = File("/tmp/root").also { it.mkdirs() }
        val sibling = File("/tmp/other/file.txt").also { it.parentFile?.mkdirs() }
        assertThrows(IllegalArgumentException::class.java) {
            PathSafety.assertContainedUnder(sibling, root)
        }
    }

    @Test
    fun `assertContainedUnder catches prefix-but-not-subdir trick`() {
        val root = File("/tmp/root-prefix-test-${System.nanoTime()}").also { it.mkdirs() }
        val sneaky = File("/tmp/root-prefix-test-evil-${System.nanoTime()}/file").also { it.parentFile?.mkdirs() }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PathSafety.assertContainedUnder(sneaky, root)
            }
        } finally {
            sneaky.parentFile?.deleteRecursively()
            root.delete()
        }
    }
}
