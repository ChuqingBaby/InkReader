package com.kovak.moyue.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kovak.moyue.AppContainer
import com.kovak.moyue.data.OpenPolicy
import com.kovak.moyue.format.ChapterSplitter
import com.kovak.moyue.format.EncodingDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    val settings: StateFlow<com.kovak.moyue.data.AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.kovak.moyue.data.AppSettings())

    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun setPolicy(v: OpenPolicy) = launch { app.settings.setOpenPolicy(v) }
    fun setTarget(v: String) = launch { app.settings.setConvertTarget(v) }
    fun setEncoding(v: String) = launch { app.settings.setEncoding(v) }
    fun setRegex(v: String) = launch { app.settings.setChapterRegex(v) }
    fun resetRegex() = launch { app.settings.setChapterRegex(ChapterSplitter.DEFAULT_REGEX) }
    fun setFontSize(v: Int) = launch { app.settings.setFontSize(v.coerceIn(12, 40)) }
    fun setJustify(v: Boolean) = launch { app.settings.setJustify(v) }
    fun setVolumeTurn(v: Boolean) = launch { app.settings.setVolumeTurn(v) }
    fun setKeepScreenOn(v: Boolean) = launch { app.settings.setKeepScreenOn(v) }

    fun exportBackup(uri: android.net.Uri) = launch {
        busy.value = true
        val (ok, err) = app.backup.exportTo(uri)
        busy.value = false
        message.value = if (ok) "备份完成" else "备份失败:$err"
    }

    fun restoreBackup(uri: android.net.Uri) = launch {
        busy.value = true
        val (n, err) = app.backup.restoreFrom(uri)
        busy.value = false
        message.value = if (err == null) "已恢复 $n 本书(文件缺失的会标记,重新导入文件即可)" else "恢复失败:$err"
    }

    fun clearCache() = launch {
        busy.value = true
        val freed = app.backup.clearCache()
        busy.value = false
        message.value = "已清理缓存,释放 ${freed / 1024} KB"
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(app: AppContainer, onOpenFonts: () -> Unit) {
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(app) } },
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var showAbout by remember { mutableStateOf(false) }
    var showRegexDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.exportBackup(it) } }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.restoreBackup(it) } }

    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(app.appContext, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.message.value = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("我的", style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionTitle("打开方式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OpenPolicy.entries.forEach { p ->
                    FilterChip(
                        selected = settings.openPolicy == p,
                        onClick = { vm.setPolicy(p) },
                        label = { Text(p.label) },
                    )
                }
            }
            Text(
                "每次询问:从其他 App 打开书籍文件时弹窗选择;仅阅读:直接入库阅读;" +
                    "自动转换:按下方默认格式转换后入库阅读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )

            SectionTitle("默认转换")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("目标格式", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = settings.defaultConvertTarget == "EPUB", onClick = { vm.setTarget("EPUB") }, label = { Text("EPUB") })
                FilterChip(selected = settings.defaultConvertTarget == "TXT", onClick = { vm.setTarget("TXT") }, label = { Text("TXT") })
            }
            Spacer(Modifier.height(8.dp))
            Text("默认编码", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EncodingDetector.SUPPORTED.forEach { e ->
                    FilterChip(
                        selected = settings.defaultEncoding == e,
                        onClick = { vm.setEncoding(e) },
                        label = { Text(if (e == EncodingDetector.AUTO) "自动" else e) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("章节切分规则", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showRegexDialog = true }) { Text("自定义") }
            }
            Text(
                settings.chapterRegex.ifBlank { ChapterSplitter.DEFAULT_REGEX },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            SectionTitle("阅读默认参数")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("字号 ${settings.fontSizeSp}", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.setFontSize(settings.fontSizeSp - 1) }) { Text("-") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { vm.setFontSize(settings.fontSizeSp + 1) }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text("两端对齐", modifier = Modifier.weight(1f))
                Switch(checked = settings.justify, onCheckedChange = { vm.setJustify(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text("音量键翻页", modifier = Modifier.weight(1f))
                Switch(checked = settings.volumeTurn, onCheckedChange = { vm.setVolumeTurn(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text("屏幕常亮", modifier = Modifier.weight(1f))
                Switch(checked = settings.keepScreenOn, onCheckedChange = { vm.setKeepScreenOn(it) })
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            SectionTitle("字体")
            Button(onClick = onOpenFonts) {
                Text("字体管理(内置思源黑体/宋体,可导入本地字体)")
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            SectionTitle("数据管理")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { exportLauncher.launch("inkreader_backup_${System.currentTimeMillis()}.json") },
                    enabled = !busy,
                ) { Text("导出备份") }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    enabled = !busy,
                ) { Text("恢复备份") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.clearCache() }, enabled = !busy) { Text("清理缓存") }
                if (busy) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            }
            Text(
                "备份包含书架元数据、阅读进度与书签。恢复后若源文件不在原路径,会标记为缺失,重新导入文件即可继续阅读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            SectionTitle("关于")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("墨阅 InkReader", modifier = Modifier.weight(1f))
                TextButton(onClick = { showAbout = true }) { Text("v1.0.0 · 许可") }
            }
            Text(
                "纯本地小说阅读器:无账号、无广告、无埋点;联网仅用于你主动输入的链接下载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于 墨阅") },
            text = {
                Text(
                    "版本 1.0.0 (versionCode 1)\n包名 com.kovak.moyue\n\n" +
                        "开源许可:\n" +
                        "· 思源黑体 / 思源宋体(Source Han Sans/Serif SC)— 版权所有 Adobe & Google,SIL Open Font License 1.1\n" +
                        "· Jetpack Compose / AndroidX(Apache License 2.0)\n" +
                        "· Kotlin / kotlinx.coroutines(Apache License 2.0)\n" +
                        "· OkHttp(Apache License 2.0)\n" +
                        "· Jsoup(MIT License)\n" +
                        "· Room / DataStore(Apache License 2.0)\n\n" +
                        "所有界面图标与启动图标均为代码自绘矢量图形。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("关闭") } },
        )
    }

    if (showRegexDialog) {
        var regex by remember { mutableStateOf(settings.chapterRegex) }
        AlertDialog(
            onDismissRequest = { showRegexDialog = false },
            title = { Text("章节切分正则") },
            text = {
                Column {
                    OutlinedTextField(
                        value = regex,
                        onValueChange = { regex = it },
                        placeholder = { Text(ChapterSplitter.DEFAULT_REGEX) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "默认匹配「第X章/卷」等标题行。留空恢复默认。正则非法时自动回退默认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.setRegex(regex.trim()); showRegexDialog = false }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { vm.resetRegex(); showRegexDialog = false }) { Text("恢复默认") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}
