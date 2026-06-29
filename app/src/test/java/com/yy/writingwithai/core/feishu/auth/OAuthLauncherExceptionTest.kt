package com.yy.writingwithai.core.feishu.auth

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ux-2026-06-28:验证 OAuthLauncher 异常分类。
 *
 * - KeyStoreException / KeyPermanentlyInvalidatedException / InvalidKeyException /
 *   UnrecoverableKeyException / GeneralSecurityException 走 KeystoreUnavailable 分支
 * - 其它类型(IOException 等)走通用 LaunchFailed / 其他分支
 * - 网络错误 message 误含 "keystore" 字样 **不应** 误分类
 * - cause 链上的 keystore 类异常也应识别
 */
class OAuthLauncherExceptionTest {

    @Test
    fun `KeyStoreException direct is classified as keystore error`() {
        assertTrue(isKeystoreErrorPublic(KeyStoreException("AndroidKeyStore reset")))
    }

    @Test
    fun `KeyPermanentlyInvalidatedException is classified as keystore error`() {
        assertTrue(isKeystoreErrorPublic(KeyPermanentlyInvalidatedException()))
    }

    @Test
    fun `InvalidKeyException is classified as keystore error`() {
        assertTrue(isKeystoreErrorPublic(InvalidKeyException("bad key")))
    }

    @Test
    fun `UnrecoverableKeyException is classified as keystore error`() {
        assertTrue(isKeystoreErrorPublic(UnrecoverableKeyException()))
    }

    @Test
    fun `GeneralSecurityException direct is classified as keystore error`() {
        assertTrue(isKeystoreErrorPublic(GeneralSecurityException("crypto failed")))
    }

    @Test
    fun `plain IOException is NOT keystore error`() {
        assertFalse(isKeystoreErrorPublic(IOException("network unreachable")))
    }

    @Test
    fun `IllegalStateException is NOT keystore error`() {
        assertFalse(isKeystoreErrorPublic(IllegalStateException("not initialized")))
    }

    /**
     * 回归:之前用 message.contains("Keystore", true) 嗅探,网络错误 message 里碰巧出现
     * "keystore" 字样(如远程日志、stackoverflow 包装)会被误分类。新实现仅按类型,
     * 这种情况不再误分类。
     */
    @Test
    fun `IOException with keystore keyword in message is NOT keystore error`() {
        assertFalse(isKeystoreErrorPublic(IOException("failed to parse: keystore not found in payload")))
    }

    @Test
    fun `cause chain containing KeyStoreException is classified as keystore error`() {
        val wrapped = RuntimeException("outer", KeyStoreException("inner"))
        assertTrue(isKeystoreErrorPublic(wrapped))
    }

    @Test
    fun `cause chain with GeneralSecurityException deep is classified as keystore error`() {
        val wrapped = IOException("io", IllegalStateException("mid", GeneralSecurityException("deep")))
        assertTrue(isKeystoreErrorPublic(wrapped))
    }

    @Test
    fun `OAuthLaunchException subclasses preserve cause`() {
        val cause = KeyStoreException("test")
        val ks = OAuthLauncher.OAuthLaunchException.KeystoreUnavailable(cause)
        assertEquals(cause, ks.cause)
        assertTrue(ks.message?.contains("Keystore", ignoreCase = true) == true)

        val cause2 = IOException("test")
        val lf = OAuthLauncher.OAuthLaunchException.LaunchFailed(cause2)
        assertEquals(cause2, lf.cause)
    }

    /**
     * 直接调用 top-level `internal fun isKeystoreError`,无需反射构造 Hilt 类。
     */
    private fun isKeystoreErrorPublic(t: Throwable): Boolean = isKeystoreError(t)
}
