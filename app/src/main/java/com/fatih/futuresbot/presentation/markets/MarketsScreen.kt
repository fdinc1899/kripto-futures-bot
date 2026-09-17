package com.fatih.futuresbot.presentation.markets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.Ticker24h
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.common.pnlColor
import com.fatih.futuresbot.presentation.theme.TradeColors

@Composable
fun MarketsScreen(container: AppContainer, onSymbolSelected: () -> Unit) {
    val vm: MarketsViewModel = viewModel(factory = MarketsViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Markets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "USDⓈ-M perpetual · hacme göre sıralı",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Parite ara (örn. ETH, SOL)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let {
            Text(
                text = it,
                color = TradeColors.Accent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            HeaderText("Parite", Modifier.weight(1f))
            HeaderText("Fiyat", Modifier)
            HeaderText("24s", Modifier.width(84.dp), TextAlign.End)
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sonuç yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.symbol }) { ticker ->
                    MarketRow(
                        ticker = ticker,
                        selected = ticker.symbol == state.selected,
                        onClick = {
                            vm.select(ticker.symbol)
                            onSymbolSelected()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = align,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MarketRow(ticker: Ticker24h, selected: Boolean, onClick: () -> Unit) {
    val change = ticker.priceChangePercent
    val changeColor = pnlColor(change)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(ticker.symbol.removeSuffix("USDT"), fontWeight = FontWeight.Bold)
                Text(
                    text = "/USDT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Hacim " + Fmt.compact(ticker.quoteVolume),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Fmt.price(ticker.lastPrice), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Surface(
            color = changeColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                text = Fmt.pct(change),
                color = changeColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.widthIn(min = 74.dp).padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }
    }
}
