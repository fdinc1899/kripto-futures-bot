package com.fatih.futuresbot.presentation.positions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.fatih.futuresbot.presentation.common.ActionResultCard
import com.fatih.futuresbot.presentation.common.ConnectionIndicator
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.common.StatRow
import com.fatih.futuresbot.presentation.common.pnlColor
import com.fatih.futuresbot.presentation.theme.TradeColors

@Composable
fun PositionsScreen(container: AppContainer) {
    val vm: PositionsViewModel = viewModel(factory = PositionsViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val ops = state.ops

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Positions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ConnectionIndicator(state.connection, "Borsa")

        if (state.emergencyStopped) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TradeColors.Short.copy(alpha = 0.18f)),
            ) {
                Text(
                    text = "ACİL DURDURMA AKTİF — açık pozisyonlarını aşağıdan market emriyle kapatabilirsin.",
                    color = TradeColors.Short,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        state.lastEvent?.let {
            Text("Son olay: $it", style = MaterialTheme.typography.bodySmall, color = TradeColors.Accent)
        }

        ops.result?.let { ActionResultCard(result = it, onDismiss = vm::clearResult) }

        if (ops.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("İşlem sürüyor, lütfen bekle…")
            }
        }

        when {
            state.loading && state.rows.isEmpty() -> CircularProgressIndicator()
            state.rows.isEmpty() -> Text(
                text = "Açık pozisyon yok.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                state.rows.forEach { row -> PositionCard(row, enabled = !ops.busy, onClose = vm::askClose) }
                Button(
                    onClick = vm::askCloseAll,
                    enabled = !ops.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TradeColors.Short,
                        contentColor = Color.White,
                    ),
                ) { Text("Tüm pozisyonları market ile kapat", fontWeight = FontWeight.Bold) }
            }
        }
    }

    ops.confirm?.let { request ->
        val target = request.symbol
        AlertDialog(
            onDismissRequest = vm::dismissConfirm,
            title = { Text(if (target != null) "$target kapatılsın mı?" else "Tüm pozisyonlar kapatılsın mı?") },
            text = {
                Text(
                    "Pozisyon market emriyle kapatılır ve bu paritedeki bekleyen SL/TP ile diğer " +
                        "emirler iptal edilir. Market emrinde fiyat kayması olabilir."
                )
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TradeColors.Short,
                        contentColor = Color.White,
                    ),
                ) { Text("Kapat", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = vm::dismissConfirm) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun PositionCard(row: PositionRow, enabled: Boolean, onClose: (String) -> Unit) {
    val p = row.position
    val isLong = p.positionAmt > 0
    val sideColor = if (isLong) TradeColors.Long else TradeColors.Short
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = p.symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Surface(color = sideColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = if (isLong) "LONG" else "SHORT",
                    color = sideColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        StatRow("Miktar", Fmt.qty(kotlin.math.abs(p.positionAmt)))
        StatRow("Giriş fiyatı", Fmt.price(p.entryPrice))
        StatRow("Mark fiyatı", Fmt.price(p.markPrice))
        StatRow("Likidasyon", Fmt.price(p.liquidationPrice))
        StatRow("Marjin", Fmt.usdt(p.initialMargin))
        StatRow("PNL", Fmt.signedUsdt(p.unrealizedPnl), pnlColor(p.unrealizedPnl))
        StatRow(
            "Koruma",
            when (row.protectedBySl) {
                true -> "Stop-Loss aktif"
                false -> "STOP-LOSS YOK!"
                null -> "—"
            },
            when (row.protectedBySl) {
                true -> TradeColors.Long
                false -> TradeColors.Short
                null -> Color.Unspecified
            },
        )
        OutlinedButton(
            onClick = { onClose(p.symbol) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TradeColors.Short),
        ) { Text("Market ile kapat") }
    }
}
