package com.kovak.moyue.work

import android.content.Context
import com.kovak.moyue.data.db.AppDatabase
import com.kovak.moyue.data.db.Book
import com.kovak.moyue.data.db.ConvertTask
import com.kovak.moyue.format.BookFormat
import com.kovak.moyue.format.ConvertOptions
import com.kovak.moyue.format.ConvertRegistry
import com.kovak.moyue.format.ChapterSplitter
import com.kovak.moyue.io.BookImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 转换任务管理:协程后台执行 + Room 记录 + 通知 + 重试/取消。
 */
class ConvertManager(
    private val context: Context,
    private val db: AppDatabase,
    private val importer: BookImporter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private var lastNotifyAt = 0L

    init {
        scope.launch {
            // 启动时把上次中断的任务标记为失败
            try {
                db.convertDao().cancelAllStale()
            } catch (e: Exception) {
            }
        }
    }

    /** 创建并入队一个转换任务,返回 taskId */
    suspend fun enqueue(book: Book, to: BookFormat, options: ConvertOptions, autoImport: Boolean): Long {
        val from = BookFormat.valueOf(book.format)
        val task = ConvertTask(
            bookId = book.id,
            fromFormat = from.name,
            toFormat = to.name,
            sourcePath = book.sourcePath,
            encodingIn = options.encodingIn,
            encodingOut = options.encodingOut,
            chapterRegex = options.chapterRegex,
            status = ConvertTask.STATUS_PENDING,
        )
        val taskId = db.convertDao().insert(task)
        start(taskId, autoImport)
        return taskId
    }

    private fun start(taskId: Long, autoImport: Boolean) {
        val job = scope.launch {
            val task = db.convertDao().byId(taskId) ?: return@launch
            val book = db.bookDao().byId(task.bookId)
            val title = book?.title ?: "未知书籍"
            runTask(taskId, task.sourcePath, BookFormat.valueOf(task.fromFormat), BookFormat.valueOf(task.toFormat),
                ConvertOptions(task.encodingIn, task.encodingOut, task.chapterRegex.ifBlank { ChapterSplitter.DEFAULT_REGEX }),
                title, autoImport)
        }
        jobs[taskId] = job
        job.invokeOnCompletion { jobs.remove(taskId) }
    }

    private suspend fun runTask(
        taskId: Long,
        sourcePath: String,
        from: BookFormat,
        to: BookFormat,
        options: ConvertOptions,
        title: String,
        autoImport: Boolean,
    ): File? {
        val dao = db.convertDao()
        val source = File(sourcePath)
        if (!source.exists()) {
            dao.update(dao.byId(taskId)!!.copy(status = ConvertTask.STATUS_FAILED, message = "源文件不存在", finishedAt = System.currentTimeMillis()))
            Notifications.done(context, taskId, title, false, "源文件不存在")
            return null
        }
        val converter = ConvertRegistry.find(from, to)
        if (converter == null) {
            dao.update(dao.byId(taskId)!!.copy(status = ConvertTask.STATUS_FAILED, message = "不支持 $from → $to", finishedAt = System.currentTimeMillis()))
            return null
        }
        dao.update(dao.byId(taskId)!!.copy(status = ConvertTask.STATUS_RUNNING, progress = 0f, message = null))
        val outDir = importer.convertedDir
        val outFile = File(outDir, "${UUID.randomUUID()}.${to.ext}")
        try {
            withContext(Dispatchers.IO) {
                converter.convert(source, outFile, options) { p ->
                    val now = System.currentTimeMillis()
                    if (now - lastNotifyAt > 300) {
                        lastNotifyAt = now
                        Notifications.progress(context, taskId, title, (p * 100).toInt())
                        scope.launch { dao.update(dao.byId(taskId)?.copy(progress = p) ?: return@launch) }
                    }
                }
            }
            dao.update(dao.byId(taskId)!!.copy(status = ConvertTask.STATUS_DONE, progress = 1f, outputPath = outFile.absolutePath, finishedAt = System.currentTimeMillis()))
            Notifications.done(context, taskId, title, true, null)
            if (autoImport) {
                val newId = importer.importConverted(outFile, to, title, bookAuthorOf(taskId))
                // 入库后已拷贝到书库,产物文件可清理
                Notifications.cancel(context, taskId)
                importedBookIds[taskId] = newId
            }
            return outFile
        } catch (e: CancellationException) {
            outFile.delete()
            dao.update(dao.byId(taskId)!!.copy(status = ConvertTask.STATUS_CANCELED, finishedAt = System.currentTimeMillis()))
            Notifications.cancel(context, taskId)
            throw e
        } catch (e: Exception) {
            outFile.delete()
            dao.update(dao.byId(taskId)!!.copy(status = ConvertTask.STATUS_FAILED, message = e.message ?: e.javaClass.simpleName, finishedAt = System.currentTimeMillis()))
            Notifications.done(context, taskId, title, false, e.message)
            return null
        }
    }

    private val importedBookIds = ConcurrentHashMap<Long, Long>()

    private suspend fun bookAuthorOf(taskId: Long): String =
        db.convertDao().byId(taskId)?.let { db.bookDao().byId(it.bookId)?.author } ?: ""

    /** 供「自动转换」流程等待结果:返回入库后的新书 id(仅 autoImport 时有效) */
    fun importedBookIdOf(taskId: Long): Long? = importedBookIds[taskId]

    fun retry(taskId: Long) {
        scope.launch {
            val task = db.convertDao().byId(taskId) ?: return@launch
            db.convertDao().update(task.copy(status = ConvertTask.STATUS_PENDING, progress = 0f, message = null, finishedAt = null))
            start(taskId, false)
        }
    }

    fun cancel(taskId: Long) {
        jobs[taskId]?.cancel()
        scope.launch {
            val task = db.convertDao().byId(taskId) ?: return@launch
            if (task.status == ConvertTask.STATUS_PENDING || task.status == ConvertTask.STATUS_RUNNING) {
                db.convertDao().update(task.copy(status = ConvertTask.STATUS_CANCELED, finishedAt = System.currentTimeMillis()))
                Notifications.cancel(context, taskId)
            }
        }
    }

    fun deleteTask(taskId: Long) {
        jobs[taskId]?.cancel()
        scope.launch {
            val task = db.convertDao().byId(taskId) ?: return@launch
            task.outputPath?.let { path ->
                // 仅删除不在书库内的产物文件
                if (!path.contains(importer.booksDir.name)) {
                    try { File(path).delete() } catch (e: Exception) {}
                }
            }
            db.convertDao().delete(taskId)
        }
    }

    /**
     * 「打开方式」自动转换流程:转换源书并等待完成,成功后自动入库。
     * 返回入书 id,失败返回 null。
     */
    suspend fun convertAndWaitImport(book: Book, to: BookFormat, options: ConvertOptions): Long? =
        withContext(Dispatchers.IO) {
            val from = BookFormat.valueOf(book.format)
            val task = ConvertTask(
                bookId = book.id,
                fromFormat = from.name,
                toFormat = to.name,
                sourcePath = book.sourcePath,
                encodingIn = options.encodingIn,
                encodingOut = options.encodingOut,
                chapterRegex = options.chapterRegex,
            )
            val taskId = db.convertDao().insert(task)
            val deferred = kotlinx.coroutines.CompletableDeferred<Long?>()
            val job = scope.launch {
                val out = runTask(taskId, book.sourcePath, from, to, options, book.title, false)
                if (out != null) {
                    try {
                        deferred.complete(importer.importConverted(out, to, book.title, book.author))
                    } catch (e: Exception) {
                        deferred.complete(null)
                    }
                } else {
                    deferred.complete(null)
                }
            }
            jobs[taskId] = job
            job.invokeOnCompletion { jobs.remove(taskId) }
            deferred.await()
        }
}
