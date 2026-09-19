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
        prefs.edit().putString(KEY_MODE, TradingMode.TESTNET.name).commit()
        _mode.value = TradingMode.TESTNET
    }

    /**
     * Modu değiştirir. Çağıran taraf ayrıca API anahtarlarını temizler ve
     * kullanıcıdan uygulamayı yeniden başlatmasını ister: borsa adresi
     * uygulama açılışında sabitlenir.
     */
    fun setMode(mode: TradingMode): Boolean {
        if (mode == TradingMode.REAL && !BuildConfig.REAL_TRADING_AVAILABLE) return false
        prefs.edit().putString(KEY_MODE, mode.name).commit()
        _mode.value = mode
        return true
    }

    /** Uygulamanın bu oturumda hangi modla başladığı (borsa adresi buna göre seçildi). */
    val sessionMode: TradingMode = _mode.value

    /** Kayıtlı mod ile oturum modu farklıysa yeniden başlatma gerekir. */
    fun restartRequired(): Boolean = _mode.value != sessionMode

    private companion object {
        const val KEY_MODE = "mode"
    }
}
