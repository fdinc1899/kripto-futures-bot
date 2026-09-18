package com.fatih.futuresbot.trading

import com.fatih.futuresbot.data.history.TradeHistoryStore
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.TradeRecord
import com.fatih.futuresbot.domain.model.TradeStatus
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.notifications.Notifier
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pozisyon kapanışlarını ve bağlantı kopukluğunu izleyip bildirim gönderir.
 * Kapanış nedenini (SL / TP) çıkış fiyatına bakarak tahmin eder.
 */
class TradeMonitor(
    private val accountRepository: AccountRepository,
    private val historySync: HistorySync,
    private val historyStore: TradeHistoryStore,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { watchPositions() }
        scope.launch { watchConnection() }
    }

    private suspend fun watchPositions() {
        var previous: Set<String>? = null
        accountRepository.positions.collect { positions ->
            val current = positions.map { it.symbol }.toSet()
            val before = previous
            if (before != null) {
                for (symbol in before - current) {
                    handleClosed(symbol)
                }
            }
            previous = current
        }
    }

    private suspend fun handleClosed(symbol: String) {
        // Borsadan çıkış fiyatı ve PNL gelsin
        runCatching { historySync.sync() }
        val record = historyStore.records.value
            .filter { it.symbol == symbol && it.status == TradeStatus.CLOSED.name }
            .maxByOrNull { it.closeTime ?: it.openTime }
        if (record == null) {
            notifier.trade("$symbol pozisyon kapandı", "Ayrıntılar History ekranında")
            return
        }
        val pnl = record.realizedPnl ?: 0.0
        val sign = if (pnl >= 0) "+" else ""
        notifier.trade(
            "$symbol ${reasonOf(record)}",
            "${record.side} · PNL $sign${String.format(Locale.US, "%.2f", pnl)} USDT · " +
                "çıkış ${String.format(Locale.US, "%.2f", record.exitPrice ?: 0.0)}",
        )
    }

    private fun reasonOf(record: TradeRecord): String {
        val exit = record.exitPrice ?: return "pozisyon kapandı"
        if (exit <= 0.0) return "pozisyon kapandı"
        val stopDistance = abs(exit - record.stopLoss) / exit
        val takeDistance = record.takeProfit?.let { abs(exit - it) / exit }
        return when {
            stopDistance <= TOLERANCE && (takeDistance == null || stopDistance <= takeDistance) ->
                "Stop-Loss gerçekleşti"
            takeDistance != null && takeDistance <= TOLERANCE -> "Take-Profit gerçekleşti"
            else -> "pozisyon kapandı"
        }
    }

    private suspend fun watchConnection() {
        var wasConnected = false
        var pending: Job? = null
        accountRepository.connection.collect { state ->
            if (state == ConnectionState.CONNECTED) {
                wasConnected = true
                pending?.cancel()
                pending = null
            } else if (wasConnected && pending == null) {
                pending = scope.launch {
                    delay(CONNECTION_GRACE_MS)
                    val error = accountRepository.lastError.value
                    notifier.alert(
                        "API bağlantısı kayboldu",
                        error?.userMessage ?: "Borsa ile bağlantı kurulamıyor",
                    )
                }
            }
        }
    }

    private companion object {
        const val TOLERANCE = 0.004
        const val CONNECTION_GRACE_MS = 30_000L
    }
}
