package com.kovak.moyue.format

import java.io.File

/**
 * MOBI 提取(纯 JVM):PDB 记录表 -> MOBI 头 -> 正文记录。
 * 支持:未压缩(2)/PalmDOC 压缩(1);不支持 HUFF/CDIC(17480)与加密,报明确错误。
 */
object MobiReader {

    fun parse(file: File, chapterRegex: String = ChapterSplitter.DEFAULT_REGEX): ParsedBook {
        val bytes = file.readBytes()
        if (bytes.size < 132 || String(bytes, 60, 4, Charsets.US_ASCII) != "BOOK" ||
            String(bytes, 64, 4, Charsets.US_ASCII) != "MOBI"
        ) throw IllegalArgumentException("不是有效的 MOBI 文件")
        val numRecords = u16(bytes, 76)
        if (numRecords < 2) throw IllegalArgumentException("MOBI 记录表为空")
        val offsets = IntArray(numRecords) { i ->
            u32(bytes, 78 + i * 8).toInt()
        }

        val rec0Start = offsets[0]
        val rec0End = if (numRecords > 1) offsets[1] else bytes.size
        val rec0 = bytes.copyOfRange(rec0Start, rec0End)

        val compression = u16(rec0, 0)
        val encryption = u16(rec0, 12)
        if (encryption != 0) throw IllegalArgumentException("该 MOBI 已加密,暂不支持")
        val textRecordCount = u16(rec0, 8)
        val mobiMagic = if (rec0.size >= 20) String(rec0, 16, 4, Charsets.US_ASCII) else ""
        val textEncoding = if (mobiMagic == "MOBI" && rec0.size >= 32) u32(rec0, 28) else 65001L
        val mobiVersion = if (mobiMagic == "MOBI" && rec0.size >= 40) u32(rec0, 36) else 0L
        var extraFlags = if (mobiMagic == "MOBI" && mobiVersion >= 5 && rec0.size >= 244) u16(rec0, 0xF2) else 0

        // 书名:record0 内偏移 fullNameOffset
        var fullName = ""
        if (mobiMagic == "MOBI" && rec0.size >= 92) {
            try {
                val nameOff = u32(rec0, 84).toInt()
                val nameLen = u32(rec0, 88).toInt()
                if (nameOff in rec0.indices && nameLen > 0 && nameOff + nameLen <= rec0.size) {
                    fullName = String(rec0, nameOff, nameLen, Charsets.UTF_16BE).trim()
                }
            } catch (e: Exception) {
            }
        }

        // EXTH 元数据(可选):100=作者 503=标题
        var exthAuthor = ""
        var exthTitle = ""
        try {
            if (mobiMagic == "MOBI" && rec0.size >= 20) {
                val headerLen = u32(rec0, 20).toInt()
                val exthOff = 16 + headerLen
                if (exthOff + 12 <= rec0.size && String(rec0, exthOff, 4, Charsets.US_ASCII) == "EXTH") {
                    var p = exthOff + 12
                    val recCount = u32(rec0, exthOff + 8).toInt()
                    repeat(recCount) {
                        if (p + 8 > rec0.size) return@repeat
                        val type = u32(rec0, p).toInt()
                        val len = u32(rec0, p + 4).toInt()
                        if (len < 8 || p + len > rec0.size) return@repeat
                        val value = String(rec0, p + 8, len - 8, Charsets.UTF_8).trim()
                        when (type) {
                            100 -> if (exthAuthor.isEmpty()) exthAuthor = value
                            503 -> if (exthTitle.isEmpty()) exthTitle = value
                        }
                        p += len
                    }
                }
            }
        } catch (e: Exception) {
        }

        // 正文
        if (compression == 17480) throw IllegalArgumentException("该 MOBI 使用 HUFF/CDIC 压缩,暂不支持")
        if (compression != 1 && compression != 2) throw IllegalArgumentException("未知压缩方式: $compression")
        val charset = when (textEncoding) {
            65001L -> Charsets.UTF_8
            936L, 10008L -> charset("GBK")
            1252L -> charset("windows-1252")
            else -> Charsets.UTF_8
        }
        val out = StringBuilder()
        val end = (textRecordCount + 1).coerceAtMost(numRecords)
        for (i in 1 until end) {
            val start = offsets[i]
            val stop = if (i + 1 < numRecords) offsets[i + 1] else bytes.size
            if (start < 0 || stop > bytes.size || start >= stop) continue
            var chunk = bytes.copyOfRange(start, stop)
            if (mobiVersion >= 5 && extraFlags != 0) {
                chunk = stripTrailing(chunk, extraFlags)
            }
            val raw = if (compression == 1) palmDocDecompress(chunk) else chunk
            out.append(String(raw, charset)).append('\n')
        }

        val title = exthTitle.ifBlank { fullName.ifBlank { file.nameWithoutExtension } }
        val chapters = ChapterSplitter.splitToParsed(out.toString(), chapterRegex)
        return ParsedBook(title, exthAuthor, chapters)
    }

    /** 去掉每条文本记录尾部的 extra data(trailing entries + multibyte overlap)。 */
    internal fun stripTrailing(data: ByteArray, extraFlags: Int): ByteArray {
        var end = data.size
        var flags = extraFlags shr 1 // bit0 = multibyte overlap,单独处理
        while (flags != 0) {
            if (flags and 1 != 0 && end > 0) {
                end -= sizeOfTrailingEntry(data, end)
            }
            flags = flags shr 1
        }
        if (extraFlags and 1 != 0 && end > 0) {
            end -= ((data[end - 1].toInt() and 0x3) + 1).coerceAtMost(end)
        }
        return if (end == data.size) data else data.copyOf(end)
    }

    /** 可变宽度反向长度编码:尾部连续高两位为 11 的字节组成 7bit 大端长度。 */
    private fun sizeOfTrailingEntry(data: ByteArray, end: Int): Int {
        var num = 0
        for (i in (end - 4).coerceAtLeast(0) until end) {
            val v = data[i].toInt() and 0xFF
            if (v and 0xC0 != 0xC0) return num
            num = (num shl 7) or (v and 0x7F)
        }
        return num
    }

    internal fun palmDocDecompress(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size * 4)
        var i = 0
        val n = data.size
        while (i < n) {
            val u = data[i].toInt() and 0xFF
            i++
            when {
                u == 0 -> out.write(0)
                u <= 8 -> { // 1..8: 后续 u 字节为字面量
                    val cnt = u
                    if (i + cnt > n) break
                    out.write(data, i, cnt)
                    i += cnt
                }
                u <= 0x7F -> out.write(u)
                u <= 0xBF -> {
                    if (i >= n) break
                    val z = (u shl 8) or (data[i].toInt() and 0xFF)
                    i++
                    val dist = (z shr 3) and 0x7FF
                    val length = (z and 0x7) + 3
                    val cur = out.size()
                    if (dist == 0 || dist > cur) break
                    val buf = out.toByteArray()
                    for (j in 0 until length) {
                        out.write(buf[cur - dist + (j % dist)].toInt())
                    }
                }
                else -> { // 0xC0..0xFF: 空格 + 字面量
                    out.write(' '.code)
                    out.write(u and 0x7F)
                }
            }
        }
        return out.toByteArray()
    }

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)
}
