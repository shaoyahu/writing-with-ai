package com.yy.writingwithai.core.feishu.converter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * markdown-docx-converter · 飞书 Docx block 数据模型。
 *
 * sealed class + kotlinx.serialization 序列化，与飞书 [Docx v1] API 真实 block 字段对齐
 * (`heading_1` / `text` / `bullet` 等)。
 *
 * spec: openspec/changes/markdown-docx-converter/spec.md
 * design: openspec/changes/markdown-docx-converter/design.md D1
 *
 * [Docx v1]: https://open.feishu.cn/document/server-docs/docs/docx-v1/block/block-structure
 */
@Serializable
sealed class FeishuBlock {
    @Serializable
    @SerialName("heading")
    data class Heading(
        // 1..6，飞书支持 1..9 但本地只暴露 1..6
        val level: Int,
        val runs: List<Run>
    ) : FeishuBlock()

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val runs: List<Run>
    ) : FeishuBlock()

    @Serializable
    @SerialName("bullet")
    data class Bullet(
        // 每个 item 一组 Run(支持行内样式)
        val items: List<List<Run>>
    ) : FeishuBlock()

    @Serializable
    @SerialName("ordered")
    data class Ordered(
        val items: List<List<Run>>
    ) : FeishuBlock()

    @Serializable
    @SerialName("code")
    data class CodeBlock(
        val language: String,
        val text: String
    ) : FeishuBlock()

    @Serializable
    @SerialName("quote")
    data class Quote(
        val runs: List<Run>
    ) : FeishuBlock()

    @Serializable
    @SerialName("divider")
    data object Divider : FeishuBlock()

    @Serializable
    @SerialName("image")
    data class Image(
        // Markdown 路径，如 "assets/img/foo.png"
        val placeholder: String
    ) : FeishuBlock()

    @Serializable
    @SerialName("table")
    data class Table(
        // 简化:v1 不支持合并单元格 / 列对齐
        val rows: List<List<String>>
    ) : FeishuBlock()

    @Serializable
    @SerialName("unsupported")
    data class Unsupported(
        // 原始 Markdown 行 / 未知 block 字符串
        val raw: String
    ) : FeishuBlock()
}

/**
 * 行内样式单元。bold / italic / code 可组合;`linkUrl` 优先于其他样式。
 */
@Serializable
data class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String? = null
)
