package com.baoji.fitness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray6

/** 折线图的一个点。[highlight] = 实心强调（PR / 最佳）。 */
data class ChartPoint(
    val label: String,
    val value: Double,
    val highlight: Boolean = false
)

/**
 * 全 App 共用的极简 Canvas 折线图：E1RM 突破曲线、近 8 周容量趋势、体重/腰围趋势
 * 都走这一份实现，避免三处复制粘贴后各画各的。
 *
 * @param zeroBased true = y 轴从 0 起（容量、围度这类绝对量）；false = 包住数据范围（E1RM，差异只有几 kg）
 * @param showXLabels 是否在底部画 x 轴标签
 * @param xLabelTextSizePx x 轴标签字号（px）。默认 24px ≈ 8sp；调用方要满足
 *   11sp 下限时传 `11.sp.toPx()`，底部留白会随之加高
 */
@Composable
fun MinimalLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    zeroBased: Boolean = true,
    showXLabels: Boolean = false,
    lineColor: Color = AppleFitnessGreen,
    xLabelTextSizePx: Float = 24f
) {
    if (points.isEmpty()) return

    val values = points.map { it.value }
    val minValue: Double
    val maxValue: Double
    if (zeroBased) {
        minValue = 0.0
        maxValue = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0) * 1.15
    } else {
        val lo = values.minOrNull() ?: 0.0
        val hi = values.maxOrNull() ?: 1.0
        minValue = lo - 5.0
        maxValue = hi + 5.0
    }
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val labelReserve = if (showXLabels) maxOf(28f, xLabelTextSizePx + 8f) else 8f

    Canvas(modifier = modifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height - labelReserve
        val stepX = if (points.size > 1) w / (points.size - 1) else w / 2

        fun xOf(index: Int): Float = if (points.size > 1) index * stepX else w / 2
        fun yOf(value: Double): Float = (h - ((value - minValue) / span) * h).toFloat()

        // 横向网格
        for (i in 0..3) {
            val y = h - (i / 3f) * h
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // 折线
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, pt ->
                if (i == 0) path.moveTo(xOf(i), yOf(pt.value)) else path.lineTo(xOf(i), yOf(pt.value))
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }

        // 数据点
        points.forEachIndexed { i, pt ->
            val x = xOf(i)
            val y = yOf(pt.value)
            val isLast = i == points.size - 1
            val big = pt.highlight || isLast
            drawCircle(
                color = if (pt.highlight) lineColor else AppleGray6,
                radius = if (big) 9f else 6f,
                center = Offset(x, y)
            )
            drawCircle(
                color = if (pt.highlight) Color.White else lineColor,
                radius = if (big) 4.5f else 3f,
                center = Offset(x, y)
            )
        }

        // x 轴标签：按实际文字宽度贪心抽稀，字号变大后自动减少刻度，绝不重叠
        if (showXLabels) {
            val paint = android.graphics.Paint().apply {
                color = 0xFF8E8E93.toInt()
                textSize = xLabelTextSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            var lastLabelRight = Float.NEGATIVE_INFINITY
            points.forEachIndexed { i, pt ->
                val x = xOf(i)
                val half = paint.measureText(pt.label) / 2f
                if (x - half >= lastLabelRight + 8f) {
                    drawContext.canvas.nativeCanvas.drawText(pt.label, x, size.height - 6f, paint)
                    lastLabelRight = x + half
                }
            }
        }
    }
}
