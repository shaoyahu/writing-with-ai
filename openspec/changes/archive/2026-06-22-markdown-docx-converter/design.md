## Context

飞书云文档 Docx v1 用 block 树存储;本地 note 是 Markdown 字符串。双向同步的核心是结构转换层。

本 change 是 **纯函数库**，不依赖飞书 API token、不依赖 Android SDK、不依赖 Room;只接 Markdown 字符串 + 飞书 block 数据模型。可 JVM 单测，可独立 review。

约束:
- v1 roadmap §3.1:Markdown 源码 + 实时预览(无富文本所见即所得)
- 输出 `FeishuBlock` 是 sealed class，序列化走 kotlinx.serialization
- 转换是 pure function，无副作用

## Goals / Non-Goals

**Goals:**
- Markdown → `List<FeishuBlock>`(heading_1-3 / paragraph / bullet / ordered / code / quote / divider)
- `List<FeishuBlock>` → Markdown(对应反向)
- 不支持元素降级规则定义 + 测试 fixture
- Round-trip 10 条 sample 自动对比

**Non-Goals:**
- 富文本所见即所得(只源码级)
- 表格样式 / 列宽 / 合并单元格
- 图片二进制上传(留给 `feishu-bidir-sync`)
- 评论 / @ / 协作
- Notion / 语雀等其他格式(v2 复用此层加新 converter)

## Decisions

### D1 · FeishuBlock sealed class

```kotlin
@Serializable
sealed class FeishuBlock {
    @Serializable @SerialName("heading") data class Heading(val level: Int, val runs: List<Run>): FeishuBlock()
    @Serializable @SerialName("paragraph") data class Paragraph(val runs: List<Run>): FeishuBlock()
    @Serializable @SerialName("bullet") data class Bullet(val items: List<List<Run>>): FeishuBlock()
    @Serializable @SerialName("ordered") data class Ordered(val items: List<List<Run>>): FeishuBlock()
    @Serializable @SerialName("code") data class CodeBlock(val language: String, val text: String): FeishuBlock()
    @Serializable @SerialName("quote") data class Quote(val runs: List<Run>): FeishuBlock()
    @Serializable @SerialName("divider") data object Divider: FeishuBlock()
    @Serializable @SerialName("image") data class Image(val placeholder: String): FeishuBlock()
    @Serializable @SerialName("table") data class Table(val rows: List<List<String>>): FeishuBlock()
    @Serializable @SerialName("unsupported") data class Unsupported(val raw: String): FeishuBlock()
}

@Serializable
data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false, val linkUrl: String? = null)
```

序列化用 `@SerialName` 方便和飞书 API 真实 block 字段对齐。

### D2 · 解析策略:行优先 + 块级 regex

- 行级识别:`^(#{1,6})\s+(.+)$` → heading;`^[-*]\s+` → bullet;`^\d+\.\s+` → ordered;`^>\s+` → quote;`^---$` → divider
- 块级识别:连续 ``` 包围 → code block;空行分隔段落
- 行内识别:`**text**` / `*text*` / `` `text` `` / `[text](url)` → Run style
- 优先级:块级先匹配，行级再扫，行内最后

### D3 · 不支持元素降级

按 proposal 列出的规则，在解析时直接产出对应的 `FeishuBlock`:

```kotlin
private fun parseImage(line: String): FeishuBlock.Image {
    val match = Regex("""!\[([^\]]*)\]\(([^)]+)\)""").matchEntire(line) ?: return FeishuBlock.Unsupported(line)
    return FeishuBlock.Image(placeholder = "图片：${match.groupValues[2]}")
}

private fun parseTable(lines: List<String>): List<FeishuBlock> {
    return lines.filter { it.contains("|") }.map { line ->
        val cells = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        FeishuBlock.Bullet(items = cells.map { listOf(Run(text = it)) })
    }
}
```

降级规则 100% 测试覆盖。

### D4 · Round-trip 接受度

```kotlin
class MarkdownRoundTripTester {
    private val samples = listOf(
        "samples/zh_basic.md",
        "samples/en_basic.md",
        "samples/zh_with_image.md",
        "samples/en_with_table.md",
        // ... 共 10 条
    )

    @Test
    fun `round-trip preserves content`() {
        samples.forEach { file ->
            val original = readFile(file)
            val blocks = MarkdownToDocxConverter.convert(original)
            val reversed = DocxToMarkdownConverter.convert(blocks)
            val normalizedOriginal = normalize(original)
            val normalizedReversed = normalize(reversed)
            assertEquals(normalizedOriginal, normalizedReversed)
        }
    }
}
```

`normalize()`:去多余空行 / trim / 统一列表符号。

接受度规则:文字内容 100% 一致;格式标记(bullet/ordered)允许在不歧义情况下互换;heading level 1:1 保留。

### D5 · 错误处理

解析失败行(畸形 Markdown)→ 走 `FeishuBlock.Unsupported(raw = 原行)`;不抛异常，确保整个文档能完成转换。

```kotlin
private fun parseLine(line: String): FeishuBlock = try {
    when {
        line.isBlank() -> null
        line.startsWith("#") -> parseHeading(line)
        line.startsWith(">") -> parseQuote(line)
        else -> FeishuBlock.Paragraph(parseRuns(line))
    }
} catch (e: Exception) {
    FeishuBlock.Unsupported(raw = line)
}
```

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| Markdown 解析 100% 自实现，边界 case 多 | 10 条 sample round-trip + 已知边界 case fixture;不引入 commonmark-java 减少依赖 |
| 飞书 block 树结构后续变更 | `FeishuBlock` 是内部数据模型，不直接绑飞书 API;caller 负责映射到飞书私有格式 |
| Round-trip 不接受度(降级元素丢失信息) | fixture 用 `assertEquals(normalize, normalize)`，允许合理格式转换;明确列出不可逆元素 |
| 代码块语言识别错误 | 优先取 ```lang 后缀;lang 不识别时存 `text` |

## Migration Plan

1. `core/feishu/converter/` 新包，纯 Kotlin
2. 10 条 sample fixture 放在 `app/src/test/resources/markdown-docx/`
3. round-trip 集成测试
4. **回滚**:新 converter 是 additive;旧路径(直接走 `Markdown` 字符串)保留，无 breaking

## Open Questions

- 是否支持 Markdown 扩展(GFM / 表格语法 / Task list)? — v1 不支持(表格走降级 bullet)，留 v2
- 飞书块格式是否直接用 Docx v1 API 真实字段? — v1 内部抽象，留给 `feishu-bidir-sync` 做映射