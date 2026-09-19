package com.fatih.futuresbot.domain.model

data class BotSettings(
    val enabled: Boolean = false,
    /** false ise sinyal yalnızca kaydedilir, emir gönderilmez. */
    val autoTrade: Boolean = false,
    val leverage: Int = 5,
    val maxTradesPerDay: Int = 10,
    /** Hangi pariteler taranacak */
    val scanMode: String = ScanMode.HYBRID.name,
    /** Hacim havuzu büyüklüğü */
    val poolSize: Int = 25,
    /** Havuzdan kaç parite taranacak */
    val scanCount: Int = 12,
    /** Havuza girmek için en az 24 saatlik hacim (USDT) */
    val minQuoteVolume: Double = 0.0,
    /** Her zaman taranacak pariteler, virgülle ayrılmış */
    val watchlist: String = "BTCUSDT,ETHUSDT,SOLUSDT",
    val allowShort: Boolean = true,
)

enum class BotLogLevel { INFO, OK, WARN, ERROR }

data class BotLogEntry(
    val time: Long,
    val level: BotLogLevel,
    val text: String,
)
