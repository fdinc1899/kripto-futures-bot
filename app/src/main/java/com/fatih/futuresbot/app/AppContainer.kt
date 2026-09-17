package com.fatih.futuresbot.app

import android.content.Context
import com.fatih.futuresbot.data.binance.BinanceAccountRepository
import com.fatih.futuresbot.data.settings.TradingModeStore
import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.network.HttpClientFactory
import com.fatih.futuresbot.network.binance.BinanceFuturesClient
import com.fatih.futuresbot.security.CredentialStore
import com.fatih.futuresbot.trading.ConnectionTester
import com.fatih.futuresbot.trading.ExchangeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Basit elle DI. Trading motoru ile UI bu kap üzerinden ayrık tutulur. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val credentialStore = CredentialStore(appContext)
    val tradingModeStore = TradingModeStore(appContext)

    private val httpClient = HttpClientFactory.create()

    // GÜVENLİK: Ortam sabit TESTNET (Binance Demo). Gerçek ortam Aşama 15'te eklenecek.
    val exchangeClient: ExchangeClient = BinanceFuturesClient(
        environment = ExchangeEnvironment.TESTNET,
        http = httpClient,
        credentialsProvider = { credentialStore.load() },
    )

    val accountRepository: AccountRepository =
        BinanceAccountRepository(exchangeClient, credentialStore, appScope)

    val connectionTester = ConnectionTester(exchangeClient)
}
