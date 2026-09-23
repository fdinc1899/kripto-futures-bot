package com.fatih.futuresbot.domain.repository

import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.domain.model.CandleSeries
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeError
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.LiveTicker
import com.fatih.futuresbot.domain.model.Ticker24h
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MarketRepository {
    /** WebSocket (canlı veri) bağlantı durumu. */
    val streamState: StateFlow<ConnectionState>
    fun liveTicker(symbol: String): Flow<LiveTicker>
    fun candles(symbol: String, interval: ChartInterval): Flow<CandleSeries>
    suspend fun allUsdtTickers(): ExchangeResult<List<Ticker24h>>

    /** Tek seferlik REST mum isteği — canlı akış (WebSocket) açmaz. */
    suspend fun recentCandles(
        symbol: String,
        interval: ChartInterval,
        limit: Int,
    ): ExchangeResult<List<Candle>> =
        ExchangeResult.Err(ExchangeError.Unknown("recentCandles desteklenmiyor"))
}
