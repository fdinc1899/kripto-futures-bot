package com.fatih.futuresbot.presentation.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.presentation.common.ConnectionIndicator
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.theme.TradeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun ChartScreen(container: AppContainer, onPickSymbol: () -> Unit) {
    val vm: ChartViewModel = viewModel(factory = ChartViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    val visible = remember(state.candles, state.visibleCount) {
        state.candles.takeLast(state.visibleCount)
    }
    var selectedIndex by remember(state.symbol, state.interval, state.visibleCount) {
        mutableStateOf<Int?>(null)
    }
    val shown: Candle? = selectedIndex?.let { visible.getOrNull(it) } ?: visible.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onPickSymbol)) {
                Text(
                    text = state.symbol + " ▾",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Perpetual · değiştirmek için dokun",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Fmt.price(visible.lastOrNull()?.close),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        ConnectionIndicator(state.stream, "Canlı veri")

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChartInterval.entries.forEach { interval ->
                IntervalChip(
                    label = interval.code,
                    selected = interval == state.interval,
                    onClick = { vm.setInterval(interval) },
                )
            }
        }

        if (shown != null) OhlcInfo(shown)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                visible.isEmpty() -> Text(
                    text = state.error ?: "Veri yok",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> CandleChart(
                    candles = visible,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (visible.isNotEmpty()) {
            state.error?.let {
                Text(it, color = TradeColors.Accent, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${visible.size} mum · ayrıntı için grafiğe dokun",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = vm::zoomOut) { Text("−") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = vm::zoomIn) { Text("+") }
        }
    }
}

@Composable
private fun IntervalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        color = if (selected) TradeColors.Accent else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = Modifier.clip(shape).clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OhlcInfo(candle: Candle) {
    val color = if (candle.close >= candle.open) TradeColors.Long else TradeColors.Short
    val time = remember(candle.openTime) { TIME_FORMAT.format(Instant.ofEpochMilli(candle.openTime)) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = time + if (candle.closed) "" else " · açık mum",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            OhlcItem("Açılış", candle.open, Color.Unspecified, Modifier.weight(1f))
            OhlcItem("Yüksek", candle.high, Color.Unspecified, Modifier.weight(1f))
        }
        Row {
            OhlcItem("Düşük", candle.low, Color.Unspecified, Modifier.weight(1f))
            OhlcItem("Kapanış", candle.close, color, Modifier.weight(1f))
        }
        Text(
            text = "Hacim " + Fmt.compact(candle.volume),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OhlcItem(label: String, value: Double, color: Color, modifier: Modifier) {
    Row(modifier) {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = Fmt.price(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
