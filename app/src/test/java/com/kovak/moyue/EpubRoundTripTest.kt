package com.kovak.moyue

import com.kovak.moyue.format.EpubReader
import com.kovak.moyue.format.EpubWriter
import com.kovak.moyue.format.ParsedBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sampleBook() = ParsedBook(
        title = "测试之书",
        author = "测试作者",
        chapters = listOf(
            ParsedBook.Chapter("第一章 起点", listOf("这是第一段正文。", "这是第二段<带>特殊&符号。")),
            ParsedBook.Chapter("第二章 发展", listOf("第二章的内容,引用“引号”测试。")),
        ),
    )

    @Test
    fun `write then read preserves chapters and text`() {
        val src = sampleBook()
        val file = tmp.newFile("out.epub")
        EpubWriter.write(src, file)
        assertTrue(file.length() > 0)

        val parsed = EpubReader.parse(file)
        assertEquals("测试之书", parsed.title)
        assertEquals("测试作者", parsed.author)
        assertEquals(2, parsed.chapters.size)
        assertEquals("第一章 起点", parsed.chapters[0].title)
        assertEquals("这是第一段正文。", parsed.chapters[0].paragraphs[0])
        assertEquals("这是第二段<带>特殊&符号。", parsed.chapters[0].paragraphs[1])
        assertEquals("第二章的内容,引用“引号”测试。", parsed.chapters[1].paragraphs[0])
    }

    @Test
    fun `parseMeta reads title and author`() {
        val file = tmp.newFile("meta.epub")
        EpubWriter.write(sampleBook(), file)
        val meta = EpubReader.parseMeta(file)
        assertEquals("测试之书", meta.title)
        assertEquals("测试作者", meta.author)
        assertEquals(2, meta.spineHrefs.size)
    }

    @Test
    fun `mimetype is first stored entry`() {
        val file = tmp.newFile("m.epub")
        EpubWriter.write(sampleBook(), file)
        java.util.zip.ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            assertEquals("mimetype", entries[0].name)
            val content = zip.getInputStream(entries[0]).readBytes().toString(Charsets.US_ASCII)
            assertEquals("application/epub+zip", content)
            // OPF / nav / ncx 存在
            assertTrue(entries.any { it.name == "OEBPS/content.opf" })
            assertTrue(entries.any { it.name == "OEBPS/nav.xhtml" })
            assertTrue(entries.any { it.name == "OEBPS/toc.ncx" })
        }
    }

    @Test
    fun `entity decoding`() {
        assertEquals("&<一AA", EpubReader.decodeEntities("&amp;&lt;&#x4E00;&#65;&#x41;"))
        assertEquals("中文“引号”", EpubReader.decodeEntities("中文&ldquo;引号&rdquo;"))
    }

    @Test
    fun `paragraph extraction from html`() {
        val html = "<html><head><style>p{}</style></head><body><h2>标题</h2><p>第一段</p><p>第二段</p><script>x</script></body></html>"
        val paras = EpubReader.extractParagraphs(html)
        assertEquals(listOf("标题", "第一段", "第二段"), paras)
    }

    @Test
    fun `path resolution`() {
        assertEquals("OEBPS/style.css", EpubReader.resolvePath("OEBPS/Text", "../style.css"))
        assertEquals("OEBPS/ch1.xhtml", EpubReader.resolvePath("OEBPS", "ch1.xhtml"))
        assertEquals("OEBPS/Text/ch1.xhtml", EpubReader.resolvePath("OEBPS/Text", "../Text/ch1.xhtml"))
    }
}
