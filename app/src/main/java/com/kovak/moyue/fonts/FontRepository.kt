package com.kovak.moyue.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.kovak.moyue.R
import com.kovak.moyue.data.db.AppDatabase
import com.kovak.moyue.data.db.FontEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 字体管理:
 * - 内置:思源黑体 / 思源宋体(子集化,src/main/res/font,来源 Adobe/Google,SIL OFL 1.1)
 * - 自定义:用户导入 ttf/otf,存私有 fonts 目录 + Room 记录
 */
class FontRepository(private val context: Context, private val db: AppDatabase) {

    companion object {
        const val SANS = "builtin:sans"
        const val SERIF = "builtin:serif"

        val BUILTIN_LABELS = mapOf(SANS to "思源黑体 (Source Han Sans)", SERIF to "思源宋体 (Source Han Serif)")
        const val LICENSE_NOTE =
            "内置字体:思源黑体/思源宋体(Source Han Sans/Serif SC)," +
                "版权所有 Adobe & Google,以 SIL Open Font License 1.1 授权使用。" +
                "子集化版本由本项目生成,仅保留常用汉字区间。"
    }

    val fontsDir: File get() = File(context.filesDir, "fonts").apply { mkdirs() }

    data class FontItem(val id: String, val name: String, val isBuiltin: Boolean, val file: File?)

    fun observeAll(): Flow<List<FontItem>> = db.fontDao().observeAll().map { list ->
        listOf(
            FontItem(SANS, BUILTIN_LABELS[SANS] ?: "思源黑体", true, null),
            FontItem(SERIF, BUILTIN_LABELS[SERIF] ?: "思源宋体", true, null),
        ) + list.map { FontItem("custom:" + it.id, it.name, false, File(it.path)) }
    }

    suspend fun import(uri: Uri, displayName: String?): Boolean = withContext(Dispatchers.IO) {
        val ext = displayName?.substringAfterLast('.', "")?.lowercase()
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
        if (ext != "ttf" && ext != "otf") return@withContext false
        val name = (displayName ?: uri.lastPathSegment ?: "自定义字体")
            .substringBeforeLast('.')
            .ifBlank { "自定义字体" }
        val dest = File(fontsDir, "${UUID.randomUUID()}.$ext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return@withContext false
        } catch (e: Exception) {
            dest.delete()
            return@withContext false
        }
        // 校验能加载
        val tf = try {
            Typeface.createFromFile(dest)
        } catch (e: Exception) {
            dest.delete()
            return@withContext false
        }
        if (tf == null) {
            dest.delete()
            return@withContext false
        }
        db.fontDao().insert(FontEntity(name = name, path = dest.absolutePath))
        true
    }

    suspend fun delete(item: FontItem) {
        if (item.isBuiltin) return
        val id = item.id.removePrefix("custom:").toLongOrNull() ?: return
        item.file?.delete()
        db.fontDao().delete(id)
    }

    /** android.graphics.Typeface,分页测量用 */
    suspend fun androidTypeface(fontId: String): Typeface? = try {
        when {
            fontId == SERIF -> ResourcesCompat.getFont(context, R.font.noto_serif_sc)
            fontId == SANS -> ResourcesCompat.getFont(context, R.font.noto_sans_sc)
            fontId.startsWith("custom:") -> {
                val f = fileOf(fontId)
                if (f?.exists() == true) Typeface.createFromFile(f) else null
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private suspend fun fileOf(fontId: String): File? {
        val id = fontId.removePrefix("custom:").toLongOrNull() ?: return null
        val e = db.fontDao().byId(id) ?: return null
        return File(e.path)
    }

    /** Compose FontFamily */
    suspend fun composeFamily(fontId: String): FontFamily = when {
        fontId == SERIF -> FontFamily(Font(R.font.noto_serif_sc))
        fontId == SANS -> FontFamily(Font(R.font.noto_sans_sc))
        fontId.startsWith("custom:") -> {
            val f = fileOf(fontId)
            if (f != null && f.exists()) {
                try {
                    FontFamily(Typeface.createFromFile(f))
                } catch (e: Exception) {
                    FontFamily.Default
                }
            } else FontFamily.Default
        }
        else -> FontFamily.Default
    }
}
