package com.fatih.futuresbot.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dashboard, Markets ve Chart ekranlarının ortak seçili paritesi. */
class SelectedSymbolStore(context: Context) {

    private val prefs = context.getSharedPreferences("market_prefs", Context.MODE_PRIVATE)
    private val _symbol = MutableStateFlow(load())
    val symbol: StateFlow<String> = _symbol.asStateFlow()

    private fun load(): String {
        val saved = prefs.getString(KEY_SYMBOL, null)
        return if (saved != null && VALID.matches(saved)) saved else DEFAULT
    }

    fun select(symbol: String) {
        val s = symbol.trim().uppercase()
        if (!VALID.matches(s)) return
        prefs.edit().putString(KEY_SYMBOL, s).apply()
        _symbol.value = s
    }

    companion object {
        const val DEFAULT = "BTCUSDT"
        private const val KEY_SYMBOL = "selected_symbol"
        private val VALID = Regex("^[A-Z0-9]{2,30}$")
    }
}
