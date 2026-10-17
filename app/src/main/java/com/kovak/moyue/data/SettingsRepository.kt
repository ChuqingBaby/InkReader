package com.kovak.moyue.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kovak.moyue.format.ChapterSplitter
import com.kovak.moyue.format.EncodingDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ink_settings")

/** 「打开方式」策略 */
enum class OpenPolicy(val key: String, val label: String) {
    ASK("ASK", "每次询问"),
    READ_ONLY("READ_ONLY", "仅阅读"),
    AUTO_CONVERT("AUTO_CONVERT", "自动转换并入库");

    companion object {
        fun from(key: String?): OpenPolicy = entries.firstOrNull { it.key == key } ?: ASK
    }
}

/** 翻页模式 */
enum class PageMode(val key: String, val label: String) {
    SIMUL("SIMUL", "仿真"),
    SLIDE("SLIDE", "平移"),
    SCROLL("SCROLL", "上下滚动"),
    NONE("NONE", "无动画");

    companion object {
        fun from(key: String?): PageMode = entries.firstOrNull { it.key == key } ?: SLIDE
    }
}

/** 阅读主题 */
enum class ReaderTheme(val key: String, val label: String, val bg: Long, val text: Long, val subText: Long, val isDark: Boolean) {
    PAPER("PAPER", "纸张", 0xFFFAF6EF, 0xFF3B332B, 0xFF8C7F6E, false),
    SEPIA("SEPIA", "羊皮", 0xFFF3E7CE, 0xFF4A3A28, 0xFF96805F, false),
    GREEN("GREEN", "护眼绿", 0xFFCCEDD9, 0xFF2C4A3A, 0xFF5F8A72, false),
    NIGHT("NIGHT", "夜间", 0xFF1E2A24, 0xFFB7C4BB, 0xFF66786D, true),
    BLACK("BLACK", "纯黑", 0xFF0A0A0A, 0xFFA8A29A, 0xFF5C5852, true);

    companion object {
        fun from(key: String?): ReaderTheme = entries.firstOrNull { it.key == key } ?: PAPER
    }
}

/** 排序方式 */
enum class SortMode(val key: String, val label: String) {
    RECENT("RECENT", "最近阅读"),
    ADDED("ADDED", "添加时间"),
    TITLE("TITLE", "书名");

    companion object {
        fun from(key: String?): SortMode = entries.firstOrNull { it.key == key } ?: RECENT
    }
}

data class AppSettings(
    val openPolicy: OpenPolicy = OpenPolicy.ASK,
    val defaultConvertTarget: String = "EPUB",
    val defaultEncoding: String = EncodingDetector.AUTO,
    val chapterRegex: String = ChapterSplitter.DEFAULT_REGEX,
    // 阅读默认参数
    val fontSizeSp: Int = 20,
    val lineSpacing: Float = 1.6f,
    val paraSpacingDp: Int = 10,
    val hMarginDp: Int = 24,
    val vMarginDp: Int = 32,
    val readerTheme: ReaderTheme = ReaderTheme.PAPER,
    val pageMode: PageMode = PageMode.SLIDE,
    val fontId: String = "builtin:sans",
    val justify: Boolean = true,
    val keepScreenOn: Boolean = false,
    val volumeTurn: Boolean = true,
    val followSystemDark: Boolean = true,
    val brightness: Float = -1f,     // -1 = 跟随系统
    val shelfView: String = "GRID",
    val sortMode: SortMode = SortMode.RECENT,
)

class SettingsRepository(private val context: Context) {

