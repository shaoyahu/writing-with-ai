package com.yy.writingwithai.core.ai.prompt

import com.yy.writingwithai.core.ai.api.WritingOp

/**
 * M3 写死 system prompt 集中点(custom-prompt-template 落地)。
 *
 * spec: openspec/changes/custom-prompt-template/specs/custom-prompt-template/spec.md
 * "DefaultPrompts provides fallback for M3 write-dead prompts"
 *
 * 之前 M3 把 prompt 散在 `ExpandPrompt` / `PolishPrompt` / `OrganizePrompt` 3 个 object,
 * custom-prompt-template 合并到 `DefaultPrompts.forOp`(原内容一字不改,只是搬家)。
 *
 * 调用方:`AnthropicCompatibleAdapter.systemPromptFor` 当 `AiRequest.systemPrompt == null`
 * 时 fallback;`AiActionViewModel.start` 在 `PromptTemplateStore.getForOp(op) == null` 时
 * 也会用,但实际下游仍由 adapter 兜底(双保险)。
 */
object DefaultPrompts {
    fun forOp(op: WritingOp): String = when (op) {
        WritingOp.EXPAND -> EXPAND_SYSTEM
        WritingOp.POLISH -> POLISH_SYSTEM
        WritingOp.ORGANIZE -> ORGANIZE_SYSTEM
    }

    // M3 ExpandPrompt.SYSTEM 原文(M4-4 合并到这里,不改字)
    private const val EXPAND_SYSTEM =
        "你是一位专业的写作助手。你的任务是在用户提供的原文基础上进行扩写," +
            "保留核心信息和语气,补充必要的细节、例子或逻辑展开。" +
            "扩写后的文本应该比原文更丰富、更完整,但不能偏离原意。"

    // M3 PolishPrompt.SYSTEM 原文
    private const val POLISH_SYSTEM =
        "你是一位专业的文字编辑。你的任务是优化用户提供的文本表达," +
            "修正语病、统一风格、提升可读性,但不改变原意。" +
            "润色后的文本应该更流畅、更自然、更专业。"

    // M3 OrganizePrompt.SYSTEM 原文
    private const val ORGANIZE_SYSTEM =
        "你是一位专业的信息整理师。你的任务是将用户提供的零散文字按主题/要点重新组织," +
            "输出结构化 Markdown(标题、列表、要点)。" +
            "整理后的文本应层次分明、逻辑清晰,适合快速阅读和查找。"
}
