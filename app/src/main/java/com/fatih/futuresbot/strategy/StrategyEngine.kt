package com.fatih.futuresbot.strategy

import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.domain.model.RuleCheck
import com.fatih.futuresbot.domain.model.SignalDirection
import com.fatih.futuresbot.domain.model.StrategyConfig
import com.fatih.futuresbot.domain.model.StrategySignal
import java.util.Locale

/**
 * Sinyal üretimi. Yalnızca KAPANMIŞ mumlar kullanılır; açık mumun verisi
 * değişebileceği için sinyal üretiminde dikkate alınmaz.
 *
 * Kural: yön belirten tüm açık kurallar aynı yönü göstermeli ve
 * tüm filtreler (hacim, ATR) geçilmelidir.
 */
object StrategyEngine {

    fun evaluate(candles: List<Candle>, config: StrategyConfig): StrategySignal {
        val closed = candles.filter { it.closed }
        val required = requiredCandles(config)
        if (closed.size < required) {
            return StrategySignal(
                error = "Yeterli kapanmış mum yok (${closed.size}/$required)",
            )
        }
        val closes = closed.map { it.close }
        val volumes = closed.map { it.volume }
        val last = closed.size - 1
        val price = closes[last]
        val checks = mutableListOf<RuleCheck>()

        if (config.rsi.enabled) {
            val value = Indicators.rsi(closes, config.rsi.period)[last]
            checks.add(
                RuleCheck(
                    name = "RSI(${config.rsi.period})",
                    detail = if (value.isNaN()) {
                        "hesaplanamadı"
                    } else {
                        "${fmt(value)} · LONG < ${fmt(config.rsi.longBelow)} · SHORT > ${fmt(config.rsi.shortAbove)}"
                    },
                    longOk = !value.isNaN() && value < config.rsi.longBelow,
                    shortOk = !value.isNaN() && value > config.rsi.shortAbove,
                    filter = false,
                )
            )
        }
        if (config.ema.enabled) {
            val fast = Indicators.ema(closes, config.ema.fast)[last]
            val slow = Indicators.ema(closes, config.ema.slow)[last]
            val ready = !fast.isNaN() && !slow.isNaN()
            checks.add(
                RuleCheck(
                    name = "EMA ${config.ema.fast}/${config.ema.slow}",
                    detail = if (ready) "${fmt(fast)} / ${fmt(slow)}" else "hesaplanamadı",
                    longOk = ready && fast > slow,
                    shortOk = ready && fast < slow,
                    filter = false,
                )
            )
        }
        if (config.sma.enabled) {
            val value = Indicators.sma(closes, config.sma.period)[last]
            val ready = !value.isNaN()
            checks.add(
                RuleCheck(
                    name = "SMA(${config.sma.period}) trend",
                    detail = if (ready) "fiyat ${fmt(price)} · SMA ${fmt(value)}" else "hesaplanamadı",
                    longOk = ready && price > value,
                    shortOk = ready && price < value,
                    filter = false,
                )
            )
        }
        if (config.macd.enabled) {
            val macd = Indicators.macd(closes, config.macd.fast, config.macd.slow, config.macd.signal)
            val line = macd.line[last]
            val signal = macd.signal[last]
            val ready = !line.isNaN() && !signal.isNaN()
            checks.add(
                RuleCheck(
                    name = "MACD ${config.macd.fast}/${config.macd.slow}/${config.macd.signal}",
                    detail = if (ready) "çizgi ${fmt(line)} · sinyal ${fmt(signal)}" else "hesaplanamadı",
                    longOk = ready && line > signal,
                    shortOk = ready && line < signal,
                    filter = false,
                )
            )
        }
        if (config.bollinger.enabled) {
            val bands = Indicators.bollinger(closes, config.bollinger.period, config.bollinger.deviation)
            val upper = bands.upper[last]
            val lower = bands.lower[last]
            val ready = !upper.isNaN() && !lower.isNaN()
            val longOk = ready && if (config.bollinger.reversion) price <= lower else price >= upper
            val shortOk = ready && if (config.bollinger.reversion) price >= upper else price <= lower
            checks.add(
                RuleCheck(
                    name = "Bollinger(${config.bollinger.period}, ${fmt(config.bollinger.deviation)})",
                    detail = if (ready) {
                        "alt ${fmt(lower)} · üst ${fmt(upper)} · " +
                            if (config.bollinger.reversion) "dönüş modu" else "kırılım modu"
                    } else {
                        "hesaplanamadı"
                    },
                    longOk = longOk,
                    shortOk = shortOk,
                    filter = false,
                )
            )
        }
        if (config.volume.enabled) {
            val average = Indicators.sma(volumes, config.volume.period)[last]
            val current = volumes[last]
            val ready = !average.isNaN() && average > 0.0
            val pass = ready && current >= average * config.volume.multiplier
            checks.add(
                RuleCheck(
                    name = "Hacim filtresi",
                    detail = if (ready) {
                        "${fmt(current)} · ortalama ${fmt(average)} × ${fmt(config.volume.multiplier)}"
                    } else {
                        "hesaplanamadı"
                    },
                    longOk = pass,
                    shortOk = pass,
                    filter = true,
                )
            )
        }
        if (config.atr.enabled) {
            val value = Indicators.atr(closed, config.atr.period)[last]
            val ready = !value.isNaN() && price > 0.0
            val percent = if (ready) value / price * 100.0 else Double.NaN
            val pass = ready && percent >= config.atr.minPercent
            checks.add(
                RuleCheck(
                    name = "ATR(${config.atr.period}) filtresi",
                    detail = if (ready) {
                        "%${fmt(percent)} · en az %${fmt(config.atr.minPercent)}"
                    } else {
                        "hesaplanamadı"
                    },
                    longOk = pass,
                    shortOk = pass,
                    filter = true,
                )
            )
        }

        val directional = checks.filter { !it.filter }
        val filtersOk = checks.filter { it.filter }.all { it.longOk }
        val direction = when {
            directional.isEmpty() -> SignalDirection.NONE
            !filtersOk -> SignalDirection.NONE
            directional.all { it.longOk } -> SignalDirection.LONG
            directional.all { it.shortOk } -> SignalDirection.SHORT
            else -> SignalDirection.NONE
        }
        return StrategySignal(
            direction = direction,
            checks = checks,
            candleTime = closed[last].openTime,
            price = price,
            error = if (directional.isEmpty()) "Yön belirten hiçbir kural açık değil" else null,
        )
    }

    /** Kuralların ihtiyaç duyduğu en uzun periyot + emniyet payı. */
    fun requiredCandles(config: StrategyConfig): Int {
        var longest = 20
        if (config.rsi.enabled) longest = maxOf(longest, config.rsi.period + 1)
        if (config.ema.enabled) longest = maxOf(longest, config.ema.slow)
        if (config.sma.enabled) longest = maxOf(longest, config.sma.period)
        if (config.macd.enabled) longest = maxOf(longest, config.macd.slow + config.macd.signal)
        if (config.bollinger.enabled) longest = maxOf(longest, config.bollinger.period)
        if (config.volume.enabled) longest = maxOf(longest, config.volume.period)
        if (config.atr.enabled) longest = maxOf(longest, config.atr.period + 1)
        return longest + 5
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}
