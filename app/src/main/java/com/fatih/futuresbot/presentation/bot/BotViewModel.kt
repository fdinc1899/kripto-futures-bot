package com.fatih.futuresbot.presentation.bot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.data.settings.StrategyStore
import com.fatih.futuresbot.domain.model.BotStatus
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.StrategyConfig
import com.fatih.futuresbot.domain.model.StrategySignal
import com.fatih.futuresbot.domain.repository.MarketRepository
import com.fatih.futuresbot.strategy.StrategyEngine
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
)

private data class SignalState(
    val signal: StrategySignal,
    val loading: Boolean,
    val candleCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BotViewModel(
    private val strategyStore: StrategyStore,
    marketRepository: MarketRepository,
    symbolStore: SelectedSymbolStore,
) : ViewModel() {

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
    ) { symbol, config, signalState, savedMessage ->
        BotUiState(
            symbol = symbol,
            config = config,
            signal = signalState.signal,
            loading = signalState.loading,
            candleCount = signalState.candleCount,
            status = BotStatus.STOPPED,
            saved = savedMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BotUiState())

    fun updateConfig(block: (StrategyConfig) -> StrategyConfig) {
        strategyStore.update(block(strategyStore.config.value))
        saved.value = "Strateji kaydedildi"
    }

    fun setInterval(interval: ChartInterval) = updateConfig { it.copy(interval = interval) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                BotViewModel(
                    strategyStore = container.strategyStore,
                    marketRepository = container.marketRepository,
                    symbolStore = container.selectedSymbolStore,
                )
            }
        }
    }
}
