package com.fatih.futuresbot.presentation.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Fmt {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val money = DecimalFormat("#,##0.00", symbols)
    private val priceFmt = DecimalFormat("#,##0.00####", symbols)
    private val qtyFmt = DecimalFormat("0.########", symbols)

    private fun sign(v: Double) = if (v > 0) "+" else ""

    fun usdt(v: Double?): String = v?.let { money.format(it) + " USDT" } ?: "—"
    fun signedUsdt(v: Double?): String = v?.let { sign(it) + money.format(it) + " USDT" } ?: "—"
    fun pct(v: Double?): String = v?.let { sign(it) + money.format(it) + "%" } ?: "—"
    fun price(v: Double?): String = v?.let { priceFmt.format(it) } ?: "—"
    fun qty(v: Double?): String = v?.let { qtyFmt.format(it) } ?: "—"
    fun compact(v: Double?): String = v?.let {
        when {
            it >= 1e9 -> String.format(Locale.US, "%.2fB", it / 1e9)
            it >= 1e6 -> String.format(Locale.US, "%.2fM", it / 1e6)
            it >= 1e3 -> String.format(Locale.US, "%.2fK", it / 1e3)
            else -> money.format(it)
        }
    } ?: "—"

    fun funding(v: Double?): String =
        v?.let { sign(it) + String.format(Locale.US, "%.4f%%", it * 100) } ?: "—"
}
