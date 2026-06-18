package com.yy.writingwithai.feature.quicknote.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingTimeTest {
    @Test
    fun empty_returns_zero() {
        assertEquals(0, ReadingTime.minutesOf(""))
    }

    @Test
    fun chinese_six_chars_rounds_up_to_one_minute() {
        assertEquals(1, ReadingTime.minutesOf("今天天气很好"))
    }

    @Test
    fun english_six_words_rounds_up_to_one_minute() {
        assertEquals(1, ReadingTime.minutesOf("Hello world this is a test"))
    }

    @Test
    fun large_chinese_takes_about_expected_minutes() {
        val text = "中".repeat(3000)
        assertEquals(10, ReadingTime.minutesOf(text))
    }

    @Test
    fun max_of_cn_and_en_takes_the_larger() {
        assertEquals(1, ReadingTime.minutesOf("中".repeat(100)))
        val english = (1..1000).joinToString(" ") { "w$it" }
        assertEquals(5, ReadingTime.minutesOf(english))
    }
}
