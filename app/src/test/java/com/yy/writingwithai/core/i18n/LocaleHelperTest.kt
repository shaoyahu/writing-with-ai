package com.yy.writingwithai.core.i18n

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * language-switcher · LocaleHelper 纯函数单测。
 */
class LocaleHelperTest {

    @Test
    fun resolveLocale_system_returnsSystemLocale() {
        val systemLocale = Locale("en", "US")
        val result = LocaleHelper.resolveLocale(LocaleSelection.SYSTEM, systemLocale)
        assertEquals(systemLocale, result)
    }

    @Test
    fun resolveLocale_zh_returnsChineseLocale() {
        val result = LocaleHelper.resolveLocale(LocaleSelection.ZH, Locale.getDefault())
        assertEquals(Locale("zh"), result)
    }

    @Test
    fun resolveLocale_en_returnsEnglishLocale() {
        val result = LocaleHelper.resolveLocale(LocaleSelection.EN, Locale.getDefault())
        assertEquals(Locale("en"), result)
    }

    @Test
    fun fromKey_zh_returnsZH() {
        assertEquals(LocaleSelection.ZH, LocaleSelection.fromKey("zh"))
    }

    @Test
    fun fromKey_en_returnsEN() {
        assertEquals(LocaleSelection.EN, LocaleSelection.fromKey("en"))
    }

    @Test
    fun fromKey_unknownOrNull_returnsSystem() {
        assertEquals(LocaleSelection.SYSTEM, LocaleSelection.fromKey(null))
        assertEquals(LocaleSelection.SYSTEM, LocaleSelection.fromKey(""))
        assertEquals(LocaleSelection.SYSTEM, LocaleSelection.fromKey("xx"))
    }
}
