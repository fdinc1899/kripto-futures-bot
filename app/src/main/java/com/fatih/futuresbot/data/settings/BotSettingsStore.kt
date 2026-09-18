package com.fatih.futuresbot.data.settings

import android.content.Context
import com.fatih.futuresbot.domain.model.BotSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BotSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<BotSettings> = _settings.asStateFlow()

    private fun load(): BotSettings {
        val d = BotSettings()
        return BotSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, d.enabled),
            autoTrade = prefs.getBoolean(KEY_AUTO, d.autoTrade),
            leverage = prefs.getInt(KEY_LEVERAGE, d.leverage),
            maxTradesPerDay = prefs.getInt(KEY_MAX_TRADES, d.maxTradesPerDay),
        )
    }

    fun setEnabled(enabled: Boolean) = update(_settings.value.copy(enabled = enabled))

    fun setAutoTrade(autoTrade: Boolean) = update(_settings.value.copy(autoTrade = autoTrade))

    fun update(value: BotSettings) {
        val safe = value.copy(
            leverage = value.leverage.coerceIn(1, 20),
            maxTradesPerDay = value.maxTradesPerDay.coerceIn(1, 100),
        )
        prefs.edit()
            .putBoolean(KEY_ENABLED, safe.enabled)
            .putBoolean(KEY_AUTO, safe.autoTrade)
            .putInt(KEY_LEVERAGE, safe.leverage)
            .putInt(KEY_MAX_TRADES, safe.maxTradesPerDay)
            .commit()
        _settings.value = safe
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_AUTO = "auto_trade"
        const val KEY_LEVERAGE = "leverage"
        const val KEY_MAX_TRADES = "max_trades"
    }
}
