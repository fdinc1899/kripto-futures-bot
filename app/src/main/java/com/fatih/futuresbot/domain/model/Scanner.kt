package com.fatih.futuresbot.domain.model

enum class ScanMode {
    /** Hacimde ilk N parite */
    TOP_VOLUME,
    /** Hacim sıçraması + momentum sıralaması */
    MOMENTUM,
    /** Yalnızca kullanıcının listesi */
    WATCHLIST,
    /** Tarama + kullanıcı listesi birlikte */
    HYBRID,
}

data class ScanCandidate(
    val symbol: String,
    val changePercent: Double,
    val quoteVolume: Double,
    /** Son mum hacmi / ortalama hacim */
    val volumeSpike: Double,
    /** Son bir saatlik fiyat değişimi (%) */
    val momentumPercent: Double,
    val score: Double,
    val fromWatchlist: Boolean = false,
)
