package com.fatih.futuresbot.trading

import com.fatih.futuresbot.data.history.TradeHistoryStore
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.TradeStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Açık kalan kayıtları borsadaki gerçek işlemlerle eşler:
 * pozisyon kapandıysa çıkış fiyatını ve gerçekleşen PNL'i doldurur.
 */
class HistorySync(
    private val client: ExchangeClient,
    private val store: TradeHistoryStore,
) {
    private val mutex = Mutex()

    suspend fun sync(): Int {
        return mutex.withLock {
            val open = store.records.value.filter { it.status == TradeStatus.OPEN.name }
            if (open.isEmpty()) {
                0
            } else {
                val positions = client.positions()
                if (positions !is ExchangeResult.Ok) {
                    0
                } else {
                    var updated = 0
                    for (record in open) {
                        if (positions.value.any { it.symbol == record.symbol }) continue
                        val fills = client.userTrades(record.symbol, record.openTime)
                        if (fills !is ExchangeResult.Ok) continue
                        val closing = fills.value.filter { it.realizedPnl != 0.0 && it.time >= record.openTime }
                        if (closing.isEmpty()) continue
                        val quantity = closing.sumOf { it.quantity }
                        val exitPrice = if (quantity > 0.0) {
                            closing.sumOf { it.price * it.quantity } / quantity
                        } else {
                            0.0
                        }
                        val pnl = closing.sumOf { it.realizedPnl } - closing.sumOf { it.commission }
                        val closeTime = closing.maxOf { it.time }
                        store.update(record.id) { current ->
                            current.copy(
                                status = TradeStatus.CLOSED.name,
                                closeTime = closeTime,
                                exitPrice = exitPrice,
                                realizedPnl = pnl,
                                pnlPercent = if (current.margin > 0.0) pnl / current.margin * 100.0 else null,
                            )
                        }
                        updated++
                    }
                    updated
                }
            }
        }
    }
}
