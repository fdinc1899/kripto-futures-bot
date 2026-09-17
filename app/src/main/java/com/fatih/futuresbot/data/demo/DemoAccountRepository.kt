package com.fatih.futuresbot.data.demo

import com.fatih.futuresbot.domain.model.AccountSummary
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.SymbolSnapshot
import com.fatih.futuresbot.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Borsaya bağlanmayan sabit demo verisi. Fiyat UYDURULMAZ: WebSocket
 * (Aşama 7) gelene kadar fiyat alanları null kalır.
 */
class DemoAccountRepository : AccountRepository {

    override val connection: Flow<ConnectionState> = flowOf(ConnectionState.DISCONNECTED)

    override fun accountSummary(): Flow<AccountSummary> = flowOf(
        AccountSummary(
            totalBalance = 10_000.0,
            availableBalance = 10_000.0,
            dailyPnl = 0.0,
            totalPnl = 0.0,
            dailyPnlPercent = 0.0,
            openPositions = 0,
        )
    )

    override fun symbolSnapshot(symbol: String): Flow<SymbolSnapshot> = flowOf(
        SymbolSnapshot(
            symbol = symbol,
            lastPrice = null,
            change24hPercent = null,
            leverage = 5,
            margin = 0.0,
            liquidationPrice = null,
        )
    )
}
