package com.fatih.futuresbot.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.trading.ActionResult
import com.fatih.futuresbot.trading.ExchangeClient
import com.fatih.futuresbot.trading.OrderManager
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OrderRow(
    val key: String,
    val id: Long,
    val symbol: String,
    val side: String,
    val title: String,
    val detail: String,
    val isConditional: Boolean,
    val isStopLoss: Boolean,
    val time: Long,
)

data class OrdersOps(
    val busy: Boolean = false,
    val confirm: OrderRow? = null,
    val result: ActionResult? = null,
)

data class OrdersUiState(
    val rows: List<OrderRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val ops: OrdersOps = OrdersOps(),
)

private data class OrdersLoad(
    val rows: List<OrderRow>,
    val error: String?,
    val loading: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModel(
    private val client: ExchangeClient,
    private val orderManager: OrderManager,
) : ViewModel() {

    private val ops = MutableStateFlow(OrdersOps())
    private val refresh = MutableStateFlow(0)

    private val load: StateFlow<OrdersLoad> = refresh
        .flatMapLatest {
            flow {
                while (currentCoroutineContext().isActive) {
                    emit(fetch())
                    delay(15_000L)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersLoad(emptyList(), null, true))

    val state: StateFlow<OrdersUiState> = combine(load, ops) { l, o ->
        OrdersUiState(rows = l.rows, loading = l.loading, error = l.error, ops = o)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    private suspend fun fetch(): OrdersLoad {
        val regular = client.openOrders(null)
        val conditional = client.openConditionalOrders(null)
        val rows = mutableListOf<OrderRow>()
        val errors = mutableListOf<String>()

        when (regular) {
            is ExchangeResult.Ok -> regular.value.forEach { o ->
                val priceText = if (o.price > 0.0) " @ ${plain(o.price)}" else ""
                rows.add(
                    OrderRow(
                        key = "o${o.orderId}",
                        id = o.orderId,
                        symbol = o.symbol,
                        side = o.side,
                        title = "${o.type} ${o.side}",
                        detail = "Miktar ${plain(o.origQty)}$priceText · dolan ${plain(o.executedQty)}" +
                            if (o.reduceOnly) " · reduce-only" else "",
                        isConditional = false,
                        isStopLoss = false,
                        time = o.time,
                    )
                )
            }
            is ExchangeResult.Err -> errors.add("Emirler: ${regular.error.userMessage}")
        }
        when (conditional) {
            is ExchangeResult.Ok -> conditional.value.forEach { a ->
                rows.add(
                    OrderRow(
                        key = "a${a.algoId}",
                        id = a.algoId,
                        symbol = a.symbol,
                        side = a.side,
                        title = "${a.orderType} ${a.side}",
                        detail = "Tetik ${plain(a.triggerPrice)} (mark)" +
                            if (a.closePosition) " · tüm pozisyonu kapatır" else "",
                        isConditional = true,
                        isStopLoss = a.orderType == "STOP_MARKET",
                        time = a.createTime,
                    )
                )
            }
            is ExchangeResult.Err -> errors.add("SL/TP: ${conditional.error.userMessage}")
        }
        return OrdersLoad(
            rows = rows.sortedByDescending { it.time },
            error = errors.firstOrNull(),
            loading = false,
        )
    }

    fun askCancel(row: OrderRow) {
        if (!ops.value.busy) ops.value = ops.value.copy(confirm = row)
    }

    fun dismissConfirm() {
        ops.value = ops.value.copy(confirm = null)
    }

    fun confirmCancel() {
        val row = ops.value.confirm ?: return
        if (ops.value.busy) return
        ops.value = OrdersOps(busy = true)
        viewModelScope.launch {
            val result = orderManager.cancelOrder(row.symbol, row.id, row.isConditional)
            ops.value = OrdersOps(result = result)
            refresh.value = refresh.value + 1
        }
    }

    fun reload() {
        refresh.value = refresh.value + 1
    }

    fun clearResult() {
        ops.value = OrdersOps()
    }

    private fun plain(v: Double): String = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { OrdersViewModel(container.exchangeClient, container.orderManager) }
        }
    }
}
