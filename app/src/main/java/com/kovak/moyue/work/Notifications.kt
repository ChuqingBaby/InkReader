package com.kovak.moyue.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kovak.moyue.R

object Notifications {
    const val CHANNEL_CONVERT = "convert"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_CONVERT,
            "格式转换",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "书籍格式转换进度与完成通知"
        }
        nm.createNotificationChannel(channel)
    }

    fun progress(context: Context, taskId: Long, title: String, percent: Int) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_CONVERT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("正在转换:$title")
                .setContentText("$percent%")
                .setProgress(100, percent, percent < 0)
                .setOngoing(true)
                .setSilent(true)
                .build()
            NotificationManagerCompat.from(context).notify(notifyId(taskId), notification)
        } catch (e: SecurityException) {
        }
    }

    fun done(context: Context, taskId: Long, title: String, ok: Boolean, message: String?) {
        try {
            val text = if (ok) "转换完成:$title" else "转换失败:$title${if (message.isNullOrBlank()) "" else "($message)"}"
            val notification = NotificationCompat.Builder(context, CHANNEL_CONVERT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(if (ok) "转换完成" else "转换失败")
                .setContentText(text)
                .setOngoing(false)
                .build()
            NotificationManagerCompat.from(context).notify(notifyId(taskId), notification)
        } catch (e: SecurityException) {
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(notifyId(taskId))
    }

    private fun notifyId(taskId: Long) = (100000 + taskId).toInt()
}
