package com.fatih.futuresbot.data.binance

import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.domain.model.CandleSeries
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.LiveTicker
import com.fatih.futuresbot.domain.model.Ticker24h
import com.fatih.futuresbot.domain.repository.MarketRepository
import com.fatih.futuresbot.network.binance.BinanceMarketStream
import com.fatih.futuresbot.network.binance.StreamEvent
import com.fatih.futuresbot.network.binance.dbl
import com.fatih.futuresbot.network.binance.lng
import com.fatih.futuresbot.network.binance.str
import com.fatih.futuresbot.trading.ExchangeClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject

class BinanceMarketRepository(
    private val client: ExchangeClient,
    private val stream: BinanceMarketStream,
) : MarketRepository {

    override val streamState: StateFlow<ConnectionState> = stream.state

    override fun liveTicker(symbol: String): Flow<LiveTicker> = flow {
        // İlk değerler REST'ten; sonrası WebSocket
        var current = LiveTicker(symbol)
        val ticker = client.ticker24h(symbol)
        if (ticker is ExchangeResult.Ok) {
            current = current.copy(
                lastPrice = ticker.value.lastPrice,
                change24hPercent = ticker.value.priceChangePercent,
            )
        }
        val mark = client.markPrice(symbol)
        if (mark is ExchangeResult.Ok) {
            current = current.copy(
                markPrice = mark.value.markPrice,
                fundingRate = mark.value.lastFundingRate,
            )
        }
        emit(current)

        val s = symbol.lowercase()
        stream.subscribe(listOf("$s@ticker", "$s@markPrice@1s")).collect { event ->
            if (event !is StreamEvent.Data) return@collect
            val d = event.data
            current = when {
                event.stream.endsWith("@ticker") -> current.copy(
                    lastPrice = d.dbl("c") ?: current.lastPrice,
                    change24hPercent = d.dbl("P") ?: current.change24hPercent,
                )
                event.stream.contains("@markPrice") -> current.copy(
                    markPrice = d.dbl("p") ?: current.markPrice,
                    fundingRate = d.dbl("r") ?: current.fundingRate,
                )
                else -> current
            }
            emit(current)
        }
    }

    override fun candles(symbol: String, interval: ChartInterval): Flow<CandleSeries> = flow {
        var series = emptyList<Candle>()
        var loaded = false
        while (!loaded && currentCoroutineContext().isActive) {
            when (val r = client.klines(symbol, interval.code, HISTORY_LIMIT)) {
                is ExchangeResult.Ok -> {
                    series = r.value
                    loaded = true
                }
                is ExchangeResult.Err -> {
                    emit(CandleSeries(series, r.error.userMessage))
                    delay(RETRY_MS)
                }
            }
        }
        emit(CandleSeries(series))

        var openedBefore = false
        val streamName = "${symbol.lowercase()}@kline_${interval.code}"
        stream.subscribe(listOf(streamName)).collect { event ->
            when (event) {
                StreamEvent.Opened -> {
                    if (openedBefore) {
                        // Yeniden bağlantı: kopukluk sırasında kaçan mumları REST ile tamamla
                        val r = client.klines(symbol, interval.code, HISTORY_LIMIT)
                        if (r is ExchangeResult.Ok) {
                            series = r.value
                            emit(CandleSeries(series))
                        }
                    }
                    openedBefore = true
                }
                is StreamEvent.Data -> {
                    val k = event.data["k"] as? JsonObject ?: return@collect
                    val candle = parseKline(k) ?: return@collect
                    series = merge(series, candle)
                    emit(CandleSeries(series))
                }
            }
        }
    }

    override suspend fun allUsdtTickers(): ExchangeResult<List<Ticker24h>> =
        when (val r = client.allTickers()) {
            is ExchangeResult.Ok -> ExchangeResult.Ok(
                r.value
                    .filter { it.symbol.endsWith("USDT") && !it.symbol.contains('_') && it.quoteVolume > 0.0 }
                    .sortedByDescending { it.quoteVolume }
            )
            is ExchangeResult.Err -> ExchangeResult.Err(r.error)
        }

    private fun parseKline(k: JsonObject): Candle? = Candle(
        openTime = k.lng("t") ?: return null,
        open = k.dbl("o") ?: return null,
        high = k.dbl("h") ?: return null,
        low = k.dbl("l") ?: return null,
        close = k.dbl("c") ?: return null,
        volume = k.dbl("v") ?: 0.0,
        closed = k.str("x") == "true",
    )

    private fun merge(series: List<Candle>, candle: Candle): List<Candle> {
        val last = series.lastOrNull() ?: return listOf(candle)
        return when {
            candle.openTime == last.openTime -> series.dropLast(1) + candle
            candle.openTime > last.openTime -> (series + candle).takeLast(MAX_CANDLES)
            else -> series
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 300
        const val MAX_CANDLES = 600
        const val RETRY_MS = 5_000L
    }
}
