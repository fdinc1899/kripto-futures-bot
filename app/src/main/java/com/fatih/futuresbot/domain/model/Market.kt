package com.fatih.futuresbot.domain.model

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closed: Boolean,
)

data class CandleSeries(
    val candles: List<Candle>,
    val error: String? = null,
)

data class LiveTicker(
    val symbol: String,
    val lastPrice: Double? = null,
    val change24hPercent: Double? = null,
    val markPrice: Double? = null,
    val fundingRate: Double? = null,
)

enum class ChartInterval(val code: String) {
    M1("1m"),
    M5("5m"),
    M15("15m"),
    H1("1h"),
    H4("4h"),
    D1("1d"),
}
