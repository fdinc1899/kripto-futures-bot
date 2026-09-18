package com.fatih.futuresbot.data.history

import android.content.Context
import com.fatih.futuresbot.domain.model.TradeRecord
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** İşlem geçmişi cihazda JSON dosyasında tutulur. */
class TradeHistoryStore(context: Context, scope: CoroutineScope) {

    private val file = File(context.filesDir, "trades.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val _records = MutableStateFlow<List<TradeRecord>>(emptyList())
    val records: StateFlow<List<TradeRecord>> = _records.asStateFlow()

    init {
        scope.launch { _records.value = read() }
    }

    suspend fun add(record: TradeRecord) {
        mutex.withLock {
            val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
            _records.value = updated
            write(updated)
        }
    }

    suspend fun update(id: String, block: (TradeRecord) -> TradeRecord) {
        mutex.withLock {
            val updated = _records.value.map { if (it.id == id) block(it) else it }
            _records.value = updated
            write(updated)
        }
    }

    suspend fun clear() {
        mutex.withLock {
            _records.value = emptyList()
            write(emptyList())
        }
    }

    private suspend fun read(): List<TradeRecord> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<TradeRecord>>(file.readText()) }
                .getOrDefault(emptyList())
        }
    }

    private suspend fun write(records: List<TradeRecord>) {
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(json.encodeToString(records)) }
        }
    }

    private companion object {
        const val MAX_RECORDS = 500
    }
}
