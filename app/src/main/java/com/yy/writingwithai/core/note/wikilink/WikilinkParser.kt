package com.yy.writingwithai.core.note.wikilink

object WikilinkParser {
    private val REGEX = Regex("""\[\[([^\[\]\n]+?)\]\]""")
    fun parse(content: String): List<String> = REGEX.findAll(content).map { it.groupValues[1].trim() }.toList()
}
