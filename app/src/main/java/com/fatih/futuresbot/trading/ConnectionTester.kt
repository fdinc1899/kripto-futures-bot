package com.fatih.futuresbot.trading

import com.fatih.futuresbot.domain.model.ExchangeResult
import java.util.Locale

data class ConnectionTestStep(val name: String, val ok: Boolean, val detail: String)

class ConnectionTester(private val client: ExchangeClient) {

    suspend fun run(): List<ConnectionTestStep> = listOf(
        step("Sunucuya erişim (ping)", client.ping()) { "OK" },
        step("Saat senkronu", client.syncServerTime()) { "Fark: $it ms" },
        step("BTCUSDT fiyatı (herkese açık)", client.ticker24h("BTCUSDT")) {
            String.format(Locale.US, "%.2f", it.lastPrice)
        },
        step("Hesap bakiyesi (imzalı)", client.balance()) {
            String.format(Locale.US, "Cüzdan: %.2f USDT", it.walletBalance)
        },
        step("Pozisyonlar (imzalı)", client.positions()) { "${it.size} açık pozisyon" },
        step("BTCUSDT kaldıraç ayarı (imzalı)", client.symbolConfig("BTCUSDT")) {
            "${it.leverage}x · ${it.marginType}"
        },
    )

    private inline fun <T> step(
        name: String,
        result: ExchangeResult<T>,
        okText: (T) -> String,
    ): ConnectionTestStep = when (result) {
        is ExchangeResult.Ok -> ConnectionTestStep(name, true, okText(result.value))
        is ExchangeResult.Err -> ConnectionTestStep(name, false, result.error.userMessage)
    }
}
