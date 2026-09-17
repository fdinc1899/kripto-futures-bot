package com.fatih.futuresbot.presentation.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Fmt {
    private val money = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    private fun sign(v: Double) = if (v > 0) "+" else ""

    fun usdt(v: Double?): String = v?.let { money.format(it) + " USDT" } ?: "—"
    fun signedUsdt(v: Double?): String = v?.let { sign(it) + money.format(it) + " USDT" } ?: "—"
    fun pct(v: Double?): String = v?.let { sign(it) + money.format(it) + "%" } ?: "—"
    fun price(v: Double?): String = v?.let { money.format(it) } ?: "—"
}
