package com.fatih.futuresbot.trading

import com.fatih.futuresbot.domain.model.AlgoOrderInfo
import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.domain.model.ConditionalOrderRequest
import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.FuturesBalance
import com.fatih.futuresbot.domain.model.FuturesPosition
import com.fatih.futuresbot.domain.model.MarkPriceInfo
import com.fatih.futuresbot.domain.model.NewOrderRequest
import com.fatih.futuresbot.domain.model.OrderInfo
import com.fatih.futuresbot.domain.model.SymbolConfig
import com.fatih.futuresbot.domain.model.SymbolRules
import com.fatih.futuresbot.domain.model.Ticker24h

/**
 * Borsa bağımsız arayüz. Binance dışındaki borsalar (Bybit, OKX, Bitget)
 * bu arayüzü uygulayarak eklenir.
 */
interface ExchangeClient {
    val environment: ExchangeEnvironment
    val endpointLabel: String

    suspend fun ping(): ExchangeResult<Unit>
    /** Sunucu ile yerel saat farkını (ms) ölçer ve imzalı isteklerde kullanır. */
    suspend fun syncServerTime(): ExchangeResult<Long>
    suspend fun ticker24h(symbol: String): ExchangeResult<Ticker24h>
    suspend fun allTickers(): ExchangeResult<List<Ticker24h>>
    suspend fun klines(symbol: String, interval: String, limit: Int): ExchangeResult<List<Candle>>
    suspend fun markPrice(symbol: String): ExchangeResult<MarkPriceInfo>
    suspend fun balance(): ExchangeResult<FuturesBalance>
    suspend fun positions(): ExchangeResult<List<FuturesPosition>>
    suspend fun symbolConfig(symbol: String): ExchangeResult<SymbolConfig>
    suspend fun realizedPnlSince(startTimeMs: Long): ExchangeResult<Double>

    // ---- Emir işlemleri
    suspend fun symbolRules(symbol: String): ExchangeResult<SymbolRules>
    suspend fun isHedgeMode(): ExchangeResult<Boolean>
    suspend fun setLeverage(symbol: String, leverage: Int): ExchangeResult<Int>
    suspend fun placeOrder(request: NewOrderRequest): ExchangeResult<OrderInfo>
    suspend fun queryOrder(symbol: String, clientOrderId: String): ExchangeResult<OrderInfo>
    suspend fun openOrders(symbol: String?): ExchangeResult<List<OrderInfo>>
    suspend fun cancelOrder(symbol: String, orderId: Long): ExchangeResult<Unit>
    suspend fun cancelAllOrders(symbol: String): ExchangeResult<Unit>

    // ---- Koşullu (algo) emirler: Stop-Loss / Take-Profit
    suspend fun placeConditionalOrder(request: ConditionalOrderRequest): ExchangeResult<AlgoOrderInfo>
    suspend fun queryConditionalOrder(clientAlgoId: String): ExchangeResult<AlgoOrderInfo>
    suspend fun openConditionalOrders(symbol: String?): ExchangeResult<List<AlgoOrderInfo>>
    suspend fun cancelConditionalOrder(algoId: Long): ExchangeResult<Unit>
    suspend fun cancelAllConditionalOrders(symbol: String): ExchangeResult<Unit>
}
