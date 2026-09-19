package com.fatih.futuresbot.presentation.bot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.BotLogEntry
import com.fatih.futuresbot.domain.model.BotLogLevel
import com.fatih.futuresbot.domain.model.BotStatus
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.ScanCandidate
import com.fatih.futuresbot.domain.model.ScanMode
import com.fatih.futuresbot.domain.model.SignalDirection
import com.fatih.futuresbot.domain.model.StrategyConfig
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.common.StatRow
import com.fatih.futuresbot.presentation.theme.TradeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())

private data class StrategyDraft(
    val rsiPeriod: String,
    val rsiLong: String,
    val rsiShort: String,
    val emaFast: String,
    val emaSlow: String,
    val smaPeriod: String,
    val macdFast: String,
    val macdSlow: String,
    val macdSignal: String,
    val bbPeriod: String,
    val bbDeviation: String,
    val volumePeriod: String,
    val volumeMultiplier: String,
    val atrPeriod: String,
    val atrMin: String,
)

private fun draftOf(config: StrategyConfig) = StrategyDraft(
    rsiPeriod = config.rsi.period.toString(),
    rsiLong = num(config.rsi.longBelow),
    rsiShort = num(config.rsi.shortAbove),
    emaFast = config.ema.fast.toString(),
    emaSlow = config.ema.slow.toString(),
    smaPeriod = config.sma.period.toString(),
    macdFast = config.macd.fast.toString(),
    macdSlow = config.macd.slow.toString(),
    macdSignal = config.macd.signal.toString(),
    bbPeriod = config.bollinger.period.toString(),
    bbDeviation = num(config.bollinger.deviation),
    volumePeriod = config.volume.period.toString(),
    volumeMultiplier = num(config.volume.multiplier),
    atrPeriod = config.atr.period.toString(),
    atrMin = num(config.atr.minPercent),
)

private fun StrategyDraft.applyTo(base: StrategyConfig): StrategyConfig = base.copy(
    rsi = base.rsi.copy(
        period = rsiPeriod.toIntOrNull() ?: base.rsi.period,
        longBelow = rsiLong.toDoubleOrNull() ?: base.rsi.longBelow,
        shortAbove = rsiShort.toDoubleOrNull() ?: base.rsi.shortAbove,
    ),
    ema = base.ema.copy(
        fast = emaFast.toIntOrNull() ?: base.ema.fast,
        slow = emaSlow.toIntOrNull() ?: base.ema.slow,
    ),
    sma = base.sma.copy(period = smaPeriod.toIntOrNull() ?: base.sma.period),
    macd = base.macd.copy(
        fast = macdFast.toIntOrNull() ?: base.macd.fast,
        slow = macdSlow.toIntOrNull() ?: base.macd.slow,
        signal = macdSignal.toIntOrNull() ?: base.macd.signal,
    ),
    bollinger = base.bollinger.copy(
        period = bbPeriod.toIntOrNull() ?: base.bollinger.period,
        deviation = bbDeviation.toDoubleOrNull() ?: base.bollinger.deviation,
    ),
    volume = base.volume.copy(
        period = volumePeriod.toIntOrNull() ?: base.volume.period,
        multiplier = volumeMultiplier.toDoubleOrNull() ?: base.volume.multiplier,
    ),
    atr = base.atr.copy(
        period = atrPeriod.toIntOrNull() ?: base.atr.period,
        minPercent = atrMin.toDoubleOrNull() ?: base.atr.minPercent,
    ),
)

