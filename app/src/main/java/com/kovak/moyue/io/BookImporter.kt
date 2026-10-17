package com.kovak.moyue.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kovak.moyue.data.db.AppDatabase
import com.kovak.moyue.data.db.Book
import com.kovak.moyue.format.BookFormat
import com.kovak.moyue.format.EpubReader
import com.kovak.moyue.format.ParsedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 书籍导入:拷贝到私有目录 -> 解析元数据 -> 统计章节 -> 入库。
 */
class BookImporter(
    private val context: Context,
    private val db: AppDatabase,
) {
    val booksDir: File get() = File(context.filesDir, "books").apply { mkdirs() }
    val convertedDir: File get() = File(context.filesDir, "converted").apply { mkdirs() }
    val fontsDir: File get() = File(context.filesDir, "fonts").apply { mkdirs() }
    val tmpDir: File get() = File(context.cacheDir, "import").apply { mkdirs() }

    /** 从内容 URI 导入。返回 bookId;同 sourcePath 已存在则返回已有 id。 */
    suspend fun importFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri) ?: "book.${BookFormat.TXT.ext}"
        val tmp = File(tmpDir, "in_${UUID.randomUUID()}_$name")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("无法读取所选文件")
            importFromFile(tmp, name)
        } finally {
            tmp.delete()
        }
    }

    /** 从本地文件导入(name 为原始显示名)。 */
    suspend fun importFromFile(file: File, displayName: String): Long = withContext(Dispatchers.IO) {
        val format = BookFormat.fromExt(displayName)
            ?: BookFormat.fromExt(file.name)
            ?: sniffFormat(file)
            ?: BookFormat.TXT

        val meta = parseMeta(file, format, displayName)

        // 去重:同路径已导入过
        val storedPath = storeSource(file, format)
        db.bookDao().bySourcePath(storedPath)?.let { existing ->
            if (existing.title != meta.first || existing.author != meta.second) {
                db.bookDao().update(existing.copy(title = meta.first, author = meta.second))
            }
            return@withContext existing.id
        }

        var totalChars = 0L
        var chapterCount = 0
        try {
            val parsed = quickParse(file, format)
            totalChars = parsed.totalChars
            chapterCount = parsed.chapters.size
        } catch (e: Exception) {
            // 统计失败不阻断导入
        }

        val book = Book(
            title = meta.first,
            author = meta.second,
            format = format.name,
            sourcePath = storedPath,
            coverColor = (meta.first.hashCode().ushr(1)) % 6,
            totalChars = totalChars,
            chapterCount = chapterCount,
        )
        db.bookDao().insert(book)
    }

    /** 转换产物直接入库为一本新书 */
    suspend fun importConverted(file: File, format: BookFormat, title: String, author: String): Long =
        withContext(Dispatchers.IO) {
            val storedPath = storeSource(file, format)
            var totalChars = 0L
            var chapterCount = 0
            try {
                val parsed = quickParse(file, format)
                totalChars = parsed.totalChars
                chapterCount = parsed.chapters.size
            } catch (e: Exception) {
            }
            db.bookDao().insert(
                Book(
                    title = title,
                    author = author,
                    format = format.name,
                    sourcePath = storedPath,
                    coverColor = (title.hashCode().ushr(1)) % 6,
                    totalChars = totalChars,
                    chapterCount = chapterCount,
                ),
            )
        }

    private fun storeSource(file: File, format: BookFormat): String {
        val dest = File(booksDir, "${UUID.randomUUID()}.${format.ext}")
        if (file.absolutePath != dest.absolutePath) {
            if (file.parentFile == booksDir) {
                // 已在书库目录(如转换产物),直接改名
                if (!file.renameTo(dest)) file.copyTo(dest, overwrite = true)
            } else {
                file.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
        }
        return dest.absolutePath
    }

    private fun parseMeta(file: File, format: BookFormat, displayName: String): Pair<String, String> {
        val baseName = displayName.substringBeforeLast('.').ifBlank { file.nameWithoutExtension }
        return when (format) {
            BookFormat.EPUB -> try {
                val meta = EpubReader.parseMeta(file)
                (meta.title.ifBlank { baseName }) to meta.author
            } catch (e: Exception) {
                baseName to ""
            }
            BookFormat.MOBI -> try {
                val parsed = MobiQuick.parseTitle(file)
                (parsed.first.ifBlank { baseName }) to parsed.second
            } catch (e: Exception) {
                baseName to ""
            }
            BookFormat.TXT -> {
                // "书名 - 作者.txt" 约定
                val parts = baseName.split(" - ", "——")
                if (parts.size >= 2) parts[0].trim() to parts[1].trim() else baseName to ""
            }
        }
    }

    private fun quickParse(file: File, format: BookFormat): ParsedBook = when (format) {
        BookFormat.EPUB -> EpubReader.parse(file)
        BookFormat.MOBI -> com.kovak.moyue.format.MobiReader.parse(file)
        BookFormat.TXT -> {
            val text = com.kovak.moyue.format.decodeText(file.readBytes(), com.kovak.moyue.format.EncodingDetector.AUTO)
            ParsedBook(file.nameWithoutExtension, "", com.kovak.moyue.format.ChapterSplitter.splitToParsed(text))
        }
    }

    private fun sniffFormat(file: File): BookFormat? = try {
        file.inputStream().use { input ->
            val head = ByteArray(64)
            val n = input.read(head)
            if (n >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) BookFormat.EPUB else null
        }
    } catch (e: Exception) {
        null
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment
        } else {
            uri.lastPathSegment
        }
    } catch (e: Exception) {
        uri.lastPathSegment
    }

    private object MobiQuick {
        /** 快速读取 MOBI 书名/作者,失败抛异常 */
        fun parseTitle(file: File): Pair<String, String> {
            val bytes = file.readBytes()
            if (bytes.size < 132) throw IllegalArgumentException("文件过小")
            if (String(bytes, 60, 4, Charsets.US_ASCII) != "BOOK") throw IllegalArgumentException("非 MOBI")
            val numRecords = ((bytes[76].toInt() and 0xFF) shl 8) or (bytes[77].toInt() and 0xFF)
            if (numRecords < 1) throw IllegalArgumentException("无记录")
            val rec0Start = b32(bytes, 78).toInt()
            val rec0End = if (numRecords > 1) b32(bytes, 86).toInt() else bytes.size
            val rec0 = bytes.copyOfRange(rec0Start, rec0End)
            if (rec0.size < 92 || String(rec0, 16, 4, Charsets.US_ASCII) != "MOBI") throw IllegalArgumentException("非 MOBI")
            val nameOff = b32(rec0, 84).toInt()
            val nameLen = b32(rec0, 88).toInt()
            val title = if (nameOff in rec0.indices && nameOff + nameLen <= rec0.size) {
                String(rec0, nameOff, nameLen, Charsets.UTF_16BE).trim()
            } else ""
            var author = ""
            try {
                val headerLen = b32(rec0, 20).toInt()
                val exthOff = 16 + headerLen
                if (exthOff + 12 <= rec0.size && String(rec0, exthOff, 4, Charsets.US_ASCII) == "EXTH") {
                    var p = exthOff + 12
                    val count = b32(rec0, exthOff + 8).toInt()
                    repeat(count) {
                        if (p + 8 > rec0.size) return@repeat
                        val type = b32(rec0, p).toInt()
                        val len = b32(rec0, p + 4).toInt()
                        if (len < 8 || p + len > rec0.size) return@repeat
                        if (type == 100 && author.isEmpty()) {
                            author = String(rec0, p + 8, len - 8, Charsets.UTF_8).trim()
                        }
                        p += len
                    }
                }
            } catch (e: Exception) {
            }
            return title to author
        }

        private fun b32(b: ByteArray, off: Int): Long =
            ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
                ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)
    }
}
