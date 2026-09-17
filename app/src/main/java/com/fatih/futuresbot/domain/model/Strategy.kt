package com.fatih.futuresbot.domain.model

/** Şartname madde 5: modüler strateji tanımı. */
data class StrategyConfig(
    val interval: ChartInterval = ChartInterval.M15,
    val rsi: RsiRule = RsiRule(),
    val ema: EmaRule = EmaRule(),
    val sma: SmaRule = SmaRule(),
    val macd: MacdRule = MacdRule(),
    val bollinger: BollingerRule = BollingerRule(),
    val volume: VolumeRule = VolumeRule(),
    val atr: AtrRule = AtrRule(),
)

/** RSI eşikleri: LONG için altına, SHORT için üstüne bakılır. */
data class RsiRule(
    val enabled: Boolean = true,
    val period: Int = 14,
    val longBelow: Double = 30.0,
    val shortAbove: Double = 70.0,
)

/** Hızlı EMA yavaş EMA'nın üstündeyse LONG, altındaysa SHORT. */
data class EmaRule(
    val enabled: Boolean = true,
    val fast: Int = 20,
    val slow: Int = 50,
)

/** Fiyat SMA'nın üstündeyse LONG, altındaysa SHORT (trend filtresi). */
data class SmaRule(
    val enabled: Boolean = false,
    val period: Int = 200,
)

/** MACD çizgisi sinyal çizgisinin üstündeyse LONG. */
data class MacdRule(
    val enabled: Boolean = false,
    val fast: Int = 12,
    val slow: Int = 26,
    val signal: Int = 9,
)

/**
 * reversion=true: alt banda değince LONG, üst banda değince SHORT.
 * reversion=false: üst bandı kırınca LONG, alt bandı kırınca SHORT.
 */
data class BollingerRule(
    val enabled: Boolean = false,
    val period: Int = 20,
    val deviation: Double = 2.0,
    val reversion: Boolean = true,
)

/** Yön belirtmez, filtredir: hacim ortalamanın katından büyük olmalı. */
data class VolumeRule(
    val enabled: Boolean = true,
    val period: Int = 20,
    val multiplier: Double = 1.0,
)

/** Yön belirtmez, filtredir: ATR/fiyat oranı en az bu yüzde olmalı. */
data class AtrRule(
    val enabled: Boolean = false,
    val period: Int = 14,
    val minPercent: Double = 0.2,
)

enum class SignalDirection { LONG, SHORT, NONE }

data class RuleCheck(
    val name: String,
    val detail: String,
    val longOk: Boolean,
    val shortOk: Boolean,
    /** true ise yön belirtmez, yalnızca izin verir (hacim, ATR). */
    val filter: Boolean,
)

data class StrategySignal(
    val direction: SignalDirection = SignalDirection.NONE,
    val checks: List<RuleCheck> = emptyList(),
    val candleTime: Long = 0L,
    val price: Double = 0.0,
    val error: String? = null,
)
