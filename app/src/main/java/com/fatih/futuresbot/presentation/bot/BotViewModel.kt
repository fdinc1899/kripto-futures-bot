package com.fatih.futuresbot.presentation.bot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.BotSettingsStore
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.data.settings.StrategyStore
import com.fatih.futuresbot.domain.model.BotLogEntry
import com.fatih.futuresbot.domain.model.BotSettings
import com.fatih.futuresbot.domain.model.BotStatus
import com.fatih.futuresbot.domain.model.RiskSettings
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.StrategyConfig
import com.fatih.futuresbot.domain.model.StrategySignal
import com.fatih.futuresbot.domain.repository.MarketRepository
import com.fatih.futuresbot.strategy.StrategyEngine
import com.fatih.futuresbot.trading.BotEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class BotUiState(
    val symbol: String = SelectedSymbolStore.DEFAULT,
    val config: StrategyConfig = StrategyConfig(),
    val signal: StrategySignal = StrategySignal(),
    val loading: Boolean = true,
    val candleCount: Int = 0,
    val status: BotStatus = BotStatus.STOPPED,
    val saved: String? = null,
    val bot: BotSettings = BotSettings(),
    val risk: RiskSettings = RiskSettings(),
    val logs: List<BotLogEntry> = emptyList(),
)

private data class BotRuntime(
    val settings: BotSettings,
    val status: BotStatus,
    val logs: List<BotLogEntry>,
    val risk: RiskSettings,
)

private data class SignalState(
    val signal: StrategySignal,
    val loading: Boolean,
    val candleCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BotViewModel(
    private val strategyStore: StrategyStore,
    private val botSettingsStore: BotSettingsStore,
    private val botEngine: BotEngine,
    riskStore: RiskSettingsStore,
    marketRepository: MarketRepository,
    symbolStore: SelectedSymbolStore,
) : ViewModel() {

    private val runtime = combine(
        botSettingsStore.settings,
        botEngine.status,
        botEngine.logs,
        riskStore.settings,
    ) { settings, status, logs, risk -> BotRuntime(settings, status, logs, risk) }

    private val saved = MutableStateFlow<String?>(null)

    private val signalFlow: Flow<SignalState> =
        combine(symbolStore.symbol, strategyStore.config) { symbol, config -> symbol to config }
            .flatMapLatest { (symbol, config) ->
                marketRepository.candles(symbol, config.interval)
                    .map { series ->
                        SignalState(
                            signal = if (series.error != null && series.candles.isEmpty()) {
                                StrategySignal(error = series.error)
                            } else {
                                StrategyEngine.evaluate(series.candles, config)
                            },
                            loading = false,
                            candleCount = series.candles.size,
                        )
                    }
                    .onStart { emit(SignalState(StrategySignal(), true, 0)) }
            }

    val state: StateFlow<BotUiState> = combine(
        symbolStore.symbol,
        strategyStore.config,
        signalFlow,
        saved,
        runtime,
    ) { symbol, config, signalState, savedMessage, run ->
        BotUiState(
            symbol = symbol,
            config = config,
            signal = signalState.signal,
            loading = signalState.loading,
            candleCount = signalState.candleCount,
            status = run.status,
            saved = savedMessage,
            bot = run.settings,
            risk = run.risk,
            logs = run.logs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BotUiState())

    fun updateConfig(block: (StrategyConfig) -> StrategyConfig) {
        strategyStore.update(block(strategyStore.config.value))
        saved.value = "Strateji kaydedildi"
    }

    fun setInterval(interval: ChartInterval) = updateConfig { it.copy(interval = interval) }

    fun setBotEnabled(enabled: Boolean) {
        botSettingsStore.setEnabled(enabled)
        saved.value = if (enabled) "Bot açıldı" else "Bot kapatıldı"
    }

    fun setAutoTrade(enabled: Boolean) {
        botSettingsStore.setAutoTrade(enabled)
        saved.value = if (enabled) "Otomatik işlem açıldı (TESTNET)" else "Yalnızca sinyal moduna geçildi"
    }

    fun updateBot(block: (BotSettings) -> BotSettings) {
        botSettingsStore.update(block(botSettingsStore.settings.value))
        saved.value = "Bot ayarları kaydedildi"
    }

    fun clearLogs() = botEngine.clearLogs()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                BotViewModel(
                    strategyStore = container.strategyStore,
                    botSettingsStore = container.botSettingsStore,
                    botEngine = container.botEngine,
                    riskStore = container.riskSettingsStore,
                    marketRepository = container.marketRepository,
                    symbolStore = container.selectedSymbolStore,
                )
            }
        }
    }
}
