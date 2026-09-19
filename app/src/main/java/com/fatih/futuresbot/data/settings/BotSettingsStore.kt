package com.fatih.futuresbot.data.settings

import android.content.Context
import com.fatih.futuresbot.domain.model.BotSettings
import com.fatih.futuresbot.domain.model.ScanMode
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
            scanMode = prefs.getString(KEY_SCAN_MODE, d.scanMode) ?: d.scanMode,
            poolSize = prefs.getInt(KEY_POOL, d.poolSize),
            scanCount = prefs.getInt(KEY_SCAN_COUNT, d.scanCount),
            minQuoteVolume = prefs.getFloat(KEY_MIN_VOLUME, d.minQuoteVolume.toFloat()).toDouble(),
            watchlist = prefs.getString(KEY_WATCHLIST, d.watchlist) ?: d.watchlist,
            allowShort = prefs.getBoolean(KEY_ALLOW_SHORT, d.allowShort),
        )
    }

    fun setEnabled(enabled: Boolean) = update(_settings.value.copy(enabled = enabled))

    fun setAutoTrade(autoTrade: Boolean) = update(_settings.value.copy(autoTrade = autoTrade))

    fun update(value: BotSettings) {
        val mode = ScanMode.entries.firstOrNull { it.name == value.scanMode } ?: ScanMode.HYBRID
        val safe = value.copy(
            leverage = value.leverage.coerceIn(1, 20),
            maxTradesPerDay = value.maxTradesPerDay.coerceIn(1, 100),
            scanMode = mode.name,
            poolSize = value.poolSize.coerceIn(5, 60),
            scanCount = value.scanCount.coerceIn(1, 30),
            minQuoteVolume = value.minQuoteVolume.coerceAtLeast(0.0),
            watchlist = value.watchlist.uppercase().filter { it.isLetterOrDigit() || it == ',' }.take(200),
            allowShort = value.allowShort,
        )
        prefs.edit()
            .putBoolean(KEY_ENABLED, safe.enabled)
            .putBoolean(KEY_AUTO, safe.autoTrade)
            .putInt(KEY_LEVERAGE, safe.leverage)
            .putInt(KEY_MAX_TRADES, safe.maxTradesPerDay)
            .putString(KEY_SCAN_MODE, safe.scanMode)
            .putInt(KEY_POOL, safe.poolSize)
            .putInt(KEY_SCAN_COUNT, safe.scanCount)
            .putFloat(KEY_MIN_VOLUME, safe.minQuoteVolume.toFloat())
            .putString(KEY_WATCHLIST, safe.watchlist)
            .putBoolean(KEY_ALLOW_SHORT, safe.allowShort)
            .commit()
        _settings.value = safe
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_AUTO = "auto_trade"
        const val KEY_LEVERAGE = "leverage"
        const val KEY_MAX_TRADES = "max_trades"
        const val KEY_SCAN_MODE = "scan_mode"
        const val KEY_POOL = "pool_size"
        const val KEY_SCAN_COUNT = "scan_count"
        const val KEY_MIN_VOLUME = "min_volume"
        const val KEY_WATCHLIST = "watchlist"
        const val KEY_ALLOW_SHORT = "allow_short"
    }
}
