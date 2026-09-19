package com.fatih.futuresbot.presentation.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.data.settings.TradingModeStore
import com.fatih.futuresbot.domain.model.AccountSummary
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.OrderType
import com.fatih.futuresbot.domain.model.PositionSide
import com.fatih.futuresbot.domain.model.RiskSettings
import com.fatih.futuresbot.domain.model.SizingMode
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.domain.model.SymbolSnapshot
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.trading.ActionResult
import com.fatih.futuresbot.trading.OrderIntent
import com.fatih.futuresbot.trading.OrderManager
import com.fatih.futuresbot.trading.OrderPreview
import com.fatih.futuresbot.trading.PreviewResult
import com.fatih.futuresbot.trading.TradingGuard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun plainOf(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** Form varsayılanları risk ayarlarından gelir. */
private fun defaultForm(risk: RiskSettings): TradeForm = TradeForm(
    leverage = minOf(5, risk.maxLeverage),
    riskPercent = plainOf(risk.riskPerTradePercent),
    stopLoss = plainOf(risk.defaultStopLossPercent),
    takeProfit = plainOf(risk.defaultTakeProfitPercent),
)

data class TradeForm(
    val side: PositionSide = PositionSide.LONG,
    val type: OrderType = OrderType.MARKET,
    val limitPrice: String = "",
    val leverage: Int = 5,
    val sizing: SizingMode = SizingMode.RISK,
    val riskPercent: String = "1",
    val margin: String = "100",
    val stopLoss: String = "2",
    val takeProfit: String = "4",
)

data class TradeOps(
    val busy: Boolean = false,
    val preview: OrderPreview? = null,
    val rejectReasons: List<String> = emptyList(),
    val result: ActionResult? = null,
)

data class TradeUiState(
    val symbol: String = SelectedSymbolStore.DEFAULT,
    val form: TradeForm = TradeForm(),
    val ops: TradeOps = TradeOps(),
    val lastPrice: Double? = null,
    val markPrice: Double? = null,
    val availableBalance: Double? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val emergencyStopped: Boolean = false,
    val risk: RiskSettings = RiskSettings(),
    val walletBalance: Double? = null,
    val mode: TradingMode = TradingMode.TESTNET,
)

private data class MarketInfo(
    val snapshot: SymbolSnapshot,
    val account: AccountSummary?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TradeViewModel(
    private val orderManager: OrderManager,
    accountRepository: AccountRepository,
    private val symbolStore: SelectedSymbolStore,
    guard: TradingGuard,
    riskStore: RiskSettingsStore,
    private val modeStore: TradingModeStore,
) : ViewModel() {

    private val form = MutableStateFlow(defaultForm(riskStore.settings.value))
    private val ops = MutableStateFlow(TradeOps())

    private val marketInfo = combine(
        symbolStore.symbol.flatMapLatest { accountRepository.symbolSnapshot(it) },
        accountRepository.accountSummary(),
    ) { snapshot, account -> MarketInfo(snapshot, account) }

    val state: StateFlow<TradeUiState> = combine(
        form,
        ops,
        marketInfo,
        accountRepository.connection,
        combine(guard.emergencyStopped, riskStore.settings) { stopped, risk -> stopped to risk },
    ) { f, o, info, connection, guardAndRisk ->
        TradeUiState(
            symbol = info.snapshot.symbol,
            form = f,
            ops = o,
            lastPrice = info.snapshot.lastPrice,
            markPrice = info.snapshot.markPrice,
            availableBalance = info.account?.availableBalance,
            connection = connection,
            emergencyStopped = guardAndRisk.first,
            risk = guardAndRisk.second,
            walletBalance = info.account?.walletBalance,
            mode = modeStore.sessionMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TradeUiState())

    /** Ekran her açıldığında çağrılır: yönü ayarlar, eski önizleme/sonucu temizler. */
    fun open(side: PositionSide) {
        if (ops.value.busy) return
        form.update { it.copy(side = side) }
        ops.value = TradeOps()
    }

    fun setSide(side: PositionSide) = updateForm { it.copy(side = side) }

    fun setType(type: OrderType) = updateForm { current ->
        val price = if (type == OrderType.LIMIT && current.limitPrice.isBlank()) {
            state.value.lastPrice?.let { plain(it) } ?: ""
        } else {
            current.limitPrice
        }
        current.copy(type = type, limitPrice = price)
    }

    fun setLimitPrice(value: String) = updateForm { it.copy(limitPrice = clean(value)) }

    fun setSizing(mode: SizingMode) = updateForm { it.copy(sizing = mode) }

    fun setRiskPercent(value: String) = updateForm { it.copy(riskPercent = clean(value)) }

    fun setLeverage(value: Int) = updateForm {
        it.copy(leverage = value.coerceIn(1, OrderManager.MAX_LEVERAGE))
    }

    fun setMargin(value: String) = updateForm { it.copy(margin = clean(value)) }

    fun setStopLoss(value: String) = updateForm { it.copy(stopLoss = clean(value)) }

    fun setTakeProfit(value: String) = updateForm { it.copy(takeProfit = clean(value)) }

    fun requestPreview() {
        if (ops.value.busy) return
        val f = form.value
        val intent = OrderIntent(
            symbol = symbolStore.symbol.value,
            side = f.side,
            type = f.type,
            limitPrice = f.limitPrice.toDoubleOrNull(),
            leverage = f.leverage,
            sizing = f.sizing,
            riskPercent = f.riskPercent.toDoubleOrNull(),
            marginUsdt = f.margin.toDoubleOrNull() ?: 0.0,
            stopLossPercent = f.stopLoss.toDoubleOrNull() ?: 0.0,
            takeProfitPercent = if (f.takeProfit.isBlank()) null else f.takeProfit.toDoubleOrNull() ?: -1.0,
        )
        ops.value = TradeOps(busy = true)
        viewModelScope.launch {
            val next = when (val r = orderManager.preview(intent)) {
                is PreviewResult.Ready -> TradeOps(preview = r.preview)
                is PreviewResult.Rejected -> TradeOps(rejectReasons = r.reasons)
            }
            ops.value = next
        }
    }

    fun confirmSubmit() {
        val current = ops.value
        val preview = current.preview ?: return
        if (current.busy) return
        ops.value = current.copy(busy = true)
        viewModelScope.launch {
            val result = orderManager.submit(preview)
            ops.value = TradeOps(result = result)
        }
    }

    fun dismissPreview() {
        if (ops.value.busy) return
        ops.value = TradeOps()
    }

    fun clearResult() {
        ops.value = TradeOps()
    }

    private fun updateForm(block: (TradeForm) -> TradeForm) {
        if (ops.value.busy) return
        form.update(block)
        // Form değişince eski önizleme geçersizdir
        if (ops.value.preview != null || ops.value.rejectReasons.isNotEmpty()) {
            ops.value = ops.value.copy(preview = null, rejectReasons = emptyList())
        }
    }

    private fun clean(value: String): String =
        value.replace(',', '.').filter { it.isDigit() || it == '.' }.take(16)

    private fun plain(value: Double): String = plainOf(value)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                TradeViewModel(
                    orderManager = container.orderManager,
                    accountRepository = container.accountRepository,
                    symbolStore = container.selectedSymbolStore,
                    guard = container.tradingGuard,
                    riskStore = container.riskSettingsStore,
                    modeStore = container.tradingModeStore,
                )
            }
        }
    }
}
