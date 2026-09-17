package com.fatih.futuresbot.network.binance

import com.fatih.futuresbot.domain.model.ExchangeError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object BinanceErrorMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun fromHttp(httpCode: Int, body: String, retryAfter: String?): ExchangeError {
        if (httpCode == 418 || httpCode == 429) {
            return ExchangeError.RateLimited(retryAfter?.toLongOrNull())
        }
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val code = obj?.str("code")?.toIntOrNull()
        val msg = obj?.str("msg").orEmpty()

        if (msg.contains("maintenance", ignoreCase = true)) return ExchangeError.Maintenance
        if (code != null) fromCode(code)?.let { return it }

        return when {
            httpCode == 401 -> ExchangeError.InvalidApiKey
            httpCode >= 500 -> ExchangeError.Server(httpCode)
            code != null -> ExchangeError.Api(code, msg.take(120))
            else -> ExchangeError.Api(httpCode, body.take(120))
        }
    }

    private fun fromCode(code: Int): ExchangeError? = when (code) {
        -2014, -2015, -1022 -> ExchangeError.InvalidApiKey
        -1021 -> ExchangeError.TimestampOutOfSync
        -1003 -> ExchangeError.RateLimited(null)
        -2018, -2019 -> ExchangeError.InsufficientMargin
        -4164 -> ExchangeError.MinOrderSize
        -1111, -1013, -4003 -> ExchangeError.InvalidQuantity
        -4028 -> ExchangeError.InvalidLeverage
        -4116 -> ExchangeError.DuplicateOrder
        -2013, -2011 -> ExchangeError.OrderNotFound
        -2021 -> ExchangeError.WouldTriggerImmediately
        -2022 -> ExchangeError.ReduceOnlyRejected
        else -> null
    }

    fun fromIo(e: IOException): ExchangeError = when (e) {
        is UnknownHostException -> ExchangeError.NoInternet
        is InterruptedIOException -> ExchangeError.Timeout
        else -> ExchangeError.ConnectionFailed(e.javaClass.simpleName)
    }
}
