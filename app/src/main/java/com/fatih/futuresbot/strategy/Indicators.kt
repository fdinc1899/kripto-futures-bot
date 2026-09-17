package com.fatih.futuresbot.strategy

import com.fatih.futuresbot.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Standart indikatör hesaplamaları. Tüm seriler giriş uzunluğundadır;
 * hesaplanamayan ilk değerler NaN döner.
 */
object Indicators {

    fun sma(values: List<Double>, period: Int): List<Double> {
        if (period <= 0) return List(values.size) { Double.NaN }
        val out = MutableList(values.size) { Double.NaN }
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    fun ema(values: List<Double>, period: Int): List<Double> {
        if (period <= 0 || values.size < period) return List(values.size) { Double.NaN }
        val out = MutableList(values.size) { Double.NaN }
        val k = 2.0 / (period + 1)
        var seed = 0.0
        for (i in 0 until period) seed += values[i]
        var prev = seed / period
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = (values[i] - prev) * k + prev
            out[i] = prev
        }
        return out
    }

    /** Wilder RSI. */
    fun rsi(values: List<Double>, period: Int): List<Double> {
        val out = MutableList(values.size) { Double.NaN }
        if (period <= 0 || values.size <= period) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val change = values[i] - values[i - 1]
            if (change >= 0) gain += change else loss -= change
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        out[period] = rsiValue(avgGain, avgLoss)
        for (i in period + 1 until values.size) {
            val change = values[i] - values[i - 1]
            val up = if (change > 0) change else 0.0
            val down = if (change < 0) -change else 0.0
            avgGain = (avgGain * (period - 1) + up) / period
            avgLoss = (avgLoss * (period - 1) + down) / period
            out[i] = rsiValue(avgGain, avgLoss)
        }
        return out
    }

    private fun rsiValue(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return if (avgGain == 0.0) 50.0 else 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1 + rs)
    }

    data class Macd(val line: List<Double>, val signal: List<Double>, val histogram: List<Double>)

    fun macd(values: List<Double>, fast: Int, slow: Int, signalPeriod: Int): Macd {
        val fastEma = ema(values, fast)
        val slowEma = ema(values, slow)
        val line = values.indices.map { i ->
            val f = fastEma[i]
            val s = slowEma[i]
            if (f.isNaN() || s.isNaN()) Double.NaN else f - s
        }
        val valid = line.filter { !it.isNaN() }
        val signalValid = ema(valid, signalPeriod)
        val offset = line.size - valid.size
        val signal = MutableList(values.size) { Double.NaN }
        for (i in signalValid.indices) signal[i + offset] = signalValid[i]
        val histogram = values.indices.map { i ->
            val l = line[i]
            val s = signal[i]
            if (l.isNaN() || s.isNaN()) Double.NaN else l - s
        }
        return Macd(line, signal, histogram)
    }

    data class Bands(val middle: List<Double>, val upper: List<Double>, val lower: List<Double>)

    fun bollinger(values: List<Double>, period: Int, deviation: Double): Bands {
        val middle = sma(values, period)
        val upper = MutableList(values.size) { Double.NaN }
        val lower = MutableList(values.size) { Double.NaN }
        if (period > 0) {
            for (i in values.indices) {
                if (i < period - 1) continue
                val mean = middle[i]
                if (mean.isNaN()) continue
                var variance = 0.0
                for (j in i - period + 1..i) {
                    val diff = values[j] - mean
                    variance += diff * diff
                }
                val sd = sqrt(variance / period)
                upper[i] = mean + deviation * sd
                lower[i] = mean - deviation * sd
            }
        }
        return Bands(middle, upper, lower)
    }

    /** Wilder ATR. */
    fun atr(candles: List<Candle>, period: Int): List<Double> {
        val out = MutableList(candles.size) { Double.NaN }
        if (period <= 0 || candles.size <= period) return out
        val trueRanges = MutableList(candles.size) { 0.0 }
        for (i in candles.indices) {
            val c = candles[i]
            trueRanges[i] = if (i == 0) {
                c.high - c.low
            } else {
                val prevClose = candles[i - 1].close
                max(c.high - c.low, max(abs(c.high - prevClose), abs(c.low - prevClose)))
            }
        }
        var sum = 0.0
        for (i in 1..period) sum += trueRanges[i]
        var prev = sum / period
        out[period] = prev
        for (i in period + 1 until candles.size) {
            prev = (prev * (period - 1) + trueRanges[i]) / period
            out[i] = prev
        }
        return out
    }
}
