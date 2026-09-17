package com.fatih.futuresbot.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fatih.futuresbot.security.ApiCredentials
import com.fatih.futuresbot.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiKeyUiState(
    val apiKey: String = "",
    val apiSecret: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

class ApiKeyViewModel(private val store: CredentialStore) : ViewModel() {

    private val _state = MutableStateFlow(ApiKeyUiState())
    val state: StateFlow<ApiKeyUiState> = _state.asStateFlow()

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value.trim(), error = null) }
    }

    fun onSecretChange(value: String) {
        _state.update { it.copy(apiSecret = value.trim(), error = null) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val error = when {
            !KEY_REGEX.matches(s.apiKey) -> "API Key geçersiz görünüyor (32–128 harf/rakam olmalı)."
            !KEY_REGEX.matches(s.apiSecret) -> "Secret geçersiz görünüyor (32–128 harf/rakam olmalı)."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.save(ApiCredentials(s.apiKey, s.apiSecret)) }
                .onSuccess { _state.value = ApiKeyUiState() } // form bellekten temizlenir
                .onFailure { e ->
                    _state.update {
                        it.copy(saving = false, error = "Kaydedilemedi: ${e.javaClass.simpleName}")
                    }
                }
        }
    }

    companion object {
        private val KEY_REGEX = Regex("^[A-Za-z0-9]{32,128}$")

        fun factory(store: CredentialStore) = viewModelFactory {
            initializer { ApiKeyViewModel(store) }
        }
    }
}
