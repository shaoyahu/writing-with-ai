package com.yy.writingwithai.core.ai.api

/**
 * fix-2026-06-26-review-r3 LOW:LLM 输出字符上限共享常量。
 *
 * ≈ 4K tokens × 4 chars/token = 16384。LlmNoteLinkExtractor / LlmEntityExtractor
 * 共用此值,避免重复定义导致不一致。
 */
const val LLM_MAX_OUTPUT_CHARS = 16384
