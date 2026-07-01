package com.yy.writingwithai.core.ai.prompt

/**
 * feishu-doc-service-refactor · 飞书操作 dispatcher 系统 prompt 模板(参考 larksuite/cli
 * `lark-doc` skill 的 sub-command JSON 形状)。
 *
 * 提供 4 个操作的 schema + 1 个 dispatcher prompt，供未来 `feishu-ai-command` change 消费:
 * - LLM 接收用户自然语言意图 + 此 prompt，输出 JSON 形如 `{"op": "create_doc", "args": {...}}`
 * - 上层 dispatcher 解析后调 `FeishuDocService` 对应方法
 *
 * 本 change 仅落 prompt 模板(不消费);`feishu-ai-command` 引入实际 orchestrator。
 *
 * spec: openspec/changes/feishu-doc-service-refactor/specs/feishu-doc-service/spec.md
 */
object FeishuCommandPrompt {

    /** 4 个操作的 JSON schema 描述(嵌入 dispatcher prompt)。 */
    val operationSchema: String = """
        Available operations:
        1. create_doc — 新建飞书 docx 文档
           Args: { "noteId": String, "title": String, "content": String (markdown) }
           Output: { "docId": String, "docUrl": String }

        2. read_doc — 读取飞书 docx 文档内容
           Args: { "docUrl": String }
           Output: { "docId": String, "title": String, "markdown": String }

        3. update_doc — 替换整篇飞书文档
           Args: { "noteId": String }
           Output: { "docId": String, "revisionId": String }

        4. append_block — 追加一段到飞书文档
           Args: { "noteId": String, "parentBlockId": String? (null = 文档末尾), "content": String }
           Output: { "docId": String, "revisionId": String }
    """.trimIndent()

    /** Dispatcher 系统 prompt:让 LLM 把用户意图解析为上述 JSON。 */
    val systemPrompt: String = """
        你是飞书文档操作 dispatcher。根据用户自然语言意图，选择最匹配的操作并输出 JSON。

        $operationSchema

        输出要求(严格 JSON，不要任何额外文字):
        { "op": "<op_name>", "args": { ... } }

        若用户意图不明确(没指定哪个笔记 / 哪个文档)，在 args 里留空并 prompt 澄清。
    """.trimIndent()
}
