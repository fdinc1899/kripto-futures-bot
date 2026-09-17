package com.fatih.futuresbot.trading

import java.math.BigDecimal
import java.math.RoundingMode

/** Adıma (stepSize) göre aşağı yuvarlar: miktar asla kullanıcının istediğini aşmaz. */
internal fun BigDecimal.floorToStep(step: BigDecimal): BigDecimal {
    if (step.signum() <= 0) return this
    return divide(step, 0, RoundingMode.DOWN).multiply(step).stripTrailingZeros()
}

/** Fiyat adımına (tickSize) göre en yakın değere yuvarlar. */
internal fun BigDecimal.roundToStep(step: BigDecimal): BigDecimal {
    if (step.signum() <= 0) return this
    return divide(step, 0, RoundingMode.HALF_UP).multiply(step).stripTrailingZeros()
}

internal fun Double.toDecimal(): BigDecimal = BigDecimal.valueOf(this)
