package com.kovak.moyue.format

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 文本编码探测:纯 JVM 实现,可单测。
 * 策略:BOM > UTF-16 零字节分布 > 严格 UTF-8 校验 > GBK/GB18030 严格解码 > 兜底 UTF-8。
 */
object EncodingDetector {

    const val AUTO = "AUTO"
    val SUPPORTED = listOf(AUTO, "UTF-8", "GBK", "UTF-16")

    data class Result(val charset: Charset, val label: String)

    fun resolve(name: String?): Charset = when (name?.uppercase()) {
        "UTF-8" -> Charsets.UTF_8
        "GBK", "GB18030", "GB2312" -> charset("GBK")
        "UTF-16", "UTF-16LE" -> Charsets.UTF_16LE
        "UTF-16BE" -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    fun detect(bytes: ByteArray): Result {
        if (bytes.size >= 3 && bytes[0].b() == 0xEF && bytes[1].b() == 0xBB && bytes[2].b() == 0xBF) {
            return Result(Charsets.UTF_8, "UTF-8")
        }
        if (bytes.size >= 2 && bytes[0].b() == 0xFF && bytes[1].b() == 0xFE) {
            return Result(Charsets.UTF_16LE, "UTF-16")
        }
        if (bytes.size >= 2 && bytes[0].b() == 0xFE && bytes[1].b() == 0xFF) {
            return Result(Charsets.UTF_16BE, "UTF-16")
        }
        // UTF-16 无 BOM:检查 NUL 字节分布
        if (bytes.size >= 8) {
            var evenZero = 0
            var oddZero = 0
            for (i in 0 until minOf(bytes.size, 4096)) {
                if (bytes[i] == 0.toByte()) {
                    if (i % 2 == 0) evenZero++ else oddZero++
                }
            }
            val half = bytes.size / 2
            if (oddZero > half * 3 / 10) return Result(Charsets.UTF_16LE, "UTF-16")
            if (evenZero > half * 3 / 10) return Result(Charsets.UTF_16BE, "UTF-16")
        }
        // 严格 UTF-8 校验(有真正的多字节序列才算强证据)
        val utf8 = validateUtf8(bytes)
        if (utf8.first && (utf8.second > 0 || isMostlyAscii(bytes))) {
            return Result(Charsets.UTF_8, "UTF-8")
        }
        // GBK / GB18030 严格解码
        if (canDecode(bytes, "GB18030")) {
            return Result(charset("GBK"), "GBK")
        }
        if (canDecode(bytes, "GBK")) {
            return Result(charset("GBK"), "GBK")
        }
        return Result(Charsets.UTF_8, "UTF-8")
    }

    private fun isMostlyAscii(bytes: ByteArray): Boolean {
        var printable = 0
        val n = minOf(bytes.size, 2048)
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x20..0x7E || b == 0x0A || b == 0x0D) printable++
        }
        return n > 0 && printable * 20 >= n * 19
    }

    /** 返回 (是否严格有效 UTF-8, 多字节序列数) */
    fun validateUtf8(bytes: ByteArray): Pair<Boolean, Int> {
        var multi = 0
        var i = 0
        val n = bytes.size
        while (i < n) {
            val b0 = bytes[i].toInt() and 0xFF
            when {
                b0 < 0x80 -> i++
                b0 in 0xC2..0xDF -> {
                    if (i + 1 >= n || !isCont(bytes[i + 1])) return false to multi
                    multi++; i += 2
                }
                b0 == 0xE0 -> {
                    if (i + 2 >= n || !isCont(bytes[i + 1], 0xA0) || !isCont(bytes[i + 2])) return false to multi
                    multi++; i += 3
                }
                b0 in 0xE1..0xEC || b0 in 0xEE..0xEF -> {
                    if (i + 2 >= n || !isCont(bytes[i + 1]) || !isCont(bytes[i + 2])) return false to multi
                    multi++; i += 3
                }
                b0 == 0xED -> {
                    if (i + 2 >= n || !isCont(bytes[i + 1], 0x80) || !isCont(bytes[i + 2])) return false to multi
                    multi++; i += 3
                }
                b0 == 0xF0 -> {
                    if (i + 3 >= n || !isCont(bytes[i + 1], 0x90) || !isCont(bytes[i + 2]) || !isCont(bytes[i + 3])) return false to multi
                    multi++; i += 4
                }
                b0 in 0xF1..0xF3 -> {
                    if (i + 3 >= n || !isCont(bytes[i + 1]) || !isCont(bytes[i + 2]) || !isCont(bytes[i + 3])) return false to multi
                    multi++; i += 4
                }
                b0 == 0xF4 -> {
                    if (i + 3 >= n || !isCont(bytes[i + 1], 0x80) || !isCont(bytes[i + 2]) || !isCont(bytes[i + 3])) return false to multi
                    multi++; i += 4
                }
                else -> return false to multi
            }
        }
        return true to multi
    }

    private fun isCont(b: Byte, lowerInclusive: Int = 0x80): Boolean {
        val v = b.toInt() and 0xFF
        return v in lowerInclusive..0xBF
    }

    private fun canDecode(bytes: ByteArray, name: String): Boolean = try {
        val decoder = Charset.forName(name).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(java.nio.ByteBuffer.wrap(bytes))
        true
    } catch (e: Exception) {
        false
    }

    private fun Byte.b(): Int = toInt() and 0xFF
}
