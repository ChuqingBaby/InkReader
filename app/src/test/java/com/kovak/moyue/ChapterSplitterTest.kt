package com.kovak.moyue

import com.kovak.moyue.format.ChapterSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSplitterTest {

    @Test
    fun `splits by chapter heading`() {
        val text = """
            第一章 起点
            内容A1
            内容A2

            第二章 发展
            内容B1
        """.trimIndent()
        val chapters = ChapterSplitter.splitToParsed(text)
        assertEquals(2, chapters.size)
        assertEquals("第一章 起点", chapters[0].title)
        assertEquals(listOf("内容A1", "内容A2"), chapters[0].paragraphs)
        assertEquals("第二章 发展", chapters[1].title)
        assertEquals(listOf("内容B1"), chapters[1].paragraphs)
    }

    @Test
    fun `supports juan hui and prologue`() {
        val text = """
            第一卷 序章
            前情

            楔子
            引子内容

            第一百零八章 决战
            高潮内容
        """.trimIndent()
        val chapters = ChapterSplitter.splitToParsed(text)
        assertTrue(chapters.size >= 3)
        assertTrue(chapters.any { it.title.contains("楔子") })
        assertTrue(chapters.any { it.title.contains("第一百零八章") })
    }

    @Test
    fun `text without heading becomes one chapter`() {
        val chapters = ChapterSplitter.splitToParsed("只有正文\n没有标题")
        assertEquals(1, chapters.size)
        assertEquals("开始", chapters[0].title)
        assertEquals(listOf("只有正文", "没有标题"), chapters[0].paragraphs)
    }

    @Test
    fun `custom regex is used`() {
        val text = """
            Section 1
            aaa
            Section 2
            bbb
        """.trimIndent()
        val chapters = ChapterSplitter.splitToParsed(text, """^\s*Section\s+\d+.*$""")
        assertEquals(2, chapters.size)
        assertEquals("Section 2", chapters[1].title)
    }

    @Test
    fun `invalid regex falls back to default`() {
        val text = "第一章 甲\n内容"
        val chapters = ChapterSplitter.splitToParsed(text, "[[非法正则")
        assertEquals(1, chapters.size)
        assertEquals("第一章 甲", chapters[0].title)
    }
}
