package com.yy.writingwithai.core.note.entity

/** entity-extraction-association · 12 类实体枚举。 */
enum class EntityType(val keyPrefix: String) {
    PERSON("person"),
    WORK("work"),
    EVENT("event"),
    LOCATION("location"),
    ORG("org"),
    CONCEPT("concept"),
    DATE("date"),
    URL("url"),
    QUOTE("quote"),
    PRODUCT("product"),
    TASK("task"),
    NUMBER("number");

    companion object {
        private val NORMALIZE_REGEX = Regex("[^a-z0-9_]+")

        /**
         * 中文类型名 → EntityType 映射。
         * 与 DefaultPrompts.ENTITY_EXTRACT_SYSTEM 里的 12 类中文名对齐。
         * LLM 返回中文 type 时(如 {"type":"概念",...})，通过此映射转成枚举。
         */
        private val ZH_NAME_MAP: Map<String, EntityType> = mapOf(
            "人物" to PERSON,
            "作品" to WORK,
            "事件" to EVENT,
            "地点" to LOCATION,
            "组织" to ORG,
            "概念" to CONCEPT,
            "日期" to DATE,
            "网址" to URL,
            "引言" to QUOTE,
            "产品" to PRODUCT,
            "任务" to TASK,
            "数字" to NUMBER
        )

        /**
         * 从 LLM 返回的 type 字符串解析 EntityType。
         * 优先匹配中文类型名(与 prompt 对齐)，fallback 到英文枚举名(valueOf)。
         * 两者都不匹配时返回 null(调用方 mapNotNull 过滤)。
         */
        fun fromTypeName(typeStr: String): EntityType? {
            // 优先中文映射(与 DefaultPrompts.ENTITY_EXTRACT_SYSTEM 对齐)
            ZH_NAME_MAP[typeStr]?.let { return it }
            // fallback 英文枚举名(兼容 LLM 返回英文 type 的情况)
            return runCatching { valueOf(typeStr.uppercase()) }.getOrNull()
        }

        fun normalizeKey(type: EntityType, rawKey: String): String {
            val normalized = rawKey
                .lowercase()
                .replace(NORMALIZE_REGEX, "_")
                .trim('_')
            val prefix = "${type.keyPrefix}::"
            return if (normalized.startsWith(prefix)) normalized else prefix + normalized
        }
    }
}
