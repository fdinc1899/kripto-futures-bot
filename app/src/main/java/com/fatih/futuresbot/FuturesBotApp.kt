package com.fatih.futuresbot

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.service.BotService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FuturesBotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        watchBotService()
    }

    /** Bot açıldığında ön plan servisi başlar, kapandığında durur. */
    private fun watchBotService() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            container.botSettingsStore.settings
                .map { it.enabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    val intent = Intent(this@FuturesBotApp, BotService::class.java)
                    if (enabled) {
                        runCatching { ContextCompat.startForegroundService(this@FuturesBotApp, intent) }
                    } else {
                        runCatching { stopService(intent) }
                    }
                }
        }
    }
}
