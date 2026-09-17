package com.fatih.futuresbot.presentation.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.theme.TradeColors

@Composable
fun OrdersScreen(container: AppContainer) {
    val vm: OrdersViewModel = viewModel(factory = OrdersViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val ops = state.ops

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Orders",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::reload) { Text("Yenile") }
        }
        Text(
            text = "Bekleyen emirler ve SL/TP (koşullu) emirleri · 15 sn'de bir yenilenir",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let { Text(it, color = TradeColors.Accent, style = MaterialTheme.typography.bodySmall) }
        ops.result?.let { ActionResultCard(result = it, onDismiss = vm::clearResult) }

        when {
            state.loading -> CircularProgressIndicator()
            state.rows.isEmpty() -> Text(
                text = "Bekleyen emir yok.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> state.rows.forEach { row ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.symbol,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = row.title,
                            color = if (row.side == "BUY") TradeColors.Long else TradeColors.Short,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { vm.askCancel(row) },
                        enabled = !ops.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("İptal et") }
                }
            }
        }
    }

    ops.confirm?.let { row ->
        AlertDialog(
            onDismissRequest = vm::dismissConfirm,
            title = { Text("Emir iptal edilsin mi?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.symbol} · ${row.title}")
                    Text(row.detail, style = MaterialTheme.typography.bodySmall)
                    if (row.isStopLoss) {
                        Text(
                            text = "DİKKAT: Bu Stop-Loss emri iptal edilirse açık pozisyonun korumasız kalır.",
                            color = TradeColors.Short,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TradeColors.Short,
                        contentColor = Color.White,
                    ),
                ) { Text("İptal et", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = vm::dismissConfirm) { Text("Vazgeç") } },
        )
    }
}
