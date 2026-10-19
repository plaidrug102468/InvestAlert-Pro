package com.investalert.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.investalert.app.model.Holding
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PortfolioScreen(
    modifier: Modifier,
    holdings: List<Holding>,
    baseCurrency: String,
    fxRates: Map<String, Double>,
    isRefreshing: Boolean,
    compactCards: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Holding) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (Holding, Boolean) -> Unit,
    onTransaction: (Holding) -> Unit
) {
    fun converted(amount: Double, currency: String): Double? =
        fxRates[currency.uppercase(Locale.US)]?.let { amount * it }

    val owned = holdings.filter { !it.watchOnly }
    val watchlist = holdings.filter { it.watchOnly }

    val investedValues = owned.map { h -> converted(h.investedAmount, h.investedCurrency) }
    val currentValues = owned.map { h -> h.currentValue?.let { converted(it, h.currency) } }
    val allInvestedConverted = owned.isEmpty() || investedValues.all { it != null }
    val allCurrentConverted = owned.isNotEmpty() && currentValues.all { it != null }
    val totalInvested = investedValues.filterNotNull().sum()
    val totalCurrent = currentValues.filterNotNull().sum()
    val pnl = if (allInvestedConverted && allCurrentConverted) totalCurrent - totalInvested else null
    val pnlPct = if (pnl != null && totalInvested > 0) pnl / totalInvested * 100.0 else null

    var previousValue = 0.0
    var todayComplete = owned.isNotEmpty()
    owned.forEach { h ->
        val cv = h.currentValue
        val rate = fxRates[h.currency.uppercase(Locale.US)]
        val change = h.dayChangePercent
        if (cv == null || rate == null || change == null || change <= -99.9) {
            todayComplete = false
        } else {
            val now = cv * rate
            previousValue += now / (1.0 + change / 100.0)
        }
    }
    val todayDelta = if (todayComplete) totalCurrent - previousValue else null
    val todayPct = if (todayDelta != null && previousValue > 0) todayDelta / previousValue * 100.0 else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Portfolio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(baseCurrency, style = MaterialTheme.typography.labelLarge)
                }
                SummaryRow("Invested", if (allInvestedConverted) money(totalInvested, baseCurrency) else "FX unavailable")
                SummaryRow("Current value", if (allCurrentConverted) money(totalCurrent, baseCurrency) else "Refresh prices")
                SummaryRow(
                    "Total return",
                    if (pnl == null) "—" else signedMoney(pnl, baseCurrency) + (pnlPct?.let { "  (${signedPct(it)})" } ?: ""),
                    when {
                        pnl == null -> MaterialTheme.colorScheme.onSurface
                        pnl > 0 -> GainGreen
                        pnl < 0 -> LossRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                SummaryRow(
                    "Today",
                    if (todayDelta == null) "—" else signedMoney(todayDelta, baseCurrency) + (todayPct?.let { "  (${signedPct(it)})" } ?: ""),
                    when {
                        todayDelta == null -> MaterialTheme.colorScheme.onSurface
                        todayDelta > 0 -> GainGreen
                        todayDelta < 0 -> LossRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.weight(1f)) {
                Text(if (isRefreshing) "Refreshing…" else "Refresh")
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Text("+ Add")
            }
        }

        if (owned.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No investments yet", fontWeight = FontWeight.Bold)
                    Text("Add an investment by search or scan a broker screenshot.")
                }
            }
        } else {
            owned.forEach { holding ->
                HoldingCard(
                    holding = holding,
                    baseCurrency = baseCurrency,
                    rateToBase = fxRates[holding.currency.uppercase(Locale.US)],
                    investedRateToBase = fxRates[holding.investedCurrency.uppercase(Locale.US)],
                    compact = compactCards,
                    onEdit = { onEdit(holding) },
                    onDelete = { onDelete(holding.id) },
                    onToggleEnabled = { onToggleEnabled(holding, it) },
                    onTransaction = { onTransaction(holding) }
                )
            }
        }

        if (watchlist.isNotEmpty()) {
            Text("Watchlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            watchlist.forEach { holding ->
                WatchCard(
                    holding = holding,
                    onEdit = { onEdit(holding) },
                    onDelete = { onDelete(holding.id) },
                    onToggleEnabled = { onToggleEnabled(holding, it) }
                )
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun HoldingCard(
    holding: Holding,
    baseCurrency: String,
    rateToBase: Double?,
    investedRateToBase: Double?,
    compact: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTransaction: () -> Unit
) {
    val currentValue = holding.currentValue
    val baseValue = currentValue?.let { value -> rateToBase?.let { value * it } }
    val baseInvested = investedRateToBase?.let { holding.investedAmount * it }
    val basePnl = if (baseValue != null && baseInvested != null) baseValue - baseInvested else null
    val pnlPct = if (basePnl != null && baseInvested != null && baseInvested > 0.0) basePnl / baseInvested * 100.0 else null
    val pnlColor = when {
        basePnl == null -> MaterialTheme.colorScheme.onSurface
        basePnl > 0 -> GainGreen
        basePnl < 0 -> LossRed
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(holding.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${holding.symbol} • ${holding.assetType}" +
                            (if (holding.whereHeld.isNotBlank()) " • ${holding.whereHeld}" else ""),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Switch(checked = holding.enabled, onCheckedChange = onToggleEnabled)
            }

            Text("Current value", style = MaterialTheme.typography.labelMedium)
            Text(baseValue?.let { money(it, baseCurrency) } ?: "Refresh price", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            if (basePnl != null) {
                Text(
                    (if (basePnl >= 0) "Profit " else "Loss ") +
                        signedMoney(basePnl, baseCurrency) +
                        (pnlPct?.let { "  (${signedPct(it)})" } ?: ""),
                    color = pnlColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            holding.dayChangePercent?.let { move ->
                Text(
                    "Today ${signedPct(move)}",
                    color = when {
                        move > 0 -> GainGreen
                        move < 0 -> LossRed
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (!compact) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Invested", style = MaterialTheme.typography.labelSmall)
                        Text(money(holding.investedAmount, holding.investedCurrency), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Units", style = MaterialTheme.typography.labelSmall)
                        Text(number(holding.units), fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Market price", style = MaterialTheme.typography.labelSmall)
                        Text(holding.lastPrice?.let { priceMoney(it, holding.currency) } ?: "—", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Avg cost / unit", style = MaterialTheme.typography.labelSmall)
                        Text(holding.averageCostPerUnit?.let { priceMoney(it, holding.investedCurrency) } ?: "—", fontWeight = FontWeight.Bold)
                    }
                }
                if (holding.lowAlert != null || holding.highAlert != null) {
                    Text(
                        "Alerts • Low ${holding.lowAlert?.let { priceMoney(it, holding.currency) } ?: "Off"} • High ${holding.highAlert?.let { priceMoney(it, holding.currency) } ?: "Off"}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                holding.lastUpdatedEpochMs?.let {
                    Text(
                        "Updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTransaction, modifier = Modifier.weight(1f)) { Text("Buy / Sell") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = LossRed) }
            }
        }
    }
}

@Composable
private fun WatchCard(
    holding: Holding,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(holding.name, fontWeight = FontWeight.Bold)
                    Text("${holding.symbol} • ${holding.assetType}", style = MaterialTheme.typography.labelMedium)
                }
                Switch(checked = holding.enabled, onCheckedChange = onToggleEnabled)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(holding.lastPrice?.let { priceMoney(it, holding.currency) } ?: "Refresh price", fontWeight = FontWeight.Bold)
                holding.dayChangePercent?.let {
                    Text(
                        signedPct(it),
                        color = if (it >= 0) GainGreen else LossRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (holding.lowAlert != null || holding.highAlert != null) {
                Text(
                    "Alerts • Low ${holding.lowAlert?.let { priceMoney(it, holding.currency) } ?: "Off"} • High ${holding.highAlert?.let { priceMoney(it, holding.currency) } ?: "Off"}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Remove", color = LossRed) }
            }
        }
    }
}
