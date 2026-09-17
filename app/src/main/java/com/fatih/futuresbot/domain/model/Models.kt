package com.fatih.futuresbot.domain.model

enum class TradingMode { TESTNET, REAL }

enum class PositionSide { LONG, SHORT }

enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED }

enum class BotStatus(val label: String) {
    ACTIVE("ACTIVE"),
    WAITING_SIGNAL("WAITING SIGNAL"),
    POSITION_OPEN("POSITION OPEN"),
    STOPPED("STOPPED"),
}

data class AccountSummary(
    val totalBalance: Double,
    val availableBalance: Double,
    val dailyPnl: Double,
    val totalPnl: Double,
    val dailyPnlPercent: Double,
    val openPositions: Int,
)

/** Fiyat alanları null ise veri henüz gelmemiştir; UI "—" gösterir. */
data class SymbolSnapshot(
    val symbol: String,
    val lastPrice: Double?,
    val change24hPercent: Double?,
    val leverage: Int,
    val margin: Double,
    val liquidationPrice: Double?,
)
