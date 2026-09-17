package com.fatih.futuresbot.network.binance

import com.fatih.futuresbot.domain.model.ExchangeEnvironment

object BinanceEndpoints {
    fun restBase(env: ExchangeEnvironment): String = when (env) {
        ExchangeEnvironment.TESTNET -> "https://demo-fapi.binance.com"
        ExchangeEnvironment.REAL -> "https://fapi.binance.com"
    }

    /** Aşama 7'de kullanılacak. Akışlar /public, /market, /private yollarına ayrılmıştır. */
    fun wsBase(env: ExchangeEnvironment): String = when (env) {
        ExchangeEnvironment.TESTNET -> "wss://demo-fstream.binance.com"
        ExchangeEnvironment.REAL -> "wss://fstream.binance.com"
    }
}
