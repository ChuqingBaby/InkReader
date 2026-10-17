package com.kovak.moyue.convert

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kovak.moyue.AppContainer
import com.kovak.moyue.data.db.ConvertTask
import com.kovak.moyue.ui.AppIcons
import com.kovak.moyue.ui.common.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversionsViewModel(private val app: AppContainer) : ViewModel() {

    val tasks: StateFlow<List<ConvertTask>> = app.db.convertDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bookTitles: StateFlow<Map<Long, String>> = app.db.bookDao().observeAll()
        .map { list -> list.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun retry(id: Long) = app.convertManager.retry(id)
    fun cancel(id: Long) = app.convertManager.cancel(id)
    fun delete(id: Long) = app.convertManager.deleteTask(id)

    /** 导出产物到用户选择的位置 */
    fun export(taskId: Long, uri: android.net.Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val task = app.db.convertDao().byId(taskId) ?: throw Exception("任务不存在")
                val path = task.outputPath ?: throw Exception("没有产物文件")
                val file = File(path)
                if (!file.exists()) throw Exception("产物文件已被清理")
                app.appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw Exception("无法写入目标位置")
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "导出失败")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionsRoute(app: AppContainer) {
    val vm: ConversionsViewModel = viewModel(
        factory = viewModelFactory { initializer { ConversionsViewModel(app) } },
    )
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val titles by vm.bookTitles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var exportTask by remember { mutableStateOf<ConvertTask?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val t = exportTask
        if (uri != null && t != null) {
            vm.export(t.id, uri) { ok, err ->
                android.widget.Toast.makeText(
                    context,
                    if (ok) "已导出" else "导出失败:$err",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
        exportTask = null
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("转换", style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (tasks.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState("暂无转换任务", "在书架长按书籍 → 转换,即可把 TXT/EPUB/MOBI 互转")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(tasks.size) { i ->
                    val task = tasks[i]
                    TaskCard(
                        task = task,
                        title = titles[task.bookId] ?: "未知书籍",
                        onRetry = { vm.retry(task.id) },
                        onCancel = { vm.cancel(task.id) },
                        onDelete = { vm.delete(task.id) },
                        onExport = { exportTask = task; exportLauncher.launch(suggestName(task)) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun suggestName(task: ConvertTask): String {
    val base = "book_${task.id}"
    return if (task.toFormat.equals("EPUB", true)) "$base.epub" else "$base.txt"
}

@Composable
private fun TaskCard(
    task: ConvertTask,
    title: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${task.fromFormat} → ${task.toFormat} · " +
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(task.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(task.status)
            }
            if (task.status == ConvertTask.STATUS_RUNNING || task.status == ConvertTask.STATUS_PENDING) {
                Spacer(Modifier.height(8.dp))
                if (task.status == ConvertTask.STATUS_RUNNING) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            task.message?.let {
                Spacer(Modifier.height(6.dp))
                Text("失败原因:$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (task.status == ConvertTask.STATUS_FAILED) {
                    IconButton(onClick = onRetry) { Icon(AppIcons.Retry, "重试") }
                }
                if (task.status == ConvertTask.STATUS_PENDING || task.status == ConvertTask.STATUS_RUNNING) {
                    IconButton(onClick = onCancel) { Icon(AppIcons.Close, "取消") }
                }
                if (task.status == ConvertTask.STATUS_DONE) {
                    IconButton(onClick = onExport) { Icon(AppIcons.Export, "导出到本地") }
                }
                IconButton(onClick = onDelete) { Icon(AppIcons.Trash, "删除记录") }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        ConvertTask.STATUS_DONE -> "完成" to MaterialTheme.colorScheme.tertiary
        ConvertTask.STATUS_RUNNING -> "转换中" to MaterialTheme.colorScheme.primary
        ConvertTask.STATUS_PENDING -> "排队中" to MaterialTheme.colorScheme.secondary
        ConvertTask.STATUS_FAILED -> "失败" to MaterialTheme.colorScheme.error
        else -> "已取消" to MaterialTheme.colorScheme.outline
    }
    AssistChip(onClick = {}, label = { Text(label) })
}
