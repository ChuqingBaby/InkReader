package com.kovak.moyue.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kovak.moyue.AppContainer
import com.kovak.moyue.data.AppSettings
import com.kovak.moyue.data.SortMode
import com.kovak.moyue.data.db.Book
import com.kovak.moyue.data.db.ReadingProgress
import com.kovak.moyue.format.BookFormat
import com.kovak.moyue.format.ConvertOptions
import com.kovak.moyue.format.EncodingDetector
import com.kovak.moyue.io.Downloader
import com.kovak.moyue.io.StorageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShelfViewModel(private val app: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val books: StateFlow<List<Book>> = app.db.bookDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<List<ReadingProgress>> = app.db.progressDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val query = MutableStateFlow("")
    val selection = MutableStateFlow<Set<Long>>(emptySet())

    // 导入状态
    val importingCount = MutableStateFlow(0)
    val lastImportError = MutableStateFlow<String?>(null)
    val urlProgress = MutableStateFlow<Float?>(null)
    val urlError = MutableStateFlow<String?>(null)
    val scanResults = MutableStateFlow<List<StorageScanner.ScannedFile>?>(null)
    val scanSelected = MutableStateFlow<Set<Uri>>(emptySet())
    val scanning = MutableStateFlow(false)

    val filteredBooks: StateFlow<List<Book>> = combine(books, query, settings) { list, q, s ->
        val base = if (q.isBlank()) list else list.filter {
            it.title.contains(q, true) || it.author.contains(q, true)
        }
        val sorted = when (s.sortMode) {
            SortMode.RECENT -> base.sortedByDescending { it.lastReadAt ?: it.addedAt }
            SortMode.ADDED -> base.sortedByDescending { it.addedAt }
            SortMode.TITLE -> base.sortedBy { it.title }
        }
        sorted
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groups: StateFlow<List<String>> = books.map { list ->
        list.mapNotNull { it.groupTag }.distinct()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeGroup = MutableStateFlow<String?>(null)

    val visibleBooks: StateFlow<List<Book>> = combine(filteredBooks, activeGroup) { list, group ->
        if (group == null) list else list.filter { it.groupTag == group }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(q: String) { query.value = q }
    fun setSort(m: SortMode) = launch { app.settings.setSortMode(m) }
    fun toggleView() = launch {
        app.settings.setShelfView(if (settings.value.shelfView == "GRID") "LIST" else "GRID")
    }
    fun setActiveGroup(g: String?) { activeGroup.value = g }

    fun toggleSelect(id: Long) {
        selection.value = selection.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }
    fun clearSelection() { selection.value = emptySet() }

    fun openBook(book: Book) = launch { app.db.bookDao().touchLastRead(book.id, System.currentTimeMillis()) }

    // ---------- 导入 ----------

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        launch {
            importingCount.value = uris.size
            var lastErr: String? = null
            for (u in uris) {
                try {
                    app.importer.importFromUri(u)
                } catch (e: Exception) {
                    lastErr = "${u.lastPathSegment ?: "文件"}:${e.message ?: "导入失败"}"
                }
                importingCount.value = importingCount.value - 1
            }
            lastImportError.value = lastErr
        }
    }

    fun scanTree(uri: Uri?) {
        if (uri == null) return
        launch {
            scanning.value = true
            val result = withContext(Dispatchers.IO) { StorageScanner.scanTree(app.appContext, uri) }
            scanning.value = false
            if (result.isEmpty()) {
                // SAF 扫不到时用媒体库兜底
                scanResults.value = withContext(Dispatchers.IO) { StorageScanner.scanMediaStore(app.appContext) }
            } else {
                scanResults.value = result
            }
            scanSelected.value = scanResults.value?.map { it.uri }?.toSet() ?: emptySet()
        }
    }

    fun scanMedia() {
        launch {
            scanning.value = true
            val result = withContext(Dispatchers.IO) { StorageScanner.scanMediaStore(app.appContext) }
            scanning.value = false
            scanResults.value = result
            scanSelected.value = result.map { it.uri }.toSet()
        }
    }

    fun toggleScanSelect(uri: Uri) {
        scanSelected.value = scanSelected.value.toMutableSet().apply { if (!add(uri)) remove(uri) }
    }

    fun dismissScan() {
        scanResults.value = null
        scanSelected.value = emptySet()
    }

    fun importScanned() {
        val uris = scanSelected.value.toList()
        dismissScan()
        importUris(uris)
    }

    private val downloader = Downloader()

    fun downloadUrl(url: String) {
        launch {
            urlError.value = null
            urlProgress.value = 0f
            try {
                val result = downloader.download(url, File(app.appContext.cacheDir, "download")) { p ->
                    urlProgress.value = p
                }
                if (result.kind == Downloader.Kind.FILE) {
                    app.importer.importFromFile(result.file, result.displayName)
                } else {
                    // 网页正文 -> 以 TXT 入库
                    app.importer.importFromFile(result.file, result.displayName)
                }
                urlProgress.value = null
            } catch (e: Exception) {
                urlProgress.value = null
                urlError.value = e.message ?: "下载失败"
            }
        }
    }

    fun clearUrlError() { urlError.value = null }

    // ---------- 多选操作 ----------

    fun deleteSelected() {
        val ids = selection.value
        launch {
            for (id in ids) {
                val b = app.db.bookDao().byId(id) ?: continue
                try { File(b.sourcePath).delete() } catch (e: Exception) {}
                app.db.bookmarkDao().deleteByBook(id)
                app.db.progressDao().delete(id)
                app.db.bookDao().delete(id)
            }
            selection.value = emptySet()
        }
    }

    fun groupSelected(tag: String?) {
        val ids = selection.value
        launch {
            for (id in ids) {
                val b = app.db.bookDao().byId(id) ?: continue
                app.db.bookDao().update(b.copy(groupTag = tag?.takeIf { it.isNotBlank() }))
            }
            selection.value = emptySet()
        }
    }

    fun renameSelected(title: String) {
        val ids = selection.value
        if (ids.size != 1) return
        launch {
            val b = app.db.bookDao().byId(ids.first()) ?: return@launch
            app.db.bookDao().update(b.copy(title = title))
            selection.value = emptySet()
        }
    }

    /** 多选转换:仅对单本生效(逐本入队也可,这里支持多本) */
    fun convertSelected(target: BookFormat) {
        val ids = selection.value
        launch {
            for (id in ids) {
                val b = app.db.bookDao().byId(id) ?: continue
                if (BookFormat.valueOf(b.format) == target) continue
                val s = settings.value
                app.convertManager.enqueue(
                    b, target,
                    ConvertOptions(s.defaultEncoding, "UTF-8", s.chapterRegex.ifBlank { com.kovak.moyue.format.ChapterSplitter.DEFAULT_REGEX }),
                    autoImport = true,
                )
            }
            selection.value = emptySet()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
