package com.fatih.futuresbot.trading

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Acil durdurma durumu. Uygulama yeniden açılsa da korunur. */
class TradingGuard(context: Context) {

    private val prefs = context.getSharedPreferences("trading_guard", Context.MODE_PRIVATE)
    private val _emergencyStopped = MutableStateFlow(prefs.getBoolean(KEY_STOPPED, false))
    val emergencyStopped: StateFlow<Boolean> = _emergencyStopped.asStateFlow()

    fun stop() {
        prefs.edit().putBoolean(KEY_STOPPED, true).commit()
        _emergencyStopped.value = true
    }

    fun reset() {
        prefs.edit().putBoolean(KEY_STOPPED, false).commit()
        _emergencyStopped.value = false
    }

    private companion object {
        const val KEY_STOPPED = "emergency_stopped"
    }
}
