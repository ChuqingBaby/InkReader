package com.kovak.moyue.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val format: String = "TXT",          // BookFormat.name
    val sourcePath: String,              // 应用私有目录内的源文件
    val groupTag: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val coverColor: Int = 0,             // 占位封面色板索引
    val fileMissing: Boolean = false,    // 备份恢复后文件缺失
    val totalChars: Long = 0,
    val chapterCount: Int = 0,
    val description: String = "",
)

@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val bookId: Long,
    val chapterIndex: Int = 0,
    val paragraphIndex: Int = 0,
    val percent: Float = 0f,
    val totalReadMillis: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val excerpt: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "convert_tasks")
data class ConvertTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val fromFormat: String,
    val toFormat: String,
    val status: String = STATUS_PENDING,  // PENDING/RUNNING/DONE/FAILED/CANCELED
    val progress: Float = 0f,
    val message: String? = null,
    val sourcePath: String,
    val outputPath: String? = null,
    val encodingIn: String = "AUTO",
    val encodingOut: String = "UTF-8",
    val chapterRegex: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELED = "CANCELED"
    }
}

@Entity(tableName = "fonts")
data class FontEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val addedAt: Long = System.currentTimeMillis(),
)
