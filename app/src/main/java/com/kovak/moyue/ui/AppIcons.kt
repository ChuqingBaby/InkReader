package com.kovak.moyue.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 极简线性自绘图标:单色、细线 1.7、圆角端点,由圆/线/圆角矩形拼成。
 * 统一用 Icon(tint) 上色(内部为黑色描边)。
 */
object AppIcons {

    private fun builder(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 1.7f, block: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }

    /** 圆:两段半弧拼成(arcTo 为 SVG A 命令式:rx,ry,theta,largeArc,sweep,x,y) */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
    }

    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
    }

    val Shelf: ImageVector by lazy {
        builder("Shelf") {
            stroke {
                moveTo(5.5f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); lineTo(5.5f, 20f); close()
            }
            stroke {
                moveTo(14f, 8f); lineTo(18.5f, 8f); lineTo(18.5f, 20f); lineTo(14f, 20f)
            }
        }
    }

    val Convert: ImageVector by lazy {
        builder("Convert") {
            stroke {
                moveTo(4f, 9f); lineTo(17f, 9f); moveTo(14f, 6f); lineTo(17f, 9f); moveTo(14f, 12f); lineTo(17f, 9f)
            }
            stroke {
                moveTo(20f, 15f); lineTo(7f, 15f); moveTo(10f, 12f); lineTo(7f, 15f); moveTo(10f, 18f); lineTo(7f, 15f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        builder("Settings") {
            stroke {
                moveTo(4.5f, 7f); lineTo(19.5f, 7f)
                moveTo(4.5f, 12f); lineTo(19.5f, 12f)
                moveTo(4.5f, 17f); lineTo(19.5f, 17f)
            }
            stroke { circle(9.5f, 7f, 1.9f) }
            stroke { circle(15f, 12f, 1.9f) }
            stroke { circle(8f, 17f, 1.9f) }
        }
    }

    val Plus: ImageVector by lazy {
        builder("Plus") {
            stroke {
                moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }
    }

    val Search: ImageVector by lazy {
        builder("Search") {
            stroke { circle(11f, 11f, 5.2f) }
            stroke { moveTo(14.8f, 14.8f); lineTo(20f, 20f) }
        }
    }

    val Close: ImageVector by lazy {
        builder("Close") {
            stroke {
                moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    val Back: ImageVector by lazy {
        builder("Back") {
            stroke {
                moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        builder("ChevronRight") {
            stroke {
                moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f)
            }
        }
    }

    val Grid: ImageVector by lazy {
        builder("Grid") {
            stroke {
                rect(4.5f, 4.5f, 6f, 6f)
                rect(13.5f, 4.5f, 6f, 6f)
                rect(4.5f, 13.5f, 6f, 6f)
                rect(13.5f, 13.5f, 6f, 6f)
            }
        }
    }

    val ListView: ImageVector by lazy {
        builder("ListView") {
            stroke {
                moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
                moveTo(4.5f, 12f); lineTo(19.5f, 12f)
                moveTo(4.5f, 17.5f); lineTo(13.5f, 17.5f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        builder("Trash") {
            stroke {
                moveTo(4.5f, 7f); lineTo(19.5f, 7f)
                moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
            }
            stroke {
                moveTo(7f, 7f); lineTo(8f, 20f); lineTo(16f, 20f); lineTo(17f, 7f)
                moveTo(10f, 10.5f); lineTo(10.4f, 16.5f)
                moveTo(14f, 10.5f); lineTo(13.6f, 16.5f)
            }
        }
    }

    val Edit: ImageVector by lazy {
        builder("Edit") {
            stroke {
                moveTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); lineTo(4.5f, 19.5f); close()
            }
            stroke { moveTo(13.8f, 6.7f); lineTo(17.3f, 10.2f) }
        }
    }

    val Sort: ImageVector by lazy {
        builder("Sort") {
            stroke {
                moveTo(4.5f, 7f); lineTo(14f, 7f)
                moveTo(4.5f, 12f); lineTo(11f, 12f)
                moveTo(4.5f, 17f); lineTo(8f, 17f)
            }
            stroke {
                moveTo(17.5f, 7f); lineTo(17.5f, 19f); moveTo(14.5f, 16f); lineTo(17.5f, 19f); moveTo(20.5f, 16f); lineTo(17.5f, 19f)
            }
        }
    }

    val Bookmark: ImageVector by lazy {
        builder("Bookmark") {
            stroke {
                moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 15.5f); lineTo(7f, 20f); close()
            }
        }
    }

    val BookmarkFill: ImageVector by lazy {
        builder("BookmarkFill") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 15.5f); lineTo(7f, 20f); close()
            }
        }
    }

    val Sun: ImageVector by lazy {
        builder("Sun") {
            stroke { circle(12f, 12f, 3.8f) }
            stroke {
                moveTo(12f, 3f); lineTo(12f, 5.5f)
                moveTo(12f, 18.5f); lineTo(12f, 21f)
                moveTo(3f, 12f); lineTo(5.5f, 12f)
                moveTo(18.5f, 12f); lineTo(21f, 12f)
                moveTo(5.6f, 5.6f); lineTo(7.4f, 7.4f)
                moveTo(16.6f, 16.6f); lineTo(18.4f, 18.4f)
                moveTo(18.4f, 5.6f); lineTo(16.6f, 7.4f)
                moveTo(7.4f, 16.6f); lineTo(5.6f, 18.4f)
            }
        }
    }

    val Font: ImageVector by lazy {
        builder("Font") {
            stroke {
                moveTo(5f, 19f); lineTo(10.2f, 5f); lineTo(15.4f, 19f)
                moveTo(6.9f, 14f); lineTo(13.5f, 14f)
            }
            stroke { moveTo(17f, 19f); lineTo(17f, 11f); moveTo(14.8f, 13f); lineTo(19.2f, 13f) }
        }
    }

    val Globe: ImageVector by lazy {
        builder("Globe") {
            stroke { circle(12f, 12f, 8f) }
            stroke { moveTo(4f, 12f); lineTo(20f, 12f) }
            stroke { moveTo(12f, 4f); lineTo(12f, 20f) }
        }
    }

    val Folder: ImageVector by lazy {
        builder("Folder") {
            stroke {
                moveTo(4f, 6.5f); lineTo(10f, 6.5f); lineTo(12f, 9f); lineTo(20f, 9f); lineTo(20f, 19f); lineTo(4f, 19f); close()
            }
        }
    }

    val Download: ImageVector by lazy {
        builder("Download") {
            stroke {
                moveTo(12f, 4f); lineTo(12f, 14.5f)
                moveTo(8f, 11f); lineTo(12f, 15f); lineTo(16f, 11f)
            }
            stroke { moveTo(4.5f, 19.5f); lineTo(19.5f, 19.5f) }
        }
    }

    val Export: ImageVector by lazy {
        builder("Export") {
            stroke {
                moveTo(12f, 15f); lineTo(12f, 4.5f)
                moveTo(8f, 8.5f); lineTo(12f, 4.5f); lineTo(16f, 8.5f)
            }
            stroke { moveTo(4.5f, 19.5f); lineTo(19.5f, 19.5f) }
        }
    }

    val Check: ImageVector by lazy {
        builder("Check") {
            stroke {
                moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
            }
        }
    }

    val Dots: ImageVector by lazy {
        builder("Dots") {
            stroke { circle(12f, 5.5f, 0.4f) }
            stroke { circle(12f, 12f, 0.4f) }
            stroke { circle(12f, 18.5f, 0.4f) }
        }
    }

    val Retry: ImageVector by lazy {
        builder("Retry") {
            stroke {
                moveTo(19.2f, 12f)
                arcTo(7.2f, 7.2f, 0f, true, true, 4.8f, 12f)
                arcTo(7.2f, 7.2f, 0f, false, true, 12f, 4.8f)
            }
            stroke {
                moveTo(12f, 4.8f); lineTo(15.2f, 2.2f)
                moveTo(12f, 4.8f); lineTo(15.4f, 7.6f)
            }
        }
    }

    val Info: ImageVector by lazy {
        builder("Info") {
            stroke { circle(12f, 12f, 8f) }
            stroke { moveTo(12f, 11f); lineTo(12f, 16.2f) }
            stroke { moveTo(12f, 7.8f); lineTo(12f, 8.6f) }
        }
    }

    val Toc: ImageVector by lazy {
        builder("Toc") {
            stroke {
                moveTo(5f, 5f); lineTo(12f, 5f)
                moveTo(5f, 9.5f); lineTo(19f, 9.5f)
                moveTo(5f, 14f); lineTo(19f, 14f)
                moveTo(5f, 18.5f); lineTo(15f, 18.5f)
            }
        }
    }

    val Star: ImageVector by lazy {
        builder("Star") {
            stroke {
                moveTo(12f, 4f); lineTo(14.2f, 9.2f); lineTo(19.8f, 9.8f)
                moveTo(19.8f, 9.8f); lineTo(15.6f, 13.6f); lineTo(16.9f, 19.6f)
                moveTo(16.9f, 19.6f); lineTo(12f, 16.4f); lineTo(7.1f, 19.6f)
                moveTo(7.1f, 19.6f); lineTo(8.4f, 13.6f); lineTo(4.2f, 9.8f)
                moveTo(4.2f, 9.8f); lineTo(9.8f, 9.2f); lineTo(12f, 4f)
            }
        }
    }
}
