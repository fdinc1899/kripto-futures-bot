package com.fatih.futuresbot.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.trading.ConnectionTestStep
import com.fatih.futuresbot.trading.ConnectionTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val tester: ConnectionTester,
    val serverHost: String,
) : ViewModel() {

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
                )
            }
        }
    }
}
