package com.kovak.moyue.io

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书籍文件扫描:
 * 1) SAF 目录树(主力,Android 13+ 也可用):递归扫描用户选择的目录;
 * 2) MediaStore 兜底(32 及以下或已授权时能列出公共 Download/Documents)。
 */
object StorageScanner {

    private val EXTS = setOf("txt", "epub", "mobi")

    data class ScannedFile(val uri: Uri, val name: String, val size: Long, val source: String)

    /** 递归扫描 SAF 目录树(深度限制 4 层,数量限制 500 个) */
    fun scanTree(context: Context, treeUri: Uri): List<ScannedFile> {
        val result = ArrayList<ScannedFile>()
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return result
            walk(root, result, 0)
        } catch (e: Exception) {
        }
        return result
    }

    private fun walk(dir: DocumentFile, out: MutableList<ScannedFile>, depth: Int) {
        if (depth > 4 || out.size > 500) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return
        }
        for (f in children) {
            if (out.size > 500) return
            if (f.isDirectory) {
                walk(f, out, depth + 1)
            } else {
                val name = f.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in EXTS && f.length() > 0) {
                    out.add(ScannedFile(f.uri, name, f.length(), "SAF"))
                }
            }
        }
    }

    /** MediaStore 兜底扫描:能读到多少算多少,失败静默 */
    fun scanMediaStore(context: Context): List<ScannedFile> {
        val result = ArrayList<ScannedFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
        )
        val like = EXTS.joinToString(" OR ") { "${MediaStore.Files.FileColumns.DATA} LIKE ?" }
        val args = EXTS.flatMap { listOf("%." + it.uppercase(), "%." + it) }
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection, "($like)", args.toTypedArray(), null,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                while (c.moveToNext()) {
                    val data = c.getString(dataCol) ?: continue
                    val ext = data.substringAfterLast('.', "").lowercase()
                    if (ext !in EXTS) continue
                    val id = c.getLong(idCol)
                    val uri = Uri.parse("content://media/external/file/$id")
                    result.add(ScannedFile(uri, c.getString(nameCol) ?: data, c.getLong(sizeCol), "媒体库"))
                }
            }
        } catch (e: Exception) {
        }
        return result
    }
}
