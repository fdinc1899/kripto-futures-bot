package com.fatih.futuresbot.network.binance

import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import com.fatih.futuresbot.domain.model.ExchangeError
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.FuturesBalance
import com.fatih.futuresbot.domain.model.FuturesPosition
import com.fatih.futuresbot.domain.model.MarkPriceInfo
import com.fatih.futuresbot.domain.model.SymbolConfig
import com.fatih.futuresbot.domain.model.Ticker24h
import com.fatih.futuresbot.security.ApiCredentials
import com.fatih.futuresbot.trading.ExchangeClient
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** Binance USDⓈ-M Futures REST istemcisi. Şimdilik yalnızca okuma (GET) işlemleri. */
class BinanceFuturesClient(
    override val environment: ExchangeEnvironment,
    private val http: OkHttpClient,
    private val credentialsProvider: () -> ApiCredentials?,
) : ExchangeClient {

    private val baseUrl = BinanceEndpoints.restBase(environment)
    private val json = Json { ignoreUnknownKeys = true }

    override val endpointLabel: String = baseUrl.removePrefix("https://")

    @Volatile private var timeOffsetMs = 0L
    @Volatile private var timeSynced = false

    override suspend fun ping(): ExchangeResult<Unit> =
        publicGet("/fapi/v1/ping").mapOk { }

    override suspend fun syncServerTime(): ExchangeResult<Long> {
        val before = System.currentTimeMillis()
        val result = publicGet("/fapi/v1/time").mapOk { it.objOrNull()?.lng("serverTime") }
        val after = System.currentTimeMillis()
        return when (result) {
            is ExchangeResult.Ok -> {
                timeOffsetMs = result.value - (before + after) / 2
                timeSynced = true
                ExchangeResult.Ok(timeOffsetMs)
            }
            is ExchangeResult.Err -> ExchangeResult.Err(result.error)
        }
    }

    override suspend fun ticker24h(symbol: String): ExchangeResult<Ticker24h> =
        publicGet("/fapi/v1/ticker/24hr", listOf("symbol" to symbol)).mapOk { el ->
            val o = el.objOrNull() ?: return@mapOk null
            Ticker24h(
                symbol = o.str("symbol") ?: symbol,
                lastPrice = o.dbl("lastPrice") ?: return@mapOk null,
                priceChangePercent = o.dbl("priceChangePercent") ?: 0.0,
            )
        }

    override suspend fun markPrice(symbol: String): ExchangeResult<MarkPriceInfo> =
        publicGet("/fapi/v1/premiumIndex", listOf("symbol" to symbol)).mapOk { el ->
            val o = el.objOrNull() ?: return@mapOk null
            MarkPriceInfo(
                symbol = o.str("symbol") ?: symbol,
                markPrice = o.dbl("markPrice") ?: return@mapOk null,
                lastFundingRate = o.dbl("lastFundingRate"),
                nextFundingTime = o.lng("nextFundingTime"),
            )
        }

    override suspend fun balance(): ExchangeResult<FuturesBalance> =
        signedGet("/fapi/v3/account").mapOk { el ->
            val o = el.objOrNull() ?: return@mapOk null
            FuturesBalance(
                walletBalance = o.dbl("totalWalletBalance") ?: return@mapOk null,
                marginBalance = o.dbl("totalMarginBalance") ?: 0.0,
                availableBalance = o.dbl("availableBalance") ?: 0.0,
                unrealizedPnl = o.dbl("totalUnrealizedProfit") ?: 0.0,
            )
        }

    override suspend fun positions(): ExchangeResult<List<FuturesPosition>> =
        signedGet("/fapi/v3/positionRisk").mapOk { el ->
            (el as? JsonArray)?.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val amt = o.dbl("positionAmt") ?: return@mapNotNull null
                if (amt == 0.0) return@mapNotNull null
                FuturesPosition(
                    symbol = o.str("symbol") ?: return@mapNotNull null,
                    positionSide = o.str("positionSide") ?: "BOTH",
                    positionAmt = amt,
                    entryPrice = o.dbl("entryPrice") ?: 0.0,
                    markPrice = o.dbl("markPrice") ?: 0.0,
                    unrealizedPnl = o.dbl("unRealizedProfit") ?: 0.0,
                    liquidationPrice = o.dbl("liquidationPrice")?.takeIf { it > 0.0 },
                    initialMargin = o.dbl("initialMargin") ?: 0.0,
                    notional = o.dbl("notional") ?: 0.0,
                )
            }
        }

    override suspend fun symbolConfig(symbol: String): ExchangeResult<SymbolConfig> =
        signedGet("/fapi/v1/symbolConfig", listOf("symbol" to symbol)).mapOk { el ->
            val o = (el as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapOk null
            SymbolConfig(
                symbol = o.str("symbol") ?: symbol,
                marginType = o.str("marginType") ?: "?",
                leverage = o.str("leverage")?.toIntOrNull() ?: return@mapOk null,
            )
        }

    override suspend fun realizedPnlSince(startTimeMs: Long): ExchangeResult<Double> =
        signedGet(
            "/fapi/v1/income",
            listOf(
                "incomeType" to "REALIZED_PNL",
                "startTime" to startTimeMs.toString(),
                "limit" to "1000",
            ),
        ).mapOk { el ->
            (el as? JsonArray)?.sumOf { item -> (item as? JsonObject)?.dbl("income") ?: 0.0 }
        }

    // ------------------------------------------------------------ HTTP katmanı

    private suspend fun publicGet(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
    ): ExchangeResult<JsonElement> {
        val url = if (params.isEmpty()) "$baseUrl$path" else "$baseUrl$path?${encode(params)}"
        return execute(Request.Builder().url(url).get().build())
    }

    private suspend fun signedGet(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
    ): ExchangeResult<JsonElement> {
        val credentials = withContext(Dispatchers.IO) { credentialsProvider() }
            ?: return ExchangeResult.Err(ExchangeError.MissingCredentials)

        if (!timeSynced) {
            val sync = syncServerTime()
            if (sync is ExchangeResult.Err) return ExchangeResult.Err(sync.error)
        }

        val query = encode(
            params + listOf(
                "recvWindow" to RECV_WINDOW_MS.toString(),
                "timestamp" to (System.currentTimeMillis() + timeOffsetMs).toString(),
            )
        )
        val signature = BinanceSigner.sign(query, credentials.apiSecret)
        val request = Request.Builder()
            .url("$baseUrl$path?$query&signature=$signature")
            .header("X-MBX-APIKEY", credentials.apiKey)
            .get()
            .build()

        val result = execute(request)
        if (result is ExchangeResult.Err && result.error == ExchangeError.TimestampOutOfSync) {
            timeSynced = false
        }
        return result
    }

    private suspend fun execute(request: Request): ExchangeResult<JsonElement> =
        withContext(Dispatchers.IO) {
            val result: ExchangeResult<JsonElement> = try {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        parseBody(body)
                    } else {
                        ExchangeResult.Err(
                            BinanceErrorMapper.fromHttp(
                                response.code,
                                body,
                                response.header("Retry-After"),
                            )
                        )
                    }
                }
            } catch (e: IOException) {
                ExchangeResult.Err(BinanceErrorMapper.fromIo(e))
            } catch (e: IllegalArgumentException) {
                ExchangeResult.Err(ExchangeError.Unknown("Geçersiz istek"))
            }
            result
        }

    private fun parseBody(body: String): ExchangeResult<JsonElement> =
        try {
            ExchangeResult.Ok(json.parseToJsonElement(body))
        } catch (e: Exception) {
            ExchangeResult.Err(ExchangeError.Unknown("Geçersiz yanıt"))
        }

    private fun encode(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    private inline fun <T> ExchangeResult<JsonElement>.mapOk(
        transform: (JsonElement) -> T?,
    ): ExchangeResult<T> = when (this) {
        is ExchangeResult.Ok -> {
            val mapped = transform(value)
            if (mapped != null) {
                ExchangeResult.Ok(mapped)
            } else {
                ExchangeResult.Err(ExchangeError.Unknown("Beklenmeyen yanıt biçimi"))
            }
        }
        is ExchangeResult.Err -> ExchangeResult.Err(error)
    }

    private companion object {
        const val RECV_WINDOW_MS = 5_000L
    }
}
