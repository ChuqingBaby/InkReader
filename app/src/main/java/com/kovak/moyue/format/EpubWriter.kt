package com.kovak.moyue.format

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * EPUB 写出:标准 ZIP + container.xml + OPF + EPUB3 nav + EPUB2 ncx + XHTML 章节。
 * 选型理由:纯手写 java.util.zip,无第三方 epub 库,JVM 可单测,结构完全可控。
 */
object EpubWriter {

    const val MIMETYPE = "application/epub+zip"

    fun write(book: ParsedBook, output: File, onProgress: (Float) -> Unit = {}) {
        val tmp = File(output.parentFile, output.name + ".tmp")
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            // mimetype 必须第一个且 STORED
            val mimeBytes = MIMETYPE.toByteArray(Charsets.US_ASCII)
            val me = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = CRC32().apply { update(mimeBytes) }.value
            }
            zip.putNextEntry(me)
            zip.write(mimeBytes)
            zip.closeEntry()

            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("META-INF/container.xml", containerXml())
            put("OEBPS/style.css", CSS)

            val n = book.chapters.size
            val files = ArrayList<Triple<String, String, Int>>(n)
            book.chapters.forEachIndexed { i, ch ->
                val id = "ch%04d".format(i)
                val href = "chapters/$id.xhtml"
                put("OEBPS/$href", chapterXhtml(ch.title, ch.paragraphs))
                files.add(Triple(id, href, i))
                onProgress((i + 1) * 0.9f / n.coerceAtLeast(1))
            }

            put("OEBPS/nav.xhtml", navXhtml(files.map { it.second to titleOf(book, it.third) }))
            put("OEBPS/toc.ncx", ncx(files.map { it.second to titleOf(book, it.third) }))
            put("OEBPS/content.opf", opf(book, files))
            onProgress(1f)
        }
        if (output.exists()) output.delete()
        if (!tmp.renameTo(output)) {
            tmp.copyTo(output, overwrite = true)
            tmp.delete()
        }
    }

    private fun titleOf(book: ParsedBook, i: Int) = book.chapters[i].title

    private fun containerXml() = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    private fun opf(book: ParsedBook, files: List<Triple<String, String, Int>>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">""")
        append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
        append("""<dc:identifier id="uid">urn:inkreader:${System.nanoTime()}</dc:identifier>""")
        append("""<dc:title>${esc(book.title)}</dc:title>""")
        append("""<dc:creator>${esc(book.author.ifBlank { "未知作者" })}</dc:creator>""")
        append("""<meta property="dcterms:modified">2024-01-01T00:00:00Z</meta>""")
        append("</metadata>")
        append("""<manifest>""")
        append("""<item id="css" href="style.css" media-type="text/css"/>""")
        append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        append("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        files.forEach { (id, href, _) ->
            append("""<item id="$id" href="$href" media-type="application/xhtml+xml"/>""")
        }
        append("</manifest>")
        append("""<spine toc="ncx">""")
        files.forEach { (id, _, _) -> append("""<itemref idref="$id"/>""") }
        append("</spine></package>")
    }

    private fun navXhtml(items: List<Pair<String, String>>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<!DOCTYPE html>""")
        append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">""")
        append("<head><title>目录</title></head><body><nav epub:type=\"toc\" id=\"toc\"><h1>目录</h1><ol>")
        items.forEach { (href, title) ->
            append("""<li><a href="${esc(href)}">${esc(title)}</a></li>""")
        }
        append("</ol></nav></body></html>")
    }

    private fun ncx(items: List<Pair<String, String>>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">""")
        append("<head><meta name=\"dtb:uid\" content=\"urn:inkreader\"/></head>")
        append("<docTitle><text>目录</text></docTitle><navMap>")
        items.forEachIndexed { i, (href, title) ->
            append("""<navPoint id="np$i" playOrder="${i + 1}"><navLabel><text>${esc(title)}</text></navLabel><content src="${esc(href)}"/></navPoint>""")
        }
        append("</navMap></ncx>")
    }

    private fun chapterXhtml(title: String, paragraphs: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<!DOCTYPE html>""")
        append("""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${esc(title)}</title>""")
        append("""<link rel="stylesheet" type="text/css" href="../style.css"/></head>""")
        append("<body>")
        append("<h2>${esc(title)}</h2>")
        paragraphs.forEach { p -> append("<p>${esc(p)}</p>") }
        append("</body></html>")
    }

    fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private const val CSS = """
body { margin: 5% 5%; }
h2 { text-align: center; }
p { text-indent: 2em; line-height: 1.7; margin: 0.4em 0; }
"""
}
