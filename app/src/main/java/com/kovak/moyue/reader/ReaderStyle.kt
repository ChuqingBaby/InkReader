package com.kovak.moyue.reader

import androidx.compose.ui.text.font.FontFamily
import com.kovak.moyue.data.PageMode
import com.kovak.moyue.data.ReaderTheme

/** 阅读器渲染样式(由设置派生,含解析好的字体族) */
data class ReaderStyle(
    val fontSizeSp: Int,
    val lineSpacing: Float,
    val paraSpacingDp: Int,
    val hMarginDp: Int,
    val vMarginDp: Int,
    val theme: ReaderTheme,
    val pageMode: PageMode,
    val justify: Boolean,
    val fontFamily: FontFamily,
)
