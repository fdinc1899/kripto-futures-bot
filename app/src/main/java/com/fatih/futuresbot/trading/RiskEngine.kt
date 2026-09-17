package com.fatih.futuresbot.trading

import com.fatih.futuresbot.domain.model.RiskSettings
import kotlin.math.abs

/** Saf hesaplamalar: hem emir motoru hem arayüz kullanır. */
object RiskEngine {

    /** İşlem başına riske edilebilecek en yüksek tutar (USDT). */
    fun maxRiskAmount(settings: RiskSettings, walletBalance: Double): Double =
        walletBalance * settings.riskPerTradePercent / 100.0

    /** Verilen risk tutarı ve SL mesafesine göre pozisyon miktarı. */
    fun quantityForRisk(riskAmount: Double, entryPrice: Double, stopLossPrice: Double): Double {
        val distance = abs(entryPrice - stopLossPrice)
        if (distance <= 0.0) return 0.0
        return riskAmount / distance
    }

    /** Gün başı bakiyeye göre bugünkü zarar yüzdesi (kâr varsa 0). */
    fun dailyLossPercent(dailyRealizedPnl: Double, walletBalance: Double): Double {
        if (dailyRealizedPnl >= 0.0) return 0.0
        val dayStart = walletBalance - dailyRealizedPnl
        if (dayStart <= 0.0) return 0.0
        return -dailyRealizedPnl / dayStart * 100.0
    }

    fun dailyLimitReached(
        settings: RiskSettings,
        dailyRealizedPnl: Double,
        walletBalance: Double,
    ): Boolean = dailyLossPercent(dailyRealizedPnl, walletBalance) >= settings.maxDailyLossPercent
}
