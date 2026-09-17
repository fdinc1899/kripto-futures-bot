package com.fatih.futuresbot.data.settings

import android.content.Context
import com.fatih.futuresbot.BuildConfig
import com.fatih.futuresbot.domain.model.TradingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Varsayılan mod TESTNET. Gerçek mod derleme bayrağı kapalıyken asla açılamaz. */
class TradingModeStore(context: Context) {

    private val prefs = context.getSharedPreferences("trading_mode", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(load())
    val mode: StateFlow<TradingMode> = _mode.asStateFlow()

    private fun load(): TradingMode {
        val saved = prefs.getString(KEY_MODE, null)
        val parsed = TradingMode.entries.firstOrNull { it.name == saved } ?: TradingMode.TESTNET
        return if (parsed == TradingMode.REAL && !BuildConfig.REAL_TRADING_AVAILABLE) {
            TradingMode.TESTNET
        } else {
            parsed
        }
    }

    fun setTestnet() {
        prefs.edit().putString(KEY_MODE, TradingMode.TESTNET.name).apply()
        _mode.value = TradingMode.TESTNET
    }

    /** Aşama 15'te iki adımlı onay akışıyla uygulanacak. Şimdilik her zaman reddeder. */
    fun requestRealMode(): Boolean = false

    private companion object {
        const val KEY_MODE = "mode"
    }
}
