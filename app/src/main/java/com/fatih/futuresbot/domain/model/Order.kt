package com.fatih.futuresbot.domain.model

import java.math.BigDecimal

enum class OrderType { MARKET, LIMIT }

/** exchangeInfo filtrelerinden gelen parite kuralları. */
data class SymbolRules(
    val symbol: String,
    val status: String,
    val tickSize: BigDecimal,
    val stepSize: BigDecimal,
    val minQty: BigDecimal,
    val maxQty: BigDecimal,
    val marketStepSize: BigDecimal,
    val marketMinQty: BigDecimal,
    val marketMaxQty: BigDecimal,
    val minNotional: BigDecimal,
)

data class NewOrderRequest(
    val symbol: String,
    /** BUY veya SELL */
    val side: String,
    val type: OrderType,
    val quantity: BigDecimal,
    val price: BigDecimal? = null,
    val reduceOnly: Boolean = false,
    val clientOrderId: String,
)

/** Pozisyonun tamamını kapatan koşullu emir (closePosition=true). */
data class ConditionalOrderRequest(
    val symbol: String,
    val side: String,
    /** STOP_MARKET veya TAKE_PROFIT_MARKET */
    val type: String,
    val triggerPrice: BigDecimal,
    val clientAlgoId: String,
)

data class OrderInfo(
    val symbol: String,
    val orderId: Long,
    val clientOrderId: String,
    val side: String,
    val type: String,
    val status: String,
    val price: Double,
    val avgPrice: Double,
    val origQty: Double,
    val executedQty: Double,
    val reduceOnly: Boolean,
    val time: Long,
)

data class AlgoOrderInfo(
    val symbol: String,
    val algoId: Long,
    val clientAlgoId: String,
    val side: String,
    val orderType: String,
    val status: String,
    val triggerPrice: Double,
    val closePosition: Boolean,
    val createTime: Long,
)
