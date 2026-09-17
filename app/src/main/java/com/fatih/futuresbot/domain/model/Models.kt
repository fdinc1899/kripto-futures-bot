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
    val walletBalance: Double,
    val marginBalance: Double,
    val availableBalance: Double,
    val unrealizedPnl: Double,
    val dailyRealizedPnl: Double,
    val dailyPnlPercent: Double,
    val openPositions: Int,
)

/** null alanlar: veri henüz gelmedi ya da o paritede pozisyon yok. */
data class SymbolSnapshot(
    val symbol: String,
    val lastPrice: Double? = null,
    val change24hPercent: Double? = null,
    val markPrice: Double? = null,
    val fundingRate: Double? = null,
    val leverage: Int? = null,
    val positionAmt: Double? = null,
    val entryPrice: Double? = null,
    val margin: Double? = null,
    val liquidationPrice: Double? = null,
    val unrealizedPnl: Double? = null,
)
