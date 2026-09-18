package com.fatih.futuresbot.domain.model

data class BotSettings(
    val enabled: Boolean = false,
    /** false ise sinyal yalnızca kaydedilir, emir gönderilmez. */
    val autoTrade: Boolean = false,
    val leverage: Int = 5,
    val maxTradesPerDay: Int = 10,
)

enum class BotLogLevel { INFO, OK, WARN, ERROR }

data class BotLogEntry(
    val time: Long,
    val level: BotLogLevel,
    val text: String,
)
