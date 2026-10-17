package com.kovak.moyue.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kovak.moyue.AppContainer
import com.kovak.moyue.fonts.FontRepository
import com.kovak.moyue.ui.AppIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FontsViewModel(private val app: AppContainer) : ViewModel() {
    val fonts: StateFlow<List<FontRepository.FontItem>> = app.fonts.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val defaultFontId: StateFlow<String> = app.settings.settings
        .map { it.fontId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val message = MutableStateFlow<String?>(null)

    fun setDefault(id: String) = viewModelScope.launch { app.settings.setFontId(id) }
    fun import(uri: android.net.Uri, name: String?) = viewModelScope.launch {
        val ok = app.fonts.import(uri, name)
        message.value = if (ok) "导入成功" else "导入失败:仅支持 ttf/otf,且文件需完整"
    }
    fun delete(item: FontRepository.FontItem) = viewModelScope.launch { app.fonts.delete(item) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontsRoute(app: AppContainer, onBack: () -> Unit) {
    val vm: FontsViewModel = viewModel(
        factory = viewModelFactory { initializer { FontsViewModel(app) } },
    )
    val fonts by vm.fonts.collectAsStateWithLifecycle()
    val defaultId by vm.defaultFontId.collectAsStateWithLifecycle()

    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.import(it, null) }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.message.collect {
            it?.let { msg ->
                android.widget.Toast.makeText(app.appContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                vm.message.value = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("字体管理") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "默认字体:${fonts.firstOrNull { it.id == defaultId }?.name ?: "系统默认"}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { pickFont.launch(arrayOf("*/*")) }) { Text("导入 ttf/otf") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            items(fonts.size) { i ->
                val f = fonts[i]
                FontCard(
                    item = f,
                    isDefault = f.id == defaultId,
                    onSetDefault = { vm.setDefault(f.id) },
                    onDelete = { vm.delete(f) },
                )
                Spacer(Modifier.height(10.dp))
            }
            item {
                Text(
                    FontRepository.LICENSE_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun FontCard(
    item: FontRepository.FontItem,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (item.isBuiltin) "内置 · SIL OFL 1.1" else "本地导入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!item.isBuiltin) {
                    IconButton(onClick = onDelete) { Icon(AppIcons.Trash, "删除") }
                }
                if (isDefault) {
                    Icon(AppIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedButton(onClick = onSetDefault) { Text("设为默认") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "永和九年,岁在癸丑。The quick brown fox 0123",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = if (item.isBuiltin) {
                    if (item.id == FontRepository.SERIF) FontFamily.Serif else FontFamily.SansSerif
                } else FontFamily.Default,
            )
        }
    }
}
