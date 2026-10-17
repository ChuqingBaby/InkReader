package com.kovak.moyue.backup

import android.content.Context
import android.net.Uri
import com.kovak.moyue.data.db.AppDatabase
import com.kovak.moyue.data.db.Book
import com.kovak.moyue.data.db.Bookmark
import com.kovak.moyue.data.db.ReadingProgress
import com.kovak.moyue.io.BookImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 备份/恢复:书架元数据 + 阅读进度 + 书签,导出为 JSON。
 * 恢复时若源文件不存在则标记 fileMissing,提示用户重新导入文件。
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val importer: BookImporter,
) {

    suspend fun exportTo(uri: Uri): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val json = buildJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return@withContext false to "无法写入目标位置"
            true to null
        } catch (e: Exception) {
            false to (e.message ?: "导出失败")
        }
    }

    private suspend fun buildJson(): String {
        val root = JSONObject()
        root.put("app", "InkReader")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val booksArr = JSONArray()
        val books = db.bookDao().observeAll().first()
        val progresses = db.progressDao().observeAll().first()
        val bookmarks = db.bookmarkDao().all()
        val progressByBook = progresses.associateBy { it.bookId }
        for (b in books) {
            val o = JSONObject()
            o.put("title", b.title)
            o.put("author", b.author)
            o.put("format", b.format)
            o.put("sourcePath", b.sourcePath)
            o.put("groupTag", b.groupTag ?: JSONObject.NULL)
            o.put("addedAt", b.addedAt)
            o.put("coverColor", b.coverColor)
            o.put("totalChars", b.totalChars)
            o.put("chapterCount", b.chapterCount)
            progressByBook[b.id]?.let { p ->
                val po = JSONObject()
                po.put("chapterIndex", p.chapterIndex)
                po.put("paragraphIndex", p.paragraphIndex)
                po.put("percent", p.percent.toDouble())
                po.put("totalReadMillis", p.totalReadMillis)
                o.put("progress", po)
            }
            val bmArr = JSONArray()
            bookmarks.filter { it.bookId == b.id }.forEach { bm ->
                val bo = JSONObject()
                bo.put("chapterIndex", bm.chapterIndex)
                bo.put("paragraphIndex", bm.paragraphIndex)
                bo.put("excerpt", bm.excerpt)
                bo.put("createdAt", bm.createdAt)
                bmArr.put(bo)
            }
            o.put("bookmarks", bmArr)
            booksArr.put(o)
        }
        root.put("books", booksArr)
        return root.toString(2)
    }

    /** 返回恢复的书籍数量 */
    suspend fun restoreFrom(uri: Uri): Pair<Int, String?> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext 0 to "无法读取备份文件"
            val root = JSONObject(text)
            if (root.optString("app") != "InkReader") return@withContext 0 to "不是本应用的备份文件"
            val arr = root.optJSONArray("books") ?: return@withContext 0 to "备份内容为空"
            var count = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sourcePath = o.optString("sourcePath")
                val exists = sourcePath.isNotBlank() && File(sourcePath).exists()
                val bookId = db.bookDao().insert(
                    Book(
                        title = o.optString("title", "未知书名"),
                        author = o.optString("author", ""),
                        format = o.optString("format", "TXT"),
                        sourcePath = sourcePath,
                        groupTag = if (o.isNull("groupTag")) null else o.optString("groupTag"),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                        coverColor = o.optInt("coverColor", 0),
                        fileMissing = !exists,
                        totalChars = o.optLong("totalChars", 0),
                        chapterCount = o.optInt("chapterCount", 0),
                    ),
                )
                o.optJSONObject("progress")?.let { p ->
                    db.progressDao().upsert(
                        ReadingProgress(
                            bookId = bookId,
                            chapterIndex = p.optInt("chapterIndex", 0),
                            paragraphIndex = p.optInt("paragraphIndex", 0),
                            percent = p.optDouble("percent", 0.0).toFloat(),
                            totalReadMillis = p.optLong("totalReadMillis", 0),
                        ),
                    )
                }
                o.optJSONArray("bookmarks")?.let { bmArr ->
                    for (j in 0 until bmArr.length()) {
                        val bm = bmArr.getJSONObject(j)
                        db.bookmarkDao().insert(
                            Bookmark(
                                bookId = bookId,
                                chapterIndex = bm.optInt("chapterIndex", 0),
                                paragraphIndex = bm.optInt("paragraphIndex", 0),
                                excerpt = bm.optString("excerpt", ""),
                                createdAt = bm.optLong("createdAt", System.currentTimeMillis()),
                            ),
                        )
                    }
                }
                count++
            }
            count to null
        } catch (e: Exception) {
            0 to (e.message ?: "恢复失败")
        }
    }

    /** 缓存目录大小(字节) */
    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        dirSize(context.cacheDir) + dirSize(importer.tmpDir)
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val before = cacheSizeBytes()
        deleteChildren(context.cacheDir)
        deleteChildren(importer.tmpDir)
        before
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun deleteChildren(dir: File) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        }
    }
}
