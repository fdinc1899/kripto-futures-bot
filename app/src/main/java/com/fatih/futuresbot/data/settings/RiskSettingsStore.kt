package com.fatih.futuresbot.data.settings

import android.content.Context
import com.fatih.futuresbot.domain.model.RiskSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RiskSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("risk_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<RiskSettings> = _settings.asStateFlow()

    private fun load(): RiskSettings {
        val d = RiskSettings()
        return RiskSettings(
            riskPerTradePercent = prefs.getFloat(KEY_RISK, d.riskPerTradePercent.toFloat()).toDouble(),
            maxDailyLossPercent = prefs.getFloat(KEY_DAILY, d.maxDailyLossPercent.toFloat()).toDouble(),
            maxOpenPositions = prefs.getInt(KEY_POSITIONS, d.maxOpenPositions),
            maxLeverage = prefs.getInt(KEY_LEVERAGE, d.maxLeverage),
            minRiskReward = prefs.getFloat(KEY_RR, d.minRiskReward.toFloat()).toDouble(),
            defaultStopLossPercent = prefs.getFloat(KEY_SL, d.defaultStopLossPercent.toFloat()).toDouble(),
            defaultTakeProfitPercent = prefs.getFloat(KEY_TP, d.defaultTakeProfitPercent.toFloat()).toDouble(),
            trailingStopEnabled = prefs.getBoolean(KEY_TRAILING, d.trailingStopEnabled),
            trailingCallbackPercent = prefs.getFloat(KEY_CALLBACK, d.trailingCallbackPercent.toFloat()).toDouble(),
        )
    }

    /** Değerleri güvenli aralıklara sıkıştırarak kaydeder. */
    fun update(value: RiskSettings) {
        val safe = RiskSettings(
            riskPerTradePercent = value.riskPerTradePercent.coerceIn(0.1, 10.0),
            maxDailyLossPercent = value.maxDailyLossPercent.coerceIn(0.5, 50.0),
            maxOpenPositions = value.maxOpenPositions.coerceIn(1, 10),
            maxLeverage = value.maxLeverage.coerceIn(1, 20),
            minRiskReward = value.minRiskReward.coerceIn(0.0, 10.0),
            defaultStopLossPercent = value.defaultStopLossPercent.coerceIn(0.1, 49.0),
            defaultTakeProfitPercent = value.defaultTakeProfitPercent.coerceIn(0.1, 200.0),
            trailingStopEnabled = value.trailingStopEnabled,
            trailingCallbackPercent = value.trailingCallbackPercent.coerceIn(0.1, 10.0),
        )
        prefs.edit()
            .putFloat(KEY_RISK, safe.riskPerTradePercent.toFloat())
            .putFloat(KEY_DAILY, safe.maxDailyLossPercent.toFloat())
            .putInt(KEY_POSITIONS, safe.maxOpenPositions)
            .putInt(KEY_LEVERAGE, safe.maxLeverage)
            .putFloat(KEY_RR, safe.minRiskReward.toFloat())
            .putFloat(KEY_SL, safe.defaultStopLossPercent.toFloat())
            .putFloat(KEY_TP, safe.defaultTakeProfitPercent.toFloat())
            .putBoolean(KEY_TRAILING, safe.trailingStopEnabled)
            .putFloat(KEY_CALLBACK, safe.trailingCallbackPercent.toFloat())
            .commit()
        _settings.value = safe
    }

    private companion object {
        const val KEY_RISK = "risk_per_trade"
        const val KEY_DAILY = "max_daily_loss"
        const val KEY_POSITIONS = "max_positions"
        const val KEY_LEVERAGE = "max_leverage"
        const val KEY_RR = "min_rr"
        const val KEY_SL = "default_sl"
        const val KEY_TP = "default_tp"
        const val KEY_TRAILING = "trailing_enabled"
        const val KEY_CALLBACK = "trailing_callback"
    }
}
