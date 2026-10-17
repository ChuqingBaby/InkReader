package com.kovak.moyue

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kovak.moyue.convert.ConversionsRoute
import com.kovak.moyue.data.OpenPolicy
import com.kovak.moyue.format.BookFormat
import com.kovak.moyue.format.ChapterSplitter
import com.kovak.moyue.format.ConvertOptions
import com.kovak.moyue.reader.KeyEventHub
import com.kovak.moyue.reader.ReaderRoute
import com.kovak.moyue.settings.FontsRoute
import com.kovak.moyue.settings.SettingsRoute
import com.kovak.moyue.shelf.ShelfRoute
import com.kovak.moyue.ui.AppIcons
import com.kovak.moyue.ui.theme.InkReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as InkApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InkReaderTheme {
                AppRoot(container)
            }
        }
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && uri.toString().isNotBlank()) {
            container.incoming.tryEmit(IncomingFile(uri, intent.type))
        }
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (ev.action == KeyEvent.ACTION_DOWN) {
            if (ev.keyCode == KeyEvent.KEYCODE_VOLUME_UP || ev.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (KeyEventHub.volumeHandler?.invoke(ev.keyCode) == true) return true
            }
        }
        return super.dispatchKeyEvent(ev)
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppRoot(app: AppContainer) {
    val nav = rememberNavController()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingIncoming by remember { mutableStateOf<IncomingFile?>(null) }
    val currentSettings by app.settings.settings.collectAsState(
        initial = com.kovak.moyue.data.AppSettings(),
    )

    val tabs = listOf(
        Tab("shelf", "书架", AppIcons.Shelf),
        Tab("conversions", "转换", AppIcons.Convert),
        Tab("settings", "我的", AppIcons.Settings),
    )

    fun openIncoming(inc: IncomingFile, convert: Boolean) {
        scope.launch {
            try {
                val bookId = app.importer.importFromUri(inc.uri)
                app.db.bookDao().touchLastRead(bookId, System.currentTimeMillis())
                var targetId = bookId
                if (convert) {
                    val s = app.settings.settings.first()
                    val target = BookFormat.valueOf(s.defaultConvertTarget)
                    val book = app.db.bookDao().byId(bookId)
                    if (book != null && BookFormat.valueOf(book.format) != target) {
                        val newId = app.convertManager.convertAndWaitImport(
                            book, target,
                            ConvertOptions(
                                s.defaultEncoding,
                                "UTF-8",
                                s.chapterRegex.ifBlank { ChapterSplitter.DEFAULT_REGEX },
                            ),
                        )
                        if (newId != null) targetId = newId
                    }
                }
                nav.navigate("reader/$targetId")
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    app.appContext,
                    "打开失败:${e.message ?: "未知错误"}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        app.incoming.collect { inc ->
            val policy = app.settings.settings.first().openPolicy
            when (policy) {
                OpenPolicy.READ_ONLY -> openIncoming(inc, false)
                OpenPolicy.AUTO_CONVERT -> openIncoming(inc, true)
                OpenPolicy.ASK -> pendingIncoming = inc
            }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "shelf",
            modifier = Modifier.padding(
                bottom = if (showBottomBar) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f),
            ),
        ) {
            composable("shelf") {
                ShelfRoute(app, onOpenBook = { id -> nav.navigate("reader/$id") }, onGoConversions = {})
            }
            composable("conversions") { ConversionsRoute(app) }
            composable("settings") {
                SettingsRoute(app, onOpenFonts = { nav.navigate("fonts") })
            }
            composable("fonts") {
                FontsRoute(app, onBack = { nav.popBackStack() })
            }
            composable("reader/{bookId}") { entry ->
                val bookId = entry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
                ReaderRoute(bookId, app, onBack = { nav.popBackStack() })
            }
        }
    }

    pendingIncoming?.let { inc ->
        AlertDialog(
            onDismissRequest = { pendingIncoming = null },
            title = { Text("打开方式") },
            text = {
                Text(
                    "「${inc.uri.lastPathSegment ?: "文件"}」已收到。\n" +
                        "仅阅读:直接入库阅读;自动转换:转为 ${currentSettings.defaultConvertTarget} 并入库。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    val p = pendingIncoming
                    pendingIncoming = null
                    p?.let { openIncoming(it, false) }
                }) { Text("仅阅读") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val p = pendingIncoming
                    pendingIncoming = null
                    p?.let { openIncoming(it, true) }
                }) { Text("转换并入库") }
            },
        )
    }
}
