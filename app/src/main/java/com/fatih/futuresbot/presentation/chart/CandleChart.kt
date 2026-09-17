package com.fatih.futuresbot.presentation.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.theme.TradeColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Hacim çubuklu, son fiyat çizgili mum grafiği. Dokunulan mum seçilir. */
@Composable
fun CandleChart(
    candles: List<Candle>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = TradeColors.TextSecondary)
    val tagStyle = MaterialTheme.typography.labelSmall.copy(
        color = Color.Black,
        fontWeight = FontWeight.Bold,
    )
    val axisWidthDp = 76.dp
    val currentCandles by rememberUpdatedState(candles)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val list = currentCandles
                val chartWidth = size.width - axisWidthDp.toPx()
                if (list.isEmpty() || offset.x > chartWidth || chartWidth <= 0f) {
                    currentOnSelect(null)
                } else {
                    val idx = (offset.x / chartWidth * list.size).toInt()
                    currentOnSelect(idx.coerceIn(0, list.size - 1))
                }
            }
        }
    ) {
        if (candles.isEmpty()) return@Canvas

        val axisWidth = axisWidthDp.toPx()
        val chartWidth = size.width - axisWidth
        if (chartWidth <= 0f) return@Canvas
        val volumeHeight = size.height * 0.18f
        val priceHeight = size.height - volumeHeight - 8.dp.toPx()

        val highest = candles.maxOf { it.high }
        val lowest = candles.minOf { it.low }
        val pad = (highest - lowest) * 0.06
        val top = highest + pad
        val range = ((highest + pad) - (lowest - pad)).takeIf { it > 0.0 } ?: 1.0
        fun yOf(price: Double): Float = ((top - price) / range * priceHeight).toFloat()

        val step = chartWidth / candles.size
        val bodyWidth = (step * 0.7f).coerceAtLeast(1f)
        val wickWidth = (step * 0.12f).coerceIn(1f, 2.dp.toPx())
        val maxVolume = candles.maxOf { it.volume }.takeIf { it > 0.0 } ?: 1.0

        // Yatay ızgara ve fiyat etiketleri
        for (i in 0..4) {
            val price = top - range * i / 4.0
            val y = yOf(price)
            drawLine(
                color = TradeColors.SurfaceHigh,
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = 1f,
            )
            val layout = textMeasurer.measure(AnnotatedString(Fmt.price(price)), style = labelStyle)
            val labelTop = (y - layout.size.height / 2f)
                .coerceIn(0f, max(0f, priceHeight - layout.size.height))
            drawText(layout, topLeft = Offset(chartWidth + 6f, labelTop))
        }

        // Seçili mum çizgisi
        val sel = selectedIndex
        if (sel != null && sel in candles.indices) {
            val x = step * sel + step / 2f
            drawLine(
                color = TradeColors.TextSecondary.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
        }

        // Mumlar ve hacim
        candles.forEachIndexed { i, c ->
            val cx = step * i + step / 2f
            val color = if (c.close >= c.open) TradeColors.Long else TradeColors.Short
            drawLine(
                color = color,
                start = Offset(cx, yOf(c.high)),
                end = Offset(cx, yOf(c.low)),
                strokeWidth = wickWidth,
            )
            val openY = yOf(c.open)
            val closeY = yOf(c.close)
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, min(openY, closeY)),
                size = Size(bodyWidth, max(abs(openY - closeY), 1f)),
            )
            val volumeBar = (c.volume / maxVolume * volumeHeight).toFloat()
            drawRect(
                color = color.copy(alpha = 0.35f),
                topLeft = Offset(cx - bodyWidth / 2f, size.height - volumeBar),
                size = Size(bodyWidth, volumeBar),
            )
        }

        // Son fiyat çizgisi ve etiketi
        val last = candles.last()
        val lastY = yOf(last.close).coerceIn(0f, priceHeight)
        val lastColor = if (last.close >= last.open) TradeColors.Long else TradeColors.Short
        drawLine(
            color = lastColor,
            start = Offset(0f, lastY),
            end = Offset(chartWidth, lastY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
        val tag = textMeasurer.measure(AnnotatedString(Fmt.price(last.close)), style = tagStyle)
        val tagHeight = tag.size.height + 6f
        val tagTop = (lastY - tagHeight / 2f).coerceIn(0f, max(0f, priceHeight - tagHeight))
        drawRect(
            color = lastColor,
            topLeft = Offset(chartWidth + 2f, tagTop),
            size = Size(axisWidth - 2f, tagHeight),
        )
        drawText(tag, topLeft = Offset(chartWidth + 6f, tagTop + 3f))
    }
}
