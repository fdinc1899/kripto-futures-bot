package com.fatih.futuresbot.trading

import com.fatih.futuresbot.data.settings.BotSettingsStore
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.data.settings.StrategyStore
import com.fatih.futuresbot.domain.model.BotLogEntry
import com.fatih.futuresbot.domain.model.BotLogLevel
import com.fatih.futuresbot.domain.model.BotSettings
import com.fatih.futuresbot.domain.model.BotStatus
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.OrderType
import com.fatih.futuresbot.domain.model.PositionSide
import com.fatih.futuresbot.domain.model.ScanCandidate
import com.fatih.futuresbot.domain.model.ScanMode
import com.fatih.futuresbot.domain.model.SignalDirection
import com.fatih.futuresbot.domain.model.SizingMode
import com.fatih.futuresbot.domain.model.StrategyConfig
import com.fatih.futuresbot.domain.model.StrategySignal
import com.fatih.futuresbot.domain.model.TradeOrigin
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.notifications.Notifier
import com.fatih.futuresbot.strategy.StrategyEngine
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Çoklu parite bot motoru.
 *  - Tarama: hacim havuzu + momentum/hacim sıçraması, artı kullanıcı listesi.
 *  - Her döngüde hedef paritelerde strateji çalışır; sinyal gelende emir açılır.
 *  - Her parite için aynı mumda ikinci kez işlem açılmaz.
 *  - Açık pozisyon sayısı risk ayarındaki sınırı geçemez; aynı paritede ikinci pozisyon açılmaz.
 *  - Acil durdurma, bağlantı kopukluğu ve günlük limitlerde emir gönderilmez.
 *  - Günlük zarar limiti dolunca bot kendini kapatır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BotEngine(
    private val botSettingsStore: BotSettingsStore,
    private val strategyStore: StrategyStore,
    private val riskStore: RiskSettingsStore,
    private val symbolStore: SelectedSymbolStore,
    private val scanner: MarketScanner,
    private val accountRepository: AccountRepository,
    private val orderManager: OrderManager,
    private val guard: TradingGuard,
    private val client: ExchangeClient,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(BotStatus.STOPPED)
    val status: StateFlow<BotStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<BotLogEntry>>(emptyList())
    val logs: StateFlow<List<BotLogEntry>> = _logs.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanCandidate>>(emptyList())
    val scanResults: StateFlow<List<ScanCandidate>> = _scanResults.asStateFlow()

    private val lastCandleBySymbol = HashMap<String, Long>()
    private var scanAt = 0L
    private var tradesToday = 0
    private var tradesDayStart = 0L

    fun start() {
        // Bot açıkken hesap verisi canlı kalsın (pozisyon ve bakiye)
        scope.launch {
            botSettingsStore.settings
                .map { it.enabled }
                .distinctUntilChanged()
                .flatMapLatest { enabled ->
                    if (enabled) accountRepository.accountSummary() else flowOf(null)
                }
                .collect { }
        }
        scope.launch { loop() }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private suspend fun loop() {
        while (currentScopeActive()) {
            val settings = botSettingsStore.settings.value
            if (!settings.enabled) {
                _status.value = BotStatus.STOPPED
                delay(IDLE_MS)
                continue
            }
            try {
                cycle(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(BotLogLevel.ERROR, "Döngü hatası: ${e.javaClass.simpleName}")
            }
            delay(CYCLE_MS)
        }
    }

    private fun currentScopeActive(): Boolean = scope.isActive

    private suspend fun cycle(settings: BotSettings) {
        if (accountRepository.connection.value != ConnectionState.CONNECTED) {
            _status.value = BotStatus.ACTIVE
            return
        }
        val config = strategyStore.config.value
        val symbols = targetSymbols(settings)
        if (symbols.isEmpty()) {
            _status.value = BotStatus.WAITING_SIGNAL
            return
        }
        val maxPositions = riskStore.settings.value.maxOpenPositions
        var signalSeen = false

        for (symbol in symbols) {
            if (!botSettingsStore.settings.value.enabled) return
            val positions = accountRepository.positions.value
            if (positions.size >= maxPositions) {
                _status.value = BotStatus.POSITION_OPEN
                return
            }
            if (positions.any { it.symbol == symbol }) continue

            val klines = client.klines(symbol, config.interval.code, candleLimit(config))
            if (klines !is ExchangeResult.Ok) continue
            val signal = StrategyEngine.evaluate(klines.value, config)
            if (signal.direction == SignalDirection.NONE) continue
            if (!settings.allowShort && signal.direction == SignalDirection.SHORT) continue

            val lastCandle = lastCandleBySymbol[symbol] ?: 0L
            if (signal.candleTime <= lastCandle) continue
            lastCandleBySymbol[symbol] = signal.candleTime
            signalSeen = true
            process(symbol, settings, config, signal)
        }

        val open = accountRepository.positions.value
        _status.value = when {
            open.isNotEmpty() -> BotStatus.POSITION_OPEN
            signalSeen -> BotStatus.ACTIVE
            else -> BotStatus.WAITING_SIGNAL
        }
    }

    /** Taranacak pariteler; tarama sonucu 5 dakika boyunca yeniden kullanılır. */
    private suspend fun targetSymbols(settings: BotSettings): List<String> {
        val mode = ScanMode.entries.firstOrNull { it.name == settings.scanMode } ?: ScanMode.HYBRID
        val watchlist = settings.watchlist
            .split(',')
            .map { it.trim().uppercase() }
            .filter { it.length in 5..20 }

        if (mode == ScanMode.WATCHLIST) {
            val list = watchlist.ifEmpty { listOf(symbolStore.symbol.value) }
            _scanResults.value = list.map { watchlistCandidate(it) }
            return list
        }

        val now = System.currentTimeMillis()
        if (now - scanAt >= SCAN_TTL_MS || _scanResults.value.isEmpty()) {
            val result = scanner.scan(
                poolSize = settings.poolSize,
                count = settings.scanCount,
                minQuoteVolume = settings.minQuoteVolume,
            )
            when (result) {
                is ExchangeResult.Ok -> {
                    scanAt = now
                    val scanned = result.value
                    val extra = if (mode == ScanMode.HYBRID) {
                        watchlist.filter { symbol -> scanned.none { it.symbol == symbol } }
                            .map { watchlistCandidate(it) }
                    } else {
                        emptyList()
                    }
                    _scanResults.value = extra + scanned
                    log(
                        BotLogLevel.INFO,
                        "Tarama: ${scanned.size} parite · ilk sıra " +
                            (scanned.firstOrNull()?.let { "${it.symbol} (%${fmt(it.momentumPercent)}, hacim ×${fmt(it.volumeSpike)})" } ?: "yok"),
                    )
                }
                is ExchangeResult.Err -> log(BotLogLevel.WARN, "Tarama başarısız: ${result.error.userMessage}")
            }
        }
        val fromScan = if (mode == ScanMode.TOP_VOLUME) {
            _scanResults.value.sortedByDescending { it.quoteVolume }.map { it.symbol }
        } else {
            _scanResults.value.map { it.symbol }
        }
        return fromScan.distinct()
    }

    private fun watchlistCandidate(symbol: String) = ScanCandidate(
        symbol = symbol,
        changePercent = 0.0,
        quoteVolume = 0.0,
        volumeSpike = 1.0,
        momentumPercent = 0.0,
        score = 0.0,
        fromWatchlist = true,
    )

    private fun candleLimit(config: StrategyConfig): Int =
        (StrategyEngine.requiredCandles(config) + 30).coerceIn(50, 300)

    private suspend fun process(
        symbol: String,
        settings: BotSettings,
        config: StrategyConfig,
        signal: StrategySignal,
    ) {
        val side = if (signal.direction == SignalDirection.LONG) PositionSide.LONG else PositionSide.SHORT
        log(BotLogLevel.INFO, "$symbol ${side.name} sinyali · ${config.interval.code}")
        notifier.bot(
            "$symbol ${side.name} sinyali",
            "${config.interval.code} · " +
                if (settings.autoTrade) "emir gönderiliyor" else "yalnızca sinyal modu",
        )

        if (guard.emergencyStopped.value) {
            log(BotLogLevel.WARN, "Acil durdurma aktif — emir gönderilmedi")
            return
        }
        if (!settings.autoTrade) {
            log(BotLogLevel.INFO, "Yalnızca sinyal modu: emir gönderilmedi")
            return
        }
        resetDailyCounterIfNeeded()
        if (tradesToday >= settings.maxTradesPerDay) {
            log(BotLogLevel.WARN, "Günlük işlem sayısı limiti doldu (${settings.maxTradesPerDay})")
            return
        }
        if (stopIfDailyLossLimitReached()) return

        val risk = riskStore.settings.value
        val intent = OrderIntent(
            symbol = symbol,
            side = side,
            type = OrderType.MARKET,
            limitPrice = null,
            leverage = min(settings.leverage, risk.maxLeverage),
            sizing = SizingMode.RISK,
            riskPercent = risk.riskPerTradePercent,
            marginUsdt = 0.0,
            stopLossPercent = risk.defaultStopLossPercent,
            takeProfitPercent = risk.defaultTakeProfitPercent,
            origin = TradeOrigin.BOT,
            reason = "Bot sinyali · ${config.interval.code} · ${side.name}",
            signals = signal.checks.map { "${it.name}: ${it.detail}" },
        )
        when (val preview = orderManager.preview(intent)) {
            is PreviewResult.Rejected -> log(
                BotLogLevel.WARN,
                "$symbol emir açılmadı: " + preview.reasons.joinToString(" "),
            )
            is PreviewResult.Ready -> {
                log(
                    BotLogLevel.INFO,
                    "$symbol emir gönderiliyor: ${preview.preview.quantity.toPlainString()} " +
                        "@ ~${preview.preview.entryPrice.toPlainString()} · " +
                        "SL ${preview.preview.stopLossPrice.toPlainString()}",
                )
                when (val result = orderManager.submit(preview.preview)) {
                    is ActionResult.Success -> {
                        tradesToday++
                        log(BotLogLevel.OK, "$symbol: ${result.message}")
                    }
                    is ActionResult.Failure -> {
                        log(BotLogLevel.ERROR, "$symbol: ${result.message}")
                        notifier.alert("$symbol emir başarısız", result.message)
                    }
                }
                stopIfDailyLossLimitReached()
            }
        }
    }

    /** Günlük zarar limiti dolduysa botu kapatır. */
    private suspend fun stopIfDailyLossLimitReached(): Boolean {
        val settings = riskStore.settings.value
        val balance = client.balance()
        if (balance !is ExchangeResult.Ok) return false
        val daily = client.realizedPnlSince(OrderManager.startOfTodayMs())
        if (daily !is ExchangeResult.Ok) return false
        if (!RiskEngine.dailyLimitReached(settings, daily.value, balance.value.walletBalance)) return false
        botSettingsStore.setEnabled(false)
        _status.value = BotStatus.STOPPED
        val percent = RiskEngine.dailyLossPercent(daily.value, balance.value.walletBalance)
        log(BotLogLevel.ERROR, "Günlük zarar limiti doldu (%${fmt(percent)}) — bot durduruldu")
        notifier.alert("Günlük zarar limiti", "Limit doldu — bot durduruldu")
        return true
    }

    private fun resetDailyCounterIfNeeded() {
        val today = OrderManager.startOfTodayMs()
        if (today != tradesDayStart) {
            tradesDayStart = today
            tradesToday = 0
        }
    }

    private fun log(level: BotLogLevel, text: String) {
        val entry = BotLogEntry(System.currentTimeMillis(), level, text)
        _logs.value = (listOf(entry) + _logs.value).take(MAX_LOGS)
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val MAX_LOGS = 60
        const val CYCLE_MS = 30_000L
        const val IDLE_MS = 3_000L
        const val SCAN_TTL_MS = 5 * 60_000L
    }
}
