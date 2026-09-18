package com.fatih.futuresbot.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).commit()
        _enabled.value = value
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
