package com.kovak.moyue.reader

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kovak.moyue.AppContainer
import com.kovak.moyue.data.PageMode
import com.kovak.moyue.data.ReaderTheme
import com.kovak.moyue.fonts.FontRepository
import com.kovak.moyue.ui.AppIcons
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** 音量键翻页桥:ReaderScreen 注册,MainActivity.dispatchKeyEvent 分发 */
object KeyEventHub {
    var volumeHandler: ((Int) -> Boolean)? = null
}

private enum class Panel { NONE, TOC, BOOKMARK, SEARCH, SETTINGS }

@Composable
fun ReaderRoute(bookId: Long, app: AppContainer, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(
        key = "reader_$bookId",
        factory = viewModelFactory { initializer { ReaderViewModel(app, bookId) } },
    )
    ReaderScreen(vm, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val content by vm.content.collectAsStateWithLifecycle()
    val style by vm.style.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val anchor by vm.anchor.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val book by vm.book.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var menuVisible by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }

    val density = LocalDensity.current
    val context = LocalContext.current
    val activity = context as? Activity

    val themeColor = Color(style.theme.bg)
    val textColor = Color(style.theme.text)
    val subColor = Color(style.theme.subText)

    val fontPx = with(density) { style.fontSizeSp.sp.toPx() }
    val paraSpacingPx = with(density) { style.paraSpacingDp.dp.toPx() }
    val hMarginPx = with(density) { style.hMarginDp.dp.toPx() }
    val vMarginPx = with(density) { style.vMarginDp.dp.toPx() }
    var containerW by remember { mutableFloatStateOf(0f) }
    var containerH by remember { mutableFloatStateOf(0f) }
    val innerW = (containerW - 2 * hMarginPx).coerceAtLeast(100f)
    val innerH = (containerH - 2 * vMarginPx).coerceAtLeast(100f)

    LaunchedEffect(innerW, innerH, fontPx, style.lineSpacing, paraSpacingPx) {
        vm.setViewport(ReaderViewModel.Viewport(innerW, innerH, fontPx, style.lineSpacing, paraSpacingPx))
    }

    // 亮度独立调节
    DisposableEffect(settings.brightness) {
        val window = activity?.window
        window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = if (settings.brightness in 0f..1f) settings.brightness
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        onDispose { }
    }
    // 屏幕常亮
    DisposableEffect(settings.keepScreenOn) {
        val window = activity?.window
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // 音量键翻页
    DisposableEffect(vm) {
        KeyEventHub.volumeHandler = { keyCode ->
            if (vm.settings.value.volumeTurn) vm.onVolumeKey(keyCode) else false
        }
        onDispose { KeyEventHub.volumeHandler = null }
    }
    BackHandler {
        if (panel != Panel.NONE) panel = Panel.NONE
        else if (menuVisible) menuVisible = false
        else onBack()
    }

    Surface(color = themeColor, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { containerW = it.width.toFloat(); containerH = it.height.toFloat() },
        ) {
            val c = content
            when {
                error != null -> ErrorState(error!!, onBack)
                c == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = textColor)
                }
                style.pageMode == PageMode.SCROLL -> ScrollReader(
                    vm, c, style, textColor, subColor,
                    scrollPx = innerH, onTap = { menuVisible = !menuVisible },
                )
                else -> PagedReader(
                    vm, c, style, pages ?: emptyList(),
                    innerW = innerW, fontPx = fontPx, textColor = textColor,
                    onMenuToggle = { menuVisible = !menuVisible },
                )
            }

            if (menuVisible) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(themeColor.copy(alpha = 0.97f))
                        .statusBarsPadding(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回", tint = textColor) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                book?.title ?: "",
                                color = textColor,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (c != null) {
                                val chTitle = c.chapterTitles.getOrElse(c.chapterOfPara(anchor)) { "" }
                                Text(
                                    "$chTitle · ${(vm.percentOf(anchor) * 100).toInt()}%",
                                    color = subColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { vm.addBookmark() }) {
                            Icon(AppIcons.Bookmark, "添加书签", tint = textColor)
                        }
                    }
                }
            }

            if (menuVisible) {
                BottomMenu(
                    vm = vm,
                    anchor = anchor,
                    content = c,
                    themeColor = themeColor,
                    textColor = textColor,
                    subColor = subColor,
                    brightness = settings.brightness,
                    onOpenPanel = { panel = it; menuVisible = false },
                )
            }
        }
    }

    if (panel == Panel.TOC && content != null) {
        ModalBottomSheet(
            onDismissRequest = { panel = Panel.NONE },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TocPanel(vm, content!!, anchor, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary) {
                panel = Panel.NONE
            }
        }
    }
    if (panel == Panel.BOOKMARK) {
        ModalBottomSheet(
            onDismissRequest = { panel = Panel.NONE },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BookmarkPanel(vm, vm.bookmarks.collectAsStateWithLifecycle().value) { panel = Panel.NONE }
        }
    }
    if (panel == Panel.SEARCH && content != null) {
        ModalBottomSheet(
            onDismissRequest = { panel = Panel.NONE },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SearchPanel(vm, content!!) { panel = Panel.NONE }
        }
    }
    if (panel == Panel.SETTINGS) {
        ModalBottomSheet(
            onDismissRequest = { panel = Panel.NONE },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ReaderSettingsPanel(vm, style, vm.fonts.collectAsStateWithLifecycle().value, settings.brightness)
        }
    }
}

