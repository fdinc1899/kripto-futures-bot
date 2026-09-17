package com.fatih.futuresbot.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.TradingMode
import com.fatih.futuresbot.presentation.common.SectionCard
import com.fatih.futuresbot.presentation.common.StatRow
import com.fatih.futuresbot.presentation.theme.TradeColors

@Composable
fun SettingsScreen(container: AppContainer) {
    val hasKeys by container.credentialStore.hasCredentials.collectAsStateWithLifecycle()
    val mode by container.tradingModeStore.mode.collectAsStateWithLifecycle()
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
            StatRow("API anahtarı", if (hasKeys) "Şifreli kayıtlı" else "Yok")
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
