package com.fatih.futuresbot.data.binance

import com.fatih.futuresbot.domain.model.AccountSummary
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeError
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.FuturesPosition
import com.fatih.futuresbot.domain.model.SymbolSnapshot
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.domain.repository.MarketRepository
import com.fatih.futuresbot.security.CredentialStore
import com.fatih.futuresbot.trading.ExchangeClient
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

/**
 * Hesap verisini REST ile periyodik çeker (hata olursa üstel bekleme).
 * Fiyat verisi MarketRepository üzerinden WebSocket ile gelir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BinanceAccountRepository(
    private val client: ExchangeClient,
    private val market: MarketRepository,
    private val credentialStore: CredentialStore,
    scope: CoroutineScope,
) : AccountRepository {

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _lastError = MutableStateFlow<ExchangeError?>(null)
    override val lastError: StateFlow<ExchangeError?> = _lastError.asStateFlow()

    private val _positions = MutableStateFlow<List<FuturesPosition>>(emptyList())
    override val positions: StateFlow<List<FuturesPosition>> = _positions.asStateFlow()

    private val account: StateFlow<AccountSummary?> = credentialStore.hasCredentials
        .flatMapLatest { hasKeys -> if (hasKeys) pollAccount() else noCredentials() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    override fun accountSummary(): Flow<AccountSummary?> = account

    private fun noCredentials(): Flow<AccountSummary?> = flow {
        _connection.value = ConnectionState.DISCONNECTED
        _lastError.value = ExchangeError.MissingCredentials
        _positions.value = emptyList()
        emit(null)
    }

    private fun pollAccount(): Flow<AccountSummary?> = flow {
        _connection.value = ConnectionState.CONNECTING
        _lastError.value = null
        var dailyRealized = 0.0
        var lastIncomeFetch = 0L
        var failures = 0

        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            if (now - lastIncomeFetch >= INCOME_INTERVAL_MS) {
                val income = client.realizedPnlSince(startOfTodayMs())
                if (income is ExchangeResult.Ok) {
                    dailyRealized = income.value
                    lastIncomeFetch = now
                }
            }

            val balance = client.balance()
            val positions = client.positions()

            if (balance is ExchangeResult.Ok && positions is ExchangeResult.Ok) {
                failures = 0
                _connection.value = ConnectionState.CONNECTED
                _lastError.value = null
                _positions.value = positions.value

                val b = balance.value
                val startOfDayBalance = b.walletBalance - dailyRealized
                emit(
                    AccountSummary(
                        walletBalance = b.walletBalance,
                        marginBalance = b.marginBalance,
                        availableBalance = b.availableBalance,
                        unrealizedPnl = b.unrealizedPnl,
                        dailyRealizedPnl = dailyRealized,
                        dailyPnlPercent = if (startOfDayBalance > 0.0) {
                            dailyRealized / startOfDayBalance * 100.0
                        } else {
                            0.0
                        },
                        openPositions = positions.value.size,
                    )
                )
                delay(ACCOUNT_POLL_MS)
            } else {
                val error = when {
                    balance is ExchangeResult.Err -> balance.error
                    positions is ExchangeResult.Err -> positions.error
                    else -> ExchangeError.Unknown("bilinmiyor")
                }
                failures++
                _lastError.value = error
                _connection.value = if (error == ExchangeError.InvalidApiKey) {
                    ConnectionState.DISCONNECTED
                } else {
                    ConnectionState.CONNECTING
                }
                delay(backoffMs(error, failures))
            }
        }
    }

    override fun symbolSnapshot(symbol: String): Flow<SymbolSnapshot> = combine(
        market.liveTicker(symbol),
        _positions,
        leverageFlow(symbol),
    ) { ticker, positions, leverage ->
        val p = positions.firstOrNull { it.symbol == symbol }
        SymbolSnapshot(
            symbol = symbol,
            lastPrice = ticker.lastPrice,
            change24hPercent = ticker.change24hPercent,
            markPrice = ticker.markPrice,
            fundingRate = ticker.fundingRate,
            leverage = leverage,
            positionAmt = p?.positionAmt,
            entryPrice = p?.entryPrice,
            margin = p?.initialMargin,
            liquidationPrice = p?.liquidationPrice,
            unrealizedPnl = p?.unrealizedPnl,
        )
    }.onStart { emit(SymbolSnapshot(symbol = symbol)) }

    private fun leverageFlow(symbol: String): Flow<Int?> = credentialStore.hasCredentials
        .flatMapLatest { hasKeys ->
            flow<Int?> {
                emit(null)
                if (hasKeys) {
                    var attempt = 0
                    while (currentCoroutineContext().isActive) {
                        val cfg = client.symbolConfig(symbol)
                        if (cfg is ExchangeResult.Ok) {
                            emit(cfg.value.leverage)
                            break
                        }
                        attempt++
                        delay(min(5_000L * attempt, MAX_BACKOFF_MS))
                    }
                }
            }
        }

    private fun backoffMs(error: ExchangeError, failures: Int): Long {
        val exponential = min(ACCOUNT_POLL_MS * (1L shl min(failures, 4)), MAX_BACKOFF_MS)
        return when (error) {
            ExchangeError.InvalidApiKey -> MAX_BACKOFF_MS
            is ExchangeError.RateLimited -> max(exponential, (error.retryAfterSec ?: 60L) * 1_000L)
            else -> exponential
        }
    }

    private companion object {
        const val ACCOUNT_POLL_MS = 5_000L
        const val INCOME_INTERVAL_MS = 60_000L
        const val MAX_BACKOFF_MS = 60_000L

        fun startOfTodayMs(): Long =
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
