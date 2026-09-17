package com.fatih.futuresbot.presentation.dashboard

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.fatih.futuresbot.presentation.common.ConnectionIndicator
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.common.ModeBadge
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.common.StatRow
import com.fatih.futuresbot.presentation.common.pnlColor
import com.fatih.futuresbot.presentation.theme.TradeColors

/** Emir sistemi Aşama 8'de (testnet) bağlanınca true yapılacak. */
private const val ORDERS_ENABLED = false

@Composable
fun DashboardScreen(container: AppContainer, onOpenBot: () -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onEmergencyStop = vm::emergencyStop,
        onResetEmergency = vm::resetEmergency,
        onOpenBot = onOpenBot,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onEmergencyStop: () -> Unit,
    onResetEmergency: () -> Unit,
    onOpenBot: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                ModeBadge(state.mode)
            }
            ConnectionIndicator(state.connection)
            state.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TradeColors.Accent)
            }

            if (state.emergencyStopped) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = TradeColors.Short.copy(alpha = 0.18f)
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ACİL DURDURMA AKTİF — yeni emir gönderilmez",
                            color = TradeColors.Short,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onResetEmergency) { Text("Sıfırla") }
                    }
                }
            }

            val acc = state.account
            SectionCard {
                Text(
                    text = "Toplam bakiye",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Fmt.usdt(acc?.walletBalance),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                StatRow("Marjin bakiyesi", Fmt.usdt(acc?.marginBalance))
                StatRow("Kullanılabilir", Fmt.usdt(acc?.availableBalance))
                StatRow(
                    "Günlük gerçekleşen PNL",
                    Fmt.signedUsdt(acc?.dailyRealizedPnl),
                    pnlColor(acc?.dailyRealizedPnl),
                )
                StatRow("Günlük %", Fmt.pct(acc?.dailyPnlPercent), pnlColor(acc?.dailyPnlPercent))
                StatRow(
                    "Gerçekleşmemiş PNL",
                    Fmt.signedUsdt(acc?.unrealizedPnl),
                    pnlColor(acc?.unrealizedPnl),
                )
                StatRow("Açık pozisyon", acc?.openPositions?.toString() ?: "—")
            }

            val sym = state.symbol
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sym?.symbol ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "24s " + Fmt.pct(sym?.change24hPercent),
                        color = pnlColor(sym?.change24hPercent),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(Fmt.price(sym?.lastPrice), style = MaterialTheme.typography.headlineSmall)
                StatRow("Mark fiyatı", Fmt.price(sym?.markPrice))
                StatRow("Funding oranı", Fmt.funding(sym?.fundingRate))
                StatRow("Kaldıraç", sym?.leverage?.let { "${it}x" } ?: "—")

                val amt = sym?.positionAmt
                val positionText = when {
                    amt == null -> "Yok"
                    amt > 0 -> "LONG " + Fmt.qty(amt)
                    else -> "SHORT " + Fmt.qty(-amt)
                }
                val positionColor = when {
                    amt == null -> Color.Unspecified
                    amt > 0 -> TradeColors.Long
                    else -> TradeColors.Short
                }
                StatRow("Pozisyon", positionText, positionColor)
                if (amt != null) {
                    StatRow("Giriş fiyatı", Fmt.price(sym?.entryPrice))
                    StatRow("Margin", Fmt.usdt(sym?.margin))
                    StatRow("Likidasyon fiyatı", Fmt.price(sym?.liquidationPrice))
                    StatRow("Pozisyon PNL", Fmt.signedUsdt(sym?.unrealizedPnl), pnlColor(sym?.unrealizedPnl))
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "BOT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(state.botStatus.label, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onOpenBot) { Text("Bot paneli") }
                }
            }

            val tradeButtonsEnabled = ORDERS_ENABLED && !state.emergencyStopped
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { },
                    enabled = tradeButtonsEnabled,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TradeColors.Long,
                        contentColor = Color.Black,
                    ),
                ) { Text("LONG", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { },
                    enabled = tradeButtonsEnabled,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TradeColors.Short,
                        contentColor = Color.White,
                    ),
                ) { Text("SHORT", fontWeight = FontWeight.Bold) }
            }
            Text(
                text = "Emir sistemi Aşama 8'de (testnet) bağlanacak.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onEmergencyStop,
                enabled = !state.emergencyStopped,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TradeColors.Short,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "EMERGENCY STOP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
