package com.kovak.moyue.reader

/**
 * 纯逻辑折行器(可单测):中文避头尾 + 拉丁单词不拆。
 * measure 由外部注入(生产环境用 Paint,测试用假宽度),保持 JVM 纯净。
 *
 * 关键点:行满时不立即换行,先看下一个字符 —— 是避头字符(如 ,。)则悬挂在行尾,
 * 否则换行并把行尾的避尾字符(如开引号)移到下一行。
 */
object LineBreaker {

    /** 不能出现在行首的字符(避头) */
    val NO_START = "，。、；：？！）)》〉」』】〕〗…—%‰”’!?.,;:)]}"

    /** 不能出现在行尾的字符(避尾) */
    val NO_END = "（《〈「『【〔〖“‘([{\$@&*#"

    private fun isLatin(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '\'' || c == '-' || c == '_'

    fun breakLines(
        text: String,
        indentChars: Int,
        width: Float,
        emWidth: Float,
        measure: (String) -> Float,
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val firstWidth = (width - indentChars * emWidth).coerceAtLeast(width * 0.4f)
        val lines = ArrayList<String>()
        val cur = StringBuilder()
        var curW = 0f
        var avail = firstWidth

        fun plainFlush() {
            if (cur.isNotEmpty()) lines.add(cur.toString())
            cur.setLength(0)
            curW = 0f
            avail = width
        }

        /** 结束当前行:行尾避尾字符移到下一行行首 */
        fun flushWithAvoidEnd() {
            var moved = StringBuilder()
            while (cur.isNotEmpty() && NO_END.contains(cur.last())) {
                moved.insert(0, cur.last())
                cur.deleteCharAt(cur.length - 1)
            }
            if (cur.isNotEmpty()) lines.add(cur.toString())
            cur.setLength(0)
            if (moved.isNotEmpty()) {
                cur.append(moved)
                curW = measure(moved.toString())
            } else {
                curW = 0f
            }
            avail = width
        }

        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            val token: String
            if (isLatin(c)) {
                var j = i
                while (j < n && isLatin(text[j])) j++
                token = text.substring(i, j)
            } else {
                token = c.toString()
            }
            i += token.length
            val tw = measure(token)

            // 上一轮留下的满行:避头字符悬挂,否则换行
            if (cur.isNotEmpty() && curW >= avail) {
                if (token.length == 1 && NO_START.contains(c)) {
                    cur.append(token)
                    curW += tw
                    flushWithAvoidEnd()
                    continue
                }
                flushWithAvoidEnd()
            }

            if (curW + tw <= avail || cur.isEmpty()) {
                if (cur.isEmpty() && tw > avail) {
                    // 超长单词整行放不下:按字符硬拆
                    var chunk = StringBuilder()
                    var chunkW = 0f
                    for (ch in token) {
                        val cw = measure(ch.toString())
                        if (chunkW + cw > avail && chunk.isNotEmpty()) {
                            lines.add(chunk.toString())
                            chunk = StringBuilder()
                            chunkW = 0f
                            avail = width
                        }
                        chunk.append(ch)
                        chunkW += cw
                    }
                    cur.append(chunk)
                    curW = chunkW
                } else {
                    cur.append(token)
                    curW += tw
                }
            } else {
                // 当前行放不下
                if (token.length == 1 && NO_START.contains(c)) {
                    // 避头:悬挂在行尾
                    cur.append(token)
                    curW += tw
                    flushWithAvoidEnd()
                } else {
                    flushWithAvoidEnd()
                    cur.append(token)
                    curW += tw
                }
            }
        }
        plainFlush()
        return lines
    }
}
