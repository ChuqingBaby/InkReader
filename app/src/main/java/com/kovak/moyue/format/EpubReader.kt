package com.kovak.moyue.format

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * EPUB 解析:container.xml -> OPF -> manifest/spine -> 逐文档提取正文。
 * 用正则解析(输入是我们自己或标准工具产出的规范 XML),保持 JVM 纯净可单测。
 */
object EpubReader {

    data class EpubMeta(val title: String, val author: String, val spineHrefs: List<String>, val navHref: String?, val ncxHref: String?)

    fun parseMeta(file: File): EpubMeta = ZipFile(file).use { zip ->
        val container = readEntry(zip, "META-INF/container.xml")
            ?: throw IllegalArgumentException("不是有效的 EPUB:缺少 container.xml")
        val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(container)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("container.xml 中缺少 rootfile")
        val opf = readEntry(zip, opfPath) ?: throw IllegalArgumentException("缺少 OPF: $opfPath")
        val base = opfPath.substringBeforeLast('/', "")

        val title = Regex("<dc:title[^>]*>([\\s\\S]*?)</dc:title>").find(opf)?.groupValues?.get(1)?.trim().orEmpty()
        val author = Regex("<dc:creator[^>]*>([\\s\\S]*?)</dc:creator>").find(opf)?.groupValues?.get(1)?.trim().orEmpty()

        // manifest: id -> href
        val manifest = Regex("<item\\s[^>]*>").findAll(opf).mapNotNull { tag ->
            val t = tag.value
            val id = Regex("id\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1) ?: return@mapNotNull null
            val href = Regex("href\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1) ?: return@mapNotNull null
            val props = Regex("properties\\s*=\\s*\"([^\"]+)\"").find(t)?.groupValues?.get(1).orEmpty()
            id to Triple(href, props, t)
        }.toMap()

        val navHref = manifest.values.firstOrNull { it.second.contains("nav") }?.first
        val ncxId = Regex("<spine[^>]*toc\\s*=\\s*\"([^\"]+)\"").find(opf)?.groupValues?.get(1)
        val ncxHref = ncxId?.let { manifest[it]?.first }

        val spineIds = Regex("<itemref\\s[^>]*idref\\s*=\\s*\"([^\"]+)\"").findAll(opf)
            .map { it.groupValues[1] }.toList()
        val spineHrefs = spineIds.mapNotNull { id -> manifest[id]?.first }

        EpubMeta(
            title = title,
            author = author,
            spineHrefs = spineHrefs.map { resolvePath(base, it) },
            navHref = navHref?.let { resolvePath(base, it) },
            ncxHref = ncxHref?.let { resolvePath(base, it) },
        )
    }

    /** 解析全书为 ParsedBook。 */
    fun parse(file: File): ParsedBook {
        val meta = parseMeta(file)
        ZipFile(file).use { zip ->
            val tocMap = readTocTitles(zip, meta)
            val chapters = ArrayList<ParsedBook.Chapter>()
            for (href in meta.spineHrefs) {
                val entry = zip.getEntry(href) ?: continue
                val html = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                val docTitle = Regex("<h[1-6][^>]*>([\\s\\S]*?)</h[1-6]>").find(html)?.let { plain(it.groupValues[1]) }?.take(60)
                val title = tocMap[href] ?: docTitle ?: href.substringAfterLast('/').removeSuffix(".xhtml").removeSuffix(".html")
                val paragraphs = extractParagraphs(html)
                // 章内首段若与章标题相同(由 <h2> 提取而来),去掉避免重复
                val body = if (paragraphs.firstOrNull() == title) paragraphs.drop(1) else paragraphs
                if (body.isNotEmpty()) chapters.add(ParsedBook.Chapter(title, body))
            }
            if (chapters.isEmpty()) throw IllegalArgumentException("EPUB 中没有可读文本")
            return ParsedBook(meta.title.ifBlank { file.nameWithoutExtension }, meta.author, chapters)
        }
    }

    fun extractParagraphs(html: String): List<String> {
        // 去掉 head/script/style
        var s = html.replace(Regex("(?is)<head[\\s\\S]*?</head>"), " ")
            .replace(Regex("(?is)<(script|style)[\\s\\S]*?</\\1>"), " ")
        // 块级标签转换行
        s = s.replace(Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote)>"), "\n")
            .replace(Regex("(?i)<(br|br/|br\\s*/)>"), "\n")
            .replace(Regex("(?i)<(p|div|h[1-6]|li)[^>]*>"), "\n")
        val text = plain(s)
        return text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 去标签 + 实体解码 */
    fun plain(fragment: String): String {
        val noTags = fragment.replace(Regex("<[^>]*>"), " ")
        return decodeEntities(noTags)
    }

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        var r = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&nbsp;", " ")
            .replace("&ldquo;", "“").replace("&rdquo;", "”").replace("&lsquo;", "‘").replace("&rsquo;", "’")
            .replace("&mdash;", "—").replace("&hellip;", "…")
        // 数字实体
        r = Regex("&#(\\d+);").replace(r) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value }
        r = Regex("&#x([0-9a-fA-F]+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        return r
    }

    private fun readTocTitles(zip: ZipFile, meta: EpubMeta): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            if (meta.navHref != null) {
                zip.getEntry(meta.navHref)?.let { e ->
                    val html = zip.getInputStream(e).readBytes().toString(Charsets.UTF_8)
                    Regex("<a\\s[^>]*href\\s*=\\s*\"([^\"]+)\"[^>]*>([\\s\\S]*?)</a>").findAll(html).forEach { m ->
                        val href = resolvePath(meta.navHref.substringBeforeLast('/'), m.groupValues[1].substringBefore('#'))
                        val title = plain(m.groupValues[2]).trim()
                        if (title.isNotEmpty()) map.putIfAbsent(href, title)
                    }
                }
            }
            if (map.isEmpty() && meta.ncxHref != null) {
                zip.getEntry(meta.ncxHref)?.let { e ->
                    val ncx = zip.getInputStream(e).readBytes().toString(Charsets.UTF_8)
                    Regex("<navPoint[\\s\\S]*?</navPoint>").findAll(ncx).forEach { np ->
                        val src = Regex("src\\s*=\\s*\"([^\"]+)\"").find(np.value)?.groupValues?.get(1) ?: return@forEach
                        val title = Regex("<text>([\\s\\S]*?)</text>").find(np.value)?.let { plain(it.groupValues[1]) }?.trim() ?: return@forEach
                        val href = resolvePath(meta.ncxHref.substringBeforeLast('/'), src.substringBefore('#'))
                        if (title.isNotEmpty()) map.putIfAbsent(href, title)
                    }
                }
            }
        } catch (e: Exception) {
            // 目录解析失败不致命
        }
        return map
    }

    fun resolvePath(baseDir: String, href: String): String {
        if (href.startsWith("/")) return href.removePrefix("/")
        if (baseDir.isBlank()) return href
        val stack = ArrayDeque(baseDir.split('/').filter { it.isNotBlank() })
        for (seg in href.split('/')) {
            when (seg) {
                "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                "" -> {}
                else -> stack.addLast(seg)
            }
        }
        return stack.joinToString("/")
    }

    private fun readEntry(zip: ZipFile, name: String): String? {
        val e = zip.getEntry(name) ?: return null
        return zip.getInputStream(e).readBytes().toString(Charsets.UTF_8)
    }
}
