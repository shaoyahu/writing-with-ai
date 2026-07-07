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
