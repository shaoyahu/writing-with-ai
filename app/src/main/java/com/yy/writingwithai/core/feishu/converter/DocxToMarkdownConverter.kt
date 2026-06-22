package com.yy.writingwithai.core.feishu.converter

/**
 * markdown-docx-converter · 飞书 Docx block 列表 → Markdown 字符串。
 *
 * spec: openspec/changes/markdown-docx-converter/spec.md
 * "Docx to Markdown conversion"
 */
interface DocxToMarkdownConverter {
    suspend fun convert(blocks: List<FeishuBlock>): String
}
