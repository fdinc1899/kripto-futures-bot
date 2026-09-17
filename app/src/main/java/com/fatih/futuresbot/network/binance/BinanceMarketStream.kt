package com.fatih.futuresbot.network.binance

import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface StreamEvent {
    /** Bağlantı (yeniden) kuruldu. */
    data object Opened : StreamEvent
    data class Data(val stream: String, val data: JsonObject) : StreamEvent
}

/**
 * Binance USDⓈ-M piyasa akışı (/market yolu, stream modu).
 * Bağlantı koparsa üstel beklemeyle (1 sn → 30 sn) otomatik yeniden bağlanır;
 * Binance'in 24 saatte bir zorunlu kopmasını da aynı yolla karşılar.
 */
class BinanceMarketStream(http: OkHttpClient, environment: ExchangeEnvironment) {

    // Ölü bağlantıyı yakalamak için istemci tarafı ping
    private val wsClient = http.newBuilder().pingInterval(20, TimeUnit.SECONDS).build()
    private val baseUrl = BinanceEndpoints.wsBase(environment) + "/market/stream?streams="
    private val json = Json { ignoreUnknownKeys = true }

    private val nextId = AtomicInteger(0)
    private val connections = ConcurrentHashMap<Int, ConnectionState>()
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun subscribe(streams: List<String>): Flow<StreamEvent> = flow {
        require(streams.isNotEmpty()) { "En az bir akış gerekli" }
        val id = nextId.incrementAndGet()
        var failures = 0
        try {
            while (currentCoroutineContext().isActive) {
                update(id, ConnectionState.CONNECTING)
                connectOnce(streams)
                    .catch { /* bağlantı hatası: aşağıda yeniden denenecek */ }
                    .collect { event ->
                        if (event is StreamEvent.Opened) {
                            failures = 0
                            update(id, ConnectionState.CONNECTED)
                        }
                        emit(event)
                    }
                update(id, ConnectionState.DISCONNECTED)
                failures++
                delay(backoffMs(failures))
            }
        } finally {
            connections.remove(id)
            publish()
        }
    }

    private fun connectOnce(streams: List<String>): Flow<StreamEvent> = callbackFlow {
        val request = Request.Builder().url(baseUrl + streams.joinToString("/")).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(StreamEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { trySend(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                channel.close(t)
            }
        }
        val socket = wsClient.newWebSocket(request, listener)
        awaitClose { socket.cancel() }
    }

    private fun parse(text: String): StreamEvent.Data? {
        val obj = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val stream = obj.str("stream") ?: return null
        val data = obj["data"] as? JsonObject ?: return null
        return StreamEvent.Data(stream, data)
    }

    private fun backoffMs(failures: Int): Long =
        min(1_000L * (1L shl min(failures - 1, 5)), 30_000L)

    private fun update(id: Int, state: ConnectionState) {
        connections[id] = state
        publish()
    }

    private fun publish() {
        val values = connections.values.toList()
        _state.value = when {
            values.any { it == ConnectionState.CONNECTED } -> ConnectionState.CONNECTED
            values.any { it == ConnectionState.CONNECTING } -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }
    }
}
