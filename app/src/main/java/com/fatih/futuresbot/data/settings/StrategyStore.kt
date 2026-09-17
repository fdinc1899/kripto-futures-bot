package com.fatih.futuresbot.data.settings

import android.content.Context
import com.fatih.futuresbot.domain.model.AtrRule
import com.fatih.futuresbot.domain.model.BollingerRule
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.EmaRule
import com.fatih.futuresbot.domain.model.MacdRule
import com.fatih.futuresbot.domain.model.RsiRule
import com.fatih.futuresbot.domain.model.SmaRule
import com.fatih.futuresbot.domain.model.StrategyConfig
import com.fatih.futuresbot.domain.model.VolumeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StrategyStore(context: Context) {

    private val prefs = context.getSharedPreferences("strategy", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<StrategyConfig> = _config.asStateFlow()

    private fun load(): StrategyConfig {
        val d = StrategyConfig()
        val intervalCode = prefs.getString(KEY_INTERVAL, d.interval.code)
        return StrategyConfig(
            interval = ChartInterval.entries.firstOrNull { it.code == intervalCode } ?: d.interval,
            rsi = RsiRule(
                enabled = prefs.getBoolean("rsi_on", d.rsi.enabled),
                period = prefs.getInt("rsi_period", d.rsi.period),
                longBelow = prefs.getFloat("rsi_long", d.rsi.longBelow.toFloat()).toDouble(),
                shortAbove = prefs.getFloat("rsi_short", d.rsi.shortAbove.toFloat()).toDouble(),
            ),
            ema = EmaRule(
                enabled = prefs.getBoolean("ema_on", d.ema.enabled),
                fast = prefs.getInt("ema_fast", d.ema.fast),
                slow = prefs.getInt("ema_slow", d.ema.slow),
            ),
            sma = SmaRule(
                enabled = prefs.getBoolean("sma_on", d.sma.enabled),
                period = prefs.getInt("sma_period", d.sma.period),
            ),
            macd = MacdRule(
                enabled = prefs.getBoolean("macd_on", d.macd.enabled),
                fast = prefs.getInt("macd_fast", d.macd.fast),
                slow = prefs.getInt("macd_slow", d.macd.slow),
                signal = prefs.getInt("macd_signal", d.macd.signal),
            ),
            bollinger = BollingerRule(
                enabled = prefs.getBoolean("bb_on", d.bollinger.enabled),
                period = prefs.getInt("bb_period", d.bollinger.period),
                deviation = prefs.getFloat("bb_dev", d.bollinger.deviation.toFloat()).toDouble(),
                reversion = prefs.getBoolean("bb_reversion", d.bollinger.reversion),
            ),
            volume = VolumeRule(
                enabled = prefs.getBoolean("vol_on", d.volume.enabled),
                period = prefs.getInt("vol_period", d.volume.period),
                multiplier = prefs.getFloat("vol_mult", d.volume.multiplier.toFloat()).toDouble(),
            ),
            atr = AtrRule(
                enabled = prefs.getBoolean("atr_on", d.atr.enabled),
                period = prefs.getInt("atr_period", d.atr.period),
                minPercent = prefs.getFloat("atr_min", d.atr.minPercent.toFloat()).toDouble(),
            ),
        )
    }

    /** Periyotları güvenli aralıklara sıkıştırarak kaydeder. */
    fun update(config: StrategyConfig) {
        val safe = StrategyConfig(
            interval = config.interval,
            rsi = config.rsi.copy(
                period = config.rsi.period.coerceIn(2, 200),
                longBelow = config.rsi.longBelow.coerceIn(1.0, 99.0),
                shortAbove = config.rsi.shortAbove.coerceIn(1.0, 99.0),
            ),
            ema = config.ema.copy(
                fast = config.ema.fast.coerceIn(2, 400),
                slow = config.ema.slow.coerceIn(3, 400),
            ),
            sma = config.sma.copy(period = config.sma.period.coerceIn(2, 400)),
            macd = config.macd.copy(
                fast = config.macd.fast.coerceIn(2, 100),
                slow = config.macd.slow.coerceIn(3, 200),
                signal = config.macd.signal.coerceIn(2, 100),
            ),
            bollinger = config.bollinger.copy(
                period = config.bollinger.period.coerceIn(5, 200),
                deviation = config.bollinger.deviation.coerceIn(0.5, 5.0),
            ),
            volume = config.volume.copy(
                period = config.volume.period.coerceIn(2, 200),
                multiplier = config.volume.multiplier.coerceIn(0.1, 10.0),
            ),
            atr = config.atr.copy(
                period = config.atr.period.coerceIn(2, 200),
                minPercent = config.atr.minPercent.coerceIn(0.0, 20.0),
            ),
        )
        prefs.edit()
            .putString(KEY_INTERVAL, safe.interval.code)
            .putBoolean("rsi_on", safe.rsi.enabled)
            .putInt("rsi_period", safe.rsi.period)
            .putFloat("rsi_long", safe.rsi.longBelow.toFloat())
            .putFloat("rsi_short", safe.rsi.shortAbove.toFloat())
            .putBoolean("ema_on", safe.ema.enabled)
            .putInt("ema_fast", safe.ema.fast)
            .putInt("ema_slow", safe.ema.slow)
            .putBoolean("sma_on", safe.sma.enabled)
            .putInt("sma_period", safe.sma.period)
            .putBoolean("macd_on", safe.macd.enabled)
            .putInt("macd_fast", safe.macd.fast)
            .putInt("macd_slow", safe.macd.slow)
            .putInt("macd_signal", safe.macd.signal)
            .putBoolean("bb_on", safe.bollinger.enabled)
            .putInt("bb_period", safe.bollinger.period)
            .putFloat("bb_dev", safe.bollinger.deviation.toFloat())
            .putBoolean("bb_reversion", safe.bollinger.reversion)
            .putBoolean("vol_on", safe.volume.enabled)
            .putInt("vol_period", safe.volume.period)
            .putFloat("vol_mult", safe.volume.multiplier.toFloat())
            .putBoolean("atr_on", safe.atr.enabled)
            .putInt("atr_period", safe.atr.period)
            .putFloat("atr_min", safe.atr.minPercent.toFloat())
            .commit()
        _config.value = safe
    }

    private companion object {
        const val KEY_INTERVAL = "interval"
    }
}
