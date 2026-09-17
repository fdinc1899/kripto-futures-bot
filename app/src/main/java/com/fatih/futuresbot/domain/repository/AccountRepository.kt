package com.fatih.futuresbot.domain.repository

import com.fatih.futuresbot.domain.model.AccountSummary
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeError
import com.fatih.futuresbot.domain.model.FuturesPosition
import com.fatih.futuresbot.domain.model.SymbolSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AccountRepository {
    val connection: StateFlow<ConnectionState>
    val lastError: StateFlow<ExchangeError?>
    val positions: StateFlow<List<FuturesPosition>>
    fun accountSummary(): Flow<AccountSummary?>
    fun symbolSnapshot(symbol: String): Flow<SymbolSnapshot>
}
