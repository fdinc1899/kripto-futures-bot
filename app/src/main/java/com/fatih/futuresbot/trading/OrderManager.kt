package com.fatih.futuresbot.trading

import com.fatih.futuresbot.BuildConfig
import com.fatih.futuresbot.domain.model.ConditionalOrderRequest
import com.fatih.futuresbot.domain.model.ConnectionState
import com.fatih.futuresbot.domain.model.ExchangeEnvironment
import com.fatih.futuresbot.domain.model.ExchangeError
import com.fatih.futuresbot.domain.model.ExchangeResult
import com.fatih.futuresbot.domain.model.NewOrderRequest
import com.fatih.futuresbot.domain.model.OrderInfo
import com.fatih.futuresbot.domain.model.OrderType
import com.fatih.futuresbot.domain.model.PositionSide
import com.fatih.futuresbot.domain.model.TradeOrigin
import com.fatih.futuresbot.domain.model.TradeRecord
import com.fatih.futuresbot.domain.model.TradeStatus
import com.fatih.futuresbot.data.history.TradeHistoryStore
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.domain.model.RiskSettings
import com.fatih.futuresbot.domain.model.SizingMode
import com.fatih.futuresbot.domain.repository.AccountRepository
import com.fatih.futuresbot.notifications.Notifier
import java.time.LocalDate
import java.time.ZoneId
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OrderIntent(
    val symbol: String,
    val side: PositionSide,
    val type: OrderType,
    val limitPrice: Double?,
    val leverage: Int,
    /** Miktar nasıl hesaplanacak: risk yüzdesinden mi, marjinden mi */
    val sizing: SizingMode,
    val riskPercent: Double?,
    val marginUsdt: Double,
    /** Giriş fiyatına göre fiyat değişim yüzdesi */
    val stopLossPercent: Double,
    val takeProfitPercent: Double?,
    val origin: TradeOrigin = TradeOrigin.MANUAL,
    /** İşlemin açılma nedeni (bot sinyali vb.) */
    val reason: String = "",
    /** Sinyal anındaki indikatör değerleri */
    val signals: List<String> = emptyList(),
)

data class OrderPreview(
    val intent: OrderIntent,
    val entryPrice: BigDecimal,
    val markPrice: Double,
    val quantity: BigDecimal,
    val notional: Double,
    val requiredMargin: Double,
    val stopLossPrice: BigDecimal,
    val takeProfitPrice: BigDecimal?,
    val estimatedLoss: Double,
    val estimatedProfit: Double?,
    val riskReward: Double?,
    val estimatedLiquidation: Double,
    val availableBalance: Double,
    val riskAmountLimit: Double,
    val dailyLossPercent: Double,
    val openPositions: Int,
    val trailingEnabled: Boolean,
    val warnings: List<String>,
    val clientOrderId: String,
    val createdAt: Long,
)

sealed interface PreviewResult {
    data class Ready(val preview: OrderPreview) : PreviewResult
    data class Rejected(val reasons: List<String>) : PreviewResult
}

data class StepLog(val text: String, val ok: Boolean)

sealed interface ActionResult {
    val steps: List<StepLog>
    val message: String

    data class Success(override val steps: List<StepLog>, override val message: String) : ActionResult
    data class Failure(override val steps: List<StepLog>, override val message: String) : ActionResult
}

private sealed interface PlaceOutcome {
    data class Placed(val order: OrderInfo) : PlaceOutcome
    data class Rejected(val error: ExchangeError) : PlaceOutcome
    data class Unknown(val error: ExchangeError) : PlaceOutcome
}

/**
 * Tüm emir akışının tek kapısı. Kurallar:
 *  - Stop-Loss olmadan pozisyon açılmaz; SL yerleşmezse pozisyon hemen kapatılır.
 *  - Aynı paritede ikinci pozisyon / bekleyen emir varken yeni emir gönderilmez.
 *  - Sonucu belirsiz emir TEKRAR GÖNDERİLMEZ; önce borsadan clientOrderId ile sorgulanır.
 *  - Her emirden sonra gerçek durum borsadan doğrulanır.
 *  - İşlemler kilit altında ve iptal edilemez bağlamda yürür (yarıda kalmaz).
 */
