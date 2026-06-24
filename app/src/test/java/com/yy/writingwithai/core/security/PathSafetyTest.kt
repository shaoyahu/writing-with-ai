package com.yy.writingwithai.core.security

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PathSafetyTest {

    // ----- safeName -----

    @Test
    fun `safeName accepts alphanumeric dot dash underscore`() {
        assertEquals("writing-with-ai-1.2.3.apk", PathSafety.safeName("writing-with-ai-1.2.3.apk"))
        assertEquals("update.apk", PathSafety.safeName("update.apk"))
        assertEquals("a_b-c.d", PathSafety.safeName("a_b-c.d"))
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
