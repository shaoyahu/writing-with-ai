// build-logic 助手,被 app/build.gradle.kts 引用,亦供 JVM 单测覆盖。
// 放在 buildSrc/ 而非 app/src/main/,因为 buildSrc 是 precompiled build script 插件,
// 自动加入所有 build.gradle.kts 的 classpath,但不进 app 运行时/test classpath。
package com.yy.writingwithai.buildlogic

/**
 * 单条 preflight 失败。
 *
 * @param checkId check-1 / check-2 / check-3 / check-4 中之一
 * @param file 出错文件路径
 * @param line 出错行号(check-3 文件缺失时为 0)
 * @param message 原始 grep 命中行 / 缺失原因
 */
data class PreflightFailure(
    val checkId: String,
    val file: String,
    val line: Int,
    val message: String
)

/**
 * 解析 grep 输出为 [PreflightFailure] 列表。
 *
 * 期望输入格式:`file:line: match`(每行一条),由 `grep -n` 在 stdout 上产生。
 *
 * - 空 / 空白输入 → 返回空 list
 * - 单行命中 → 1 条记录
 * - 不匹配正则的行被忽略(防御性:grep 偶尔会向 stdout 写告警文本)
 */
fun parseGrepOutput(text: String): List<PreflightFailure> {
    if (text.isBlank()) return emptyList()
    val pattern = Regex("""^([^:]+):(\d+):\s*(.*)$""")
    return text.lineSequence()
        .mapNotNull { line ->
            val match = pattern.matchEntire(line) ?: return@mapNotNull null
            PreflightFailure(
                checkId = "",
                file = match.groupValues[1],
                line = match.groupValues[2].toIntOrNull() ?: 0,
                message = match.groupValues[3]
            )
        }
        .toList()
}
