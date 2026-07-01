package com.yy.writingwithai.feature.quicknote.model

import kotlin.math.ceil

/**
 * 字数计算(spec §"Word count and reading time on detail page"):
 * - CJK(汉字 / 平假名 / 片假名)按 1 字
 * - 英文 / 拉丁字母按空格分词求和
 * - 数字 / 标点不计入
 */
object WordCount {
    fun of(content: String): Int {
        if (content.isBlank()) return 0
        return cjkCount(content) + englishWordCount(content)
    }

    internal fun cjkCount(content: String): Int = CJK_REGEX.findAll(content).count()

    internal fun englishWordCount(content: String): Int {
        // 先把 CJK 字符替换成空格，避免它们被英文 split 当成"1 个词"
        val noCjk = CJK_REGEX.replace(content, " ")
        val stripped = NON_WORD.replace(noCjk, " ")
        return stripped.split(' ').count { it.isNotBlank() }
    }

    // CJK 统一表意 + 平假名 + 片假名(显式范围，避免依赖 script flag)
    private val CJK_REGEX = Regex("[\\u4e00-\\u9fff\\u3040-\\u309f\\u30a0-\\u30ff]")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}']+")
}

/**
 * 预估阅读时间(分钟):取中文速率与英文速率较慢者，向上取整(spec §"Word count and reading time")。
 */
object ReadingTime {
    private const val CN_WPM = 300
    private const val EN_WPM = 200

    fun minutesOf(content: String): Int {
        if (content.isBlank()) return 0
        val cjk = WordCount.cjkCount(content)
        val words = WordCount.englishWordCount(content)
        val cnMin = if (cjk == 0) 0 else ceil(cjk.toDouble() / CN_WPM).toInt()
        val enMin = if (words == 0) 0 else ceil(words.toDouble() / EN_WPM).toInt()
        return maxOf(cnMin, enMin)
    }
}
