package com.fatih.futuresbot.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.NotificationSettingsStore
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.data.settings.TradingModeStore
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.security.CredentialStore
import com.fatih.futuresbot.domain.model.RiskSettings
import com.fatih.futuresbot.trading.ConnectionTestStep
import com.fatih.futuresbot.notifications.Notifier
import com.fatih.futuresbot.trading.ConnectionTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RiskForm(
    val riskPerTrade: String = "",
    val maxDailyLoss: String = "",
    val maxOpenPositions: String = "",
    val maxLeverage: String = "",
    val minRiskReward: String = "",
    val defaultStopLoss: String = "",
    val defaultTakeProfit: String = "",
    val trailingEnabled: Boolean = false,
    val trailingCallback: String = "",
)

private fun RiskSettings.toForm(): RiskForm = RiskForm(
    riskPerTrade = num(riskPerTradePercent),
    maxDailyLoss = num(maxDailyLossPercent),
    maxOpenPositions = maxOpenPositions.toString(),
    maxLeverage = maxLeverage.toString(),
    minRiskReward = num(minRiskReward),
    defaultStopLoss = num(defaultStopLossPercent),
    defaultTakeProfit = num(defaultTakeProfitPercent),
    trailingEnabled = trailingStopEnabled,
    trailingCallback = num(trailingCallbackPercent),
)

private fun num(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

class SettingsViewModel(
    private val tester: ConnectionTester,
    val serverHost: String,
    private val riskStore: RiskSettingsStore,
    private val notificationStore: NotificationSettingsStore,
    private val notifier: Notifier,
    private val modeStore: TradingModeStore,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    /** 0: kapalı, 1: uyarı, 2: yazarak onay */
    private val _realStep = MutableStateFlow(0)
    val realStep: StateFlow<Int> = _realStep.asStateFlow()

    private val _realConfirmText = MutableStateFlow("")
    val realConfirmText: StateFlow<String> = _realConfirmText.asStateFlow()

    private val _modeMessage = MutableStateFlow<String?>(null)
    val modeMessage: StateFlow<String?> = _modeMessage.asStateFlow()

    fun startRealModeFlow() {
        _realConfirmText.value = ""
        _realStep.value = 1
    }

    fun continueRealModeFlow() {
        _realStep.value = 2
    }

    fun cancelRealModeFlow() {
        _realStep.value = 0
        _realConfirmText.value = ""
    }

    fun setRealConfirmText(value: String) {
        _realConfirmText.value = value.uppercase().take(10)
    }

    /** İkinci onay: "GERÇEK" yazılmadan etkinleşmez. */
    fun confirmRealMode() {
        if (_realConfirmText.value.trim() != CONFIRM_WORD) {
            _modeMessage.value = "Onay için $CONFIRM_WORD yazmalısın"
            return
        }
        val applied = modeStore.setMode(TradingMode.REAL)
        if (!applied) {
            _modeMessage.value = "Gerçek mod bu sürümde kapalı"
            cancelRealModeFlow()
            return
        }
        // Testnet anahtarlarıyla gerçek borsaya bağlanılmasın
        credentialStore.clear()
        cancelRealModeFlow()
        _modeMessage.value = "GERÇEK moda geçildi. Uygulamayı kapatıp yeniden aç ve " +
            "gerçek hesabın API anahtarını gir."
    }

    fun switchToTestnet() {
        modeStore.setMode(TradingMode.TESTNET)
        credentialStore.clear()
        _modeMessage.value = "TESTNET moduna geçildi. Uygulamayı kapatıp yeniden aç ve " +
            "demo API anahtarını gir."
    }

    val notificationsEnabled: StateFlow<Boolean> = notificationStore.enabled

    private val _notificationMessage = MutableStateFlow<String?>(null)
    val notificationMessage: StateFlow<String?> = _notificationMessage.asStateFlow()

    fun setNotificationsEnabled(value: Boolean) {
        notificationStore.setEnabled(value)
        _notificationMessage.value = if (value) "Bildirimler açık" else "Bildirimler kapalı"
    }

    fun sendTestNotification() {
        if (!notifier.hasPermission()) {
            _notificationMessage.value = "Bildirim izni yok — Android ayarlarından izin ver"
            return
        }
        if (!notificationStore.enabled.value) {
            _notificationMessage.value = "Önce bildirimleri aç"
            return
        }
        notifier.trade("Test bildirimi", "Bildirimler çalışıyor")
        _notificationMessage.value = "Test bildirimi gönderildi"
    }

    private val _riskForm = MutableStateFlow(riskStore.settings.value.toForm())
    val riskForm: StateFlow<RiskForm> = _riskForm.asStateFlow()

    private val _riskSaved = MutableStateFlow<String?>(null)
    val riskSaved: StateFlow<String?> = _riskSaved.asStateFlow()

    fun updateRiskForm(block: (RiskForm) -> RiskForm) {
        _riskForm.value = block(_riskForm.value)
        _riskSaved.value = null
    }

    /** Değerler kaydedilirken güvenli aralıklara sıkıştırılır. */
    fun saveRisk() {
        val f = _riskForm.value
        val current = riskStore.settings.value
        val updated = RiskSettings(
            riskPerTradePercent = f.riskPerTrade.toDoubleOrNull() ?: current.riskPerTradePercent,
            maxDailyLossPercent = f.maxDailyLoss.toDoubleOrNull() ?: current.maxDailyLossPercent,
            maxOpenPositions = f.maxOpenPositions.toIntOrNull() ?: current.maxOpenPositions,
            maxLeverage = f.maxLeverage.toIntOrNull() ?: current.maxLeverage,
            minRiskReward = f.minRiskReward.toDoubleOrNull() ?: current.minRiskReward,
            defaultStopLossPercent = f.defaultStopLoss.toDoubleOrNull() ?: current.defaultStopLossPercent,
            defaultTakeProfitPercent = f.defaultTakeProfit.toDoubleOrNull() ?: current.defaultTakeProfitPercent,
            trailingStopEnabled = f.trailingEnabled,
            trailingCallbackPercent = f.trailingCallback.toDoubleOrNull() ?: current.trailingCallbackPercent,
        )
        riskStore.update(updated)
        _riskForm.value = riskStore.settings.value.toForm()
        _riskSaved.value = "Risk ayarları kaydedildi"
    }

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _results = MutableStateFlow<List<ConnectionTestStep>>(emptyList())
    val results: StateFlow<List<ConnectionTestStep>> = _results.asStateFlow()

    fun runTest() {
        if (_testing.value) return
        _testing.value = true
        _results.value = emptyList()
        viewModelScope.launch {
            try {
                _results.value = tester.run()
            } finally {
                _testing.value = false
            }
        }
    }

    companion object {
        const val CONFIRM_WORD = "GERÇEK"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    tester = container.connectionTester,
                    serverHost = container.exchangeClient.endpointLabel,
                    riskStore = container.riskSettingsStore,
                    notificationStore = container.notificationSettingsStore,
                    notifier = container.notifier,
                    modeStore = container.tradingModeStore,
                    credentialStore = container.credentialStore,
                )
            }
        }
    }
}
