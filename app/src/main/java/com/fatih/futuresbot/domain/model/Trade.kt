package com.fatih.futuresbot.domain.model

import kotlinx.serialization.Serializable

enum class TradeStatus { OPEN, CLOSED }

enum class TradeOrigin { MANUAL, BOT }

/** Borsadan gelen tekil işlem (fill). */
data class Fill(
    val symbol: String,
    val id: Long,
    val orderId: Long,
    val side: String,
    val price: Double,
    val quantity: Double,
    val realizedPnl: Double,
    val commission: Double,
    val time: Long,
)

/** Şartname madde 9: kaydedilen işlem. */
@Serializable
data class TradeRecord(
    val id: String,
    val symbol: String,
    val side: String,
    val origin: String,
    val openTime: Long,
    val closeTime: Long? = null,
    val entryPrice: Double,
    val exitPrice: Double? = null,
    val quantity: Double,
    val leverage: Int,
    val notional: Double,
    val margin: Double,
    val stopLoss: Double,
    val takeProfit: Double? = null,
    val realizedPnl: Double? = null,
    val pnlPercent: Double? = null,
    val status: String = TradeStatus.OPEN.name,
    /** İşlemin açılma nedeni */
    val reason: String = "",
    /** Sinyal anındaki indikatör değerleri */
    val signals: List<String> = emptyList(),
)

data class TradeStats(
    val total: Int = 0,
    val open: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRate: Double = 0.0,
    val lossRate: Double = 0.0,
    val totalPnl: Double = 0.0,
    val averageProfit: Double = 0.0,
    val averageLoss: Double = 0.0,
    val profitFactor: Double? = null,
    val maxDrawdown: Double = 0.0,
)
