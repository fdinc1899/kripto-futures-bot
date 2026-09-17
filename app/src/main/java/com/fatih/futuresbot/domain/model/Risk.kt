package com.fatih.futuresbot.domain.model

enum class SizingMode { RISK, MARGIN }

/** Şartname madde 6: risk yönetimi ayarları. */
data class RiskSettings(
    /** İşlem başına maksimum risk (cüzdan bakiyesinin yüzdesi) */
    val riskPerTradePercent: Double = 1.0,
    /** Günlük maksimum zarar (gün başı bakiyenin yüzdesi) */
    val maxDailyLossPercent: Double = 5.0,
    val maxOpenPositions: Int = 3,
    val maxLeverage: Int = 10,
    val minRiskReward: Double = 1.5,
    val defaultStopLossPercent: Double = 2.0,
    val defaultTakeProfitPercent: Double = 4.0,
    val trailingStopEnabled: Boolean = false,
    /** Trailing stop geri çekilme oranı (%) — Binance sınırı 0.1–10 */
    val trailingCallbackPercent: Double = 1.0,
)
