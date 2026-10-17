package com.kovak.moyue.reader

import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kovak.moyue.AppContainer
import com.kovak.moyue.data.AppSettings
import com.kovak.moyue.data.db.Bookmark
import com.kovak.moyue.data.db.ReadingProgress
import com.kovak.moyue.format.BookFormat
import com.kovak.moyue.format.ChapterSplitter
import com.kovak.moyue.format.EpubReader
import com.kovak.moyue.format.MobiReader
import com.kovak.moyue.format.ParsedBook
import com.kovak.moyue.format.decodeText
import com.kovak.moyue.format.EncodingDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(kotlinx.coroutines.FlowPreview::class)
class ReaderViewModel(
    private val app: AppContainer,
    val bookId: Long,
) : ViewModel() {

    private val _content = MutableStateFlow<BookContent?>(null)
    val content: StateFlow<BookContent?> = _content.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _book = MutableStateFlow<com.kovak.moyue.data.db.Book?>(null)
    val book: StateFlow<com.kovak.moyue.data.db.Book?> = _book.asStateFlow()

    val anchor = MutableStateFlow(0)

    private val viewport = MutableStateFlow<Viewport?>(null)
    data class Viewport(val width: Float, val height: Float, val fontPx: Float, val lineSpacing: Float, val paraSpacingPx: Float)

    val settings: StateFlow<AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val fonts: StateFlow<List<com.kovak.moyue.fonts.FontRepository.FontItem>> = app.fonts.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 音量键翻页事件:-1 上一页,+1 下一页 */
    val turnBus = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 4)

    fun onVolumeKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> { turnBus.tryEmit(-1); true }
        KeyEvent.KEYCODE_VOLUME_DOWN -> { turnBus.tryEmit(1); true }
        else -> false
    }

    val style: StateFlow<ReaderStyle> = settings.map { s ->
        ReaderStyle(
            fontSizeSp = s.fontSizeSp,
            lineSpacing = s.lineSpacing,
            paraSpacingDp = s.paraSpacingDp,
            hMarginDp = s.hMarginDp,
            vMarginDp = s.vMarginDp,
            theme = s.readerTheme,
            pageMode = s.pageMode,
            justify = s.justify,
            fontFamily = app.fonts.composeFamily(s.fontId),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultStyle())

    private fun defaultStyle() = ReaderStyle(
        20, 1.6f, 10, 24, 32,
        com.kovak.moyue.data.ReaderTheme.PAPER,
        com.kovak.moyue.data.PageMode.SLIDE,
        true,
        androidx.compose.ui.text.font.FontFamily.Default,
    )

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val pages: StateFlow<List<Page>?> = combine(_content, viewport, settings) { c, vp, s ->
        if (c == null || vp == null) null
        else withContext(Dispatchers.Default) {
            val tf = app.fonts.androidTypeface(s.fontId)
            TextLayouter(tf).paginate(
                content = c,
                widthPx = vp.width,
                heightPx = vp.height,
                fontPx = vp.fontPx,
                lineSpacing = vp.lineSpacing,
                paraSpacingPx = vp.paraSpacingPx,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bookmarks: StateFlow<List<Bookmark>> = app.db.bookmarkDao().observeByBook(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var sessionStart = SystemClock.elapsedRealtime()

    init {
        load()
        // 进度防抖保存
        viewModelScope.launch {
            anchor.debounce(500).distinctUntilChanged().collect { a ->
                saveProgress(a)
            }
        }
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val b = app.db.bookDao().byId(bookId)
            if (b == null) {
                _error.value = "书籍不存在"
                return@launch
            }
            _book.value = b
            val f = File(b.sourcePath)
            if (!f.exists()) {
                _error.value = "书籍文件缺失,请重新导入"
                return@launch
            }
            try {
                val regex = settings.value.chapterRegex.ifBlank { ChapterSplitter.DEFAULT_REGEX }
                val parsed: ParsedBook = when (BookFormat.valueOf(b.format)) {
                    BookFormat.EPUB -> EpubReader.parse(f)
                    BookFormat.MOBI -> MobiReader.parse(f, regex)
                    BookFormat.TXT -> {
                        val text = decodeText(f.readBytes(), EncodingDetector.AUTO)
                        ParsedBook(b.title, b.author, ChapterSplitter.splitToParsed(text, regex))
                    }
                }
                val c = BookContent(parsed)
                _content.value = c
                val prog = app.db.progressDao().byBook(bookId)
                anchor.value = if (prog != null && c.paras.isNotEmpty()) {
                    c.toGlobalIndex(prog.chapterIndex, prog.paragraphIndex)
                } else 0
            } catch (e: Exception) {
                _error.value = "解析失败:${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private suspend fun saveProgress(a: Int) {
        val c = _content.value ?: return
        if (c.paras.isEmpty()) return
        val (ch, para) = c.toChapterPosition(a)
        val prev = app.db.progressDao().byBook(bookId)
        val percent = c.percentOf(a)
        app.db.progressDao().upsert(
            ReadingProgress(
                bookId = bookId,
                chapterIndex = ch,
                paragraphIndex = para,
                percent = percent,
                totalReadMillis = prev?.totalReadMillis ?: 0,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        app.db.bookDao().touchLastRead(bookId, System.currentTimeMillis())
    }

    fun setViewport(vp: Viewport) {
        val old = viewport.value
        if (old != vp) viewport.value = vp
    }

    fun jumpTo(para: Int) {
        val c = _content.value ?: return
        anchor.value = para.coerceIn(0, (c.paras.size - 1).coerceAtLeast(0))
        jumpTick.value = jumpTick.value + 1
    }

    val jumpTick = MutableStateFlow(0)

    fun percentToAnchor(p: Float): Int {
        val c = _content.value ?: return 0
        return c.anchorForPercent(p)
    }

    fun percentOf(anchor: Int): Float {
        val c = _content.value ?: return 0f
        return c.percentOf(anchor)
    }

    data class SearchHit(val para: Int, val chapterTitle: String, val snippet: String)

    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.Default) {
        val c = _content.value ?: return@withContext emptyList()
        if (query.isBlank()) return@withContext emptyList()
        val hits = ArrayList<SearchHit>()
        for (i in c.paras.indices) {
            val p = c.paras[i]
            if (p.isTitle) continue
            val idx = p.text.indexOf(query)
            if (idx >= 0) {
                val from = (idx - 12).coerceAtLeast(0)
                val to = (idx + query.length + 20).coerceAtMost(p.text.length)
                hits.add(
                    SearchHit(
                        i,
                        c.chapterTitles.getOrElse(p.chapter) { "" },
                        (if (from > 0) "…" else "") + p.text.substring(from, to) + (if (to < p.text.length) "…" else ""),
                    ),
                )
                if (hits.size >= 200) break
            }
        }
        hits
    }

    fun addBookmark() {
        val c = _content.value ?: return
        val a = anchor.value.coerceIn(0, c.paras.size - 1)
        viewModelScope.launch {
            app.db.bookmarkDao().insert(
                Bookmark(
                    bookId = bookId,
                    chapterIndex = c.paras[a].chapter,
                    paragraphIndex = a,
                    excerpt = c.paras[a].text.take(60),
                ),
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { app.db.bookmarkDao().delete(id) }
    }

    fun rename(newTitle: String) {
        val b = _book.value ?: return
        viewModelScope.launch {
            app.db.bookDao().update(b.copy(title = newTitle))
            _book.value = b.copy(title = newTitle)
        }
    }

    // 设置便捷入口
    fun setFontSize(v: Int) = launchSet { app.settings.setFontSize(v.coerceIn(12, 40)) }
    fun setLineSpacing(v: Float) = launchSet { app.settings.setLineSpacing(v) }
    fun setParaSpacing(v: Int) = launchSet { app.settings.setParaSpacing(v) }
    fun setHMargin(v: Int) = launchSet { app.settings.setHMargin(v) }
    fun setVMargin(v: Int) = launchSet { app.settings.setVMargin(v) }
    fun setTheme(v: com.kovak.moyue.data.ReaderTheme) = launchSet { app.settings.setReaderTheme(v) }
    fun setPageMode(v: com.kovak.moyue.data.PageMode) = launchSet { app.settings.setPageMode(v) }
    fun setJustify(v: Boolean) = launchSet { app.settings.setJustify(v) }
    fun setFontId(v: String) = launchSet { app.settings.setFontId(v) }
    fun setBrightness(v: Float) = launchSet { app.settings.setBrightness(v) }
    fun setVolumeTurn(v: Boolean) = launchSet { app.settings.setVolumeTurn(v) }
    fun setKeepScreenOn(v: Boolean) = launchSet { app.settings.setKeepScreenOn(v) }

    private fun launchSet(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    override fun onCleared() {
        val elapsed = SystemClock.elapsedRealtime() - sessionStart
        val c = _content.value
        val a = anchor.value
        if (c != null && c.paras.isNotEmpty()) {
            runBlocking {
                val (ch, para) = c.toChapterPosition(a)
                val prev = app.db.progressDao().byBook(bookId)
                app.db.progressDao().upsert(
                    ReadingProgress(
                        bookId = bookId,
                        chapterIndex = ch,
                        paragraphIndex = para,
                        percent = c.percentOf(a),
                        totalReadMillis = (prev?.totalReadMillis ?: 0) + elapsed,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        super.onCleared()
    }
}
