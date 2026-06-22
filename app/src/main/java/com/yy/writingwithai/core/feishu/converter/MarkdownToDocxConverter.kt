package com.yy.writingwithai.core.feishu.converter

/**
 * markdown-docx-converter · Markdown 字符串 → 飞书 Docx block 列表。
 *
 * spec: openspec/changes/markdown-docx-converter/spec.md
 * "Markdown to Docx block conversion"
 */
interface MarkdownToDocxConverter {
    suspend fun convert(markdown: String): List<FeishuBlock>
}
