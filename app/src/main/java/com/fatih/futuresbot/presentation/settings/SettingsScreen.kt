package com.fatih.futuresbot.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.common.StatRow
import com.fatih.futuresbot.presentation.theme.TradeColors

@Composable
fun SettingsScreen(container: AppContainer, onAddKeys: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val hasKeys by container.credentialStore.hasCredentials.collectAsStateWithLifecycle()
    val mode by container.tradingModeStore.mode.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val riskForm by vm.riskForm.collectAsStateWithLifecycle()
    val riskSaved by vm.riskSaved.collectAsStateWithLifecycle()
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    val notificationMessage by vm.notificationMessage.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard {
            StatRow(
                "İşlem modu",
                if (mode == TradingMode.TESTNET) "TESTNET / DEMO" else "GERÇEK",
                if (mode == TradingMode.TESTNET) TradeColors.Accent else TradeColors.Short,
            )
            StatRow("Sunucu", vm.serverHost)
            StatRow("API anahtarı", if (hasKeys) "Şifreli kayıtlı" else "Yok")
            if (!hasKeys) {
                Button(onClick = onAddKeys, modifier = Modifier.fillMaxWidth()) {
                    Text("API anahtarı ekle")
                }
            }
        }

        SectionCard {
            Text("Bağlantı testi", fontWeight = FontWeight.Bold)
            Button(
                onClick = vm::runTest,
                enabled = !testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (testing) "Test ediliyor…" else "Bağlantıyı test et")
            }
            results.forEach { step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (step.ok) "✓" else "✗",
                        color = if (step.ok) TradeColors.Long else TradeColors.Short,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp),
                    )
                    Column {
                        Text(step.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bildirimler", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Pozisyon açma/kapanma, SL/TP, bot sinyali ve bağlantı uyarıları",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = notificationsEnabled, onCheckedChange = vm::setNotificationsEnabled)
            }
            OutlinedButton(onClick = vm::sendTestNotification, modifier = Modifier.fillMaxWidth()) {
                Text("Test bildirimi gönder")
            }
            notificationMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TradeColors.Accent)
            }
        }

        SectionCard {
            Text("Risk ayarları", fontWeight = FontWeight.Bold)
            Text(
                text = "Bu sınırlar hem elle açılan emirlerde hem de bot aşamasında uygulanır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    RiskField("İşlem riski %", riskForm.riskPerTrade) { value ->
                        vm.updateRiskForm { it.copy(riskPerTrade = value) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    RiskField("Günlük zarar %", riskForm.maxDailyLoss) { value ->
                        vm.updateRiskForm { it.copy(maxDailyLoss = value) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    RiskField("Maks. pozisyon", riskForm.maxOpenPositions) { value ->
                        vm.updateRiskForm { it.copy(maxOpenPositions = value) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    RiskField("Maks. kaldıraç", riskForm.maxLeverage) { value ->
                        vm.updateRiskForm { it.copy(maxLeverage = value) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    RiskField("Min Risk/Ödül", riskForm.minRiskReward) { value ->
                        vm.updateRiskForm { it.copy(minRiskReward = value) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    RiskField("Trailing %", riskForm.trailingCallback) { value ->
                        vm.updateRiskForm { it.copy(trailingCallback = value) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    RiskField("Varsayılan SL %", riskForm.defaultStopLoss) { value ->
                        vm.updateRiskForm { it.copy(defaultStopLoss = value) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    RiskField("Varsayılan TP %", riskForm.defaultTakeProfit) { value ->
                        vm.updateRiskForm { it.copy(defaultTakeProfit = value) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trailing stop", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Açıkken Take-Profit yerine trailing stop kullanılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = riskForm.trailingEnabled,
                    onCheckedChange = { checked ->
                        vm.updateRiskForm { it.copy(trailingEnabled = checked) }
                    },
                )
            }
            Button(onClick = vm::saveRisk, modifier = Modifier.fillMaxWidth()) {
                Text("Risk ayarlarını kaydet")
            }
            riskSaved?.let {
                Text(it, color = TradeColors.Long, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GERÇEK İŞLEMLERİ ETKİNLEŞTİR", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Son aşamada, ikinci onay ekranıyla açılacak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = false, onCheckedChange = null, enabled = false)
            }
        }

        if (hasKeys) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TradeColors.Short),
            ) { Text("API anahtarlarını sil") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Anahtarlar silinsin mi?") },
            text = { Text("Şifreli API key ve secret bu cihazdan kalıcı olarak silinir.") },
            confirmButton = {
                TextButton(onClick = {
                    container.credentialStore.clear()
                    confirmDelete = false
                }) { Text("Sil", color = TradeColors.Short) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun RiskField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.replace(',', '.').filter { it.isDigit() || it == '.' }.take(8)) },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