    private object K {
        val openPolicy = stringPreferencesKey("open_policy")
        val convertTarget = stringPreferencesKey("convert_target")
        val encoding = stringPreferencesKey("default_encoding")
        val chapterRegex = stringPreferencesKey("chapter_regex")
        val fontSize = intPreferencesKey("font_size_sp")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val paraSpacing = intPreferencesKey("para_spacing_dp")
        val hMargin = intPreferencesKey("h_margin_dp")
        val vMargin = intPreferencesKey("v_margin_dp")
        val readerTheme = stringPreferencesKey("reader_theme")
        val pageMode = stringPreferencesKey("page_mode")
        val fontId = stringPreferencesKey("font_id")
        val justify = booleanPreferencesKey("justify")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val volumeTurn = booleanPreferencesKey("volume_turn")
        val followSystemDark = booleanPreferencesKey("follow_system_dark")
        val brightness = floatPreferencesKey("brightness")
        val shelfView = stringPreferencesKey("shelf_view")
        val sortMode = stringPreferencesKey("sort_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            openPolicy = OpenPolicy.from(p[K.openPolicy]),
            defaultConvertTarget = p[K.convertTarget] ?: "EPUB",
            defaultEncoding = p[K.encoding] ?: EncodingDetector.AUTO,
            chapterRegex = p[K.chapterRegex] ?: ChapterSplitter.DEFAULT_REGEX,
            fontSizeSp = p[K.fontSize] ?: 20,
            lineSpacing = p[K.lineSpacing] ?: 1.6f,
            paraSpacingDp = p[K.paraSpacing] ?: 10,
            hMarginDp = p[K.hMargin] ?: 24,
            vMarginDp = p[K.vMargin] ?: 32,
            readerTheme = ReaderTheme.from(p[K.readerTheme]),
            pageMode = PageMode.from(p[K.pageMode]),
            fontId = p[K.fontId] ?: "builtin:sans",
            justify = p[K.justify] ?: true,
            keepScreenOn = p[K.keepScreenOn] ?: false,
            volumeTurn = p[K.volumeTurn] ?: true,
            followSystemDark = p[K.followSystemDark] ?: true,
            brightness = p[K.brightness] ?: -1f,
            shelfView = p[K.shelfView] ?: "GRID",
            sortMode = SortMode.from(p[K.sortMode]),
        )
    }

    suspend fun setOpenPolicy(v: OpenPolicy) = context.dataStore.edit { it[K.openPolicy] = v.key }
    suspend fun setConvertTarget(v: String) = context.dataStore.edit { it[K.convertTarget] = v }
    suspend fun setEncoding(v: String) = context.dataStore.edit { it[K.encoding] = v }
    suspend fun setChapterRegex(v: String) = context.dataStore.edit { it[K.chapterRegex] = v }
    suspend fun setFontSize(v: Int) = context.dataStore.edit { it[K.fontSize] = v }
    suspend fun setLineSpacing(v: Float) = context.dataStore.edit { it[K.lineSpacing] = v }
    suspend fun setParaSpacing(v: Int) = context.dataStore.edit { it[K.paraSpacing] = v }
    suspend fun setHMargin(v: Int) = context.dataStore.edit { it[K.hMargin] = v }
    suspend fun setVMargin(v: Int) = context.dataStore.edit { it[K.vMargin] = v }
    suspend fun setReaderTheme(v: ReaderTheme) = context.dataStore.edit { it[K.readerTheme] = v.key }
    suspend fun setPageMode(v: PageMode) = context.dataStore.edit { it[K.pageMode] = v.key }
    suspend fun setFontId(v: String) = context.dataStore.edit { it[K.fontId] = v }
    suspend fun setJustify(v: Boolean) = context.dataStore.edit { it[K.justify] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.keepScreenOn] = v }
    suspend fun setVolumeTurn(v: Boolean) = context.dataStore.edit { it[K.volumeTurn] = v }
    suspend fun setFollowSystemDark(v: Boolean) = context.dataStore.edit { it[K.followSystemDark] = v }
    suspend fun setBrightness(v: Float) = context.dataStore.edit { it[K.brightness] = v }
    suspend fun setShelfView(v: String) = context.dataStore.edit { it[K.shelfView] = v }
    suspend fun setSortMode(v: SortMode) = context.dataStore.edit { it[K.sortMode] = v.key }
}
