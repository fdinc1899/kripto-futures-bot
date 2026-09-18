package com.fatih.futuresbot.trading

import com.fatih.futuresbot.data.settings.BotSettingsStore
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.data.settings.StrategyStore
import com.fatih.futuresbot.domain.model.BotLogEntry
import com.fatih.futuresbot.domain.model.BotLogLevel
import com.fatih.futuresbot.domain.model.BotSettings
import com.fatih.futuresbot.domain.model.BotStatus
import com.fatih.futuresbot.domain.model.CandleSeries
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.OrderType
import com.fatih.futuresbot.domain.model.PositionSide
import com.fatih.futuresbot.domain.model.SignalDirection
import com.fatih.futuresbot.domain.model.SizingMode
import com.fatih.futuresbot.domain.model.StrategyConfig
import com.fatih.futuresbot.domain.model.StrategySignal
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.domain.repository.MarketRepository
import com.fatih.futuresbot.strategy.StrategyEngine
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class BotContext(
    val settings: BotSettings,
    val symbol: String,
    val config: StrategyConfig,
)

/**
 * Bot motoru. Sinyal oluşunca risk kurallarına uygun emri OrderManager üzerinden açar.
 * Kurallar:
 *  - Her mumda en fazla bir işlem; aynı mum ikinci kez işlenmez.
 *  - Aynı paritede açık pozisyon varken yeni işlem açılmaz.
 *  - Acil durdurma, bağlantı kopukluğu veya günlük limitlerde emir gönderilmez.
 *  - Günlük zarar limiti dolunca bot kendini kapatır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BotEngine(
    private val botSettingsStore: BotSettingsStore,
    private val strategyStore: StrategyStore,
    private val riskStore: RiskSettingsStore,
    private val symbolStore: SelectedSymbolStore,
    private val marketRepository: MarketRepository,
    private val accountRepository: AccountRepository,
    private val orderManager: OrderManager,
    private val guard: TradingGuard,
    private val client: ExchangeClient,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(BotStatus.STOPPED)
    val status: StateFlow<BotStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<BotLogEntry>>(emptyList())
    val logs: StateFlow<List<BotLogEntry>> = _logs.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var lastProcessedCandle = 0L
    @Volatile private var tradesToday = 0
    @Volatile private var tradesDayStart = 0L

    fun start() {
        // Bot açıkken hesap verisi canlı kalsın (pozisyon ve bakiye güncellemesi)
        scope.launch {
            botSettingsStore.settings
                .map { it.enabled }
                .distinctUntilChanged()
                .flatMapLatest { enabled ->
                    if (enabled) accountRepository.accountSummary() else flowOf(null)
                }
                .collect { }
        }
        scope.launch {
            combine(
                botSettingsStore.settings,
                symbolStore.symbol,
                strategyStore.config,
            ) { settings, symbol, config -> BotContext(settings, symbol, config) }
                .flatMapLatest { ctx ->
                    if (ctx.settings.enabled) {
                        marketRepository.candles(ctx.symbol, ctx.config.interval)
                            .map { series -> ctx to series }
                    } else {
                        flowOf(null)
                    }
                }
                .collect { tick ->
                    if (tick == null) {
                        _status.value = BotStatus.STOPPED
                    } else {
                        handle(tick.first, tick.second)
                    }
                }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private suspend fun handle(ctx: BotContext, series: CandleSeries) {
        if (series.candles.isEmpty()) {
            _status.value = BotStatus.ACTIVE
            return
        }
        val signal = StrategyEngine.evaluate(series.candles, ctx.config)
        val hasPosition = accountRepository.positions.value.any { it.symbol == ctx.symbol }
        _status.value = when {
            hasPosition -> BotStatus.POSITION_OPEN
            signal.direction == SignalDirection.NONE -> BotStatus.WAITING_SIGNAL
            else -> BotStatus.ACTIVE
        }
        if (signal.direction == SignalDirection.NONE) return
        if (signal.candleTime <= lastProcessedCandle) return

        mutex.withLock {
            if (signal.candleTime <= lastProcessedCandle) return@withLock
            lastProcessedCandle = signal.candleTime
            process(ctx, signal)
        }
    }

    private suspend fun process(ctx: BotContext, signal: StrategySignal) {
        val side = if (signal.direction == SignalDirection.LONG) PositionSide.LONG else PositionSide.SHORT
        log(BotLogLevel.INFO, "${ctx.symbol} ${side.name} sinyali · ${ctx.config.interval.code}")

        if (guard.emergencyStopped.value) {
            log(BotLogLevel.WARN, "Acil durdurma aktif — emir gönderilmedi")
            return
        }
        if (!ctx.settings.autoTrade) {
            log(BotLogLevel.INFO, "Yalnızca sinyal modu: emir gönderilmedi")
            return
        }
        if (accountRepository.connection.value != ConnectionState.CONNECTED) {
            log(BotLogLevel.WARN, "Borsa bağlantısı güvenilir değil — emir gönderilmedi")
            return
        }
        if (accountRepository.positions.value.any { it.symbol == ctx.symbol }) {
            log(BotLogLevel.WARN, "${ctx.symbol} için zaten açık pozisyon var")
            return
        }
        resetDailyCounterIfNeeded()
        if (tradesToday >= ctx.settings.maxTradesPerDay) {
            log(BotLogLevel.WARN, "Günlük işlem sayısı limiti doldu (${ctx.settings.maxTradesPerDay})")
            return
        }
        if (stopIfDailyLossLimitReached()) return

        val risk = riskStore.settings.value
        val intent = OrderIntent(
            symbol = ctx.symbol,
            side = side,
            type = OrderType.MARKET,
            limitPrice = null,
            leverage = min(ctx.settings.leverage, risk.maxLeverage),
            sizing = SizingMode.RISK,
            riskPercent = risk.riskPerTradePercent,
            marginUsdt = 0.0,
            stopLossPercent = risk.defaultStopLossPercent,
            takeProfitPercent = risk.defaultTakeProfitPercent,
        )
        when (val preview = orderManager.preview(intent)) {
            is PreviewResult.Rejected -> log(
                BotLogLevel.WARN,
                "Emir açılmadı: " + preview.reasons.joinToString(" "),
            )
            is PreviewResult.Ready -> {
                log(
                    BotLogLevel.INFO,
                    "Emir gönderiliyor: ${preview.preview.quantity.toPlainString()} " +
                        "@ ~${preview.preview.entryPrice.toPlainString()} · " +
                        "SL ${preview.preview.stopLossPrice.toPlainString()}",
                )
                when (val result = orderManager.submit(preview.preview)) {
                    is ActionResult.Success -> {
                        tradesToday++
                        log(BotLogLevel.OK, result.message)
                    }
                    is ActionResult.Failure -> log(BotLogLevel.ERROR, result.message)
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
        log(
            BotLogLevel.ERROR,
            "Günlük zarar limiti doldu (%" +
                String.format(java.util.Locale.US, "%.2f", RiskEngine.dailyLossPercent(daily.value, balance.value.walletBalance)) +
                ") — bot durduruldu",
        )
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

    private companion object {
        const val MAX_LOGS = 60
    }
}
