package com.fatih.futuresbot.presentation.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.OrderType
import com.fatih.futuresbot.domain.model.PositionSide
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.presentation.common.ActionResultCard
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.common.ModeBadge
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.common.StatRow
import com.fatih.futuresbot.presentation.theme.TradeColors
import com.fatih.futuresbot.trading.ActionResult
import com.fatih.futuresbot.trading.OrderManager
import com.fatih.futuresbot.trading.OrderPreview
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TradeScreen(
    container: AppContainer,
    side: PositionSide,
    onClose: () -> Unit,
    onOpenPositions: () -> Unit,
) {
    val vm: TradeViewModel = viewModel(factory = TradeViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(side) { vm.open(side) }

    val form = state.form
    val ops = state.ops
    val isLong = form.side == PositionSide.LONG
    val sideColor = if (isLong) TradeColors.Long else TradeColors.Short
    val connected = state.connection == ConnectionState.CONNECTED
    val canPreview = !ops.busy && connected && !state.emergencyStopped

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
                TextButton(onClick = onClose, enabled = !ops.busy) { Text("‹ Geri") }
                Spacer(Modifier.weight(1f))
                ModeBadge(TradingMode.TESTNET)
            }
            Text(
                text = "${state.symbol} emri",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            SectionCard {
                StatRow("Son fiyat", Fmt.price(state.lastPrice))
                StatRow("Mark fiyatı", Fmt.price(state.markPrice))
                StatRow("Kullanılabilir", Fmt.usdt(state.availableBalance))
            }

            if (state.emergencyStopped) {
                WarningText("Acil durdurma aktif — emir gönderilemez. Dashboard'dan sıfırlayabilirsin.")
            } else if (!connected) {
                WarningText("Borsa bağlantısı yok — emir gönderilemez.")
            }

            SegmentRow(
                options = listOf("LONG", "SHORT"),
                selectedIndex = if (isLong) 0 else 1,
                selectedColor = sideColor,
                onSelect = { vm.setSide(if (it == 0) PositionSide.LONG else PositionSide.SHORT) },
            )
            SegmentRow(
                options = listOf("Market", "Limit"),
                selectedIndex = if (form.type == OrderType.MARKET) 0 else 1,
                selectedColor = TradeColors.Accent,
                onSelect = { vm.setType(if (it == 0) OrderType.MARKET else OrderType.LIMIT) },
            )
            if (form.type == OrderType.LIMIT) {
                NumberField("Limit fiyatı (USDT)", form.limitPrice, vm::setLimitPrice)
            }

            Text("Kaldıraç: ${form.leverage}x", fontWeight = FontWeight.SemiBold)
            Slider(
                value = form.leverage.toFloat(),
                onValueChange = { vm.setLeverage(it.roundToInt()) },
                valueRange = 1f..OrderManager.MAX_LEVERAGE.toFloat(),
                steps = OrderManager.MAX_LEVERAGE - 2,
            )

            NumberField("Marjin (USDT)", form.margin, vm::setMargin)
            val margin = form.margin.toDoubleOrNull()
            Text(
                text = "Pozisyon büyüklüğü ≈ " + Fmt.usdt(margin?.let { it * form.leverage }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    NumberField("Stop-Loss %", form.stopLoss, vm::setStopLoss)
                }
                Box(Modifier.weight(1f)) {
                    NumberField("Take-Profit %", form.takeProfit, vm::setTakeProfit)
                }
            }
            Text(
                text = "SL ve TP, giriş fiyatına göre fiyat değişim yüzdesidir. SL zorunludur; " +
                    "TP istemiyorsan boş bırak.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (ops.rejectReasons.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TradeColors.Short.copy(alpha = 0.12f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Emir önizlenemedi", color = TradeColors.Short, fontWeight = FontWeight.Bold)
                        ops.rejectReasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            ops.result?.let { result ->
                ActionResultCard(
                    result = result,
                    onDismiss = vm::clearResult,
                    extraActionLabel = if (result is ActionResult.Success) "Pozisyonlara git" else null,
                    onExtraAction = onOpenPositions,
                )
            }

            Button(
                onClick = vm::requestPreview,
                enabled = canPreview,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = sideColor, contentColor = Color.Black),
            ) {
                if (ops.busy && ops.preview == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black,
                    )
                } else {
                    Text(
                        text = "Önizle · ${if (isLong) "LONG" else "SHORT"} ${state.symbol}",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    ops.preview?.let { preview ->
        ConfirmOrderDialog(
            preview = preview,
            busy = ops.busy,
            onConfirm = vm::confirmSubmit,
            onDismiss = vm::dismissPreview,
        )
    }
}

@Composable
private fun WarningText(text: String) {
    Text(text = text, color = TradeColors.Short, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SegmentRow(
    options: List<String>,
    selectedIndex: Int,
    selectedColor: Color,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) selectedColor else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmOrderDialog(
    preview: OrderPreview,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val intent = preview.intent
    val isLong = intent.side == PositionSide.LONG
    val sideColor = if (isLong) TradeColors.Long else TradeColors.Short
    val isMarket = intent.type == OrderType.MARKET

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Emri onayla", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatRow("Mod", "TESTNET / DEMO", TradeColors.Accent)
                StatRow("Coin", intent.symbol)
                StatRow("Yön", if (isLong) "LONG" else "SHORT", sideColor)
                StatRow("Emir tipi", if (isMarket) "Market" else "Limit")
                StatRow(
                    if (isMarket) "Giriş (tahmini)" else "Limit fiyatı",
                    Fmt.price(preview.entryPrice.toDouble()),
                )
                StatRow("Kaldıraç", "${intent.leverage}x")
                StatRow("Miktar", preview.quantity.toPlainString())
                StatRow("Pozisyon büyüklüğü", Fmt.usdt(preview.notional))
                StatRow("Gerekli marjin", Fmt.usdt(preview.requiredMargin))
                StatRow("Stop-Loss", Fmt.price(preview.stopLossPrice.toDouble()), TradeColors.Short)
                StatRow(
                    "Take-Profit",
                    preview.takeProfitPrice?.let { Fmt.price(it.toDouble()) } ?: "Yok",
                    TradeColors.Long,
                )
                StatRow("Tahmini risk (SL'de)", "-" + Fmt.usdt(preview.estimatedLoss), TradeColors.Short)
                preview.estimatedProfit?.let {
                    StatRow("Tahmini kâr (TP'de)", "+" + Fmt.usdt(it), TradeColors.Long)
                }
                preview.riskReward?.let {
                    StatRow("Risk/Ödül", String.format(Locale.US, "1 : %.2f", it))
                }
                StatRow("Tahmini likidasyon*", Fmt.price(preview.estimatedLiquidation))
                StatRow("Kullanılabilir bakiye", Fmt.usdt(preview.availableBalance))
                preview.warnings.forEach {
                    Text("⚠ $it", color = TradeColors.Accent, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "* İzole marjin varsayımıyla yaklaşık değer; çapraz marjinde gerçek " +
                        "likidasyon tüm bakiyeye bağlıdır. Önizleme 30 sn geçerlidir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = sideColor, contentColor = Color.Black),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Gönderiliyor…", fontWeight = FontWeight.Bold)
                } else {
                    Text("Onayla ve gönder", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") }
        },
    )
}
