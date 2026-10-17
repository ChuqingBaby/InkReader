package com.kovak.moyue.format

/**
 * 章节切分:按正则把整本文本切成章节,空行合并段落。
 */
object ChapterSplitter {

    const val DEFAULT_REGEX = """^\s*(第\s*[0-9一二三四五六七八九十百千万零〇两]+\s*[章节回卷部篇集]|序章|楔子|前言|后记|尾声|番外|终章|Chapter\s+\d+)[^\n]{0,40}$"""

    data class RawChapter(val title: String, val body: String)

    fun split(text: String, regexStr: String = DEFAULT_REGEX): List<RawChapter> {
        val regex = try {
            Regex(regexStr, RegexOption.MULTILINE)
        } catch (e: Exception) {
            Regex(DEFAULT_REGEX, RegexOption.MULTILINE)
        }
        val chapters = ArrayList<RawChapter>()
        var pendingTitle: String? = null
        val body = StringBuilder()

        fun flush() {
            val b = body.toString()
            if (pendingTitle != null || b.isNotBlank()) {
                chapters.add(RawChapter(pendingTitle ?: "开始", b))
            }
            body.setLength(0)
        }

        text.lineSequence().forEach { line ->
            if (regex.containsMatchIn(line)) {
                flush()
                pendingTitle = line.trim()
            } else {
                body.append(line).append('\n')
            }
        }
        flush()
        return chapters
    }

    /** 把正文按空行/换行切成段落列表。 */
    fun toParagraphs(body: String): List<String> =
        body.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun splitToParsed(text: String, regexStr: String = DEFAULT_REGEX): List<ParsedBook.Chapter> =
        split(text, regexStr).map { ParsedBook.Chapter(it.title, toParagraphs(it.body)) }
}
