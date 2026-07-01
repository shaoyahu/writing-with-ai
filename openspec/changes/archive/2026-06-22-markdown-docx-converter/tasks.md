## 1. 转换接口

- [ ] 1.1 新建 `core/feishu/converter/MarkdownToDocxConverter.kt`(interface):`convert(markdown: String): List<DocxBlock>`
- [ ] 1.2 新建 `core/feishu/converter/DocxToMarkdownConverter.kt`(interface):`convert(blocks: List<DocxBlock>): String`
- [ ] 1.3 新建 `core/feishu/converter/DocxBlock.kt`(`@Serializable` data class):枚举 `type` + `elements: List<BlockElement>` + 嵌套 `BlockElement.TextRun` / `Link` 等
- [ ] 1.4 Hilt module 注入两个 converter

## 2. MarkdownToDocxConverter 实现

- [ ] 2.1 新建 `MarkdownToDocxConverterImpl.kt`:基于 `jetbrains/markdown` 库解析 Markdown AST → 遍历节点映射 DocxBlock
- [ ] 2.2 元素映射:
  - `# H1-H3` → `heading_1` / `_2` / `_3`
  - 段落 → `text`
  - `- bullet` → `bullet`
  - `1. ordered` → `ordered`
  - `> quote` → `quote`
  - ` ``` ` → `code`(language 字段从 infoString 取)
  - `**bold**` / `*italic*` → `text.run.text_element_style`(bold / italic)
  - `[text](url)` → `text.run` + link
  - `---` → `divider`
- [ ] 2.3 不支持元素降级:`![image](path)` → `text` block content = `[图片：path]`;`<html>` 标签 → `text` block content = raw HTML 字符串;`mermaid` code block → `text` block content = `[不支持类型:mermaid]`
- [ ] 2.4 表格:`|` 表格 → `table` block + `table_property` + text-grid layout(不支持合并单元格，合并单元格文档里写明不支持)

## 3. DocxToMarkdownConverter 实现

- [ ] 3.1 新建 `DocxToMarkdownConverterImpl.kt`:遍历 blocks → 映射回 Markdown
- [ ] 3.2 元素映射:`heading_1-3` → `# H1-3`;`bullet` → `- item`;`ordered` → `1. item`;`quote` → `> item`;`code` → ``` ``` ```;`text` → 段落;`table` → markdown table;`divider` → `---`
- [ ] 3.3 不支持 block type → `[不支持类型:grid]`

## 4. Round-trip 测试

- [ ] 4.1 `app/src/test/resources/converter/sample.md`:固定 10 条样本(覆盖所有 supported 元素)
- [ ] 4.2 `MarkdownDocxRoundTripTest`:对每条样本跑 `md → docx → md`，断言结果与输入一致(deepEquals，忽略降级 placeholder)
- [ ] 4.3 `MarkdownDocxUnsupportedTest`:故意写 `![img](x)` / `<div>html</div>` / ` ```mermaid ``` ` 三条样本，断言降级 placeholder 出现 + round-trip 仍稳定

## 5. i18n

- [ ] 5.1 `core/feishu/converter/` 内的错误信息走 `stringResource`(如有)或硬编码中英

## 6. 依赖

- [ ] 6.1 `gradle/libs.versions.toml` 加 `markdown = "0.7.3"`(或当前最新稳定版)
- [ ] 6.2 `app/build.gradle.kts` 加 `implementation(libs.markdown)`

## 7. 编译 + ktlint

- [ ] 7.1 `./gradlew :app:assembleDebug` 通过
- [ ] 7.2 `./gradlew :app:ktlintCheck` 通过
- [ ] 7.3 `./gradlew :app:testDebugUnitTest` 全绿(尤其 round-trip 测试)
- [ ] 7.4 `./gradlew :app:check` 全绿