package com.fatih.futuresbot.presentation.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.domain.model.CandleSeries
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.repository.MarketRepository
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

data class ChartUiState(
    val symbol: String = SelectedSymbolStore.DEFAULT,
    val interval: ChartInterval = ChartInterval.M15,
    val candles: List<Candle> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val visibleCount: Int = 60,
    val stream: ConnectionState = ConnectionState.DISCONNECTED,
)

private data class SeriesState(
    val symbol: String,
    val interval: ChartInterval,
    val series: CandleSeries?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModel(
    market: MarketRepository,
    symbolStore: SelectedSymbolStore,
) : ViewModel() {

    private val interval = MutableStateFlow(ChartInterval.M15)
    private val visibleCount = MutableStateFlow(DEFAULT_VISIBLE)

    private val series: Flow<SeriesState> = combine(symbolStore.symbol, interval) { s, i -> s to i }
        .flatMapLatest { (s, i) ->
            market.candles(s, i)
                .map { SeriesState(s, i, it) }
                .onStart { emit(SeriesState(s, i, null)) }
        }

    val state: StateFlow<ChartUiState> = combine(
        series,
        visibleCount,
        market.streamState,
    ) { data, visible, stream ->
        ChartUiState(
            symbol = data.symbol,
            interval = data.interval,
            candles = data.series?.candles.orEmpty(),
            loading = data.series == null,
            error = data.series?.error,
            visibleCount = visible,
            stream = stream,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartUiState())

    fun setInterval(value: ChartInterval) {
        interval.value = value
    }

    fun zoomIn() {
        visibleCount.value = (visibleCount.value - ZOOM_STEP).coerceAtLeast(MIN_VISIBLE)
    }

    fun zoomOut() {
        visibleCount.value = (visibleCount.value + ZOOM_STEP).coerceAtMost(MAX_VISIBLE)
    }

    companion object {
        private const val DEFAULT_VISIBLE = 60
        private const val MIN_VISIBLE = 20
        private const val MAX_VISIBLE = 200
        private const val ZOOM_STEP = 20

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ChartViewModel(container.marketRepository, container.selectedSymbolStore)
            }
        }
    }
}
