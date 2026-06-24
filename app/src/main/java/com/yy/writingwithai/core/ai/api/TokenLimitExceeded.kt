package com.yy.writingwithai.core.ai.api

/**
 * fix-2026-06-24-review-r1-critical · LLM 输出超过字符上限时抛出,触发熔断。
 *
 * 调用方应:
 * 1. catch 后不再调 `historyRepo.record()` 计费
 * 2. 返回 0 link / 空 entity list,UI 显示"输出过长,已熔断"
 */
class TokenLimitExceeded(val maxChars: Int) : RuntimeException("LLM output exceeded $maxChars chars")
