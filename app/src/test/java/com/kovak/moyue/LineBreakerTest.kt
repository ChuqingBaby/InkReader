package com.kovak.moyue

import com.kovak.moyue.reader.LineBreaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineBreakerTest {

    /** 假测量:全角字符 10f,半角 5f */
    private val measure: (String) -> Float = { s ->
        s.sumOf { c -> if (c.code > 0x2E80) 10.0 else 5.0 }.toFloat()
    }
    private val em = 10f

    @Test
    fun `wraps at width and hangs punctuation`() {
        // 宽度 50 → 每行 5 个全角
        val text = "一二三四五六七八九十,你好"
        val lines = LineBreaker.breakLines(text, 0, 50f, em, measure)
        assertEquals(listOf("一二三四五", "六七八九十,", "你好"), lines)
    }

    @Test
    fun `first line indent reduces width`() {
        val text = "一二三四五六七八"
        val lines = LineBreaker.breakLines(text, 2, 50f, em, measure)
        // 首行可用 30 → 3 个字符
        assertEquals("一二三", lines[0])
        assertEquals("四五六七八", lines[1])
    }

    @Test
    fun `latin words are not split`() {
        val text = "Hello 世界"
        val lines = LineBreaker.breakLines(text, 0, 50f, em, measure)
        assertEquals(listOf("Hello 世界"), lines)
    }

    @Test
    fun `long latin word is hard split`() {
        val text = "AAAAAAAAAAAB"
        val lines = LineBreaker.breakLines(text, 0, 25f, em, measure)
        // 每个拉丁字符 5f,一行 25f = 5 个字符
        assertEquals(listOf("AAAAA", "AAAAA", "AB"), lines)
    }

    @Test
    fun `opening quote moves to next line`() {
        // 行尾不能是开引号:构造"四字+开引号+一字"在宽度 50 下
        val text = "甲乙丙丁“戊"
        val lines = LineBreaker.breakLines(text, 0, 50f, em, measure)
        // "甲乙丙丁" 占满一行;行尾不能留“ → “ 移到下一行与"戊"合并
        assertEquals(listOf("甲乙丙丁", "“戊"), lines)
    }

    @Test
    fun `blank text yields no lines`() {
        assertTrue(LineBreaker.breakLines("   ", 0, 50f, em, measure).isEmpty())
    }
}
