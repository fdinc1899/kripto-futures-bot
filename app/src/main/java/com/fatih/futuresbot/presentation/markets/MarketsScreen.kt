package com.fatih.futuresbot.presentation.markets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.domain.model.Ticker24h
import com.fatih.futuresbot.presentation.common.Fmt
import com.fatih.futuresbot.presentation.common.pnlColor
import com.fatih.futuresbot.presentation.theme.TradeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(container: AppContainer, onSymbolSelected: () -> Unit) {
    val vm: MarketsViewModel = viewModel(factory = MarketsViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val windowed = state.window.interval != null

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Markets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = buildString {
                append("USDⓈ-M perpetual · ")
                append(state.sort.subtitle)
                append(" (")
                append(state.window.label)
                append(")")
                if (windowed) append(" · hacimce ilk ${state.windowLimit}")
            },
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

        // Sıralama: Hacim / Yükselen / Düşen
        Row(
            modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortMode.values().forEach { mode ->
                FilterChip(
                    selected = state.sort == mode,
                    onClick = { vm.onSortChange(mode) },
                    label = { Text(mode.label) },
                )
            }
        }

        // Zaman penceresi: 5dk / 15dk / 1s / 4s / 24s
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChangeWindow.values().forEach { w ->
                FilterChip(
                    selected = state.window == w,
                    onClick = { vm.onWindowChange(w) },
                    label = { Text(w.label) },
                )
            }
            if (state.windowRefreshing && state.items.isNotEmpty()) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = TradeColors.Accent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            HeaderText("#", Modifier.width(28.dp))
            HeaderText("Parite", Modifier.weight(1f))
            HeaderText("Fiyat", Modifier)
            HeaderText(state.window.label, Modifier.width(84.dp), TextAlign.End)
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sonuç yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.items, key = { _, t -> t.symbol }) { index, ticker ->
                    MarketRow(
                        rank = index + 1,
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
private fun MarketRow(rank: Int, ticker: Ticker24h, selected: Boolean, onClick: () -> Unit) {
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
        Text(
            text = rank.toString(),
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = ticker.symbol.removeSuffix("USDT"),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "/USDT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = "Hacim " + Fmt.compact(ticker.quoteVolume),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(Fmt.price(ticker.lastPrice), fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                maxLines = 1,
                modifier = Modifier.widthIn(min = 74.dp).padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }
    }
}
