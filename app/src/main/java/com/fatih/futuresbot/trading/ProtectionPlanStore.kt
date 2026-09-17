package com.fatih.futuresbot.trading

import android.content.Context
import com.fatih.futuresbot.domain.model.PositionSide

/** Dolmayı bekleyen limit emirler için SL/TP planı. */
data class ProtectionPlan(
    val symbol: String,
    val side: PositionSide,
    val stopLoss: String,
    val takeProfit: String?,
    val entryClientOrderId: String,
    val createdAt: Long,
)

class ProtectionPlanStore(context: Context) {

    private val prefs = context.getSharedPreferences("protection_plans", Context.MODE_PRIVATE)
    private val lock = Any()

    fun save(plan: ProtectionPlan) {
        synchronized(lock) { prefs.edit().putString(plan.symbol, encode(plan)).commit() }
    }

    fun remove(symbol: String) {
        synchronized(lock) { prefs.edit().remove(symbol).commit() }
    }

    fun all(): List<ProtectionPlan> = synchronized(lock) {
        prefs.all.values.mapNotNull { value -> (value as? String)?.let { decode(it) } }
    }

    private fun encode(plan: ProtectionPlan): String = listOf(
        plan.symbol,
        plan.side.name,
        plan.stopLoss,
        plan.takeProfit.orEmpty(),
        plan.entryClientOrderId,
        plan.createdAt.toString(),
    ).joinToString("|")

    private fun decode(raw: String): ProtectionPlan? {
        val p = raw.split('|')
        if (p.size != 6) return null
        val side = PositionSide.entries.firstOrNull { it.name == p[1] } ?: return null
        return ProtectionPlan(
            symbol = p[0],
            side = side,
            stopLoss = p[2],
            takeProfit = p[3].ifEmpty { null },
            entryClientOrderId = p[4],
            createdAt = p[5].toLongOrNull() ?: 0L,
        )
    }
}
