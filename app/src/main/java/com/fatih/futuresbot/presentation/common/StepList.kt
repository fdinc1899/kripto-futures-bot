package com.fatih.futuresbot.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatih.futuresbot.presentation.theme.TradeColors
import com.fatih.futuresbot.trading.ActionResult
import com.fatih.futuresbot.trading.StepLog

@Composable
fun StepList(steps: List<StepLog>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = if (step.ok) "✓" else "✗",
                    color = if (step.ok) TradeColors.Long else TradeColors.Short,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(20.dp),
                )
                Text(step.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ActionResultCard(
    result: ActionResult,
    onDismiss: () -> Unit,
    extraActionLabel: String? = null,
    onExtraAction: () -> Unit = {},
) {
    val success = result is ActionResult.Success
    val color = if (success) TradeColors.Long else TradeColors.Short
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = (if (success) "✓ " else "✗ ") + result.message,
                color = color,
                fontWeight = FontWeight.Bold,
            )
            StepList(result.steps)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (extraActionLabel != null) {
                    TextButton(onClick = onExtraAction) { Text(extraActionLabel) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }
        }
    }
}
