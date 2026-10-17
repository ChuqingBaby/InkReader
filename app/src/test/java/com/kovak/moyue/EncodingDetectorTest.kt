package com.kovak.moyue

import com.kovak.moyue.format.EncodingDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class EncodingDetectorTest {

    @Test
    fun `utf8 chinese detected`() {
        val bytes = "你好,世界!Hello".toByteArray(Charsets.UTF_8)
        val r = EncodingDetector.detect(bytes)
        assertEquals("UTF-8", r.label)
    }

    @Test
    fun `gbk chinese detected`() {
        // 「你好」的 GBK 编码首字节即破坏 UTF-8 序列,确保判定不歧义
        val bytes = "你好世界,这段中文用国标编码保存。".toByteArray(Charset.forName("GBK"))
        val r = EncodingDetector.detect(bytes)
        assertEquals("GBK", r.label)
    }

    @Test
    fun `utf16le bom detected`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "你好".toByteArray(Charsets.UTF_16LE)
        val r = EncodingDetector.detect(bytes)
        assertEquals("UTF-16", r.label)
    }

    @Test
    fun `utf16be bom detected`() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "你好".toByteArray(Charsets.UTF_16BE)
        val r = EncodingDetector.detect(bytes)
        assertEquals("UTF-16", r.label)
    }

    @Test
    fun `ascii stays utf8`() {
        val r = EncodingDetector.detect("plain ascii text 123".toByteArray(Charsets.US_ASCII))
        assertEquals("UTF-8", r.label)
    }

    @Test
    fun `validateUtf8 rejects broken sequences`() {
        val broken = byteArrayOf(0x61.toByte(), 0xE4.toByte(), 0xB8.toByte(), 0x61.toByte()) // E4 后缺连续字节
        val (valid, _) = EncodingDetector.validateUtf8(broken)
        assertFalse(valid)
        val good = "中文abc".toByteArray(Charsets.UTF_8)
        val (valid2, multi) = EncodingDetector.validateUtf8(good)
        assertTrue(valid2)
        assertEquals(2, multi)
    }

    @Test
    fun `resolve manual encoding`() {
        assertEquals(Charset.forName("GBK"), EncodingDetector.resolve("GBK"))
        assertEquals(Charsets.UTF_8, EncodingDetector.resolve("UTF-8"))
        assertEquals(Charsets.UTF_16LE, EncodingDetector.resolve("UTF-16"))
    }
}
