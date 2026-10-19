package com.investalert.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun MarketChart(
    prices: List<Double>,
    label: String,
    modifier: Modifier = Modifier
) {
    if (prices.size < 2) return

    val min = prices.minOrNull() ?: return
    val max = prices.maxOrNull() ?: return
    val first = prices.first()
    val last = prices.last()
    val changePct = if (first > 0) (last / first - 1.0) * 100.0 else 0.0

    val lineColor = when {
        changePct > 0 -> GainGreen
        changePct < 0 -> LossRed
        else -> MaterialTheme.colorScheme.primary
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)

    Column(modifier = modifier) {
        Text(
            text = "$label • ${signedPct(changePct)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = lineColor
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val left = 6f
            val right = size.width - 6f
            val top = 10f
            val bottom = size.height - 10f
            val usableW = (right - left).coerceAtLeast(1f)
            val usableH = (bottom - top).coerceAtLeast(1f)
            val range = (max - min).takeIf { abs(it) > 0.0000001 } ?: 1.0

            for (i in 1..3) {
                val y = top + usableH * i / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1f
                )
            }

            val path = Path()
            prices.forEachIndexed { index, price ->
                val x = left + usableW * index / (prices.size - 1).toFloat()
                val yNorm = ((price - min) / range).toFloat().coerceIn(0f, 1f)
                val y = bottom - usableH * yNorm
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}
