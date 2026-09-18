package com.fatih.futuresbot.domain.model

import kotlin.math.abs

object TradeStatsCalculator {

    fun from(records: List<TradeRecord>): TradeStats {
        val closed = records
            .filter { it.status == TradeStatus.CLOSED.name && it.realizedPnl != null }
            .sortedBy { it.closeTime ?: it.openTime }
        val open = records.count { it.status == TradeStatus.OPEN.name }
        if (closed.isEmpty()) return TradeStats(total = records.size, open = open)

        val profits = closed.mapNotNull { it.realizedPnl }.filter { it > 0.0 }
        val losses = closed.mapNotNull { it.realizedPnl }.filter { it < 0.0 }
        val totalPnl = closed.sumOf { it.realizedPnl ?: 0.0 }
        val grossProfit = profits.sum()
        val grossLoss = abs(losses.sum())

        // Maksimum drawdown: kümülatif PNL eğrisinde zirveden en derin düşüş
        var cumulative = 0.0
        var peak = 0.0
        var maxDrawdown = 0.0
        for (record in closed) {
            cumulative += record.realizedPnl ?: 0.0
            if (cumulative > peak) peak = cumulative
            val drawdown = peak - cumulative
            if (drawdown > maxDrawdown) maxDrawdown = drawdown
        }

        return TradeStats(
            total = records.size,
            open = open,
            wins = profits.size,
            losses = losses.size,
            winRate = profits.size.toDouble() / closed.size * 100.0,
            lossRate = losses.size.toDouble() / closed.size * 100.0,
            totalPnl = totalPnl,
            averageProfit = if (profits.isEmpty()) 0.0 else grossProfit / profits.size,
            averageLoss = if (losses.isEmpty()) 0.0 else -grossLoss / losses.size,
            profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else null,
            maxDrawdown = maxDrawdown,
        )
    }
}
