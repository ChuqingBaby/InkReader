package com.kovak.moyue.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 莫兰迪系占位封面色板 */
val CoverPalette = listOf(
    Color(0xFFB7A99A),
    Color(0xFFA8B5A2),
    Color(0xFFB5A8B5),
    Color(0xFFA9B6C4),
    Color(0xFFC9B27C),
    Color(0xFFC4A49A),
)

/** 无封面书籍:纯色底 + 书名首字 */
@Composable
fun PlaceholderCover(title: String, colorIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    val color = CoverPalette[colorIndex.coerceIn(0, CoverPalette.size - 1)]
    val firstChar = title.trim().firstOrNull()?.toString() ?: "书"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = firstChar,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = (size.value * 0.38f).sp,
            maxLines = 1,
        )
    }
}

/** 空状态:简单 Canvas 小书 + 引导文案,带轻微呼吸动效 */
@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "empty")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val lineColor = MaterialTheme.colorScheme.outline
        Canvas(modifier = Modifier.height(84.dp).alpha(alpha)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 3f, cap = StrokeCap.Round)
            // 简笔翻开的书:两页 + 中缝
            drawLine(lineColor, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), stroke.width)
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.12f, h * 0.18f),
                size = Size(w * 0.38f, h * 0.6f),
                cornerRadius = CornerRadius(10f, 10f),
                style = stroke,
            )
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(w * 0.5f, h * 0.18f),
                size = Size(w * 0.38f, h * 0.6f),
                cornerRadius = CornerRadius(10f, 10f),
                style = stroke,
            )
            drawLine(lineColor, Offset(w * 0.2f, h * 0.38f), Offset(w * 0.42f, h * 0.38f), stroke.width * 0.6f)
            drawLine(lineColor, Offset(w * 0.2f, h * 0.52f), Offset(w * 0.42f, h * 0.52f), stroke.width * 0.6f)
            drawLine(lineColor, Offset(w * 0.58f, h * 0.38f), Offset(w * 0.8f, h * 0.38f), stroke.width * 0.6f)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
