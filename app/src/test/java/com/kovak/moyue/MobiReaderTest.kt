package com.kovak.moyue

import com.kovak.moyue.format.MobiReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class MobiReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun putU32(out: ByteArrayOutputStream, v: Long) {
        out.write(((v shr 24) and 0xFF).toInt())
        out.write(((v shr 16) and 0xFF).toInt())
        out.write(((v shr 8) and 0xFF).toInt())
        out.write((v and 0xFF).toInt())
    }

    private fun putU16(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    /** 构造一个最小可解析的未压缩 MOBI(KF6) */
    private fun buildMobi(fullName: String, text: String): ByteArray {
        val nameBytes = fullName.toByteArray(Charsets.UTF_16BE)
        val rec0 = ByteArrayOutputStream()
        // PalmDOC header (16 bytes)
        putU16(rec0, 2)          // compression = 2 (无压缩)
        putU16(rec0, 0)          // unused
        putU32(rec0, text.toByteArray(Charsets.UTF_8).size.toLong()) // textLength
        putU16(rec0, 1)          // textRecordCount = 1
        putU16(rec0, 4096)       // recordSize
        putU16(rec0, 0)          // encryptionType
        putU16(rec0, 0)          // unused
        // MOBI header (232 bytes)
        rec0.write("MOBI".toByteArray(Charsets.US_ASCII))
        putU32(rec0, 232)        // headerLength
        putU32(rec0, 2)          // mobiType = BOOK
        putU32(rec0, 65001)      // textEncoding = UTF-8
        putU32(rec0, 0x1234)     // uniqueID
        putU32(rec0, 6)          // fileVersion
        // 剩余保留字段到 offset 84(fullNameOffset 字段位置)
        repeat(44) { rec0.write(0) } // 40..83
        putU32(rec0, 248L)       // fullNameOffset
        putU32(rec0, nameBytes.size.toLong()) // fullNameLength
        // 92..241 保留(locale、huffman 等全部 0)
        repeat(150) { rec0.write(0) }
        putU16(rec0, 0)          // extraFlags = 0 (242..244)
        putU32(rec0, 0xFFFFFFFFL) // INDX rec offset = -1? 用 0xFFFFFFFF 常见
        // 到 248 为止(16+232)。写书名
        rec0.write(nameBytes)

        val rec0Bytes = rec0.toByteArray()

        val pdb = ByteArrayOutputStream()
        // PDB header 78 bytes
        pdb.write("TestBook".toByteArray(Charsets.US_ASCII))
        repeat(24) { pdb.write(0) }  // name 补齐 32
        putU16(pdb, 0)               // attributes
        putU16(pdb, 0)               // version
        putU32(pdb, 0)               // created
        putU32(pdb, 0)               // modified
        putU32(pdb, 0)               // backup
        putU32(pdb, 0)               // modificationNumber
        putU32(pdb, 0)               // appInfoID
        putU32(pdb, 0)               // sortInfoID
        pdb.write("BOOK".toByteArray(Charsets.US_ASCII))  // type @60
        pdb.write("MOBI".toByteArray(Charsets.US_ASCII))  // creator @64
        putU32(pdb, 0)               // uniqueIDseed
        putU32(pdb, 0)               // nextRecordListID
        putU16(pdb, 2)               // numRecords = 2 @76
        // record list:每条 8 字节 = offset(4) + attributes(1) + uniqueID(3)
        putU32(pdb, 96L)             // rec0 offset: 78 + 2*8 + 2 = 96
        pdb.write(0); pdb.write(0); pdb.write(0); pdb.write(0)
        putU32(pdb, (96 + rec0Bytes.size).toLong()) // rec1 offset
        pdb.write(0); pdb.write(0); pdb.write(0); pdb.write(0)
        pdb.write(0); pdb.write(0)   // gap
        assertEquals(96, pdb.size())

        pdb.write(rec0Bytes)
        pdb.write(text.toByteArray(Charsets.UTF_8))
        return pdb.toByteArray()
    }

    @Test
    fun `parses minimal uncompressed mobi`() {
        val bytes = buildMobi("测试之书", "第一章 起点\n\n你好,世界。\n\n第二章 继续\n\n更多内容。")
        val file = tmp.newFile("test.mobi")
        file.writeBytes(bytes)
        val book = MobiReader.parse(file)
        assertEquals("测试之书", book.title)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 起点", book.chapters[0].title)
        assertTrue(book.chapters[0].paragraphs.contains("你好,世界。"))
        assertEquals("第二章 继续", book.chapters[1].title)
    }

    @Test
    fun `rejects non-mobi file`() {
        val file = tmp.newFile("bad.mobi")
        file.writeBytes("这不是一个mobi文件,内容随意填充一点长度xxxxxxxxxxxxxxxxxxxx".toByteArray())
        try {
            MobiReader.parse(file)
            throw AssertionError("应当抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("MOBI"))
        }
    }

    @Test
    fun `palmdoc decompression`() {
        // 字面量: 0x41 'A'; 空格+字面量: 0xE1 -> " A" 之外形式; 距离引用: 需要历史
        val data = byteArrayOf(0x05, 'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte())
        val out = String(MobiReader.palmDocDecompress(data), Charsets.US_ASCII)
        assertEquals("Hello", out)
    }
}