private fun num(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

@Composable
fun BotScreen(container: AppContainer) {
    val vm: BotViewModel = viewModel(factory = BotViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val config = state.config
    var draft by remember(config) { mutableStateOf(draftOf(config)) }
    var askAuto by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Bot, taranan paritelerde çalışır. Aşağıdaki sinyal kartı seçili pariteyi " +
                    "(${state.symbol}) canlı gösterir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ControlCard(
                state = state,
                onToggleBot = vm::setBotEnabled,
                onAskAutoTrade = { askAuto = true },
                onDisableAutoTrade = { vm.setAutoTrade(false) },
                onSaveBot = { leverage, maxTrades ->
                    vm.updateBot { it.copy(leverage = leverage, maxTradesPerDay = maxTrades) }
                },
            )

            ScanCard(
                state = state,
                onSaveScan = { mode, count, watchlist, allowShort ->
                    vm.updateBot {
                        it.copy(
                            scanMode = mode.name,
                            scanCount = count,
                            watchlist = watchlist,
                            allowShort = allowShort,
                        )
                    }
                },
            )

            SignalCard(state)

            LogCard(logs = state.logs, onClear = vm::clearLogs)

            SectionCard {
                Text("Zaman aralığı", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChartInterval.entries.forEach { interval ->
                        Chip(
                            label = interval.code,
                            selected = interval == config.interval,
                            onClick = { vm.setInterval(interval) },
                        )
                    }
                }
                Text(
                    text = "Sinyaller yalnızca kapanmış mumlarla hesaplanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RuleCard(
                title = "RSI",
                description = "LONG için eşiğin altı, SHORT için üstü",
                enabled = config.rsi.enabled,
                onToggle = { on -> vm.updateConfig { it.copy(rsi = it.rsi.copy(enabled = on)) } },
            ) {
                FieldRow(
                    "Periyot", draft.rsiPeriod, { draft = draft.copy(rsiPeriod = it) },
                    "LONG <", draft.rsiLong, { draft = draft.copy(rsiLong = it) },
                )
                FieldRow(
                    "SHORT >", draft.rsiShort, { draft = draft.copy(rsiShort = it) },
                    null, "", {},
                )
            }

            RuleCard(
                title = "EMA kesişimi",
                description = "Hızlı EMA yavaşın üstündeyse LONG",
                enabled = config.ema.enabled,
                onToggle = { on -> vm.updateConfig { it.copy(ema = it.ema.copy(enabled = on)) } },
            ) {
                FieldRow(
                    "Hızlı", draft.emaFast, { draft = draft.copy(emaFast = it) },
                    "Yavaş", draft.emaSlow, { draft = draft.copy(emaSlow = it) },
                )
            }

            RuleCard(
                title = "SMA trend filtresi",
                description = "Fiyat SMA üstündeyse LONG",
                enabled = config.sma.enabled,
                onToggle = { on -> vm.updateConfig { it.copy(sma = it.sma.copy(enabled = on)) } },
            ) {
                FieldRow(
                    "Periyot", draft.smaPeriod, { draft = draft.copy(smaPeriod = it) },
                    null, "", {},
                )
            }

            RuleCard(
                title = "MACD",
                description = "MACD çizgisi sinyalin üstündeyse LONG",
                enabled = config.macd.enabled,
                onToggle = { on -> vm.updateConfig { it.copy(macd = it.macd.copy(enabled = on)) } },
            ) {
                FieldRow(
                    "Hızlı", draft.macdFast, { draft = draft.copy(macdFast = it) },
                    "Yavaş", draft.macdSlow, { draft = draft.copy(macdSlow = it) },
                )
                FieldRow(
                    "Sinyal", draft.macdSignal, { draft = draft.copy(macdSignal = it) },
                    null, "", {},
                )
            }

            RuleCard(
                title = "Bollinger Bands",
                description = if (config.bollinger.reversion) "Dönüş modu" else "Kırılım modu",
                enabled = config.bollinger.enabled,
                onToggle = { on ->
                    vm.updateConfig { it.copy(bollinger = it.bollinger.copy(enabled = on)) }
                },
            ) {
                FieldRow(
                    "Periyot", draft.bbPeriod, { draft = draft.copy(bbPeriod = it) },
                    "Sapma", draft.bbDeviation, { draft = draft.copy(bbDeviation = it) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dönüş modu (banda değince ters yön)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = config.bollinger.reversion,
                        onCheckedChange = { on ->
                            vm.updateConfig { it.copy(bollinger = it.bollinger.copy(reversion = on)) }
                        },
                    )
                }
            }

            RuleCard(
                title = "Hacim filtresi",
                description = "Hacim, ortalamanın katından büyük olmalı",
                enabled = config.volume.enabled,
                onToggle = { on -> vm.updateConfig { it.copy(volume = it.volume.copy(enabled = on)) } },
            ) {
                FieldRow(
                    "Periyot", draft.volumePeriod, { draft = draft.copy(volumePeriod = it) },
                    "Çarpan", draft.volumeMultiplier, { draft = draft.copy(volumeMultiplier = it) },
                )
            }

            RuleCard(
                title = "ATR filtresi",
                description = "Volatilite en az bu yüzde olmalı",
                enabled = config.atr.enabled,
                onToggle = { on -> vm.updateConfig { it.copy(atr = it.atr.copy(enabled = on)) } },
            ) {
                FieldRow(
                    "Periyot", draft.atrPeriod, { draft = draft.copy(atrPeriod = it) },
                    "Min %", draft.atrMin, { draft = draft.copy(atrMin = it) },
                )
            }

            Button(
                onClick = { vm.updateConfig { base -> draft.applyTo(base) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Strateji ayarlarını kaydet") }
            state.saved?.let {
                Text(it, color = TradeColors.Long, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (askAuto) {
        AlertDialog(
            onDismissRequest = { askAuto = false },
            title = { Text("Otomatik işlem açılsın mı?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Bot, sinyal oluştuğunda TESTNET hesabında kendiliğinden emir gönderecek. " +
                            "Her emir risk ayarlarına göre boyutlanır, Stop-Loss zorunludur ve " +
                            "günlük zarar limiti dolunca bot kendini kapatır."
                    )
                    Text(
                        "Emirler yalnızca uygulama açıkken gönderilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.setAutoTrade(true)
                        askAuto = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TradeColors.Accent,
                        contentColor = Color.Black,
                    ),
                ) { Text("Etkinleştir", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { askAuto = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ControlCard(
    state: BotUiState,
    onToggleBot: (Boolean) -> Unit,
    onAskAutoTrade: () -> Unit,
    onDisableAutoTrade: () -> Unit,
    onSaveBot: (Int, Int) -> Unit,
) {
    val bot = state.bot
    var leverage by remember(bot.leverage) { mutableStateOf(bot.leverage.toString()) }
    var maxTrades by remember(bot.maxTradesPerDay) { mutableStateOf(bot.maxTradesPerDay.toString()) }
    val (statusText, statusColor) = when (state.status) {
        BotStatus.ACTIVE -> "ACTIVE" to TradeColors.Long
        BotStatus.WAITING_SIGNAL -> "WAITING SIGNAL" to TradeColors.Accent
        BotStatus.POSITION_OPEN -> "POSITION OPEN" to TradeColors.Long
        BotStatus.STOPPED -> "STOPPED" to TradeColors.Short
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BOT", fontWeight = FontWeight.Bold)
                Text(statusText, color = statusColor, fontWeight = FontWeight.Bold)
            }
            Switch(checked = bot.enabled, onCheckedChange = onToggleBot)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Otomatik işlem", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (bot.autoTrade) {
                        "Sinyal oluşunca emir gönderilir"
                    } else {
                        "Yalnızca sinyal kaydedilir"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = bot.autoTrade,
                onCheckedChange = { checked -> if (checked) onAskAutoTrade() else onDisableAutoTrade() },
            )
        }
        FieldRow(
            "Kaldıraç", leverage, { leverage = it },
            "Günlük maks işlem", maxTrades, { maxTrades = it },
        )
        Button(
            onClick = {
                onSaveBot(
                    leverage.toIntOrNull() ?: bot.leverage,
                    maxTrades.toIntOrNull() ?: bot.maxTradesPerDay,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Bot ayarlarını kaydet") }

        StatRow("İşlem riski", "%" + num(state.risk.riskPerTradePercent))
        StatRow("Varsayılan SL / TP", "%" + num(state.risk.defaultStopLossPercent) + " / %" + num(state.risk.defaultTakeProfitPercent))
        StatRow("Günlük zarar limiti", "%" + num(state.risk.maxDailyLossPercent))
        StatRow("Maks. açık pozisyon", state.risk.maxOpenPositions.toString())
        Text(
            text = "Bot her mumda en fazla bir işlem açar ve aynı paritede ikinci pozisyon açmaz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanCard(
    state: BotUiState,
    onSaveScan: (ScanMode, Int, String, Boolean) -> Unit,
) {
    val bot = state.bot
    val currentMode = ScanMode.entries.firstOrNull { it.name == bot.scanMode } ?: ScanMode.HYBRID
    var scanCount by remember(bot.scanCount) { mutableStateOf(bot.scanCount.toString()) }
    var watchlist by remember(bot.watchlist) { mutableStateOf(bot.watchlist) }

    SectionCard {
        Text("Parite taraması", fontWeight = FontWeight.Bold)
        Text(
            text = "Hacim havuzundan momentum ve hacim sıçramasına göre en hareketli pariteler " +
                "seçilir; liste 5 dakikada bir yenilenir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScanMode.entries.forEach { mode ->
                Chip(
                    label = modeLabel(mode),
                    selected = mode == currentMode,
                    onClick = { onSaveScan(mode, scanCount.toIntOrNull() ?: bot.scanCount, watchlist, bot.allowShort) },
                )
            }
        }
        FieldRow(
            "Taranacak parite", scanCount, { scanCount = it },
            null, "", {},
        )
        OutlinedTextField(
            value = watchlist,
            onValueChange = { watchlist = it.uppercase() },
            label = { Text("Kendi listem (virgülle)", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SHORT işlemleri", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (bot.allowShort) "LONG ve SHORT açılır" else "Yalnızca LONG açılır",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = bot.allowShort,
                onCheckedChange = { checked ->
                    onSaveScan(currentMode, scanCount.toIntOrNull() ?: bot.scanCount, watchlist, checked)
                },
            )
        }
        Button(
            onClick = {
                onSaveScan(currentMode, scanCount.toIntOrNull() ?: bot.scanCount, watchlist, bot.allowShort)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Tarama ayarlarını kaydet") }

        if (state.scan.isEmpty()) {
            Text(
                text = "Tarama sonucu yok. Bot açıkken liste birkaç saniye içinde dolar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Taranan pariteler (${state.scan.size})", fontWeight = FontWeight.SemiBold)
            state.scan.take(15).forEach { candidate -> ScanRow(candidate) }
        }
    }
}

@Composable
private fun ScanRow(candidate: ScanCandidate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = candidate.symbol.removeSuffix("USDT") + if (candidate.fromWatchlist) " ★" else "",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (candidate.fromWatchlist) {
                "listemden"
            } else {
                "24s %" + num(candidate.changePercent) +
                    " · 1s %" + num(candidate.momentumPercent) +
                    " · hacim ×" + num(candidate.volumeSpike)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (candidate.momentumPercent >= 0.0) TradeColors.Long else TradeColors.Short,
        )
    }
}

private fun modeLabel(mode: ScanMode): String = when (mode) {
    ScanMode.TOP_VOLUME -> "Hacim"
    ScanMode.MOMENTUM -> "Momentum"
    ScanMode.WATCHLIST -> "Listem"
    ScanMode.HYBRID -> "Karma"
}

@Composable
private fun LogCard(logs: List<BotLogEntry>, onClear: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bot kaydı", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Temizle") }
        }
        if (logs.isEmpty()) {
            Text(
                text = "Henüz kayıt yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            logs.take(20).forEach { entry ->
                val color = when (entry.level) {
                    BotLogLevel.OK -> TradeColors.Long
                    BotLogLevel.WARN -> TradeColors.Accent
                    BotLogLevel.ERROR -> TradeColors.Short
                    BotLogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = TIME_FORMAT.format(Instant.ofEpochMilli(entry.time)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(entry.text, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
    }
}

@Composable
private fun SignalCard(state: BotUiState) {
    val signal = state.signal
    val (label, color) = when (signal.direction) {
        SignalDirection.LONG -> "LONG SİNYALİ" to TradeColors.Long
        SignalDirection.SHORT -> "SHORT SİNYALİ" to TradeColors.Short
        SignalDirection.NONE -> "SİNYAL YOK" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.loading) CircularProgressIndicator(Modifier.padding(start = 8.dp))
        }
        StatRow("Parite", state.symbol)
        StatRow("Zaman aralığı", state.config.interval.code)
        StatRow("Son kapanış", Fmt.price(signal.price.takeIf { it > 0.0 }))
        StatRow(
            "Mum zamanı",
            if (signal.candleTime > 0L) TIME_FORMAT.format(Instant.ofEpochMilli(signal.candleTime)) else "—",
        )
        StatRow("Yüklenen mum", state.candleCount.toString())
        signal.error?.let {
            Text(it, color = TradeColors.Accent, style = MaterialTheme.typography.bodySmall)
        }
        signal.checks.forEach { check ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = check.name + if (check.filter) " (filtre)" else "",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (check.filter) {
                        Text(
                            text = if (check.longOk) "✓" else "✗",
                            color = if (check.longOk) TradeColors.Long else TradeColors.Short,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = "L " + (if (check.longOk) "✓" else "✗") +
                                "  S " + (if (check.shortOk) "✓" else "✗"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) content()
    }
}

@Composable
private fun FieldRow(
    firstLabel: String,
    firstValue: String,
    onFirstChange: (String) -> Unit,
    secondLabel: String?,
    secondValue: String,
    onSecondChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            NumberField(firstLabel, firstValue, onFirstChange)
        }
        Box(Modifier.weight(1f)) {
            if (secondLabel != null) NumberField(secondLabel, secondValue, onSecondChange)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onChange(raw.replace(',', '.').filter { it.isDigit() || it == '.' }.take(6))
        },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        color = if (selected) TradeColors.Accent else MaterialTheme.colorScheme.surfaceVariant,
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
