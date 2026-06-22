## Why

飞书云文档(Docx v1)用 block 树存储;本地笔记是 Markdown。双向同步的核心障碍是结构差异。本 change 实现双向转换层,作为 `feishu-bidir-sync` 的纯函数依赖,**不**依赖飞书 token,可独立单测 + round-trip。

## What Changes

- **新增** `MarkdownToDocxConverter`:接收 Markdown 字符串 → 输出 `List<FeishuBlock>`(heading / paragraph / bullet / ordered / code / quote / divider / link run)
- **新增** `DocxToMarkdownConverter`:接收 `List<FeishuBlock>` → 输出 Markdown 字符串
- **新增** `FeishuBlock` 数据模型:sealed class,12 种 block type:`Heading(level=1..3)` / `Paragraph(runs: List<Run>)` / `Bullet(items)` / `Ordered(items)` / `CodeBlock(language, text)` / `Quote` / `Divider` / `Image(placeholder)` / `Table(cells)` / `Unsupported(raw)`;`Run` 含 `text` / `bold` / `italic` / `linkUrl` / `code`
- **新增** 转换矩阵文档(`docs/usage/markdown-docx-mapping.md`):每种 Markdown 元素 ↔ 飞书 block 映射表 + 不支持元素降级规则
- **新增** v1 支持子集:
  - `# / ## / ###` ↔ heading_1/2/3
  - 段落 ↔ text
  - `- / *` ↔ bullet
  - `1.` ↔ ordered
  - ```` ``` ```` ↔ code
  - `>` ↔ quote
  - `---` ↔ divider
  - `**bold**` / `*italic*` / `` `inline code` `` ↔ text.run style
  - `[text](url)` ↔ text.run link
  - `[text][ref]` ↔ text.run link(ref 表由 caller 提供,本 change 只解析行内形式)
- **新增** 不支持元素降级:
  - 图片 `![alt](path)` → `Paragraph("图片：<path>")`(占位文本,飞书不渲染图片)
  - 表格 `| ... |` → `Bullet(items = cells每行)`(不支持单元格合并 / 列对齐)
  - HTML 标签 `<div>...</div>` → 原样保留为段落文本
  - mermaid / 流程图 → `CodeBlock(language="mermaid", text=raw)`
  - 数学公式 `$...$` → `Paragraph(raw)`
- **新增** `MarkdownRoundTripTester`:固定 10 条 sample 笔记 → 转 Docx → 转回 Markdown → diff(逐字符);任何不对齐写进测试失败用例
- **新增** `FeishuBlockConverterError`:解析异常 + 不支持元素的 fallback 路径
- **新增** `core/feishu/converter/` 包:`MarkdownToDocxConverter` / `DocxToMarkdownConverter` / `FeishuBlock` / `Run` / 测试 fixture

## Capabilities

### New Capabilities

- `markdown-docx-converter`:Markdown ↔ 飞书 Docx block 双向转换 + 12 种 block 数据模型 + v1 支持子集 + 不支持元素降级 + round-trip 测试

### Modified Capabilities

无。

## Impact

- **代码**:`core/feishu/converter/`(纯 Kotlin,无 Android 依赖,可 JVM 单测);`MarkdownRoundTripTester` 集成测试;`docs/usage/markdown-docx-mapping.md` 文档
- **依赖**:无新增三方库;kotlinx.serialization JSON 已在
- **测试**:10 条固定 sample(中英混合 + 各种 Markdown 元素);round-trip diff 工具 `MarkdownDiff` 自实现(LCS + char-level);覆盖率 100%(每种 block type 至少 1 个 case)
- **不在范围**:富文本所见即所得同步(只做源码级);表格样式(列宽 / 背景);图片二进制上传(留给 `feishu-bidir-sync`);评论 / @ / 协作(超出 scope)
- **不在范围**:飞书云文档以外的格式(Notion / 语雀 / 石墨)— v2 复用此转换层只需加新 converter