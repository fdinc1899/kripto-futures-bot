package com.fatih.futuresbot

import android.app.Application
import com.fatih.futuresbot.app.AppContainer

class FuturesBotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
