package com.fatih.futuresbot.domain.model

enum class ExchangeEnvironment { TESTNET, REAL }

data class FuturesBalance(
    val walletBalance: Double,
    val marginBalance: Double,
    val availableBalance: Double,
    val unrealizedPnl: Double,
)

data class FuturesPosition(
    val symbol: String,
    val positionSide: String,
    val positionAmt: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val unrealizedPnl: Double,
    val liquidationPrice: Double?,
    val initialMargin: Double,
    val notional: Double,
)

data class Ticker24h(
    val symbol: String,
    val lastPrice: Double,
    val priceChangePercent: Double,
    val quoteVolume: Double = 0.0,
)

data class MarkPriceInfo(
    val symbol: String,
    val markPrice: Double,
    val lastFundingRate: Double?,
    val nextFundingTime: Long?,
)

data class SymbolConfig(
    val symbol: String,
    val marginType: String,
    val leverage: Int,
)

sealed interface ExchangeResult<out T> {
    data class Ok<out T>(val value: T) : ExchangeResult<T>
    data class Err(val error: ExchangeError) : ExchangeResult<Nothing>
}

/** Şartname madde 14'teki hata durumlarının tamamı. */
sealed class ExchangeError(val userMessage: String) {
    data object NoInternet : ExchangeError("İnternet bağlantısı yok")
    data object Timeout : ExchangeError("Borsa yanıt vermedi (zaman aşımı)")
    data class ConnectionFailed(val detail: String) : ExchangeError("Bağlantı hatası ($detail)")
    data object MissingCredentials : ExchangeError("API anahtarı kayıtlı değil — Settings'ten ekle")
    data object InvalidApiKey : ExchangeError("API anahtarı geçersiz ya da Futures izni kapalı")
    data object TimestampOutOfSync : ExchangeError("Saat senkronizasyon hatası, tekrar deneniyor")
    data object InsufficientMargin : ExchangeError("Yetersiz marjin/bakiye")
    data object MinOrderSize : ExchangeError("Minimum emir büyüklüğünün altında")
    data object InvalidQuantity : ExchangeError("Geçersiz miktar veya fiyat hassasiyeti")
    data object InvalidLeverage : ExchangeError("Geçersiz kaldıraç")
    data object Maintenance : ExchangeError("Borsa bakımda")
    data object DuplicateOrder : ExchangeError("Aynı emir zaten gönderilmiş")
    data object OrderNotFound : ExchangeError("Emir borsada bulunamadı")
    data object WouldTriggerImmediately : ExchangeError("Tetik fiyatı mevcut fiyatın yanlış tarafında (hemen tetiklenir)")
    data object ReduceOnlyRejected : ExchangeError("Pozisyon azaltma emri reddedildi")
    data class RateLimited(val retryAfterSec: Long?) : ExchangeError("İstek limiti aşıldı, bekleniyor")
    data class Server(val httpCode: Int) : ExchangeError("Borsa sunucu hatası ($httpCode)")
    data class Api(val code: Int, val msg: String) : ExchangeError("Borsa hatası $code: $msg")
    data class Unknown(val detail: String) : ExchangeError("Beklenmeyen hata: $detail")
}
