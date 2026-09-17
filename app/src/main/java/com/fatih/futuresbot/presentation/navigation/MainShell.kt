package com.fatih.futuresbot.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatih.futuresbot.app.AppContainer
import com.fatih.futuresbot.presentation.chart.ChartScreen
import com.fatih.futuresbot.presentation.common.PlaceholderScreen
import com.fatih.futuresbot.domain.model.PositionSide
import com.fatih.futuresbot.presentation.dashboard.DashboardScreen
import com.fatih.futuresbot.presentation.orders.OrdersScreen
import com.fatih.futuresbot.presentation.positions.PositionsScreen
import com.fatih.futuresbot.presentation.trade.TradeScreen
import com.fatih.futuresbot.presentation.markets.MarketsScreen
import com.fatih.futuresbot.presentation.settings.SettingsScreen

enum class Dest(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    MARKETS("Markets", Icons.Filled.Search),
    CHART("Chart", Icons.Filled.ShowChart),
    POSITIONS("Positions", Icons.Filled.AccountBalanceWallet),
    ORDERS("Orders", Icons.Filled.Receipt),
    BOT("Bot", Icons.Filled.SmartToy),
    HISTORY("History", Icons.Filled.History),
    SETTINGS("Settings", Icons.Filled.Settings),
    MORE("Daha", Icons.Filled.MoreHoriz),
    TRADE("Emir", Icons.Filled.Receipt),
}

private val BOTTOM_ITEMS = listOf(Dest.DASHBOARD, Dest.MARKETS, Dest.CHART, Dest.BOT, Dest.MORE)
private val MORE_ITEMS = listOf(Dest.POSITIONS, Dest.ORDERS, Dest.HISTORY, Dest.SETTINGS)

@Composable
fun MainShell(container: AppContainer, onAddKeys: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Dest.DASHBOARD) }
    var tradeSide by rememberSaveable { mutableStateOf(PositionSide.LONG) }
    val selectedBottom = when (current) {
        in BOTTOM_ITEMS -> current
        Dest.TRADE -> Dest.DASHBOARD
        else -> Dest.MORE
    }

    BackHandler(enabled = current != Dest.DASHBOARD) {
        current = if (current in MORE_ITEMS) Dest.MORE else Dest.DASHBOARD
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                BOTTOM_ITEMS.forEach { d ->
                    NavigationBarItem(
                        selected = selectedBottom == d,
                        onClick = { current = d },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (current) {
                Dest.DASHBOARD -> DashboardScreen(
                    container = container,
                    onOpenBot = { current = Dest.BOT },
                    onPickSymbol = { current = Dest.MARKETS },
                    onTrade = { side ->
                        tradeSide = side
                        current = Dest.TRADE
                    },
                    onOpenPositions = { current = Dest.POSITIONS },
                )
                Dest.TRADE -> TradeScreen(
                    container = container,
                    side = tradeSide,
                    onClose = { current = Dest.DASHBOARD },
                    onOpenPositions = { current = Dest.POSITIONS },
                )
                Dest.MARKETS -> MarketsScreen(container, onSymbolSelected = { current = Dest.CHART })
                Dest.CHART -> ChartScreen(container, onPickSymbol = { current = Dest.MARKETS })
                Dest.POSITIONS -> PositionsScreen(container)
                Dest.ORDERS -> OrdersScreen(container)
                Dest.BOT -> PlaceholderScreen("Bot", "Bot paneli Aşama 11'de gelecek")
                Dest.HISTORY -> PlaceholderScreen("History", "İşlem geçmişi Aşama 12'de gelecek")
                Dest.SETTINGS -> SettingsScreen(container, onAddKeys = onAddKeys)
                Dest.MORE -> MoreScreen(onOpen = { current = it })
            }
        }
    }
}

@Composable
private fun MoreScreen(onOpen: (Dest) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Daha fazla", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        MORE_ITEMS.forEach { d ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(d) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(d.icon, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(d.label)
                }
            }
        }
    }
}
