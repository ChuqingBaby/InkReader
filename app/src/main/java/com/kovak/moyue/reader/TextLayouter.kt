package com.kovak.moyue.reader

import android.graphics.Paint
import android.graphics.Typeface

/** 分页结果:一行文本 */
data class PageLine(
    val para: Int,            // 全局段落下标
    val text: String,
    val isTitle: Boolean,
    val isFirstOfPara: Boolean,
    val isLastOfPara: Boolean,
    val naturalWidth: Float,  // 未两端对齐时的自然宽度
)

data class Page(
    val lines: List<PageLine>,
    val firstPara: Int,
    val lastPara: Int,
)

/**
 * 分页排版器:用 android.graphics.Paint 测量,LineBreaker 折行,贪心装页。
 * 章节标题起新页。
 */
class TextLayouter(private val typeface: Typeface?) {

    fun paginate(
        content: BookContent,
        widthPx: Float,
        heightPx: Float,
        fontPx: Float,
        lineSpacing: Float,
        paraSpacingPx: Float,
    ): List<Page> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPx
            typeface?.let { this.typeface = it }
        }
        val em = paint.measureText("字").coerceAtLeast(fontPx * 0.9f)
        val lineH = fontPx * lineSpacing
        val measure: (String) -> Float = { paint.measureText(it) }

        val pages = ArrayList<Page>()
        var cur = ArrayList<PageLine>()
        var used = 0f

        fun newPage() {
            if (cur.isNotEmpty()) {
                pages.add(Page(cur.toList(), cur.first().para, cur.last().para))
            }
            cur = ArrayList()
            used = 0f
        }

        for (pIndex in content.paras.indices) {
            val p = content.paras[pIndex]
            val indent = if (p.isTitle) 0 else 2
            val lines = LineBreaker.breakLines(p.text, indent, widthPx, em, measure)
            if (lines.isEmpty()) continue
            // 章节标题:新起一页
            if (p.isTitle && cur.isNotEmpty()) newPage()

            val lineCount = lines.size
            var first = true
            for ((li, ln) in lines.withIndex()) {
                var need = lineH
                if (first && cur.isNotEmpty()) {
                    need += paraSpacingPx * (if (p.isTitle) 2f else 1f)
                }
                if (used + need > heightPx + 0.5f) {
                    newPage()
                    need = lineH
                }
                cur.add(
                    PageLine(
                        para = pIndex,
                        text = ln,
                        isTitle = p.isTitle,
                        isFirstOfPara = first,
                        isLastOfPara = li == lineCount - 1,
                        naturalWidth = measure(ln),
                    ),
                )
                used += need
                first = false
            }
        }
        if (cur.isNotEmpty()) {
            pages.add(Page(cur.toList(), cur.first().para, cur.last().para))
        }
        return pages.ifEmpty { listOf(Page(emptyList(), 0, 0)) }
    }

    /** 页码 -> 页首全局段落 */
    fun pageForAnchor(pages: List<Page>, anchor: Int): Int {
        var lo = 0
        var hi = pages.size - 1
        var best = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (pages[mid].firstPara <= anchor) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
