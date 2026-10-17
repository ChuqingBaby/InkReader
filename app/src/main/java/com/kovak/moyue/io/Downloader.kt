package com.kovak.moyue.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * URL 导入:直链下载 txt/epub/mobi(带进度);普通网页则抓取正文转存为 txt。
 */
class Downloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    enum class Kind { FILE, WEBPAGE }

    data class Downloaded(val file: File, val displayName: String, val kind: Kind)

    /** 下载到 cacheDir/download。onProgress 0..1。 */
    suspend fun download(
        url: String,
        destDir: File,
        onProgress: (Float) -> Unit,
    ): Downloaded = withContext(Dispatchers.IO) {
        val httpUrl = url.trim()
        require(httpUrl.startsWith("http://") || httpUrl.startsWith("https://")) { "URL 必须以 http(s):// 开头" }
        destDir.mkdirs()
        val request = Request.Builder().url(httpUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) InkReader/1.0")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw RuntimeException("网络连接失败:${e.message ?: "未知错误"}")
        }
        response.use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("服务器返回错误码 ${resp.code}")
            val contentType = resp.header("Content-Type") ?: ""
            val disposition = resp.header("Content-Disposition") ?: ""
            val isHtml = contentType.contains("text/html", ignoreCase = true)

            if (isHtml) {
                // 网页正文 -> txt
                val bodyText = try {
                    resp.body?.string() ?: throw RuntimeException("响应为空")
                } catch (e: Exception) {
                    throw RuntimeException("读取网页失败:${e.message}")
                }
                val doc = Jsoup.parse(bodyText, httpUrl)
                val title = doc.title().ifBlank { "网页文章" }
                val paras = doc.body()?.select("p, article, div[class~=content], h1, h2")
                    ?.orEmpty()
                    ?.mapNotNull { el ->
                        val t = el.ownText().trim()
                        t.ifEmpty { null }
                    }
                    ?.filter { it.length >= 4 }
                    ?.distinct()
                    ?: emptyList()
                val text = buildString {
                    append(title).append("\n\n")
                    if (paras.isEmpty()) {
                        append(doc.body()?.text()?.replace(Regex("。"), "。\n") ?: "(未能提取正文)")
                    } else {
                        paras.forEach { append(it).append("\n\n") }
                    }
                }
                val file = File(destDir, "web_${System.currentTimeMillis()}.txt")
                file.writeText(text, Charsets.UTF_8)
                onProgress(1f)
                Downloaded(file, "$title.txt", Kind.WEBPAGE)
            } else {
                val body = resp.body ?: throw RuntimeException("响应为空")
                val total = body.contentLength()
                val name = parseFileName(disposition, httpUrl) ?: "download_${System.currentTimeMillis()}.txt"
                val file = File(destDir, name)
                var written = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            written += n
                            if (total > 0) {
                                val pct = (written * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(written.toFloat() / total)
                                }
                            }
                        }
                    }
                }
                if (total <= 0) onProgress(1f)
                if (file.length() == 0L) throw RuntimeException("下载内容为空")
                Downloaded(file, name, Kind.FILE)
            }
        }
    }

    private fun parseFileName(disposition: String, url: String): String? {
        Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)").find(disposition)?.let {
            return java.net.URLDecoder.decode(it.groupValues[1].trim(), "UTF-8")
        }
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        return if (last.isNotBlank() && last.contains('.')) {
            java.net.URLDecoder.decode(last, "UTF-8")
        } else null
    }
}
