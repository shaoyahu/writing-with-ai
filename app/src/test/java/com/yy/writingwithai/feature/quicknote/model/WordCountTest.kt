package com.yy.writingwithai.feature.quicknote.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WordCountTest {
    @Test
    fun empty_and_blank_content_returns_zero() {
        assertEquals(0, WordCount.of(""))
        assertEquals(0, WordCount.of("   \n\t  "))
    }

    @Test
    fun chinese_chars_count_one_each() {
        assertEquals(6, WordCount.of("今天天气很好"))
    }

    @Test
    fun english_words_split_by_space() {
        assertEquals(6, WordCount.of("Hello world this is a test"))
    }

    @Test
    fun mixed_chinese_and_english_sums_both() {
        assertEquals(6, WordCount.of("hello 你好 world 中文"))
    }

    @Test
    fun punctuation_not_counted() {
        assertEquals(2, WordCount.of("hello, world!"))
    }

    @Test
    fun hiragana_and_katakana_count_as_cjk() {
        assertEquals(2, WordCount.of("あア"))
    }
}
