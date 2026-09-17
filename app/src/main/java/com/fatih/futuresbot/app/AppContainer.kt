package com.fatih.futuresbot.app

import android.content.Context
import com.fatih.futuresbot.data.demo.DemoAccountRepository
import com.fatih.futuresbot.data.settings.TradingModeStore
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.security.CredentialStore

/** Basit elle DI. Trading motoru ile UI bu kap üzerinden ayrık tutulur. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val credentialStore = CredentialStore(appContext)
    val tradingModeStore = TradingModeStore(appContext)

    // Aşama 6'da testnet repository ile değiştirilecek
    val accountRepository: AccountRepository = DemoAccountRepository()
}
