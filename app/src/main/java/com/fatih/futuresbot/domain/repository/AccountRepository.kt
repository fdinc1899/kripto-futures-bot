package com.fatih.futuresbot.domain.repository

import com.fatih.futuresbot.domain.model.AccountSummary
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.SymbolSnapshot
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    val connection: Flow<ConnectionState>
    fun accountSummary(): Flow<AccountSummary>
    fun symbolSnapshot(symbol: String): Flow<SymbolSnapshot>
}
