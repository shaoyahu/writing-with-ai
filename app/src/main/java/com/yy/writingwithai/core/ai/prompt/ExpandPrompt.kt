package com.yy.writingwithai.core.ai.prompt

internal object ExpandPrompt {
    const val SYSTEM =
        "你是一位专业的写作助手。你的任务是在用户提供的原文基础上进行扩写," +
            "保留核心信息和语气,补充必要的细节、例子或逻辑展开。" +
            "扩写后的文本应该比原文更丰富、更完整,但不能偏离原意。"
}
