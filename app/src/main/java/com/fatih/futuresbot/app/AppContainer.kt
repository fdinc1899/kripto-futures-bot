package com.fatih.futuresbot.app

import android.content.Context
import com.fatih.futuresbot.data.binance.BinanceAccountRepository
import com.fatih.futuresbot.data.binance.BinanceMarketRepository
import com.fatih.futuresbot.data.history.TradeHistoryStore
import com.fatih.futuresbot.data.settings.BotSettingsStore
import com.fatih.futuresbot.data.settings.NotificationSettingsStore
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.data.settings.StrategyStore
import com.fatih.futuresbot.data.settings.TradingModeStore
import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.domain.repository.MarketRepository
import com.fatih.futuresbot.network.HttpClientFactory
import com.fatih.futuresbot.network.binance.BinanceFuturesClient
import com.fatih.futuresbot.network.binance.BinanceMarketStream
import com.fatih.futuresbot.security.CredentialStore
import com.fatih.futuresbot.trading.ConnectionTester
import com.fatih.futuresbot.trading.BotEngine
import com.fatih.futuresbot.trading.ExchangeClient
import com.fatih.futuresbot.notifications.Notifier
import com.fatih.futuresbot.trading.HistorySync
import com.fatih.futuresbot.trading.MarketScanner
import com.fatih.futuresbot.trading.TradeMonitor
import com.fatih.futuresbot.trading.OrderManager
import com.fatih.futuresbot.trading.ProtectionPlanStore
import com.fatih.futuresbot.trading.TradingGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Basit elle DI. Trading motoru ile UI bu kap üzerinden ayrık tutulur. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val credentialStore = CredentialStore(appContext)
    val tradingModeStore = TradingModeStore(appContext)
    val selectedSymbolStore = SelectedSymbolStore(appContext)
    val riskSettingsStore = RiskSettingsStore(appContext)
    val strategyStore = StrategyStore(appContext)
    val botSettingsStore = BotSettingsStore(appContext)
    val tradeHistoryStore = TradeHistoryStore(appContext, appScope)
    val notificationSettingsStore = NotificationSettingsStore(appContext)
    val notifier = Notifier(appContext, notificationSettingsStore)

    private val httpClient = HttpClientFactory.create()

    /**
     * GÜVENLİK: Borsa adresi uygulama açılışında seçilir ve oturum boyunca değişmez.
     * Varsayılan TESTNET; gerçek moda geçiş Settings'te iki adımlı onay ister ve
     * uygulamanın yeniden başlatılmasını gerektirir.
     */
    val environment: ExchangeEnvironment =
        if (tradingModeStore.sessionMode == TradingMode.REAL) {
            ExchangeEnvironment.REAL
        } else {
            ExchangeEnvironment.TESTNET
        }

    val exchangeClient: ExchangeClient = BinanceFuturesClient(
        environment = environment,
        http = httpClient,
        credentialsProvider = { credentialStore.load() },
    )

    private val marketStream = BinanceMarketStream(httpClient, environment)

    val marketRepository: MarketRepository = BinanceMarketRepository(exchangeClient, marketStream)

    val accountRepository: AccountRepository = BinanceAccountRepository(
        client = exchangeClient,
        market = marketRepository,
        credentialStore = credentialStore,
        scope = appScope,
    )

    val connectionTester = ConnectionTester(exchangeClient)

    val tradingGuard = TradingGuard(appContext)

    val orderManager = OrderManager(
        client = exchangeClient,
        accountRepository = accountRepository,
        guard = tradingGuard,
        planStore = ProtectionPlanStore(appContext),
        riskStore = riskSettingsStore,
        historyStore = tradeHistoryStore,
        modeStore = tradingModeStore,
        notifier = notifier,
    ).also { it.startProtectionWatcher(appScope, credentialStore.hasCredentials) }

    val historySync = HistorySync(exchangeClient, tradeHistoryStore)

    private val tradeMonitor = TradeMonitor(
        accountRepository = accountRepository,
        historySync = historySync,
        historyStore = tradeHistoryStore,
        notifier = notifier,
        scope = appScope,
    ).also { it.start() }

    val marketScanner = MarketScanner(exchangeClient)

    val botEngine = BotEngine(
        botSettingsStore = botSettingsStore,
        strategyStore = strategyStore,
        riskStore = riskSettingsStore,
        symbolStore = selectedSymbolStore,
        scanner = marketScanner,
        accountRepository = accountRepository,
        orderManager = orderManager,
        guard = tradingGuard,
        client = exchangeClient,
        notifier = notifier,
        scope = appScope,
    ).also { it.start() }
}
