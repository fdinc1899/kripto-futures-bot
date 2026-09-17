package com.fatih.futuresbot.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.data.settings.RiskSettingsStore
import com.fatih.futuresbot.domain.model.RiskSettings
import com.fatih.futuresbot.trading.ConnectionTestStep
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
) : ViewModel() {

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
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    tester = container.connectionTester,
                    serverHost = container.exchangeClient.endpointLabel,
                    riskStore = container.riskSettingsStore,
                )
            }
        }
    }
}
