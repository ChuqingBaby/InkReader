package com.kovak.moyue.format

import java.io.File
import java.nio.charset.Charset

/**
 * 抽象转换器接口:from -> to。后续扩展 fb2/azw3 等只需实现并注册。
 */
interface Converter {
    val from: BookFormat
    val to: BookFormat
    fun convert(input: File, output: File, options: ConvertOptions, onProgress: (Float) -> Unit)
}

data class ConvertOptions(
    val encodingIn: String = EncodingDetector.AUTO,
    val encodingOut: String = "UTF-8",
    val chapterRegex: String = ChapterSplitter.DEFAULT_REGEX,
)

object ConvertRegistry {

    private val converters: List<Converter> = listOf(
        TxtToEpubConverter(),
        EpubToTxtConverter(),
        MobiToEpubConverter(),
        MobiToTxtConverter(),
    )

    fun find(from: BookFormat, to: BookFormat): Converter? =
        converters.firstOrNull { it.from == from && it.to == to }
}

/** 解码文本:手动指定或自动探测;无效字节替换,不抛异常 */
fun decodeText(bytes: ByteArray, encodingIn: String): String {
    val charset: Charset = if (encodingIn == EncodingDetector.AUTO) {
        EncodingDetector.detect(bytes).charset
    } else {
        EncodingDetector.resolve(encodingIn)
    }
    val decoder = charset.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
    return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
}

/** TXT -> EPUB:编码探测 + 章节切分 + 标准 epub 打包 */
class TxtToEpubConverter : Converter {
    override val from = BookFormat.TXT
    override val to = BookFormat.EPUB

    override fun convert(input: File, output: File, options: ConvertOptions, onProgress: (Float) -> Unit) {
        val text = decodeText(input.readBytes(), options.encodingIn)
        onProgress(0.3f)
        val parsed = ParsedBook(input.nameWithoutExtension, "", ChapterSplitter.splitToParsed(text, options.chapterRegex))
        onProgress(0.5f)
        EpubWriter.write(parsed, output) { onProgress(0.5f + it * 0.5f) }
    }
}

/** EPUB -> TXT:解析 spine,按章节拼接为纯文本 */
class EpubToTxtConverter : Converter {
    override val from = BookFormat.EPUB
    override val to = BookFormat.TXT

    override fun convert(input: File, output: File, options: ConvertOptions, onProgress: (Float) -> Unit) {
        val parsed = EpubReader.parse(input)
        onProgress(0.6f)
        writeTxt(parsed, output, options.encodingOut)
        onProgress(1f)
    }
}

/** MOBI -> EPUB */
class MobiToEpubConverter : Converter {
    override val from = BookFormat.MOBI
    override val to = BookFormat.EPUB

    override fun convert(input: File, output: File, options: ConvertOptions, onProgress: (Float) -> Unit) {
        val parsed = MobiReader.parse(input, options.chapterRegex)
        onProgress(0.5f)
        EpubWriter.write(parsed, output) { onProgress(0.5f + it * 0.5f) }
    }
}

/** MOBI -> TXT */
class MobiToTxtConverter : Converter {
    override val from = BookFormat.MOBI
    override val to = BookFormat.TXT

    override fun convert(input: File, output: File, options: ConvertOptions, onProgress: (Float) -> Unit) {
        val parsed = MobiReader.parse(input, options.chapterRegex)
        onProgress(0.6f)
        writeTxt(parsed, output, options.encodingOut)
        onProgress(1f)
    }
}

/** 按章节写出纯文本(章节标题行 + 缩进段落) */
fun writeTxt(parsed: ParsedBook, output: File, encodingOut: String) {
    val sb = StringBuilder()
    parsed.chapters.forEachIndexed { i, ch ->
        if (i > 0) sb.append('\n')
        sb.append(ch.title).append("\n\n")
        ch.paragraphs.forEach { p ->
            sb.append("    ").append(p).append("\n\n")
        }
    }
    val charset = EncodingDetector.resolve(encodingOut)
    output.writeText(sb.toString(), charset)
}
