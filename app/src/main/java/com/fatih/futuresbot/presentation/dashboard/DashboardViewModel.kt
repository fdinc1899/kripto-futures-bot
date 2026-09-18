package com.fatih.futuresbot.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.data.settings.TradingModeStore
import com.fatih.futuresbot.domain.model.AccountSummary
import com.fatih.futuresbot.domain.model.BotStatus
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.RiskSettings
import com.fatih.futuresbot.domain.model.SymbolSnapshot
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.domain.repository.MarketRepository
import com.fatih.futuresbot.trading.BotEngine
import com.fatih.futuresbot.trading.OrderManager
import com.fatih.futuresbot.trading.TradingGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val mode: TradingMode = TradingMode.TESTNET,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val streamConnection: ConnectionState = ConnectionState.DISCONNECTED,
    val errorMessage: String? = null,
    val account: AccountSummary? = null,
    val symbol: SymbolSnapshot? = null,
    val botStatus: BotStatus = BotStatus.STOPPED,
    val emergencyStopped: Boolean = false,
    val risk: RiskSettings = RiskSettings(),
)

private data class ConnectionInfo(
    val rest: ConnectionState,
    val stream: ConnectionState,
    val error: String?,
    val risk: RiskSettings,
    val botStatus: BotStatus,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    accountRepository: AccountRepository,
    marketRepository: MarketRepository,
    modeStore: TradingModeStore,
    symbolStore: SelectedSymbolStore,
    riskStore: RiskSettingsStore,
    botEngine: BotEngine,
    private val guard: TradingGuard,
    private val orderManager: OrderManager,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val connectionInfo = combine(
        accountRepository.connection,
        marketRepository.streamState,
        accountRepository.lastError,
        riskStore.settings,
        botEngine.status,
    ) { rest, stream, error, risk, botStatus ->
        ConnectionInfo(rest, stream, error?.userMessage, risk, botStatus)
    }

    val state: StateFlow<DashboardUiState> = combine(
        modeStore.mode,
        connectionInfo,
        accountRepository.accountSummary(),
        symbolStore.symbol.flatMapLatest { accountRepository.symbolSnapshot(it) },
        guard.emergencyStopped,
    ) { mode, conn, account, symbol, emergencyStopped ->
        DashboardUiState(
            mode = mode,
            connection = conn.rest,
            streamConnection = conn.stream,
            errorMessage = conn.error,
            account = account,
            symbol = symbol,
            botStatus = conn.botStatus,
            emergencyStopped = emergencyStopped,
            risk = conn.risk,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Onay istemez, anında etkili olur: yeni emirler kilitlenir, bekleyen limit girişleri iptal edilir. */
    fun emergencyStop() {
        orderManager.emergencyStop(appScope)
    }

    fun resetEmergency() {
        guard.reset()
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                DashboardViewModel(
                    accountRepository = container.accountRepository,
                    marketRepository = container.marketRepository,
                    modeStore = container.tradingModeStore,
                    symbolStore = container.selectedSymbolStore,
                    riskStore = container.riskSettingsStore,
                    botEngine = container.botEngine,
                    guard = container.tradingGuard,
                    orderManager = container.orderManager,
                    appScope = container.appScope,
                )
            }
        }
    }
}
