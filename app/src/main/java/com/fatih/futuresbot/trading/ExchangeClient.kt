package com.fatih.futuresbot.trading

import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.FuturesBalance
import com.fatih.futuresbot.domain.model.FuturesPosition
import com.fatih.futuresbot.domain.model.MarkPriceInfo
import com.fatih.futuresbot.domain.model.SymbolConfig
import com.fatih.futuresbot.domain.model.Ticker24h

/**
 * Borsa bağımsız arayüz. Binance dışındaki borsalar (Bybit, OKX, Bitget)
 * bu arayüzü uygulayarak eklenir. Emir fonksiyonları Aşama 8'de eklenecek.
 */
interface ExchangeClient {
    val environment: ExchangeEnvironment
    val endpointLabel: String

    suspend fun ping(): ExchangeResult<Unit>
    /** Sunucu ile yerel saat farkını (ms) ölçer ve imzalı isteklerde kullanır. */
    suspend fun syncServerTime(): ExchangeResult<Long>
    suspend fun ticker24h(symbol: String): ExchangeResult<Ticker24h>
    suspend fun markPrice(symbol: String): ExchangeResult<MarkPriceInfo>
    suspend fun balance(): ExchangeResult<FuturesBalance>
    suspend fun positions(): ExchangeResult<List<FuturesPosition>>
    suspend fun symbolConfig(symbol: String): ExchangeResult<SymbolConfig>
    suspend fun realizedPnlSince(startTimeMs: Long): ExchangeResult<Double>
}
