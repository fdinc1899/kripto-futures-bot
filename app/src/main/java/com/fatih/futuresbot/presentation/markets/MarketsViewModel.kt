package com.fatih.futuresbot.presentation.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.Ticker24h
import com.fatih.futuresbot.domain.repository.MarketRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

data class MarketsUiState(
    val query: String = "",
    val items: List<Ticker24h> = emptyList(),
    val selected: String = SelectedSymbolStore.DEFAULT,
    val loading: Boolean = true,
    val error: String? = null,
)

private data class TickerListState(
    val items: List<Ticker24h>,
    val error: String?,
    val loading: Boolean,
)

class MarketsViewModel(
    private val market: MarketRepository,
    private val symbolStore: SelectedSymbolStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Yalnızca ekran açıkken 15 sn'de bir yenilenir
    private val tickerList: StateFlow<TickerListState> = flow {
        var last = emptyList<Ticker24h>()
        while (currentCoroutineContext().isActive) {
            when (val r = market.allUsdtTickers()) {
                is ExchangeResult.Ok -> {
                    last = r.value
                    emit(TickerListState(last, null, false))
                }
                is ExchangeResult.Err -> emit(TickerListState(last, r.error.userMessage, false))
            }
            delay(REFRESH_MS)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TickerListState(emptyList(), null, true),
    )

    val state: StateFlow<MarketsUiState> = combine(
        query,
        tickerList,
        symbolStore.symbol,
    ) { q, list, selected ->
        val needle = q.trim().uppercase()
        val filtered = if (needle.isEmpty()) {
            list.items
        } else {
            list.items.filter { it.symbol.contains(needle) }
        }
        MarketsUiState(
            query = q,
            items = filtered.take(MAX_ROWS),
            selected = selected,
            loading = list.loading,
            error = list.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketsUiState())

    fun onQueryChange(value: String) {
        query.value = value.take(20)
    }

    fun select(symbol: String) {
        symbolStore.select(symbol)
    }

    companion object {
        private const val REFRESH_MS = 15_000L
        private const val MAX_ROWS = 200

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                MarketsViewModel(container.marketRepository, container.selectedSymbolStore)
            }
        }
    }
}
