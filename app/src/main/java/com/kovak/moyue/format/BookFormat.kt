package com.kovak.moyue.format

/** 书籍文件格式。MOBI 为只读支持(未压缩/PalmDOC 压缩),可转换导出。 */
enum class BookFormat(val ext: String, val mime: String, val label: String) {
    TXT("txt", "text/plain", "TXT"),
    EPUB("epub", "application/epub+zip", "EPUB"),
    MOBI("mobi", "application/x-mobipocket-ebook", "MOBI");

    companion object {
        fun fromExt(name: String): BookFormat? {
            val e = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.ext == e }
        }

        fun fromMime(mime: String?): BookFormat? = when {
            mime == null -> null
            mime.contains("epub", ignoreCase = true) -> EPUB
            mime.contains("mobipocket", ignoreCase = true) -> MOBI
            mime.contains("octet-stream", ignoreCase = true) -> null
            mime.contains("text", ignoreCase = true) -> TXT
            else -> null
        }
    }
}

/** 解析后的书内容:章节 + 每章段落列表。 */
data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
) {
    data class Chapter(val title: String, val paragraphs: List<String>)

    val totalChars: Long by lazy { chapters.sumOf { c -> c.paragraphs.sumOf { it.length.toLong() } + c.title.length } }

    /** 每章起始累计字符数,用于全局百分比。 */
    fun cumulativeChars(): LongArray {
        val arr = LongArray(chapters.size + 1)
        for (i in chapters.indices) {
            val ch = chapters[i]
            arr[i + 1] = arr[i] + ch.title.length + ch.paragraphs.sumOf { it.length.toLong() }
        }
        return arr
    }
}
