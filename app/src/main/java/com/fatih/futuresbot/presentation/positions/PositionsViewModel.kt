package com.fatih.futuresbot.presentation.positions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.FuturesPosition
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.trading.ActionResult
import com.fatih.futuresbot.trading.ExchangeClient
import com.fatih.futuresbot.trading.OrderManager
import com.fatih.futuresbot.trading.TradingGuard
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Kapatma onayı bekleyen istek: symbol null ise tüm pozisyonlar. */
data class CloseRequest(val symbol: String?)

data class PositionRow(
    val position: FuturesPosition,
    /** true: SL aktif, false: SL yok, null: bilinmiyor */
    val protectedBySl: Boolean?,
)

data class PositionsOps(
    val busy: Boolean = false,
    val confirm: CloseRequest? = null,
    val result: ActionResult? = null,
)

data class PositionsUiState(
    val rows: List<PositionRow> = emptyList(),
    val loading: Boolean = true,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val emergencyStopped: Boolean = false,
    val lastEvent: String? = null,
    val ops: PositionsOps = PositionsOps(),
)

private data class StatusInfo(
    val connection: ConnectionState,
    val emergencyStopped: Boolean,
    val lastEvent: String?,
)

class PositionsViewModel(
    private val orderManager: OrderManager,
    accountRepository: AccountRepository,
    client: ExchangeClient,
    guard: TradingGuard,
) : ViewModel() {

    private val ops = MutableStateFlow(PositionsOps())

    // SL koruması olan pariteler (10 sn'de bir, ekran açıkken)
    private val slSymbols: StateFlow<Set<String>?> = flow {
        while (currentCoroutineContext().isActive) {
            val r = client.openConditionalOrders(null)
            val set = if (r is ExchangeResult.Ok) {
                r.value.filter { it.orderType == "STOP_MARKET" }.map { it.symbol }.toSet()
            } else {
                null
            }
            emit(set)
            delay(10_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val status = combine(
        accountRepository.connection,
        guard.emergencyStopped,
        orderManager.lastEvent,
    ) { connection, stopped, event -> StatusInfo(connection, stopped, event) }

    val state: StateFlow<PositionsUiState> = combine(
        accountRepository.accountSummary(),
        accountRepository.positions,
        slSymbols,
        status,
        ops,
    ) { account, positions, protectedSet, info, o ->
        PositionsUiState(
            rows = positions.map { p -> PositionRow(p, protectedSet?.contains(p.symbol)) },
            loading = account == null && info.connection != ConnectionState.DISCONNECTED,
            connection = info.connection,
            emergencyStopped = info.emergencyStopped,
            lastEvent = info.lastEvent,
            ops = o,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PositionsUiState())

    fun askClose(symbol: String) {
        if (!ops.value.busy) ops.value = ops.value.copy(confirm = CloseRequest(symbol))
    }

    fun askCloseAll() {
        if (!ops.value.busy) ops.value = ops.value.copy(confirm = CloseRequest(null))
    }

    fun dismissConfirm() {
        ops.value = ops.value.copy(confirm = null)
    }

    fun confirmClose() {
        val request = ops.value.confirm ?: return
        if (ops.value.busy) return
        ops.value = PositionsOps(busy = true)
        viewModelScope.launch {
            val symbol = request.symbol
            val result = if (symbol != null) {
                orderManager.closePosition(symbol)
            } else {
                orderManager.closeAllPositions()
            }
            ops.value = PositionsOps(result = result)
        }
    }

    fun clearResult() {
        ops.value = PositionsOps()
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                PositionsViewModel(
                    orderManager = container.orderManager,
                    accountRepository = container.accountRepository,
                    client = container.exchangeClient,
                    guard = container.tradingGuard,
                )
            }
        }
    }
}
