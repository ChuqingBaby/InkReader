package com.kovak.moyue

import android.app.Application
import android.net.Uri
import com.kovak.moyue.backup.BackupManager
import com.kovak.moyue.data.db.AppDatabase
import com.kovak.moyue.data.SettingsRepository
import com.kovak.moyue.fonts.FontRepository
import com.kovak.moyue.io.BookImporter
import com.kovak.moyue.work.ConvertManager
import com.kovak.moyue.work.Notifications
import kotlinx.coroutines.flow.MutableSharedFlow

/** 外部「打开方式」传入的文件 */
data class IncomingFile(val uri: Uri, val mime: String?)

class AppContainer(app: Application) {
    val appContext: android.content.Context = app.applicationContext
    val db: AppDatabase by lazy { AppDatabase.get(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val importer: BookImporter by lazy { BookImporter(app, db) }
    val convertManager: ConvertManager by lazy { ConvertManager(app, db, importer) }
    val fonts: FontRepository by lazy { FontRepository(app, db) }
    val backup: BackupManager by lazy { BackupManager(app, db, importer) }
    val incoming = MutableSharedFlow<IncomingFile>(extraBufferCapacity = 8)
}

class InkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
