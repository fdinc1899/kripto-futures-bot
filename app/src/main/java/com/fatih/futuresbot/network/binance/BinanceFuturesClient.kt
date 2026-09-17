package com.fatih.futuresbot.network.binance

import com.fatih.futuresbot.domain.model.AlgoOrderInfo
import com.fatih.futuresbot.domain.model.Candle
import com.fatih.futuresbot.domain.model.ConditionalOrderRequest
import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import com.fatih.futuresbot.domain.model.ExchangeError
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.FuturesBalance
import com.fatih.futuresbot.domain.model.FuturesPosition
import com.fatih.futuresbot.domain.model.MarkPriceInfo
import com.fatih.futuresbot.domain.model.NewOrderRequest
import com.fatih.futuresbot.domain.model.OrderInfo
import com.fatih.futuresbot.domain.model.OrderType
import com.fatih.futuresbot.domain.model.SymbolConfig
import com.fatih.futuresbot.domain.model.SymbolRules
import com.fatih.futuresbot.domain.model.Ticker24h
import com.fatih.futuresbot.security.ApiCredentials
import com.fatih.futuresbot.trading.ExchangeClient
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Binance USDⓈ-M Futures REST istemcisi. İmzalı isteklerde otomatik tekrar YOKTUR (yalnızca -1021 saat hatası). */
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

    private val rulesMutex = Mutex()
    @Volatile private var rulesCache: Map<String, SymbolRules> = emptyMap()
    @Volatile private var rulesFetchedAt = 0L

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
            el.objOrNull()?.let { parseTicker(it, symbol) }
        }

    override suspend fun allTickers(): ExchangeResult<List<Ticker24h>> =
        publicGet("/fapi/v1/ticker/24hr").mapOk { el ->
            (el as? JsonArray)?.mapNotNull { item ->
                (item as? JsonObject)?.let { parseTicker(it, null) }
            }
        }

    override suspend fun klines(
        symbol: String,
        interval: String,
        limit: Int,
    ): ExchangeResult<List<Candle>> =
        publicGet(
            "/fapi/v1/klines",
            listOf("symbol" to symbol, "interval" to interval, "limit" to limit.toString()),
        ).mapOk { el ->
            val now = System.currentTimeMillis()
            (el as? JsonArray)?.mapNotNull { row ->
                val r = row as? JsonArray ?: return@mapNotNull null
                Candle(
                    openTime = r.textAt(0)?.toLongOrNull() ?: return@mapNotNull null,
                    open = r.textAt(1)?.toDoubleOrNull() ?: return@mapNotNull null,
                    high = r.textAt(2)?.toDoubleOrNull() ?: return@mapNotNull null,
                    low = r.textAt(3)?.toDoubleOrNull() ?: return@mapNotNull null,
                    close = r.textAt(4)?.toDoubleOrNull() ?: return@mapNotNull null,
                    volume = r.textAt(5)?.toDoubleOrNull() ?: 0.0,
                    closed = (r.textAt(6)?.toLongOrNull() ?: Long.MAX_VALUE) < now,
                )
            }
        }

    private fun parseTicker(o: JsonObject, fallbackSymbol: String?): Ticker24h? {
        val symbol = o.str("symbol") ?: fallbackSymbol ?: return null
        val last = o.dbl("lastPrice") ?: return null
        return Ticker24h(
            symbol = symbol,
            lastPrice = last,
            priceChangePercent = o.dbl("priceChangePercent") ?: 0.0,
            quoteVolume = o.dbl("quoteVolume") ?: 0.0,
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


    // ------------------------------------------------------------ Emir işlemleri

    override suspend fun symbolRules(symbol: String): ExchangeResult<SymbolRules> {
        val stale = System.currentTimeMillis() - rulesFetchedAt >= RULES_TTL_MS
        if (stale || !rulesCache.containsKey(symbol)) {
            rulesMutex.withLock {
                val stillStale = System.currentTimeMillis() - rulesFetchedAt >= RULES_TTL_MS
                if (stillStale || !rulesCache.containsKey(symbol)) {
                    val r = publicGet("/fapi/v1/exchangeInfo").mapOk { parseExchangeInfo(it) }
                    if (r is ExchangeResult.Ok) {
                        rulesCache = r.value
                        rulesFetchedAt = System.currentTimeMillis()
                    } else if (r is ExchangeResult.Err && rulesCache.isEmpty()) {
                        return ExchangeResult.Err(r.error)
                    }
                }
            }
        }
        val rules = rulesCache[symbol]
            ?: return ExchangeResult.Err(ExchangeError.Api(-1121, "Geçersiz parite: $symbol"))
        return ExchangeResult.Ok(rules)
    }

    override suspend fun isHedgeMode(): ExchangeResult<Boolean> =
        signedGet("/fapi/v1/positionSide/dual").mapOk { it.objOrNull()?.bool("dualSidePosition") }

    override suspend fun setLeverage(symbol: String, leverage: Int): ExchangeResult<Int> =
        signedRequest(
            "POST",
            "/fapi/v1/leverage",
            listOf("symbol" to symbol, "leverage" to leverage.toString()),
        ).mapOk { it.objOrNull()?.str("leverage")?.toIntOrNull() }

    override suspend fun placeOrder(request: NewOrderRequest): ExchangeResult<OrderInfo> {
        val params = mutableListOf(
            "symbol" to request.symbol,
            "side" to request.side,
            "type" to request.type.name,
            "quantity" to request.quantity.toPlainString(),
            "newClientOrderId" to request.clientOrderId,
            "newOrderRespType" to "RESULT",
        )
        if (request.type == OrderType.LIMIT) {
            val price = request.price
                ?: return ExchangeResult.Err(ExchangeError.Unknown("Limit fiyatı eksik"))
            params.add("price" to price.toPlainString())
            params.add("timeInForce" to "GTC")
        }
        if (request.reduceOnly) params.add("reduceOnly" to "true")
        return signedRequest("POST", "/fapi/v1/order", params).mapOk { el ->
            el.objOrNull()?.let { o -> parseOrder(o) }
        }
    }

    override suspend fun queryOrder(symbol: String, clientOrderId: String): ExchangeResult<OrderInfo> =
        signedGet(
            "/fapi/v1/order",
            listOf("symbol" to symbol, "origClientOrderId" to clientOrderId),
        ).mapOk { el -> el.objOrNull()?.let { o -> parseOrder(o) } }

    override suspend fun openOrders(symbol: String?): ExchangeResult<List<OrderInfo>> {
        val params = if (symbol != null) listOf("symbol" to symbol) else emptyList()
        return signedGet("/fapi/v1/openOrders", params).mapOk { el ->
            (el as? JsonArray)?.mapNotNull { item -> (item as? JsonObject)?.let { o -> parseOrder(o) } }
        }
    }

    override suspend fun cancelOrder(symbol: String, orderId: Long): ExchangeResult<Unit> =
        signedRequest(
            "DELETE",
            "/fapi/v1/order",
            listOf("symbol" to symbol, "orderId" to orderId.toString()),
        ).mapOk { }

    override suspend fun cancelAllOrders(symbol: String): ExchangeResult<Unit> =
        signedRequest("DELETE", "/fapi/v1/allOpenOrders", listOf("symbol" to symbol)).mapOk { }

    override suspend fun placeConditionalOrder(
        request: ConditionalOrderRequest,
    ): ExchangeResult<AlgoOrderInfo> =
        signedRequest(
            "POST",
            "/fapi/v1/algoOrder",
            listOf(
                "algoType" to "CONDITIONAL",
                "symbol" to request.symbol,
                "side" to request.side,
                "type" to request.type,
                "triggerPrice" to request.triggerPrice.toPlainString(),
                "closePosition" to "true",
                "workingType" to "MARK_PRICE",
                "clientAlgoId" to request.clientAlgoId,
            ),
        ).mapOk { el -> el.objOrNull()?.let { o -> parseAlgo(o) } }

    override suspend fun queryConditionalOrder(clientAlgoId: String): ExchangeResult<AlgoOrderInfo> =
        signedGet("/fapi/v1/algoOrder", listOf("clientAlgoId" to clientAlgoId)).mapOk { el ->
            el.objOrNull()?.let { o -> parseAlgo(o) }
        }

    override suspend fun openConditionalOrders(symbol: String?): ExchangeResult<List<AlgoOrderInfo>> {
        val params = mutableListOf("algoType" to "CONDITIONAL")
        if (symbol != null) params.add("symbol" to symbol)
        return signedGet("/fapi/v1/openAlgoOrders", params).mapOk { el ->
            when (el) {
                is JsonArray -> el.mapNotNull { item -> (item as? JsonObject)?.let { o -> parseAlgo(o) } }
                is JsonObject -> listOfNotNull(parseAlgo(el))
                else -> null
            }
        }
    }

    override suspend fun cancelConditionalOrder(algoId: Long): ExchangeResult<Unit> =
        signedRequest("DELETE", "/fapi/v1/algoOrder", listOf("algoId" to algoId.toString())).mapOk { }

    override suspend fun cancelAllConditionalOrders(symbol: String): ExchangeResult<Unit> =
        signedRequest("DELETE", "/fapi/v1/algoOpenOrders", listOf("symbol" to symbol)).mapOk { }

    private fun parseOrder(o: JsonObject): OrderInfo? {
        return OrderInfo(
            symbol = o.str("symbol") ?: return null,
            orderId = o.lng("orderId") ?: return null,
            clientOrderId = o.str("clientOrderId").orEmpty(),
            side = o.str("side").orEmpty(),
            type = o.str("origType") ?: o.str("type").orEmpty(),
            status = o.str("status") ?: "UNKNOWN",
            price = o.dbl("price") ?: 0.0,
            avgPrice = o.dbl("avgPrice") ?: 0.0,
            origQty = o.dbl("origQty") ?: 0.0,
            executedQty = o.dbl("executedQty") ?: 0.0,
            reduceOnly = o.bool("reduceOnly") ?: false,
            time = o.lng("updateTime") ?: o.lng("time") ?: 0L,
        )
    }

    private fun parseAlgo(o: JsonObject): AlgoOrderInfo? {
        return AlgoOrderInfo(
            symbol = o.str("symbol") ?: return null,
            algoId = o.lng("algoId") ?: return null,
            clientAlgoId = o.str("clientAlgoId").orEmpty(),
            side = o.str("side").orEmpty(),
            orderType = o.str("orderType") ?: o.str("type").orEmpty(),
            status = o.str("algoStatus") ?: "UNKNOWN",
            triggerPrice = o.dbl("triggerPrice") ?: 0.0,
            closePosition = o.bool("closePosition") ?: false,
            createTime = o.lng("createTime") ?: 0L,
        )
    }

    private fun parseExchangeInfo(el: JsonElement): Map<String, SymbolRules>? {
        val symbols = el.objOrNull()?.get("symbols") as? JsonArray ?: return null
        val out = HashMap<String, SymbolRules>(symbols.size * 2)
        for (item in symbols) {
            val o = item as? JsonObject ?: continue
            val symbol = o.str("symbol") ?: continue
            val filters = (o["filters"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            val priceFilter = filters.firstOrNull { it.str("filterType") == "PRICE_FILTER" }
            val lotFilter = filters.firstOrNull { it.str("filterType") == "LOT_SIZE" }
            val marketFilter = filters.firstOrNull { it.str("filterType") == "MARKET_LOT_SIZE" }
                ?: lotFilter
            val notionalFilter = filters.firstOrNull { it.str("filterType") == "MIN_NOTIONAL" }
            val tick = priceFilter?.dec("tickSize") ?: continue
            val step = lotFilter?.dec("stepSize") ?: continue
            out[symbol] = SymbolRules(
                symbol = symbol,
                status = o.str("status") ?: "UNKNOWN",
                tickSize = tick,
                stepSize = step,
                minQty = lotFilter?.dec("minQty") ?: BigDecimal.ZERO,
                maxQty = lotFilter?.dec("maxQty") ?: BigDecimal.ZERO,
                marketStepSize = marketFilter?.dec("stepSize") ?: step,
                marketMinQty = marketFilter?.dec("minQty") ?: BigDecimal.ZERO,
                marketMaxQty = marketFilter?.dec("maxQty") ?: BigDecimal.ZERO,
                minNotional = notionalFilter?.dec("notional") ?: BigDecimal.ZERO,
            )
        }
        return out
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
    ): ExchangeResult<JsonElement> = signedRequest("GET", path, params)

    private suspend fun signedRequest(
        method: String,
        path: String,
        params: List<Pair<String, String>>,
    ): ExchangeResult<JsonElement> {
        val credentials = withContext(Dispatchers.IO) { credentialsProvider() }
            ?: return ExchangeResult.Err(ExchangeError.MissingCredentials)

        if (!timeSynced) {
            val sync = syncServerTime()
            if (sync is ExchangeResult.Err) return ExchangeResult.Err(sync.error)
        }

        val first = executeSigned(method, path, params, credentials)
        if (first is ExchangeResult.Err && first.error == ExchangeError.TimestampOutOfSync) {
            // -1021: istek sunucuda İŞLENMEDEN reddedildi; saat eşitlenip bir kez güvenle tekrar denenebilir
            timeSynced = false
            val sync = syncServerTime()
            if (sync is ExchangeResult.Err) return ExchangeResult.Err(sync.error)
            return executeSigned(method, path, params, credentials)
        }
        return first
    }

    private suspend fun executeSigned(
        method: String,
        path: String,
        params: List<Pair<String, String>>,
        credentials: ApiCredentials,
    ): ExchangeResult<JsonElement> {
        val payload = encode(
            params + listOf(
                "recvWindow" to RECV_WINDOW_MS.toString(),
                "timestamp" to (System.currentTimeMillis() + timeOffsetMs).toString(),
            )
        )
        val signed = payload + "&signature=" + BinanceSigner.sign(payload, credentials.apiSecret)
        val builder = Request.Builder().header("X-MBX-APIKEY", credentials.apiKey)
        val request = when (method) {
            "GET" -> builder.url("$baseUrl$path?$signed").get().build()
            "DELETE" -> builder.url("$baseUrl$path?$signed").delete().build()
            "POST" -> builder.url("$baseUrl$path").post(signed.toRequestBody(FORM_MEDIA_TYPE)).build()
            else -> return ExchangeResult.Err(ExchangeError.Unknown("Desteklenmeyen metot: $method"))
        }
        return execute(request)
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
        const val RULES_TTL_MS = 60 * 60 * 1_000L
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
    }
}
