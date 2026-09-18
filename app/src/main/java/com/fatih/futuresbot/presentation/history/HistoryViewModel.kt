package com.fatih.futuresbot.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.history.TradeHistoryStore
import com.fatih.futuresbot.domain.model.TradeRecord
import com.fatih.futuresbot.domain.model.TradeStats
import com.fatih.futuresbot.domain.model.TradeStatsCalculator
import com.fatih.futuresbot.trading.HistorySync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryOps(
    val syncing: Boolean = false,
    val message: String? = null,
    val confirmClear: Boolean = false,
)

data class HistoryUiState(
    val records: List<TradeRecord> = emptyList(),
    val stats: TradeStats = TradeStats(),
    val ops: HistoryOps = HistoryOps(),
)

class HistoryViewModel(
    private val store: TradeHistoryStore,
    private val sync: HistorySync,
) : ViewModel() {

    private val ops = MutableStateFlow(HistoryOps())

    val state: StateFlow<HistoryUiState> = combine(store.records, ops) { records, o ->
        HistoryUiState(
            records = records.sortedByDescending { it.closeTime ?: it.openTime },
            stats = TradeStatsCalculator.from(records),
            ops = o,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        refresh()
    }

    /** Kapanan pozisyonların çıkış ve PNL bilgisini borsadan tamamlar. */
    fun refresh() {
        if (ops.value.syncing) return
        ops.value = HistoryOps(syncing = true)
        viewModelScope.launch {
            val updated = runCatching { sync.sync() }.getOrDefault(0)
            ops.value = HistoryOps(
                message = if (updated > 0) "$updated işlem güncellendi" else "Güncellenecek işlem yok",
            )
        }
    }

    fun askClear() {
        ops.value = ops.value.copy(confirmClear = true)
    }

    fun dismissClear() {
        ops.value = ops.value.copy(confirmClear = false)
    }

    fun confirmClear() {
        ops.value = HistoryOps()
        viewModelScope.launch {
            store.clear()
            ops.value = HistoryOps(message = "Geçmiş silindi")
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HistoryViewModel(container.tradeHistoryStore, container.historySync) }
        }
    }
}