// ---------- 分页阅读(SLIDE / NONE / SIMUL) ----------

@Composable
private fun PagedReader(
    vm: ReaderViewModel,
    content: BookContent,
    style: ReaderStyle,
    pages: List<Page>,
    innerW: Float,
    fontPx: Float,
    textColor: Color,
    onMenuToggle: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val layouter = remember { TextLayouter(null) }
    val mode = style.pageMode
    val pageCount = pages.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var manualIdx by remember { mutableIntStateOf(0) } // SIMUL / NONE 模式的当前页

    val currentPageIdx = { if (mode == PageMode.SLIDE) pagerState.currentPage else manualIdx }
    val gotoPage = { idx: Int, animated: Boolean ->
        val target = idx.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (mode == PageMode.SLIDE) {
            scope.launch {
                if (animated) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
            }
        } else {
            manualIdx = target
        }
    }
    val next = { gotoPage(currentPageIdx() + 1, mode == PageMode.SLIDE) }
    val prev = { gotoPage(currentPageIdx() - 1, mode == PageMode.SLIDE) }

    // 手势区
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(mode, pageCount) {
                detectTapGestures { offset ->
                    val w = size.width
                    when {
                        offset.x < w / 3f -> prev()
                        offset.x > w * 2f / 3f -> next()
                        else -> onMenuToggle()
                    }
                }
            },
    ) {
        when (mode) {
            PageMode.SLIDE -> {
                LaunchedEffect(pages) {
                    if (pages.isNotEmpty()) {
                        pagerState.scrollToPage(layouter.pageForAnchor(pages, vm.anchor.value))
                    }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.drop(1).collect { idx ->
                        if (idx < pages.size) vm.anchor.value = pages[idx].firstPara
                    }
                }
                LaunchedEffect(vm.jumpTick.value) {
                    if (vm.jumpTick.value > 0 && pages.isNotEmpty()) {
                        pagerState.scrollToPage(layouter.pageForAnchor(pages, vm.anchor.value))
                    }
                }
                LaunchedEffect(vm) {
                    vm.turnBus.collect { dir ->
                        val target = pagerState.currentPage + dir
                        if (target in 0 until pageCount) {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                }
                HorizontalPager(state = pagerState) { idx ->
                    if (idx < pages.size) {
                        PageView(pages[idx], content, style, innerW, fontPx, textColor)
                    }
                }
            }
            else -> { // NONE 无动画 / SIMUL 仿真(缩放+淡入)
                LaunchedEffect(pages) {
                    if (pages.isNotEmpty()) manualIdx = layouter.pageForAnchor(pages, vm.anchor.value)
                }
                LaunchedEffect(manualIdx) {
                    if (manualIdx < pages.size) vm.anchor.value = pages[manualIdx].firstPara
                }
                LaunchedEffect(vm.jumpTick.value) {
                    if (vm.jumpTick.value > 0 && pages.isNotEmpty()) {
                        manualIdx = layouter.pageForAnchor(pages, vm.anchor.value)
                    }
                }
                LaunchedEffect(vm) {
                    vm.turnBus.collect { dir ->
                        val target = manualIdx + dir
                        if (target in 0 until pageCount) manualIdx = target
                    }
                }
                val content0: @Composable (Int) -> Unit = { idx ->
                    if (idx < pages.size) {
                        PageView(pages[idx], content, style, innerW, fontPx, textColor)
                    }
                }
                if (mode == PageMode.NONE) {
                    content0(manualIdx)
                } else {
                    AnimatedContent(
                        targetState = manualIdx,
                        transitionSpec = {
                            val forward = targetState > initialState
                            if (forward) {
                                (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut()
                            } else {
                                (fadeIn() + scaleIn(initialScale = 1.06f)) togetherWith fadeOut()
                            }
                        },
                        label = "simul",
                    ) { idx -> content0(idx) }
                }
            }
        }
    }
}

// ---------- 滚动阅读 ----------

@Composable
private fun ScrollReader(
    vm: ReaderViewModel,
    content: BookContent,
    style: ReaderStyle,
    textColor: Color,
    subColor: Color,
    scrollPx: Float,
    onTap: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val fontPx = with(density) { style.fontSizeSp.sp.toPx() }
    val indentSp = with(density) { (2 * fontPx).toSp() }
    val lineHeightSp = with(density) { (fontPx * style.lineSpacing).toSp() }

    LaunchedEffect(content) {
        if (content.paras.isNotEmpty()) {
            listState.scrollToItem(vm.anchor.value.coerceAtMost(content.paras.size - 1))
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.drop(1).collect { idx ->
            vm.anchor.value = idx
        }
    }
    LaunchedEffect(vm.jumpTick.value) {
        if (vm.jumpTick.value > 0 && content.paras.isNotEmpty()) {
            listState.scrollToItem(vm.anchor.value.coerceAtMost(content.paras.size - 1))
        }
    }
    LaunchedEffect(vm) {
        vm.turnBus.collect { dir ->
            listState.dispatchRawDelta(if (dir > 0) scrollPx else -scrollPx)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = style.hMarginDp.dp, vertical = style.vMarginDp.dp),
        ) {
            items(content.paras.size) { i ->
                val p = content.paras[i]
                if (p.isTitle) {
                    Text(
                        text = p.text,
                        color = textColor,
                        style = TextStyle(
                            fontSize = style.fontSizeSp.sp,
                            lineHeight = lineHeightSp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = style.fontFamily,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                    )
                } else {
                    Text(
                        text = p.text,
                        color = textColor,
                        style = TextStyle(
                            fontSize = style.fontSizeSp.sp,
                            lineHeight = lineHeightSp,
                            fontFamily = style.fontFamily,
                            textIndent = TextIndent(firstLine = indentSp),
                        ),
                        textAlign = if (style.justify) TextAlign.Justify else TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = style.paraSpacingDp.dp),
                    )
                }
            }
        }
    }
}

// ---------- 页面渲染(逐行,支持两端对齐) ----------

@Composable
private fun PageView(
    page: Page,
    content: BookContent,
    style: ReaderStyle,
    innerW: Float,
    fontPx: Float,
    textColor: Color,
) {
    val density = LocalDensity.current
    val lineHeightDp = with(density) { (fontPx * style.lineSpacing).toDp() }
    val lineHeightSp = with(density) { (fontPx * style.lineSpacing).toSp() }
    val fontSizeSp = with(density) { fontPx.toSp() }
    val indentPad = 2 * fontPx

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = style.hMarginDp.dp, vertical = style.vMarginDp.dp),
    ) {
        for (line in page.lines) {
            val spacingSp = if (style.justify && !line.isTitle && !line.isLastOfPara && line.text.length > 1) {
                val pad = if (line.isFirstOfPara) indentPad else 0f
                val extra = (innerW - pad - line.naturalWidth) / (line.text.length - 1)
                with(density) { extra.coerceIn(0f, fontPx * 0.5f).toSp() }
            } else 0.sp
            Text(
                text = line.text,
                color = textColor,
                style = TextStyle(
                    fontSize = fontSizeSp,
                    lineHeight = lineHeightSp,
                    letterSpacing = spacingSp,
                    fontWeight = if (line.isTitle) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = style.fontFamily,
                ),
                textAlign = if (line.isTitle) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .let { m ->
                        if (line.isFirstOfPara && !line.isTitle) m.padding(start = with(density) { indentPad.toDp() })
                        else m
                    }
                    .height(lineHeightDp),
            )
        }
    }
}

