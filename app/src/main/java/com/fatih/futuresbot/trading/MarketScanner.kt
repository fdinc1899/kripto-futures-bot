package com.fatih.futuresbot.trading

import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.ScanCandidate
import kotlin.math.abs
import kotlin.math.min

/**
 * Hareketli pariteleri bulur: önce hacimde ilk N parite havuzu,
 * sonra bu havuzda hacim sıçraması ve kısa vadeli momentum sıralaması.
 * Haber kaynaklı sert hareketler bu iki veride iz bırakır.
 */
class MarketScanner(private val client: ExchangeClient) {

    suspend fun scan(
        poolSize: Int,
        count: Int,
        minQuoteVolume: Double,
    ): ExchangeResult<List<ScanCandidate>> {
        val tickers = when (val r = client.allTickers()) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return ExchangeResult.Err(r.error)
        }
        val pool = tickers
            .filter { it.symbol.endsWith("USDT") && !it.symbol.contains('_') }
            .filter { it.quoteVolume >= minQuoteVolume }
            .sortedByDescending { it.quoteVolume }
            .take(poolSize.coerceIn(5, 60))

        val candidates = mutableListOf<ScanCandidate>()
        for (ticker in pool) {
            var spike = 1.0
            var momentum = 0.0
            val klines = client.klines(ticker.symbol, SCAN_INTERVAL, SCAN_LIMIT)
            if (klines is ExchangeResult.Ok) {
                val closed = klines.value.filter { it.closed }
                if (closed.size >= 13) {
                    val last = closed.last()
                    val history = closed.dropLast(1).takeLast(20)
                    val averageVolume = if (history.isEmpty()) 0.0 else history.sumOf { it.volume } / history.size
                    if (averageVolume > 0.0) spike = last.volume / averageVolume
                    val past = closed[closed.size - 13]
                    if (past.close > 0.0) momentum = (last.close - past.close) / past.close * 100.0
                }
            }
            candidates.add(
                ScanCandidate(
                    symbol = ticker.symbol,
                    changePercent = ticker.priceChangePercent,
                    quoteVolume = ticker.quoteVolume,
                    volumeSpike = spike,
                    momentumPercent = momentum,
                    score = score(ticker.priceChangePercent, momentum, spike),
                )
            )
        }
        return ExchangeResult.Ok(
            candidates.sortedByDescending { it.score }.take(count.coerceIn(1, 50))
        )
    }

    /** Skor: kısa vadeli momentum ve hacim sıçraması ağır basar. */
    private fun score(changePercent: Double, momentumPercent: Double, volumeSpike: Double): Double =
        abs(changePercent) * 0.5 + abs(momentumPercent) * 1.5 + min(volumeSpike, 5.0) * 2.0

    private companion object {
        const val SCAN_INTERVAL = "5m"
        const val SCAN_LIMIT = 50
    }
}
