package com.kovak.moyue.shelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kovak.moyue.AppContainer
import com.kovak.moyue.data.SortMode
import com.kovak.moyue.data.db.Book
import com.kovak.moyue.format.BookFormat
import com.kovak.moyue.ui.AppIcons
import com.kovak.moyue.ui.common.EmptyState
import com.kovak.moyue.ui.common.PlaceholderCover

@Composable
fun ShelfRoute(app: AppContainer, onOpenBook: (Long) -> Unit, onGoConversions: () -> Unit) {
    val vm: ShelfViewModel = viewModel(
        factory = viewModelFactory { initializer { ShelfViewModel(app) } },
    )
    ShelfScreen(vm, onOpenBook, onGoConversions)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(vm: ShelfViewModel, onOpenBook: (Long) -> Unit, onGoConversions: () -> Unit) {
    val books by vm.visibleBooks.collectAsStateWithLifecycle()
    val allBooks by vm.books.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val activeGroup by vm.activeGroup.collectAsStateWithLifecycle()
    val importingCount by vm.importingCount.collectAsStateWithLifecycle()
    val urlProgress by vm.urlProgress.collectAsStateWithLifecycle()
    val urlError by vm.urlError.collectAsStateWithLifecycle()
    val scanResults by vm.scanResults.collectAsStateWithLifecycle()
    val scanSelected by vm.scanSelected.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importUris(uris)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.scanTree(uri)
    }
    val percentMap = remember(progress) { progress.associate { it.bookId to it.percent } }

    fun openBook(b: Book) {
        vm.openBook(b)
        onOpenBook(b.id)
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏:普通态 / 多选态
        if (selection.isEmpty()) {
            TopAppBar(
                title = { Text("墨阅", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    if (importingCount > 0) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(AppIcons.Search, "搜索")
                    }
                    IconButton(onClick = { vm.toggleView() }) {
                        Icon(
                            if (settings.shelfView == "GRID") AppIcons.ListView else AppIcons.Grid,
                            "切换视图",
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(AppIcons.Sort, "排序") }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label + if (settings.sortMode == m) " ✓" else "") },
                                    onClick = { vm.setSort(m); showSortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showAddSheet = true }) { Icon(AppIcons.Plus, "添加书籍") }
                },
            )
        } else {
            TopAppBar(
                title = { Text("已选 ${selection.size} 项") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { vm.clearSelection() }) { Icon(AppIcons.Close, "取消") }
                },
                actions = {
                    if (selection.size == 1) {
                        IconButton(onClick = { showRenameDialog = true }) { Icon(AppIcons.Edit, "重命名") }
                    }
                    IconButton(onClick = { showConvertDialog = true }) { Icon(AppIcons.Convert, "转换") }
                    IconButton(onClick = { showGroupDialog = true }) { Icon(AppIcons.Folder, "分组") }
                    IconButton(onClick = { vm.deleteSelected() }) { Icon(AppIcons.Trash, "删除") }
                },
            )
        }

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text("搜索书名或作者") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // 分组过滤 chips
        if (groups.isNotEmpty() && selection.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = activeGroup == null,
                    onClick = { vm.setActiveGroup(null) },
                    label = { Text("全部") },
                )
                groups.take(4).forEach { g ->
                    FilterChip(
                        selected = activeGroup == g,
                        onClick = { vm.setActiveGroup(if (activeGroup == g) null else g) },
                        label = { Text(g, maxLines = 1) },
                    )
                }
            }
        }

        if (allBooks.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("书架空空如也", "点击右下角 + 添加你的第一本书\n支持 TXT / EPUB / MOBI,也可从网页导入")
                    Button(onClick = { showAddSheet = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("导入书籍")
                    }
                }
            }
        } else if (books.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState("没有匹配的书籍", "换个关键词或分组试试")
            }
        } else {
            if (settings.shelfView == "GRID") {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(books, key = { it.id }) { book ->
                        GridBookCard(
                            book = book,
                            percent = percentMap[book.id] ?: 0f,
                            selected = book.id in selection,
                            onClick = {
                                if (selection.isEmpty()) openBook(book) else vm.toggleSelect(book.id)
                            },
                            onLongClick = { vm.toggleSelect(book.id) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(books, key = { it.id }) { book ->
                        ListBookRow(
                            book = book,
                            percent = percentMap[book.id] ?: 0f,
                            selected = book.id in selection,
                            onClick = {
                                if (selection.isEmpty()) openBook(book) else vm.toggleSelect(book.id)
                            },
                            onLongClick = { vm.toggleSelect(book.id) },
                        )
                    }
                }
            }
        }
    }

    // 添加方式底部弹层
    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("添加书籍", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                AddOption(AppIcons.Folder, "从文件导入", "选择 txt / epub / mobi,支持多选") {
                    showAddSheet = false
                    pickFiles.launch(arrayOf("*/*"))
                }
                AddOption(AppIcons.Search, "扫描本机书籍", "选择目录后批量勾选导入") {
                    showAddSheet = false
                    pickTree.launch(null)
                }
                AddOption(AppIcons.Globe, "从链接导入", "下载 txt / epub,或抓取网页正文") {
                    showAddSheet = false
                    showUrlDialog = true
                }
            }
        }
    }

    // URL 导入对话框
    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("从链接导入") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                    )
                    urlProgress?.let { p ->
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    }
                    urlError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("失败:$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.downloadUrl(url)
                    },
                    enabled = url.startsWith("http") && urlProgress == null,
                ) { Text("下载") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.clearUrlError()
                    showUrlDialog = false
                }) { Text("关闭") }
            },
        )
    }

    // 扫描结果对话框
    scanResults?.let { results ->
        AlertDialog(
            onDismissRequest = { vm.dismissScan() },
            title = { Text("找到 ${results.size} 本可导入的书籍") },
            text = {
                if (scanning) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (results.isEmpty()) {
                    Text("目录中没有 txt/epub/mobi 文件")
                } else {
                    LazyColumn(Modifier.height(360.dp)) {
                        items(results, key = { it.uri.toString() }) { f ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = f.uri in scanSelected,
                                    onCheckedChange = { vm.toggleScanSelect(f.uri) },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${f.size / 1024} KB · ${f.source}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.importScanned() }, enabled = scanSelected.isNotEmpty()) { Text("导入所选") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissScan() }) { Text("取消") } },
        )
    }

    // 分组/重命名/转换对话框
    if (showGroupDialog) {
        var tag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("设置分组") },
            text = {
                Column {
                    OutlinedTextField(value = tag, onValueChange = { tag = it }, placeholder = { Text("输入分组名,留空移除分组") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.take(3).forEach { g ->
                            FilterChip(selected = tag == g, onClick = { tag = g }, label = { Text(g) })
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { vm.groupSelected(tag); showGroupDialog = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showGroupDialog = false }) { Text("取消") } },
        )
    }
    if (showRenameDialog) {
        val current = allBooks.firstOrNull { it.id in selection }
        var title by remember { mutableStateOf(current?.title ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = { if (title.isNotBlank()) vm.renameSelected(title.trim()); showRenameDialog = false }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } },
        )
    }
    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = { Text("转换为") },
            text = { Text("转换完成后自动入库,可在「转换」页查看进度与导出。") },
            confirmButton = {
                TextButton(onClick = { vm.convertSelected(BookFormat.EPUB); showConvertDialog = false }) { Text("EPUB") }
            },
            dismissButton = {
                TextButton(onClick = { vm.convertSelected(BookFormat.TXT); showConvertDialog = false }) { Text("TXT") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridBookCard(
    book: Book,
    percent: Float,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box {
                PlaceholderCover(book.title, book.coverColor, 96.dp, Modifier.fillMaxWidth().height(126.dp))
                if (selected) {
                    Icon(
                        AppIcons.Check,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                book.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )
            if (percent > 0f) {
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
                Text("${(percent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("未读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListBookRow(
    book: Book,
    percent: Float,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderCover(book.title, book.coverColor, 56.dp, Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(book.author, book.format, book.groupTag).filter { !it.isNullOrBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            if (percent > 0f) "${(percent * 100).toInt()}%" else "未读",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