// ---------- 底部菜单 ----------

@Composable
private fun BottomMenu(
    vm: ReaderViewModel,
    anchor: Int,
    content: BookContent?,
    themeColor: Color,
    textColor: Color,
    subColor: Color,
    brightness: Float,
    onOpenPanel: (Panel) -> Unit,
) {
    var dragPercent by remember { mutableStateOf<Float?>(null) }
    val percent = dragPercent ?: vm.percentOf(anchor)

    Column(
        Modifier
            .fillMaxWidth()
            .background(themeColor.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${(percent * 100).toInt()}%",
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(44.dp),
            )
            Slider(
                value = percent,
                onValueChange = { dragPercent = it },
                onValueChangeFinished = {
                    dragPercent?.let { vm.jumpTo(vm.percentToAnchor(it)) }
                    dragPercent = null
                },
                modifier = Modifier.weight(1f),
            )
        }
        content?.let { c ->
            val chTitle = c.chapterTitles.getOrElse(c.chapterOfPara(anchor)) { "" }
            Text(chTitle, color = subColor, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MenuButton(AppIcons.Toc, "目录", textColor) { onOpenPanel(Panel.TOC) }
            MenuButton(AppIcons.Bookmark, "书签", textColor) { onOpenPanel(Panel.BOOKMARK) }
            MenuButton(AppIcons.Search, "搜索", textColor) { onOpenPanel(Panel.SEARCH) }
            MenuButton(AppIcons.Settings, "设置", textColor) { onOpenPanel(Panel.SETTINGS) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Sun, "亮度", tint = subColor)
            Slider(
                value = if (brightness in 0f..1f) brightness else 0.5f,
                onValueChange = { vm.setBrightness(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun MenuButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(icon, label, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------- 目录 ----------

@Composable
private fun TocPanel(
    vm: ReaderViewModel,
    content: BookContent,
    anchor: Int,
    textColor: Color,
    highlight: Color,
    onDismiss: () -> Unit,
) {
    val current = content.chapterOfPara(anchor)
    Text(
        "目录 · 共 ${content.chapterTitles.size} 章",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
    LazyColumn(Modifier.heightIn(max = 480.dp).padding(bottom = 24.dp)) {
        items(content.chapterTitles.size) { i ->
            val selected = i == current
            Text(
                text = content.chapterTitles[i],
                color = if (selected) highlight else textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.jumpTo(content.chapterStartPara[i])
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

// ---------- 书签 ----------

@Composable
private fun BookmarkPanel(vm: ReaderViewModel, bookmarks: List<com.kovak.moyue.data.db.Bookmark>, onDismiss: () -> Unit) {
    Text(
        "书签 · ${bookmarks.size}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
    if (bookmarks.isEmpty()) {
        Text(
            "暂无书签。阅读时点右上角书签图标即可收藏当前位置。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
    LazyColumn(Modifier.heightIn(max = 440.dp).padding(bottom = 24.dp)) {
        items(bookmarks.size) { i ->
            val bm = bookmarks[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.jumpTo(bm.paragraphIndex)
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(bm.excerpt, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "第 ${bm.chapterIndex + 1} 章",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.deleteBookmark(bm.id) }) {
                    Icon(AppIcons.Close, "删除书签", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ---------- 搜索 ----------

@Composable
private fun SearchPanel(vm: ReaderViewModel, content: BookContent, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ReaderViewModel.SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text("全文搜索", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("输入关键词") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch { results = vm.search(query); searched = true }
            }) { Text("搜索") }
        }
        Spacer(Modifier.height(8.dp))
        if (searched && results.isEmpty()) {
            Text("没有找到\"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    LazyColumn(Modifier.heightIn(max = 420.dp).padding(bottom = 24.dp)) {
        items(results.size) { i ->
            val hit = results[i]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.jumpTo(hit.para)
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(hit.chapterTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(hit.snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- 阅读设置 ----------

@Composable
private fun ReaderSettingsPanel(
    vm: ReaderViewModel,
    style: ReaderStyle,
    fonts: List<FontRepository.FontItem>,
    brightness: Float,
) {
    LazyColumn(Modifier.padding(horizontal = 20.dp).heightIn(max = 520.dp)) {
        item {
            Text("翻页模式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageMode.entries.forEach { m ->
                    FilterChip(
                        selected = style.pageMode == m,
                        onClick = { vm.setPageMode(m) },
                        label = { Text(m.label) },
                    )
                }
            }
        }
        item {
            Text("主题", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { t ->
                    FilterChip(
                        selected = style.theme == t,
                        onClick = { vm.setTheme(t) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .width(14.dp)
                                        .height(14.dp)
                                        .background(Color(t.bg), CircleShape),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(t.label)
                            }
                        },
                    )
                }
            }
        }
        item {
            Text("字体", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fonts.take(3).forEach { f ->
                    FilterChip(
                        selected = vm.settings.value.fontId == f.id,
                        onClick = { vm.setFontId(f.id) },
                        label = { Text(f.name.substringBefore(" ("), maxLines = 1) },
                    )
                }
            }
            Text(
                "更多字体在「设置 → 字体管理」中设置默认",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                Text("字号", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.setFontSize(style.fontSizeSp - 1) }) { Text("A-") }
                Text(
                    " ${style.fontSizeSp} ",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                OutlinedButton(onClick = { vm.setFontSize(style.fontSizeSp + 1) }) { Text("A+") }
            }
        }
        item {
            Text("行距 ${"%.2f".format(style.lineSpacing)}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = style.lineSpacing,
                onValueChange = { vm.setLineSpacing((it * 20).toInt() / 20f) },
                valueRange = 1.0f..2.4f,
            )
        }
        item {
            Text("段距 ${style.paraSpacingDp}dp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = style.paraSpacingDp.toFloat(),
                onValueChange = { vm.setParaSpacing(it.toInt()) },
                valueRange = 0f..24f,
            )
        }
        item {
            Text("左右边距 ${style.hMarginDp}dp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = style.hMarginDp.toFloat(),
                onValueChange = { vm.setHMargin(it.toInt()) },
                valueRange = 8f..56f,
            )
        }
        item {
            Text("上下边距 ${style.vMarginDp}dp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = style.vMarginDp.toFloat(),
                onValueChange = { vm.setVMargin(it.toInt()) },
                valueRange = 12f..80f,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("两端对齐", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = style.justify, onCheckedChange = { vm.setJustify(it) })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("音量键翻页", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = vm.settings.value.volumeTurn, onCheckedChange = { vm.setVolumeTurn(it) })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("屏幕常亮", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = vm.settings.value.keepScreenOn, onCheckedChange = { vm.setKeepScreenOn(it) })
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

// ---------- 错误态 ----------

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("返回书架") }
        }
    }
}
