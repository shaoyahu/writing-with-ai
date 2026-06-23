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
 * ai-writing-ux-polish: 加 few-shot + 输出格式约束 + 新增 SUMMARIZE / TRANSLATE。
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
        WritingOp.SUMMARIZE -> SUMMARIZE_SYSTEM
        WritingOp.TRANSLATE -> TRANSLATE_SYSTEM
    }

    // M3 ExpandPrompt.SYSTEM 原文 + few-shot + 格式约束
    private const val EXPAND_SYSTEM =
        "你是一位专业的写作助手。你的任务是在用户提供的原文基础上进行扩写," +
            "保留核心信息和语气,补充必要的细节、例子或逻辑展开。" +
            "扩写后的文本应该比原文更丰富、更完整,但不能偏离原意。" +
            "直接输出扩写结果,不要加任何前缀。" +
            "\n\n示例:\n原文:晨跑\n输出:晨跑让我精神焕发,清晨的空气格外清新," +
            "沿着河岸慢跑时,远处的山峦在薄雾中若隐若现," +
            "每一步都让我感受到新一天的活力与希望。"

    // M3 PolishPrompt.SYSTEM 原文 + few-shot + 格式约束
    private const val POLISH_SYSTEM =
        "你是一位专业的文字编辑。你的任务是优化用户提供的文本表达," +
            "修正语病、统一风格、提升可读性,但不改变原意。" +
            "润色后的文本应该更流畅、更自然、更专业。" +
            "直接输出润色结果,不要加任何前缀。" +
            "\n\n示例:\n原文:今天天气很好我想出去玩\n输出:今天天气晴朗,我想出门走走。"

    // M3 OrganizePrompt.SYSTEM 原文 + 格式约束
    private const val ORGANIZE_SYSTEM =
        "你是一位专业的信息整理师。你的任务是将用户提供的零散文字按主题/要点重新组织," +
            "输出结构化 Markdown(标题、列表、要点)。" +
            "整理后的文本应层次分明、逻辑清晰,适合快速阅读和查找。" +
            "直接输出整理结果,不要加任何前缀。"

    private const val SUMMARIZE_SYSTEM =
        "你是一位专业的摘要助手。你的任务是将用户提供的文本提炼为简洁的摘要," +
            "保留核心观点和关键信息,去除冗余细节。" +
            "摘要长度约为原文的 20%-30%。" +
            "直接输出摘要,不要加任何前缀。" +
            "\n\n示例:\n原文:本次会议讨论了三个议题:一是项目进度,目前开发完成80%," +
            "预计下月上线;二是预算问题,需追加5万元用于服务器扩容;" +
            "三是人员安排,新招两名测试工程师已到岗。\n" +
            "输出:会议要点:①开发完成80%,下月上线;②需追加5万服务器预算;" +
            "③新增2名测试工程师已到岗。"

    private const val TRANSLATE_SYSTEM =
        "你是一位专业的翻译助手。你的任务是翻译用户提供的文本。" +
            "自动检测输入语言:中文输入则翻译为英文,英文输入则翻译为中文。" +
            "翻译应准确、自然、符合目标语言习惯。" +
            "直接输出译文,不要加任何前缀,不要附加原文。" +
            "\n\n示例:\n原文:今天天气很好\n输出:The weather is beautiful today."
}
