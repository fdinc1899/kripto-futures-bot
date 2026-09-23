package com.fatih.futuresbot.presentation.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.SelectedSymbolStore
import com.fatih.futuresbot.domain.model.ChartInterval
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.Ticker24h
import com.fatih.futuresbot.domain.repository.MarketRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class SortMode(val label: String, val subtitle: String) {
    VOLUME("Hacim", "hacme göre sıralı"),
    GAINERS("Yükselen", "en çok yükselenler"),
    LOSERS("Düşen", "en çok düşenler"),
}

/**
 * Değişim penceresi. 24s dışındakiler mum verisinden hesaplanır:
 * [interval] aralığında [bars] mum geriye bakılır.
 */
enum class ChangeWindow(val label: String, val interval: ChartInterval?, val bars: Int) {
    M5("5dk", ChartInterval.M1, 5),
    M15("15dk", ChartInterval.M1, 15),
    H1("1s", ChartInterval.M5, 12),
    H4("4s", ChartInterval.M15, 16),
    D1("24s", null, 0),
}

data class MarketsUiState(
    val query: String = "",
    val items: List<Ticker24h> = emptyList(),
    val selected: String = SelectedSymbolStore.DEFAULT,
    val loading: Boolean = true,
    val error: String? = null,
    val sort: SortMode = SortMode.VOLUME,
    val window: ChangeWindow = ChangeWindow.D1,
    val windowRefreshing: Boolean = false,
    val windowLimit: Int = WINDOW_SYMBOLS,
)

/** Mum verisiyle hesaplanan pencerelerde kaç parite (hacimce ilk N) hesaplansın. */
const val WINDOW_SYMBOLS = 150

private data class TickerListState(
    val items: List<Ticker24h>,
    val error: String?,
    val loading: Boolean,
)

private data class ViewSettings(
    val sort: SortMode = SortMode.VOLUME,
    val window: ChangeWindow = ChangeWindow.D1,
)

private data class WindowChanges(
    val window: ChangeWindow,
    val changes: Map<String, Double>,
    val refreshing: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MarketsViewModel(
    private val market: MarketRepository,
    private val symbolStore: SelectedSymbolStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val settings = MutableStateFlow(ViewSettings())

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

    // 5dk/15dk/1s/4s seçiliyken dakikada bir mum verisinden değişim hesaplanır
    private val windowChanges: StateFlow<WindowChanges> = settings
        .map { it.window }
        .distinctUntilChanged()
        .flatMapLatest { w ->
            if (w.interval == null) {
                flowOf(WindowChanges(w, emptyMap(), false))
            } else {
                flow {
                    var changes = emptyMap<String, Double>()
                    emit(WindowChanges(w, changes, true))
                    while (currentCoroutineContext().isActive) {
                        val symbols = tickerList.value.items.take(WINDOW_SYMBOLS).map { it.symbol }
                        if (symbols.isEmpty()) {
                            delay(1_000)
                            continue
                        }
                        emit(WindowChanges(w, changes, true))
                        changes = computeChanges(symbols, w)
                        emit(WindowChanges(w, changes, false))
                        delay(WINDOW_REFRESH_MS)
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WindowChanges(ChangeWindow.D1, emptyMap(), false),
        )

    val state: StateFlow<MarketsUiState> = combine(
        query,
        tickerList,
        symbolStore.symbol,
        settings,
        windowChanges,
    ) { q, list, selected, s, wc ->
        val windowed = s.window.interval != null
        val changes = if (wc.window == s.window) wc.changes else emptyMap()

        val base = if (!windowed) {
            list.items
        } else {
            list.items.mapNotNull { t -> changes[t.symbol]?.let { t.copy(priceChangePercent = it) } }
        }

        val needle = q.trim().uppercase()
        val filtered = if (needle.isEmpty()) base else base.filter { it.symbol.contains(needle) }

        val sorted = when (s.sort) {
            SortMode.VOLUME -> filtered.sortedByDescending { it.quoteVolume }
            SortMode.GAINERS -> filtered.sortedByDescending { it.priceChangePercent }
            SortMode.LOSERS -> filtered.sortedBy { it.priceChangePercent }
        }

        MarketsUiState(
            query = q,
            items = sorted.take(MAX_ROWS),
            selected = selected,
            loading = list.loading || (windowed && changes.isEmpty() && wc.refreshing),
            error = list.error,
            sort = s.sort,
            window = s.window,
            windowRefreshing = windowed && wc.refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketsUiState())

    fun onQueryChange(value: String) {
        query.value = value.take(20)
    }

    fun onSortChange(mode: SortMode) {
        settings.value = settings.value.copy(sort = mode)
    }

    fun onWindowChange(window: ChangeWindow) {
        settings.value = settings.value.copy(window = window)
    }

    fun select(symbol: String) {
        symbolStore.select(symbol)
    }

    /** Aynı anda en fazla [PARALLEL] istek; hata veren parite atlanır. */
    private suspend fun computeChanges(symbols: List<String>, w: ChangeWindow): Map<String, Double> {
        val interval = w.interval ?: return emptyMap()
        val gate = Semaphore(PARALLEL)
        return coroutineScope {
            symbols.map { symbol ->
                async {
                    gate.withPermit {
                        val r = market.recentCandles(symbol, interval, w.bars + 1)
                        if (r is ExchangeResult.Ok && r.value.size > w.bars) {
                            val candles = r.value
                            val ref = candles[candles.size - 1 - w.bars].close
                            val now = candles.last().close
                            if (ref > 0.0) symbol to (now / ref - 1.0) * 100.0 else null
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    companion object {
        private const val REFRESH_MS = 15_000L
        private const val WINDOW_REFRESH_MS = 60_000L
        private const val PARALLEL = 6
        private const val MAX_ROWS = 200

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                MarketsViewModel(container.marketRepository, container.selectedSymbolStore)
            }
        }
    }
}
