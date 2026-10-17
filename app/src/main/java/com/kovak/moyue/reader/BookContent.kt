package com.kovak.moyue.reader

import com.kovak.moyue.format.ParsedBook

/**
 * 阅读内容模型:把章节拍平成全局段落序列(章节标题作为特殊段落),
 * 支持全局锚点(段落下标)与百分比互转。
 */
class BookContent(book: ParsedBook) {

    data class Para(
        val chapter: Int,
        val indexInChapter: Int,
        val text: String,
        val isTitle: Boolean,
        val charStart: Long,
    )

    val chapterTitles: List<String> = book.chapters.map { it.title }
    val paras: List<Para>
    val chapterStartPara: IntArray
    val totalChars: Long = book.totalChars.coerceAtLeast(1)

    init {
        val list = ArrayList<Para>()
        val starts = IntArray(book.chapters.size)
        var running = 0L
        book.chapters.forEachIndexed { ci, ch ->
            starts[ci] = list.size
            list.add(Para(ci, -1, ch.title, isTitle = true, charStart = running))
            running += ch.title.length
            ch.paragraphs.forEachIndexed { pi, p ->
                list.add(Para(ci, pi, p, isTitle = false, charStart = running))
                running += p.length
            }
        }
        paras = list
        chapterStartPara = starts
    }

    fun chapterOfPara(globalIndex: Int): Int {
        if (paras.isEmpty()) return 0
        val idx = globalIndex.coerceIn(0, paras.size - 1)
        return paras[idx].chapter
    }

    /** 全局段落 -> (章, 章内段) */
    fun toChapterPosition(globalIndex: Int): Pair<Int, Int> {
        val p = paras[globalIndex.coerceIn(0, paras.size - 1)]
        return if (p.isTitle) p.chapter to 0 else p.chapter to p.indexInChapter
    }

    /** (章, 章内段) -> 全局段落 */
    fun toGlobalIndex(chapter: Int, paragraph: Int): Int {
        if (chapter !in chapterStartPara.indices) return 0
        val start = chapterStartPara[chapter]
        val next = if (chapter + 1 < chapterStartPara.size) chapterStartPara[chapter + 1] else paras.size
        return (start + paragraph.coerceAtLeast(0)).coerceIn(start, (next - 1).coerceAtLeast(start))
    }

    fun percentOf(globalIndex: Int): Float {
        if (paras.isEmpty()) return 0f
        val p = paras[globalIndex.coerceIn(0, paras.size - 1)]
        return (p.charStart.toFloat() / totalChars).coerceIn(0f, 1f)
    }

    fun anchorForPercent(percent: Float): Int {
        if (paras.isEmpty()) return 0
        val target = (percent.coerceIn(0f, 1f) * totalChars).toLong()
        // 二分找最后一个 charStart <= target 的段落
        var lo = 0
        var hi = paras.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (paras[mid].charStart <= target) lo = mid else hi = mid - 1
        }
        return lo
    }
}