class OrderManager(
    private val client: ExchangeClient,
    private val accountRepository: AccountRepository,
    private val guard: TradingGuard,
    private val planStore: ProtectionPlanStore,
    private val riskStore: RiskSettingsStore,
    private val historyStore: TradeHistoryStore,
    private val notifier: Notifier,
) {
    private val mutex = Mutex()
    @Volatile private var lastSubmitKey: String? = null
    @Volatile private var lastSubmitAt = 0L

    private val _lastEvent = MutableStateFlow<String?>(null)
    /** Arka planda (limit emir dolunca) olan son olay. */
    val lastEvent: StateFlow<String?> = _lastEvent.asStateFlow()

    // ================================================================ Önizleme

    suspend fun preview(intent: OrderIntent): PreviewResult {
        val settings = riskStore.settings.value
        val reasons = mutableListOf<String>()
        reasons.addAll(commonBlockers())

        val maxLeverage = min(MAX_LEVERAGE, settings.maxLeverage)
        if (intent.leverage !in 1..maxLeverage) {
            reasons.add("Kaldıraç 1–${maxLeverage}x arasında olmalı (risk ayarındaki üst sınır).")
        }
        val riskPercent = intent.riskPercent
        when (intent.sizing) {
            SizingMode.RISK -> when {
                riskPercent == null || riskPercent <= 0.0 -> reasons.add("İşlem riski yüzdesi girilmeli.")
                riskPercent > settings.riskPerTradePercent -> reasons.add(
                    "İşlem riski, ayarlardaki üst sınırı (%${num(settings.riskPerTradePercent)}) aşıyor."
                )
            }
            SizingMode.MARGIN -> if (intent.marginUsdt <= 0.0) {
                reasons.add("Marjin tutarı 0'dan büyük olmalı.")
            }
        }
        if (intent.stopLossPercent <= 0.0) reasons.add("Stop-Loss zorunlu; 0'dan büyük bir yüzde gir.")
        if (intent.stopLossPercent >= 50.0) reasons.add("Stop-Loss %50'den küçük olmalı.")
        val tpPercent = intent.takeProfitPercent
        if (tpPercent != null && tpPercent <= 0.0) reasons.add("Take-Profit 0'dan büyük olmalı (istemiyorsan boş bırak).")
        val limitPrice = intent.limitPrice
        if (intent.type == OrderType.LIMIT && (limitPrice == null || limitPrice <= 0.0)) {
            reasons.add("Limit fiyatı girilmeli.")
        }
        if (reasons.isNotEmpty()) return PreviewResult.Rejected(reasons)

        val rules = when (val r = client.symbolRules(intent.symbol)) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return rejected("Parite kuralları alınamadı: ${r.error.userMessage}")
        }
        if (rules.status != "TRADING") {
            return rejected("${intent.symbol} şu an işleme kapalı (${rules.status}).")
        }
        val mark = when (val r = client.markPrice(intent.symbol)) {
            is ExchangeResult.Ok -> r.value.markPrice
            is ExchangeResult.Err -> return rejected("Güncel fiyat alınamadı: ${r.error.userMessage}")
        }
        val balance = when (val r = client.balance()) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return rejected("Bakiye alınamadı: ${r.error.userMessage}")
        }
        val positions = when (val r = client.positions()) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return rejected("Pozisyonlar okunamadı: ${r.error.userMessage}")
        }
        val dailyPnl = when (val r = client.realizedPnlSince(startOfTodayMs())) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return rejected("Günlük PNL okunamadı: ${r.error.userMessage}")
        }

        val warnings = mutableListOf<String>()
        reasons.addAll(riskBlockers(settings, balance.walletBalance, dailyPnl, positions.size))
        val dailyLossPercent = RiskEngine.dailyLossPercent(dailyPnl, balance.walletBalance)

        val isLong = intent.side == PositionSide.LONG
        val isMarket = intent.type == OrderType.MARKET
        val rawEntry = if (isMarket) mark else (limitPrice ?: 0.0)
        val entry = rawEntry.toDecimal().roundToStep(rules.tickSize)
        if (entry.signum() <= 0) return rejected("Giriş fiyatı geçersiz.")
        val entryD = entry.toDouble()

        if (!isMarket) {
            if (isLong && entryD > mark) warnings.add("Limit fiyatı piyasanın üstünde: emir hemen dolabilir.")
            if (!isLong && entryD < mark) warnings.add("Limit fiyatı piyasanın altında: emir hemen dolabilir.")
            val distance = abs(entryD - mark) / mark * 100.0
            if (distance > 10.0) warnings.add("Limit fiyatı piyasadan %${num(distance)} uzakta.")
            warnings.add("Limit emir dolduğunda SL/TP, uygulama açıkken otomatik eklenir.")
        }

        // Stop-Loss / Take-Profit fiyatları (miktar bunlara göre hesaplanır)
        val slFactor = intent.stopLossPercent / 100.0
        val slRaw = if (isLong) entryD * (1 - slFactor) else entryD * (1 + slFactor)
        val stopLoss = slRaw.toDecimal().roundToStep(rules.tickSize)
        val takeProfit = tpPercent?.let { p ->
            val f = p / 100.0
            val raw = if (isLong) entryD * (1 + f) else entryD * (1 - f)
            raw.toDecimal().roundToStep(rules.tickSize)
        }
        val slD = stopLoss.toDouble()
        if (stopLoss.signum() <= 0) return rejected("Stop-Loss fiyatı geçersiz.")
        if (isLong && slD >= mark) reasons.add("Stop-Loss mevcut fiyatın altında olmalı (aksi halde hemen tetiklenir).")
        if (!isLong && slD <= mark) reasons.add("Stop-Loss mevcut fiyatın üstünde olmalı (aksi halde hemen tetiklenir).")
        val tpD = takeProfit?.toDouble()
        if (tpD != null) {
            if (tpD <= 0.0) reasons.add("Take-Profit fiyatı geçersiz.")
            if (isLong && tpD <= mark) reasons.add("Take-Profit mevcut fiyatın üstünde olmalı.")
            if (!isLong && tpD >= mark) reasons.add("Take-Profit mevcut fiyatın altında olmalı.")
        }
        if (reasons.isNotEmpty()) return PreviewResult.Rejected(reasons)

        // Miktar: risk moduysa SL mesafesinden, marjin moduysa marjin × kaldıraçtan
        val riskLimit = RiskEngine.maxRiskAmount(settings, balance.walletBalance)
        val step = if (isMarket) rules.marketStepSize else rules.stepSize
        val minQty = if (isMarket) rules.marketMinQty else rules.minQty
        val maxQty = if (isMarket) rules.marketMaxQty else rules.maxQty
        val targetQty = when (intent.sizing) {
            SizingMode.RISK -> {
                val riskAmount = balance.walletBalance * (riskPercent ?: 0.0) / 100.0
                RiskEngine.quantityForRisk(riskAmount, entryD, slD)
            }
            SizingMode.MARGIN -> intent.marginUsdt * intent.leverage / entryD
        }
        val quantity = targetQty.toDecimal().floorToStep(step)
        if (quantity.signum() <= 0 || quantity < minQty) {
            reasons.add(
                "Miktar çok küçük (en az ${minQty.toPlainString()}). " +
                    if (intent.sizing == SizingMode.RISK) {
                        "Risk yüzdesini artır ya da Stop-Loss'u yaklaştır."
                    } else {
                        "Marjini veya kaldıracı artır."
                    }
            )
        }
        if (maxQty.signum() > 0 && quantity > maxQty) {
            reasons.add("Miktar üst sınırı aşıyor (en fazla ${maxQty.toPlainString()}).")
        }
        val qtyD = quantity.toDouble()
        val notional = qtyD * entryD
        if (rules.minNotional.signum() > 0 && notional < rules.minNotional.toDouble()) {
            reasons.add("Emir büyüklüğü minimumun altında (en az ${rules.minNotional.toPlainString()} USDT).")
        }
        val requiredMargin = notional / intent.leverage
        if (requiredMargin * FEE_BUFFER > balance.availableBalance) {
            reasons.add(
                "Yetersiz bakiye: gerekli ≈ ${num(requiredMargin * FEE_BUFFER)} USDT, " +
                    "kullanılabilir ${num(balance.availableBalance)} USDT."
            )
        }

        val liquidation = estimateLiquidation(entryD, intent.leverage, isLong)
        if (isLong && slD <= liquidation) {
            reasons.add("Stop-Loss tahmini likidasyon fiyatının altında kalıyor. Kaldıracı düşür veya SL'i yaklaştır.")
        }
        if (!isLong && slD >= liquidation) {
            reasons.add("Stop-Loss tahmini likidasyon fiyatının üstünde kalıyor. Kaldıracı düşür veya SL'i yaklaştır.")
        }

        val loss = qtyD * abs(entryD - slD)
        val profit = tpD?.let { qtyD * abs(it - entryD) }
        val riskReward = if (profit != null && loss > 0.0) profit / loss else null

        // İşlem başına maksimum risk kuralı (şartname madde 6)
        if (loss > riskLimit * RISK_TOLERANCE && riskLimit > 0.0) {
            reasons.add(
                "İşlem riski ${num(loss)} USDT, üst sınır ${num(riskLimit)} USDT " +
                    "(bakiyenin %${num(settings.riskPerTradePercent)}'i). Miktarı veya SL mesafesini küçült."
            )
        }
        if (riskReward != null && riskReward < settings.minRiskReward) {
            reasons.add(
                "Risk/Ödül ${num(riskReward)}, ayarlanan alt sınırın (${num(settings.minRiskReward)}) altında."
            )
        }
        if (reasons.isNotEmpty()) return PreviewResult.Rejected(reasons)

        if (takeProfit == null && !settings.trailingStopEnabled) {
            warnings.add("Take-Profit yok: pozisyon yalnızca Stop-Loss ile kapanır.")
        }
        if (settings.trailingStopEnabled) {
            warnings.add("Trailing stop açık (%${num(settings.trailingCallbackPercent)}): TP yerine trailing kullanılır.")
        }
        if (dailyLossPercent > 0.0) {
            warnings.add(
                "Bugünkü zarar %${num(dailyLossPercent)} · günlük limit %${num(settings.maxDailyLossPercent)}."
            )
        }

        return PreviewResult.Ready(
            OrderPreview(
                intent = intent,
                entryPrice = entry,
                markPrice = mark,
                quantity = quantity,
                notional = notional,
                requiredMargin = requiredMargin,
                stopLossPrice = stopLoss,
                takeProfitPrice = takeProfit,
                estimatedLoss = loss,
                estimatedProfit = profit,
                riskReward = riskReward,
                estimatedLiquidation = liquidation,
                availableBalance = balance.availableBalance,
                riskAmountLimit = riskLimit,
                dailyLossPercent = dailyLossPercent,
                openPositions = positions.size,
                trailingEnabled = settings.trailingStopEnabled,
                warnings = warnings,
                clientOrderId = newClientId("e"),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** Günlük zarar limiti ve maksimum açık pozisyon kontrolü. */
    private fun riskBlockers(
        settings: RiskSettings,
        walletBalance: Double,
        dailyRealizedPnl: Double,
        openPositions: Int,
    ): List<String> {
        val out = mutableListOf<String>()
        if (RiskEngine.dailyLimitReached(settings, dailyRealizedPnl, walletBalance)) {
            out.add(
                "Günlük zarar limiti doldu (bugün %" +
                    num(RiskEngine.dailyLossPercent(dailyRealizedPnl, walletBalance)) +
                    ", limit %${num(settings.maxDailyLossPercent)}). Bugün yeni işlem açılmaz."
            )
        }
        if (openPositions >= settings.maxOpenPositions) {
            out.add("Maksimum açık pozisyon sayısına ulaşıldı (${settings.maxOpenPositions}).")
        }
        return out
    }

    // ================================================================ Gönderim

    suspend fun submit(preview: OrderPreview): ActionResult {
        return withContext(NonCancellable) {
            mutex.withLock { doSubmit(preview) }
        }
    }

    private suspend fun doSubmit(preview: OrderPreview): ActionResult {
        val steps = mutableListOf<StepLog>()
        val intent = preview.intent
        val symbol = intent.symbol
        val isLong = intent.side == PositionSide.LONG

        val blockers = commonBlockers()
        if (blockers.isNotEmpty()) return failure(steps, blockers.joinToString(" "))
        if (System.currentTimeMillis() - preview.createdAt > PREVIEW_TTL_MS) {
            return failure(steps, "Önizleme süresi doldu (30 sn). Yeniden önizle.")
        }
        val key = intentKey(intent)
        val now = System.currentTimeMillis()
        if (key == lastSubmitKey && now - lastSubmitAt < DUPLICATE_WINDOW_MS) {
            return failure(steps, "Aynı emir az önce gönderildi; tekrar gönderim engellendi.")
        }
        steps.ok("Güvenlik kontrolleri geçti (TESTNET)")

        when (val r = client.isHedgeMode()) {
            is ExchangeResult.Ok -> if (r.value) {
                return failure(steps, "Hesap Hedge modunda. Bu uygulama yalnızca One-way modunu destekler.")
            }
            is ExchangeResult.Err -> return failure(steps, "Pozisyon modu okunamadı: ${r.error.userMessage}")
        }
        steps.ok("Pozisyon modu: One-way")

        val positions = when (val r = client.positions()) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return failure(steps, "Pozisyonlar okunamadı: ${r.error.userMessage}")
        }
        if (positions.any { it.symbol == symbol }) {
            return failure(steps, "$symbol için zaten açık pozisyon var. Önce mevcut pozisyonu kapat.")
        }
        val pending = when (val r = client.openOrders(symbol)) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return failure(steps, "Açık emirler okunamadı: ${r.error.userMessage}")
        }
        if (pending.isNotEmpty()) {
            return failure(steps, "$symbol için bekleyen ${pending.size} emir var. Önce Orders ekranından iptal et.")
        }
        val pendingAlgo = when (val r = client.openConditionalOrders(symbol)) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return failure(steps, "Koşullu emirler okunamadı: ${r.error.userMessage}")
        }
        if (pendingAlgo.isNotEmpty()) {
            return failure(steps, "$symbol için bekleyen ${pendingAlgo.size} SL/TP emri var. Önce Orders ekranından iptal et.")
        }
        steps.ok("$symbol için açık pozisyon/emir yok")

        val balance = when (val r = client.balance()) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return failure(steps, "Bakiye okunamadı: ${r.error.userMessage}")
        }
        if (preview.requiredMargin * FEE_BUFFER > balance.availableBalance) {
            return failure(steps, "Yetersiz bakiye (kullanılabilir ${num(balance.availableBalance)} USDT).")
        }
        steps.ok("Bakiye yeterli")

        val settings = riskStore.settings.value
        val dailyPnl = when (val r = client.realizedPnlSince(startOfTodayMs())) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> return failure(steps, "Günlük PNL okunamadı: ${r.error.userMessage}")
        }
        val riskIssues = riskBlockers(settings, balance.walletBalance, dailyPnl, positions.size)
        if (riskIssues.isNotEmpty()) return failure(steps, riskIssues.joinToString(" "))
        val riskLimit = RiskEngine.maxRiskAmount(settings, balance.walletBalance)
        if (riskLimit > 0.0 && preview.estimatedLoss > riskLimit * RISK_TOLERANCE) {
            return failure(steps, "İşlem riski üst sınırı aşıyor (${num(preview.estimatedLoss)} > ${num(riskLimit)} USDT).")
        }
        steps.ok(
            "Risk kuralları: risk ${num(preview.estimatedLoss)}/${num(riskLimit)} USDT · " +
                "pozisyon ${positions.size}/${settings.maxOpenPositions} · günlük zarar %${num(RiskEngine.dailyLossPercent(dailyPnl, balance.walletBalance))}"
        )

        val mark = when (val r = client.markPrice(symbol)) {
            is ExchangeResult.Ok -> r.value.markPrice
            is ExchangeResult.Err -> return failure(steps, "Güncel fiyat alınamadı: ${r.error.userMessage}")
        }
        val drift = abs(mark - preview.markPrice) / preview.markPrice * 100.0
        if (drift > MAX_PRICE_DRIFT_PERCENT) {
            return failure(steps, "Fiyat önizlemeden bu yana %${num(drift)} değişti. Yeniden önizle.")
        }
        val slD = preview.stopLossPrice.toDouble()
        if ((isLong && slD >= mark) || (!isLong && slD <= mark)) {
            return failure(steps, "Stop-Loss artık mevcut fiyatın yanlış tarafında. Yeniden önizle.")
        }
        steps.ok("Fiyat kontrolü (mark ${num(mark)})")

        when (val cfg = client.symbolConfig(symbol)) {
            is ExchangeResult.Ok -> if (cfg.value.leverage != intent.leverage) {
                when (val set = client.setLeverage(symbol, intent.leverage)) {
                    is ExchangeResult.Ok -> if (set.value != intent.leverage) {
                        return failure(steps, "Kaldıraç ayarlanamadı (borsa ${set.value}x döndürdü).")
                    }
                    is ExchangeResult.Err -> return failure(steps, "Kaldıraç ayarlanamadı: ${set.error.userMessage}")
                }
            }
            is ExchangeResult.Err -> return failure(steps, "Kaldıraç okunamadı: ${cfg.error.userMessage}")
        }
        steps.ok("Kaldıraç ${intent.leverage}x")

        lastSubmitKey = key
        lastSubmitAt = System.currentTimeMillis()

        val request = NewOrderRequest(
            symbol = symbol,
            side = if (isLong) "BUY" else "SELL",
            type = intent.type,
            quantity = preview.quantity,
            price = if (intent.type == OrderType.LIMIT) preview.entryPrice else null,
            reduceOnly = false,
            clientOrderId = preview.clientOrderId,
        )
        val placed = when (val outcome = placeWithVerification(request)) {
            is PlaceOutcome.Placed -> outcome.order
            is PlaceOutcome.Rejected -> return failure(steps, "Emir reddedildi: ${outcome.error.userMessage}")
            is PlaceOutcome.Unknown -> return failure(
                steps,
                "Emir durumu doğrulanamadı (${outcome.error.userMessage}). Orders ve Positions " +
                    "ekranlarını kontrol et; emin olmadan tekrar gönderme.",
            )
        }
        steps.ok("Emir borsaya iletildi (#${placed.orderId})")

        val order = verifyOrder(symbol, request.clientOrderId, placed, intent.type)
        steps.ok("Borsadan doğrulandı: ${order.status}, dolan ${qtyText(order.executedQty)}")

        return when (intent.type) {
            OrderType.MARKET -> {
                if (order.status != "FILLED") {
                    if (order.status == "NEW" || order.status == "PARTIALLY_FILLED") {
                        client.cancelOrder(symbol, order.orderId)
                    }
                    if (order.executedQty <= 0.0) {
                        return failure(steps, "Market emri dolmadı (${order.status}).")
                    }
                    steps.add(StepLog("Emir kısmen doldu; kalan kısım iptal edildi.", false))
                }
                steps.ok("Giriş: ${qtyText(order.executedQty)} @ ${num(order.avgPrice)}")
                recordTrade(preview, order)
                protectOrRollback(
                    steps,
                    symbol,
                    intent.side,
                    preview.stopLossPrice,
                    preview.takeProfitPrice,
                    order.executedQty.toDecimal(),
                )
            }
            OrderType.LIMIT -> when (order.status) {
                "FILLED", "PARTIALLY_FILLED" -> {
                    steps.ok("Limit emir doldu (${order.status})")
                    recordTrade(preview, order)
                    protectOrRollback(
                        steps,
                        symbol,
                        intent.side,
                        preview.stopLossPrice,
                        preview.takeProfitPrice,
                        order.executedQty.toDecimal(),
                    )
                }
                "NEW" -> {
                    planStore.save(
                        ProtectionPlan(
                            symbol = symbol,
                            side = intent.side,
                            stopLoss = preview.stopLossPrice.toPlainString(),
                            takeProfit = preview.takeProfitPrice?.toPlainString(),
                            entryClientOrderId = request.clientOrderId,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    steps.ok("Limit emir defterde bekliyor; dolunca SL/TP otomatik eklenecek")
                    ActionResult.Success(steps.toList(), "Limit emir yerleştirildi")
                }
                else -> failure(steps, "Limit emir aktif değil (${order.status}).")
            }
        }
    }

    // ================================================================ Kapatma / iptal

    suspend fun closePosition(symbol: String): ActionResult {
        return withContext(NonCancellable) {
            mutex.withLock {
                val steps = mutableListOf<StepLog>()
                if (closePositionInternal(symbol, steps)) {
                    ActionResult.Success(steps.toList(), "$symbol pozisyonu kapatıldı")
                } else {
                    ActionResult.Failure(steps.toList(), "$symbol pozisyonu kapatılamadı — tekrar dene")
                }
            }
        }
    }

    suspend fun closeAllPositions(): ActionResult {
        return withContext(NonCancellable) {
            mutex.withLock {
                val steps = mutableListOf<StepLog>()
                val positions = when (val r = client.positions()) {
                    is ExchangeResult.Ok -> r.value
                    is ExchangeResult.Err -> {
                        steps.add(StepLog("Pozisyonlar okunamadı: ${r.error.userMessage}", false))
                        null
                    }
                }
                if (positions == null) {
                    ActionResult.Failure(steps.toList(), "Pozisyonlar okunamadı")
                } else {
                    var allOk = true
                    for (symbol in positions.map { it.symbol }.distinct()) {
                        if (!closePositionInternal(symbol, steps)) allOk = false
                    }
                    if (allOk) {
                        ActionResult.Success(steps.toList(), "Tüm pozisyonlar kapatıldı (${positions.size})")
                    } else {
                        ActionResult.Failure(steps.toList(), "Bazı pozisyonlar kapatılamadı — tekrar dene")
                    }
                }
            }
        }
    }

    suspend fun cancelOrder(symbol: String, id: Long, isConditional: Boolean): ActionResult {
        return withContext(NonCancellable) {
            mutex.withLock {
                val steps = mutableListOf<StepLog>()
                val r = if (isConditional) client.cancelConditionalOrder(id) else client.cancelOrder(symbol, id)
                when (r) {
                    is ExchangeResult.Ok -> {
                        steps.ok("Emir iptal edildi (#$id)")
                        ActionResult.Success(steps.toList(), "$symbol emri iptal edildi")
                    }
                    is ExchangeResult.Err -> {
                        steps.add(StepLog("İptal başarısız: ${r.error.userMessage}", false))
                        ActionResult.Failure(steps.toList(), "İptal başarısız")
                    }
                }
            }
        }
    }

    /** Acil durdurma: yeni emirleri kilitler ve dolmamış limit girişlerini iptal eder. */
    fun emergencyStop(scope: CoroutineScope) {
        guard.stop()
        scope.launch {
            withContext(NonCancellable) {
                mutex.withLock { cancelPendingEntries() }
            }
        }
    }

    // ================================================================ Arka plan izleyici

    /** Dolan limit emirlere SL/TP ekler. Uygulama açıkken 5 sn'de bir çalışır. */
    fun startProtectionWatcher(scope: CoroutineScope, hasCredentials: StateFlow<Boolean>) {
        scope.launch {
            while (isActive) {
                delay(WATCH_INTERVAL_MS)
                if (!hasCredentials.value) continue
                val plans = planStore.all()
                if (plans.isEmpty()) continue
                for (plan in plans) {
                    try {
                        withContext(NonCancellable) {
                            mutex.withLock { checkPlan(plan) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Bir sonraki turda tekrar denenir
                    }
                }
            }
        }
    }

    private suspend fun checkPlan(plan: ProtectionPlan) {
        val order = when (val r = client.queryOrder(plan.symbol, plan.entryClientOrderId)) {
            is ExchangeResult.Ok -> r.value
            is ExchangeResult.Err -> {
                if (r.error == ExchangeError.OrderNotFound) planStore.remove(plan.symbol)
                return
            }
        }
        when (order.status) {
            "NEW" -> Unit
            "FILLED", "PARTIALLY_FILLED" -> {
                val existing = client.openConditionalOrders(plan.symbol)
                if (existing is ExchangeResult.Ok && existing.value.isNotEmpty()) {
                    planStore.remove(plan.symbol)
                    return
                }
                val stopLoss = plan.stopLoss.toBigDecimalOrNull()
                if (stopLoss == null) {
                    planStore.remove(plan.symbol)
                    return
                }
                val steps = mutableListOf<StepLog>()
                val result = protectOrRollback(
                    steps,
                    plan.symbol,
                    plan.side,
                    stopLoss,
                    plan.takeProfit?.toBigDecimalOrNull(),
                    order.executedQty.toDecimal(),
                )
                planStore.remove(plan.symbol)
                _lastEvent.value = "${plan.symbol} limit emri doldu: ${result.message}"
            }
            else -> {
                planStore.remove(plan.symbol)
                _lastEvent.value = "${plan.symbol} limit emri ${order.status}; SL/TP planı silindi"
            }
        }
    }

    private suspend fun cancelPendingEntries() {
        for (plan in planStore.all()) {
            val order = client.queryOrder(plan.symbol, plan.entryClientOrderId)
            if (order is ExchangeResult.Ok) {
                val info = order.value
                if (info.status == "NEW" || info.status == "PARTIALLY_FILLED") {
                    client.cancelOrder(plan.symbol, info.orderId)
                }
                if (info.executedQty > 0.0) {
                    // Kısmen dolmuşsa dolan kısım korumasız kalmasın
                    val stopLoss = plan.stopLoss.toBigDecimalOrNull()
                    if (stopLoss != null) {
                        protectOrRollback(
                            mutableListOf(),
                            plan.symbol,
                            plan.side,
                            stopLoss,
                            plan.takeProfit?.toBigDecimalOrNull(),
                            info.executedQty.toDecimal(),
                        )
                    }
                }
            }
            planStore.remove(plan.symbol)
        }
    }

    // ================================================================ Yardımcılar

    private fun commonBlockers(): List<String> {
        val out = mutableListOf<String>()
        if (client.environment != ExchangeEnvironment.TESTNET && !BuildConfig.REAL_TRADING_AVAILABLE) {
            out.add("Gerçek işlem modu kapalı.")
        }
        if (guard.emergencyStopped.value) {
            out.add("Acil durdurma aktif — önce Dashboard'dan sıfırla.")
        }
        if (accountRepository.connection.value != ConnectionState.CONNECTED) {
            out.add("Borsa bağlantısı güvenilir değil; bağlantı düzelince tekrar dene.")
        }
        return out
    }

    private suspend fun placeWithVerification(request: NewOrderRequest): PlaceOutcome {
        val error = when (val r = client.placeOrder(request)) {
            is ExchangeResult.Ok -> return PlaceOutcome.Placed(r.value)
            is ExchangeResult.Err -> r.error
        }
        if (!isAmbiguous(error)) return PlaceOutcome.Rejected(error)

        // Sonuç belirsiz (zaman aşımı / sunucu hatası): TEKRAR GÖNDERMEDEN borsaya sor
        var notFound = 0
        repeat(VERIFY_ATTEMPTS) {
            delay(VERIFY_DELAY_MS)
            when (val q = client.queryOrder(request.symbol, request.clientOrderId)) {
                is ExchangeResult.Ok -> return PlaceOutcome.Placed(q.value)
                is ExchangeResult.Err -> if (q.error == ExchangeError.OrderNotFound) notFound++
            }
            if (notFound >= 2) return PlaceOutcome.Rejected(error)
        }
        return PlaceOutcome.Unknown(error)
    }

    private suspend fun placeConditionalWithVerification(request: ConditionalOrderRequest): PlaceOutcome {
        val error = when (val r = client.placeConditionalOrder(request)) {
            is ExchangeResult.Ok -> return verifyConditional(request.clientAlgoId)
            is ExchangeResult.Err -> r.error
        }
        if (!isAmbiguous(error)) return PlaceOutcome.Rejected(error)
        var notFound = 0
        repeat(VERIFY_ATTEMPTS) {
            delay(VERIFY_DELAY_MS)
            when (val q = client.queryConditionalOrder(request.clientAlgoId)) {
                is ExchangeResult.Ok -> return if (q.value.status == "NEW") {
                    PlaceOutcome.Placed(dummyOrder(request.symbol))
                } else {
                    PlaceOutcome.Rejected(ExchangeError.Api(0, "Koşullu emir aktif değil (${q.value.status})"))
                }
                is ExchangeResult.Err -> if (q.error == ExchangeError.OrderNotFound) notFound++
            }
            if (notFound >= 2) return PlaceOutcome.Rejected(error)
        }
        return PlaceOutcome.Unknown(error)
    }

    /** Koşullu emrin borsada gerçekten aktif (NEW) olduğunu doğrular. */
    private suspend fun verifyConditional(clientAlgoId: String): PlaceOutcome {
        repeat(VERIFY_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(VERIFY_DELAY_MS)
            when (val q = client.queryConditionalOrder(clientAlgoId)) {
                is ExchangeResult.Ok -> return if (q.value.status == "NEW") {
                    PlaceOutcome.Placed(dummyOrder(q.value.symbol))
                } else {
                    PlaceOutcome.Rejected(ExchangeError.Api(0, "Koşullu emir aktif değil (${q.value.status})"))
                }
                is ExchangeResult.Err -> Unit
            }
        }
        return PlaceOutcome.Unknown(ExchangeError.Unknown("Koşullu emir doğrulanamadı"))
    }

    private suspend fun verifyOrder(
        symbol: String,
        clientOrderId: String,
        initial: OrderInfo,
        type: OrderType,
    ): OrderInfo {
        var current = initial
        val first = client.queryOrder(symbol, clientOrderId)
        if (first is ExchangeResult.Ok) current = first.value
        if (type == OrderType.LIMIT) return current
        repeat(FILL_POLL_ATTEMPTS) {
            if (current.status == "FILLED" || current.status in TERMINAL_STATUSES) return current
            delay(FILL_POLL_DELAY_MS)
            val q = client.queryOrder(symbol, clientOrderId)
            if (q is ExchangeResult.Ok) current = q.value
        }
        return current
    }

    /** SL yerleşirse başarı; yerleşmezse GÜVENLİK için pozisyonu hemen kapatır. */
    private suspend fun protectOrRollback(
        steps: MutableList<StepLog>,
        symbol: String,
        side: PositionSide,
        stopLoss: BigDecimal,
        takeProfit: BigDecimal?,
        positionQuantity: BigDecimal,
    ): ActionResult {
        val settings = riskStore.settings.value
        val closeSide = if (side == PositionSide.LONG) "SELL" else "BUY"
        val sl = placeConditionalWithVerification(
            ConditionalOrderRequest(symbol, closeSide, "STOP_MARKET", stopLoss, newClientId("sl"))
        )
        if (sl !is PlaceOutcome.Placed) {
            val reason = when (sl) {
                is PlaceOutcome.Rejected -> sl.error.userMessage
                is PlaceOutcome.Unknown -> "durum doğrulanamadı (${sl.error.userMessage})"
                else -> ""
            }
            steps.add(StepLog("Stop-Loss yerleştirilemedi: $reason", false))
            notifier.alert("$symbol Stop-Loss yerleştirilemedi", "Pozisyon güvenlik için kapatılıyor")
            steps.add(StepLog("GÜVENLİK: SL'siz pozisyon bırakılmaz, pozisyon kapatılıyor…", false))
            val closed = closePositionInternal(symbol, steps)
            val message = if (closed) {
                "SL yerleştirilemediği için pozisyon güvenlik amacıyla kapatıldı."
            } else {
                "SL yerleştirilemedi ve pozisyon KAPATILAMADI! Positions ekranından hemen kapat."
            }
            return ActionResult.Failure(steps.toList(), message)
        }
        steps.ok("Stop-Loss aktif @ ${stopLoss.toPlainString()} (mark fiyatı)")
        notifier.trade(
            "$symbol ${side.name} açıldı",
            "Stop-Loss ${stopLoss.toPlainString()}" +
                (takeProfit?.let { " · Take-Profit ${it.toPlainString()}" } ?: ""),
        )

        if (settings.trailingStopEnabled && positionQuantity.signum() > 0) {
            val trailingQty = quantityForStep(symbol, positionQuantity)
            val trailing = placeConditionalWithVerification(
                ConditionalOrderRequest(
                    symbol = symbol,
                    side = closeSide,
                    type = "TRAILING_STOP_MARKET",
                    triggerPrice = null,
                    clientAlgoId = newClientId("ts"),
                    closePosition = false,
                    quantity = trailingQty,
                    reduceOnly = true,
                    callbackRate = settings.trailingCallbackPercent,
                )
            )
            when (trailing) {
                is PlaceOutcome.Placed -> steps.ok("Trailing stop aktif (%${num(settings.trailingCallbackPercent)})")
                is PlaceOutcome.Rejected -> steps.add(
                    StepLog("Trailing stop yerleştirilemedi: ${trailing.error.userMessage} (SL aktif)", false)
                )
                is PlaceOutcome.Unknown -> steps.add(
                    StepLog("Trailing stop durumu doğrulanamadı; Orders ekranını kontrol et", false)
                )
            }
        } else if (takeProfit != null) {
            val tp = placeConditionalWithVerification(
                ConditionalOrderRequest(symbol, closeSide, "TAKE_PROFIT_MARKET", takeProfit, newClientId("tp"))
            )
            when (tp) {
                is PlaceOutcome.Placed -> steps.ok("Take-Profit aktif @ ${takeProfit.toPlainString()}")
                is PlaceOutcome.Rejected -> steps.add(
                    StepLog("Take-Profit yerleştirilemedi: ${tp.error.userMessage} (SL aktif, pozisyon korumalı)", false)
                )
                is PlaceOutcome.Unknown -> steps.add(
                    StepLog("Take-Profit durumu doğrulanamadı; Orders ekranını kontrol et", false)
                )
            }
        }
        planStore.remove(symbol)
        return ActionResult.Success(steps.toList(), "Pozisyon açıldı ve Stop-Loss ile korumaya alındı")
    }

    private suspend fun closePositionInternal(symbol: String, steps: MutableList<StepLog>): Boolean {
        when (val r = client.cancelAllConditionalOrders(symbol)) {
            is ExchangeResult.Ok -> steps.ok("$symbol SL/TP emirleri iptal edildi")
            is ExchangeResult.Err -> steps.add(StepLog("SL/TP iptali: ${r.error.userMessage}", false))
        }
        when (val r = client.cancelAllOrders(symbol)) {
            is ExchangeResult.Ok -> steps.ok("$symbol bekleyen emirleri iptal edildi")
            is ExchangeResult.Err -> steps.add(StepLog("Bekleyen emir iptali: ${r.error.userMessage}", false))
        }
        planStore.remove(symbol)

        val position = when (val r = client.positions()) {
            is ExchangeResult.Ok -> r.value.firstOrNull { it.symbol == symbol }
            is ExchangeResult.Err -> {
                steps.add(StepLog("Pozisyon okunamadı: ${r.error.userMessage}", false))
                return false
            }
        }
        if (position == null) {
            steps.ok("$symbol için açık pozisyon yok")
            return true
        }

        val quantity = abs(position.positionAmt).toDecimal().stripTrailingZeros()
        val request = NewOrderRequest(
            symbol = symbol,
            side = if (position.positionAmt > 0) "SELL" else "BUY",
            type = OrderType.MARKET,
            quantity = quantity,
            price = null,
            reduceOnly = true,
            clientOrderId = newClientId("c"),
        )
        when (val outcome = placeWithVerification(request)) {
            is PlaceOutcome.Placed -> {
                val done = verifyOrder(symbol, request.clientOrderId, outcome.order, OrderType.MARKET)
                steps.add(
                    StepLog("Kapatma emri: ${done.status} (${qtyText(done.executedQty)})", done.status == "FILLED")
                )
            }
            is PlaceOutcome.Rejected -> {
                steps.add(StepLog("Kapatma emri reddedildi: ${outcome.error.userMessage}", false))
                return false
            }
            is PlaceOutcome.Unknown -> {
                steps.add(StepLog("Kapatma emri durumu belirsiz: ${outcome.error.userMessage}", false))
            }
        }

        delay(CLOSE_CHECK_DELAY_MS)
        return when (val r = client.positions()) {
            is ExchangeResult.Ok -> {
                val stillOpen = r.value.any { it.symbol == symbol }
                if (stillOpen) {
                    steps.add(StepLog("$symbol pozisyonu hâlâ açık görünüyor", false))
                } else {
                    steps.ok("$symbol pozisyonu kapandı (borsadan doğrulandı)")
                }
                !stillOpen
            }
            is ExchangeResult.Err -> {
                steps.add(StepLog("Kapanış doğrulanamadı: ${r.error.userMessage}", false))
                false
            }
        }
    }

    /** Açılan pozisyonu işlem geçmişine yazar. */
    private suspend fun recordTrade(preview: OrderPreview, order: OrderInfo) {
        val intent = preview.intent
        val entry = if (order.avgPrice > 0.0) order.avgPrice else preview.entryPrice.toDouble()
        val quantity = if (order.executedQty > 0.0) order.executedQty else preview.quantity.toDouble()
        val notional = entry * quantity
        runCatching {
            historyStore.add(
                TradeRecord(
                    id = preview.clientOrderId,
                    symbol = intent.symbol,
                    side = intent.side.name,
                    origin = intent.origin.name,
                    openTime = if (order.time > 0L) order.time else System.currentTimeMillis(),
                    entryPrice = entry,
                    quantity = quantity,
                    leverage = intent.leverage,
                    notional = notional,
                    margin = if (intent.leverage > 0) notional / intent.leverage else notional,
                    stopLoss = preview.stopLossPrice.toDouble(),
                    takeProfit = preview.takeProfitPrice?.toDouble(),
                    status = TradeStatus.OPEN.name,
                    reason = intent.reason.ifBlank {
                        if (intent.origin == TradeOrigin.BOT) "Bot sinyali" else "Elle açıldı"
                    },
                    signals = intent.signals,
                )
            )
        }
    }

    /** Trailing stop için miktarı parite adımına yuvarlar. */
    private suspend fun quantityForStep(symbol: String, quantity: BigDecimal): BigDecimal {
        val rules = client.symbolRules(symbol)
        return if (rules is ExchangeResult.Ok) {
            quantity.floorToStep(rules.value.stepSize)
        } else {
            quantity
        }
    }

    private fun isAmbiguous(error: ExchangeError): Boolean =
        error is ExchangeError.Timeout ||
            error is ExchangeError.ConnectionFailed ||
            error is ExchangeError.Server ||
            error is ExchangeError.Unknown

    private fun estimateLiquidation(entry: Double, leverage: Int, isLong: Boolean): Double {
        val initialMargin = 1.0 / leverage
        return if (isLong) {
            entry * (1 - initialMargin + MAINT_MARGIN_RATE)
        } else {
            entry * (1 + initialMargin - MAINT_MARGIN_RATE)
        }
    }

    private fun intentKey(i: OrderIntent): String =
        listOf(i.symbol, i.side, i.type, i.limitPrice, i.leverage, i.marginUsdt).joinToString("|")

    private fun dummyOrder(symbol: String): OrderInfo = OrderInfo(
        symbol = symbol, orderId = 0L, clientOrderId = "", side = "", type = "",
        status = "NEW", price = 0.0, avgPrice = 0.0, origQty = 0.0, executedQty = 0.0,
        reduceOnly = true, time = 0L,
    )

    private fun rejected(reason: String): PreviewResult = PreviewResult.Rejected(listOf(reason))

    private fun failure(steps: MutableList<StepLog>, text: String): ActionResult {
        steps.add(StepLog(text, false))
        return ActionResult.Failure(steps.toList(), text)
    }

    private fun MutableList<StepLog>.ok(text: String) {
        add(StepLog(text, true))
    }

    private fun num(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun qtyText(v: Double): String = v.toDecimal().stripTrailingZeros().toPlainString()

    companion object {
        const val MAX_LEVERAGE = 20
        private const val MAINT_MARGIN_RATE = 0.004
        private const val FEE_BUFFER = 1.02
        private const val PREVIEW_TTL_MS = 30_000L
        private const val DUPLICATE_WINDOW_MS = 15_000L
        private const val MAX_PRICE_DRIFT_PERCENT = 1.0
        private const val FILL_POLL_ATTEMPTS = 5
        private const val FILL_POLL_DELAY_MS = 1_000L
        private const val VERIFY_ATTEMPTS = 3
        private const val VERIFY_DELAY_MS = 1_500L
        private const val WATCH_INTERVAL_MS = 5_000L
        private const val CLOSE_CHECK_DELAY_MS = 700L
        private const val RISK_TOLERANCE = 1.05

        fun startOfTodayMs(): Long =
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        private val TERMINAL_STATUSES = setOf("CANCELED", "EXPIRED", "REJECTED", "EXPIRED_IN_MATCH")

        fun newClientId(prefix: String): String =
            "fb${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
    }
}
