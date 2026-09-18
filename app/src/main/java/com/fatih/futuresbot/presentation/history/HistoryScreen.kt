package com.fatih.futuresbot.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.TradeRecord
import com.fatih.futuresbot.domain.model.TradeStatus
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.common.StatRow
import com.fatih.futuresbot.presentation.common.pnlColor
import com.fatih.futuresbot.presentation.theme.TradeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(container: AppContainer) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val stats = state.stats

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (state.ops.syncing) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                } else {
                    TextButton(onClick = vm::refresh) { Text("Güncelle") }
                }
            }
            state.ops.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TradeColors.Accent)
            }

            SectionCard {
                Text("İstatistikler", fontWeight = FontWeight.Bold)
                StatRow("Toplam işlem", stats.total.toString())
                StatRow("Açık işlem", stats.open.toString())
                StatRow("Kazanan / kaybeden", "${stats.wins} / ${stats.losses}")
                StatRow("Win rate", percent(stats.winRate), TradeColors.Long)
                StatRow("Loss rate", percent(stats.lossRate), TradeColors.Short)
                StatRow("Toplam PNL", Fmt.signedUsdt(stats.totalPnl), pnlColor(stats.totalPnl))
                StatRow("Ortalama kâr", Fmt.signedUsdt(stats.averageProfit), TradeColors.Long)
                StatRow("Ortalama zarar", Fmt.signedUsdt(stats.averageLoss), TradeColors.Short)
                StatRow("Profit factor", stats.profitFactor?.let { two(it) } ?: "—")
                StatRow("Maksimum drawdown", Fmt.usdt(stats.maxDrawdown), TradeColors.Short)
                Text(
                    text = "PNL değerleri borsadan alınan gerçekleşen kâr/zarardır (komisyon düşülmüş).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.records.isEmpty()) {
                Text(
                    text = "Henüz kayıtlı işlem yok. Uygulamadan açılan her pozisyon buraya yazılır.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.records.forEach { record -> TradeCard(record) }
                TextButton(onClick = vm::askClear, modifier = Modifier.fillMaxWidth()) {
                    Text("Geçmişi temizle", color = TradeColors.Short)
                }
            }
        }
    }

    if (state.ops.confirmClear) {
        AlertDialog(
            onDismissRequest = vm::dismissClear,
            title = { Text("Geçmiş silinsin mi?") },
            text = { Text("Cihazdaki işlem kayıtları ve istatistikler silinir. Borsadaki işlemler etkilenmez.") },
            confirmButton = {
                Button(
                    onClick = vm::confirmClear,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TradeColors.Short,
                        contentColor = Color.White,
                    ),
                ) { Text("Sil", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = vm::dismissClear) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun TradeCard(record: TradeRecord) {
    val isLong = record.side == "LONG"
    val sideColor = if (isLong) TradeColors.Long else TradeColors.Short
    val closed = record.status == TradeStatus.CLOSED.name
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.symbol,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Surface(color = sideColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = record.side,
                    color = sideColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        StatRow("Durum", if (closed) "Kapandı" else "Açık", if (closed) Color.Unspecified else TradeColors.Accent)
        StatRow("Açılış", TIME_FORMAT.format(Instant.ofEpochMilli(record.openTime)))
        record.closeTime?.let { StatRow("Kapanış", TIME_FORMAT.format(Instant.ofEpochMilli(it))) }
        duration(record)?.let { StatRow("İşlem süresi", it) }
        StatRow("Giriş", Fmt.price(record.entryPrice))
        StatRow("Çıkış", Fmt.price(record.exitPrice))
        StatRow("Miktar", Fmt.qty(record.quantity))
        StatRow("Kaldıraç", "${record.leverage}x")
        StatRow("Pozisyon büyüklüğü", Fmt.usdt(record.notional))
        StatRow("Stop-Loss", Fmt.price(record.stopLoss), TradeColors.Short)
        StatRow("Take-Profit", record.takeProfit?.let { Fmt.price(it) } ?: "—", TradeColors.Long)
        StatRow("PNL", Fmt.signedUsdt(record.realizedPnl), pnlColor(record.realizedPnl))
        StatRow("PNL %", record.pnlPercent?.let { percent(it) } ?: "—", pnlColor(record.pnlPercent))
        StatRow("Kaynak", if (record.origin == "BOT") "Bot" else "Elle")
        Text(
            text = "Neden: ${record.reason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        record.signals.forEach {
            Text(
                text = "• $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun duration(record: TradeRecord): String? {
    val close = record.closeTime ?: return null
    val minutes = (close - record.openTime) / 60_000
    if (minutes < 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours} sa ${rest} dk" else "${rest} dk"
}

private fun percent(value: Double): String = String.format(Locale.US, "%%%.2f", value)

private fun two(value: Double): String = String.format(Locale.US, "%.2f", value)
