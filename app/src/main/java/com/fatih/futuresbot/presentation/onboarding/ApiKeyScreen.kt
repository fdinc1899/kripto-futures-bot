package com.fatih.futuresbot.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.presentation.common.ModeBadge
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.theme.TradeColors

@Composable
fun ApiKeyScreen(container: AppContainer, onSkip: () -> Unit) {
    val vm: ApiKeyViewModel = viewModel(factory = ApiKeyViewModel.factory(container.credentialStore))
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Futures Bot",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                ModeBadge(TradingMode.TESTNET)
            }

            Text(
                text = "Binance Futures DEMO/Testnet API anahtarını gir. Anahtarlar Android " +
                    "Keystore ile şifrelenir ve yalnızca bu cihazda saklanır.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard {
                Text("Güvenlik", fontWeight = FontWeight.Bold, color = TradeColors.Accent)
                Text(
                    text = "• Sadece Futures işlem iznini aç.\n" +
                        "• Withdraw (para çekme) iznini ASLA açma.\n" +
                        "• Anahtarı kimseyle paylaşma, ekran görüntüsüne düşürme.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = vm::onApiKeyChange,
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiSecret,
                onValueChange = vm::onSecretChange,
                label = { Text("Secret Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = vm::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (state.saving) "Kaydediliyor…" else "Şifreli kaydet", fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Anahtarsız demo ile devam et")
            }
        }
    }
}
