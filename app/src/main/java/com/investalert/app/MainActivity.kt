package com.investalert.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.investalert.app.data.AppSettings
import com.investalert.app.data.AssetSearchProvider
import com.investalert.app.data.AssetSuggestion
import com.investalert.app.data.CurrencyConverter
import com.investalert.app.data.HoldingStore
import com.investalert.app.data.TraderProEngine
import com.investalert.app.model.Holding
import com.investalert.app.monitoring.ActiveMonitorService
import com.investalert.app.monitoring.PriceCheckEngine
import com.investalert.app.monitoring.PriceCheckWorker
import com.investalert.app.notifications.AppNotifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.absoluteValue

private val GainGreen = Color(0xFF17823B)
private val LossRed = Color(0xFFC62828)
private val NeutralAmber = Color(0xFF9A6700)

class MainActivity : ComponentActivity() {
    private lateinit var store: HoldingStore
    private lateinit var settings: AppSettings
    private var holdings by mutableStateOf<List<Holding>>(emptyList())
    private var usdZar by mutableStateOf<Double?>(null)
    private var isRefreshing by mutableStateOf(false)
    private var activeMonitoring by mutableStateOf(false)
    private var baseCurrency by mutableStateOf("ZAR")
    private var fxRates by mutableStateOf<Map<String, Double>>(emptyMap())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Notifications are disabled. Monitoring can run, but alerts will not appear.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HoldingStore(this)
        settings = AppSettings(this)
        AppNotifications.createChannels(this)
        PriceCheckWorker.schedule(this)
        reload()
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                InvestAlertApp(
                    holdings = holdings,
                    usdZar = usdZar,
                    isRefreshing = isRefreshing,
                    activeMonitoring = activeMonitoring,
                    baseCurrency = baseCurrency,
                    fxRates = fxRates,
                    onRefresh = ::refreshPrices,
                    onSave = { holding ->
                        store.upsert(holding)
                        reload()
                        refreshPrices()
                    },
                    onDelete = { id -> store.delete(id); reload() },
                    onToggleEnabled = { holding, enabled -> store.upsert(holding.copy(enabled = enabled)); reload() },
                    onToggleActive = ::updateActiveMonitoring,
                    onSetBaseCurrency = { currency ->
                        settings.setBaseCurrency(currency)
                        baseCurrency = currency
                        refreshFxRates()
                    },
                    onSearchAssets = { query, callback ->
                        val local = AssetSearchProvider.localSearch(query)
                        callback(local)
                        lifecycleScope.launch {
                            val full = runCatching { AssetSearchProvider.search(query) }.getOrDefault(local)
                            if (full != local) callback(full)
                        }
                    },
                    onTestNotification = {
                        requestNotificationPermissionIfNeeded()
                        AppNotifications.showTestAlert(this)
                    },
                    onAskTrader = { question, callback ->
                        lifecycleScope.launch {
                            val reply = runCatching { TraderProEngine.answer(question, holdings) }
                                .getOrElse { TraderProEngine.AssistantReply("Trader Pro", "I couldn't complete that analysis: ${it.message ?: "market data unavailable"}.") }
                            callback(reply)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) reload()
    }

    private fun reload() {
        holdings = store.getAll()
        usdZar = store.getUsdZar()
        activeMonitoring = store.isActiveMonitoringEnabled()
        baseCurrency = settings.baseCurrency()
        refreshFxRates()
    }

    private fun refreshFxRates() {
        if (!::settings.isInitialized) return
        val target = settings.baseCurrency()
        val currencies = holdings.flatMap {
            listOf(it.currency.uppercase(Locale.US), it.investedCurrency.uppercase(Locale.US))
        }.distinct()
        lifecycleScope.launch {
            val rates = mutableMapOf<String, Double>()
            rates[target] = 1.0
            for (currency in currencies) {
                rates[currency] = if (currency == target) 1.0 else runCatching { CurrencyConverter.rate(currency, target) }.getOrNull() ?: continue
            }
            fxRates = rates
        }
    }

    private fun refreshPrices() {
        if (isRefreshing) return
        isRefreshing = true
        lifecycleScope.launch {
            val summary = PriceCheckEngine.checkAll(this@MainActivity, notify = true)
            reload()
            isRefreshing = false
            val message = if (summary.errors.isEmpty()) {
                "Updated ${summary.updated} investment(s)."
            } else {
                "Updated ${summary.updated}; ${summary.errors.size} price(s) unavailable."
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateActiveMonitoring(enabled: Boolean) {
        requestNotificationPermissionIfNeeded()
        if (enabled) {
            ContextCompat.startForegroundService(this, Intent(this, ActiveMonitorService::class.java))
        } else {
            stopService(Intent(this, ActiveMonitorService::class.java))
            store.setActiveMonitoringEnabled(false)
        }
        activeMonitoring = enabled
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class AppTab { PORTFOLIO, TRADER, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestAlertApp(
    holdings: List<Holding>,
    usdZar: Double?,
    isRefreshing: Boolean,
    activeMonitoring: Boolean,
    baseCurrency: String,
    fxRates: Map<String, Double>,
    onRefresh: () -> Unit,
    onSave: (Holding) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (Holding, Boolean) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onSetBaseCurrency: (String) -> Unit,
    onSearchAssets: (String, (List<AssetSuggestion>) -> Unit) -> Unit,
    onTestNotification: () -> Unit,
    onAskTrader: (String, (TraderProEngine.AssistantReply) -> Unit) -> Unit
) {
    var tab by remember { mutableStateOf(AppTab.PORTFOLIO) }
    var editing by remember { mutableStateOf<Holding?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("InvestAlert Pro", fontWeight = FontWeight.Bold)
                        Text("Simple portfolio intelligence", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == AppTab.PORTFOLIO,
                    onClick = { tab = AppTab.PORTFOLIO },
                    icon = { Text("▣") },
                    label = { Text("Portfolio") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.TRADER,
                    onClick = { tab = AppTab.TRADER },
                    icon = { Text("✦") },
                    label = { Text("Trader Pro") }
                )
                NavigationBarItem(
                    selected = tab == AppTab.SETTINGS,
                    onClick = { tab = AppTab.SETTINGS },
                    icon = { Text("⚙") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            AppTab.PORTFOLIO -> PortfolioScreen(
                modifier = Modifier.padding(padding),
                holdings = holdings,
                usdZar = usdZar,
                baseCurrency = baseCurrency,
                fxRates = fxRates,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onAdd = { creating = true },
                onEdit = { editing = it },
                onDelete = onDelete,
                onToggleEnabled = onToggleEnabled
            )
            AppTab.TRADER -> TraderProScreen(
                modifier = Modifier.padding(padding),
                holdings = holdings,
                onAskTrader = onAskTrader
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                activeMonitoring = activeMonitoring,
                baseCurrency = baseCurrency,
                onToggleActive = onToggleActive,
                onSetBaseCurrency = onSetBaseCurrency,
                onTestNotification = onTestNotification
            )
        }
    }

    if (creating || editing != null) {
        InvestmentDialog(
            initial = editing,
            baseCurrency = baseCurrency,
            onDismiss = {
                creating = false
                editing = null
            },
            onSearchAssets = onSearchAssets,
            onSave = {
                onSave(it)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun PortfolioScreen(
    modifier: Modifier,
    holdings: List<Holding>,
    usdZar: Double?,
    baseCurrency: String,
    fxRates: Map<String, Double>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Holding) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (Holding, Boolean) -> Unit
) {
    fun converted(amount: Double, currency: String): Double? =
        fxRates[currency.uppercase(Locale.US)]?.let { amount * it }

    val investedValues = holdings.map { h -> converted(h.investedAmount, h.investedCurrency) }
    val currentValues = holdings.map { h -> h.currentValue?.let { converted(it, h.currency) } }
    val allInvestedConverted = holdings.isEmpty() || investedValues.all { it != null }
    val allCurrentConverted = holdings.isNotEmpty() && currentValues.all { it != null }
    val totalInvested = investedValues.filterNotNull().sum()
    val totalCurrent = currentValues.filterNotNull().sum()
    val pnl = if (allInvestedConverted && allCurrentConverted) totalCurrent - totalInvested else null
    val pnlPct = if (pnl != null && totalInvested > 0) pnl / totalInvested * 100.0 else null
    val pnlColor = when {
        pnl == null -> MaterialTheme.colorScheme.onSurface
        pnl > 0 -> GainGreen
        pnl < 0 -> LossRed
        else -> MaterialTheme.colorScheme.onSurface
    }

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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Portfolio snapshot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SummaryRow("Base currency", baseCurrency)
                SummaryRow("Total invested", if (allInvestedConverted) money(totalInvested, baseCurrency) else "FX unavailable")
                SummaryRow("Current value", if (allCurrentConverted) money(totalCurrent, baseCurrency) else "Refresh prices")
                SummaryRow(
                    if ((pnl ?: 0.0) >= 0) "Profit" else "Loss",
                    if (pnl == null) "—" else signedMoney(pnl, baseCurrency) + (pnlPct?.let { "  (${signedPct(it)})" } ?: ""),
                    valueColor = pnlColor
                )
                if (usdZar != null) {
                    Text("USD/ZAR reference: R${String.format(Locale.US, "%.2f", usdZar)}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.weight(1f)) {
                Text(if (isRefreshing) "Refreshing…" else "Refresh all")
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Text("+ Add investment")
            }
        }

        if (holdings.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No investments yet", fontWeight = FontWeight.Bold)
                    Text("Add your first investment. Search by company, ticker, coin, forex pair, ETF, index or commodity.")
                }
            }
        } else {
            holdings.forEach { holding ->
                HoldingCard(
                    holding = holding,
                    baseCurrency = baseCurrency,
                    rateToBase = fxRates[holding.currency.uppercase(Locale.US)],
                    investedRateToBase = fxRates[holding.investedCurrency.uppercase(Locale.US)],
                    onEdit = { onEdit(holding) },
                    onDelete = { onDelete(holding.id) },
                    onToggleEnabled = { onToggleEnabled(holding, it) }
                )
            }
        }
        Spacer(Modifier.height(88.dp))
    }
}
@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun HoldingCard(
    holding: Holding,
    baseCurrency: String,
    rateToBase: Double?,
    investedRateToBase: Double?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(holding.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${holding.symbol} • ${holding.assetType} • ${holding.market} • ${holding.currency}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (holding.whereHeld.isNotBlank()) {
                        Text("Held at ${holding.whereHeld}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(checked = holding.enabled, onCheckedChange = onToggleEnabled)
            }

            HorizontalDivider()

            SummaryRow("Units", number(holding.units))
            SummaryRow("Invested", money(holding.investedAmount, holding.investedCurrency))
            holding.averageCostPerUnit?.let { SummaryRow("Avg cost / unit", priceMoney(it, holding.investedCurrency)) }
            SummaryRow(
                "Current price",
                holding.lastPrice?.let { priceMoney(it, holding.currency) } ?: "Not refreshed"
            )
            SummaryRow(
                "Current value",
                currentValue?.let { money(it, holding.currency) } ?: "—"
            )

            if (baseCurrency != holding.currency.uppercase(Locale.US) && baseValue != null) {
                SummaryRow("Value in $baseCurrency", money(baseValue, baseCurrency))
            }

            SummaryRow(
                if ((basePnl ?: 0.0) >= 0) "Profit" else "Loss",
                if (basePnl != null) signedMoney(basePnl, baseCurrency) + (pnlPct?.let { "  (${signedPct(it)})" } ?: "") else "—",
                valueColor = pnlColor
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Low: ${holding.lowAlert?.let { money(it, holding.currency) } ?: "Off"}",
                    color = if (holding.lowTriggered) LossRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "High: ${holding.highAlert?.let { money(it, holding.currency) } ?: "Off"}",
                    color = if (holding.highTriggered) GainGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            val updated = holding.lastUpdatedEpochMs?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            }
            if (updated != null) {
                Text(
                    "Updated $updated • ${holding.lastSource ?: "market feed"}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("Delete", color = LossRed)
                }
            }
        }
    }
}

@Composable
private fun TraderProScreen(
    modifier: Modifier,
    holdings: List<Holding>,
    onAskTrader: (String, (TraderProEngine.AssistantReply) -> Unit) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf<TraderProEngine.AssistantReply?>(null) }

    fun ask(text: String) {
        if (text.isBlank() || isThinking) return
        question = text
        isThinking = true
        onAskTrader(text) {
            reply = it
            isThinking = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Trader Pro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("A focused market research assistant for your portfolio and watchlist.")
                Text(
                    "Combines trend, momentum, volatility, portfolio context and recent public headlines. Signals are research, not guarantees.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = false,
                onClick = { ask("Review my portfolio risk") },
                label = { Text("Portfolio") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = false,
                onClick = { ask("Best JSE opportunities to research") },
                label = { Text("JSE") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = false,
                onClick = { ask("Best US opportunities to research") },
                label = { Text("US") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = false,
                onClick = { ask("Best crypto opportunities to research") },
                label = { Text("Crypto") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = false,
                onClick = { ask("Best forex opportunities to research") },
                label = { Text("Forex") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = false,
                onClick = { ask("What should I review for selling or reducing?") },
                label = { Text("Sell review") },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Trader Pro") },
            placeholder = { Text("e.g. Analyse Aspen, best US opportunities, portfolio risk…") },
            minLines = 2
        )
        Button(
            onClick = { ask(question) },
            enabled = question.isNotBlank() && !isThinking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isThinking) "Analysing market data…" else "Analyse")
        }

        reply?.let { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(result.body)
                    result.analyses.forEach { a ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(a.symbol, fontWeight = FontWeight.Bold)
                            Text("${a.score}/100", fontWeight = FontWeight.Bold)
                        }
                        Text("${a.stance} • ${a.confidence} confidence")
                        Text(
                            "Price ${priceMoney(a.price, a.currency)}" +
                                (a.rsi14?.let { " • RSI ${String.format(Locale.US, "%.1f", it)}" } ?: "") +
                                (a.momentum30?.let { " • 30d ${signedPct(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium
                        )
                        a.reasons.take(3).forEach { reason ->
                            Text("• $reason", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(result.sourceNote, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (holdings.isEmpty()) {
            Text("Tip: add your own investments so Trader Pro can make the analysis portfolio-aware.")
        }
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    activeMonitoring: Boolean,
    baseCurrency: String,
    onToggleActive: (Boolean) -> Unit,
    onSetBaseCurrency: (String) -> Unit,
    onTestNotification: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Display currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("All portfolio totals are converted into this base currency using the latest available FX rate.")

        AppSettings.supportedCurrencies.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { currency ->
                    FilterChip(
                        selected = currency == baseCurrency,
                        onClick = { onSetBaseCurrency(currency) },
                        label = { Text(currency) }
                    )
                }
            }
        }

        HorizontalDivider()
        Text("Monitoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Active monitoring", fontWeight = FontWeight.Bold)
                Text(
                    if (activeMonitoring) "On • approximately every 5 minutes" else "Off • background checks still run about every 15 minutes",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Switch(checked = activeMonitoring, onCheckedChange = onToggleActive)
        }
        OutlinedButton(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
            Text("Send test notification")
        }
        Text(
            "Prices and instrument search use public internet market-data endpoints on a best-effort basis. No paid API key or InvestAlert account is required.",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(88.dp))
    }
}
@Composable
private fun InvestmentDialog(
    initial: Holding?,
    baseCurrency: String,
    onSearchAssets: (String, (List<AssetSuggestion>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Holding) -> Unit
) {
    var searchQuery by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var suggestions by remember { mutableStateOf<List<AssetSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var selectedAsset by remember(initial?.id) {
        mutableStateOf(
            initial?.let {
                AssetSuggestion(it.symbol, it.name, it.market, it.assetType, it.currency)
            }
        )
    }
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var symbol by remember(initial?.id) { mutableStateOf(initial?.symbol ?: "") }
    var market by remember(initial?.id) { mutableStateOf(initial?.market ?: "") }
    var assetType by remember(initial?.id) { mutableStateOf(initial?.assetType ?: "Investment") }
    var currency by remember(initial?.id) { mutableStateOf(initial?.currency ?: "USD") }
    var investedCurrency by remember(initial?.id) { mutableStateOf(initial?.investedCurrency ?: baseCurrency) }
    var whereHeld by remember(initial?.id) { mutableStateOf(initial?.whereHeld ?: "") }
    var unitsText by remember(initial?.id) { mutableStateOf(initial?.units?.toString() ?: "") }
    var investedText by remember(initial?.id) { mutableStateOf(initial?.investedAmount?.toString() ?: "") }
    var lowText by remember(initial?.id) { mutableStateOf(initial?.lowAlert?.toString() ?: "") }
    var highText by remember(initial?.id) { mutableStateOf(initial?.highAlert?.toString() ?: "") }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualMode by remember(initial?.id) { mutableStateOf(false) }

    LaunchedEffect(searchQuery, manualMode, selectedAsset) {
        if (manualMode) {
            suggestions = emptyList()
            searching = false
            return@LaunchedEffect
        }
        val query = searchQuery.trim()
        if (selectedAsset != null && query == selectedAsset?.name) {
            suggestions = emptyList()
            searching = false
            return@LaunchedEffect
        }
        if (query.isBlank()) {
            onSearchAssets("") {
                suggestions = it
                searching = false
            }
            return@LaunchedEffect
        }
        if (query.length < 2) {
            suggestions = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(220)
        searching = true
        onSearchAssets(query) {
            suggestions = it
            searching = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (initial == null) "Add investment" else "Edit investment",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Search thousands of companies, ETFs, funds, coins, forex pairs, indices and commodities.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!manualMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (selectedAsset?.name != it) selectedAsset = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search and select investment") },
                            placeholder = { Text("Aspen, Bitcoin, USD/ZAR, Apple…") },
                            singleLine = true
                        )
                    }

                    if (searching && !manualMode) {
                        Text("Searching global markets…", style = MaterialTheme.typography.labelMedium)
                    }

                    if (suggestions.isNotEmpty() && selectedAsset == null && !manualMode) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp)
                            ) {
                                suggestions.forEach { suggestion ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedAsset = suggestion
                                                searchQuery = suggestion.name
                                                name = suggestion.name
                                                symbol = suggestion.symbol
                                                market = suggestion.exchange
                                                assetType = suggestion.assetType
                                                suggestion.currency?.let { currency = if (it.equals("ZAc", true)) "ZAR" else it.uppercase(Locale.US) }
                                                suggestions = emptyList()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(suggestion.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(suggestion.symbol, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            "${suggestion.assetType} • ${suggestion.exchange}" +
                                                (suggestion.currency?.let { " • $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    selectedAsset?.let { asset ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Selected", style = MaterialTheme.typography.labelSmall)
                                Text(asset.name, fontWeight = FontWeight.Bold)
                                Text("${asset.symbol} • ${asset.assetType} • ${asset.exchange} • ${currency}")
                                TextButton(onClick = {
                                    selectedAsset = null
                                    searchQuery = ""
                                    name = ""
                                    symbol = ""
                                    market = ""
                                    assetType = "Investment"
                                }) {
                                    Text("Choose a different investment")
                                }
                            }
                        }
                    }

                    if (selectedAsset == null && !manualMode) {
                        TextButton(onClick = {
                            manualMode = true
                            suggestions = emptyList()
                        }) {
                            Text("Can't find it? Enter manually")
                        }
                    }

                    if (manualMode) {
                        Text("Manual investment details", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Investment name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = symbol,
                            onValueChange = { symbol = it.uppercase(Locale.US) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ticker / market symbol") },
                            placeholder = { Text("APN.JO, AAPL, BTC-USD, USDZAR=X…") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = market,
                            onValueChange = { market = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Market / exchange") },
                            singleLine = true
                        )
                        Text("Market price currency", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        AppSettings.supportedCurrencies.chunked(5).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                row.forEach { ccy ->
                                    FilterChip(
                                        selected = ccy == currency,
                                        onClick = { currency = ccy },
                                        label = { Text(ccy) }
                                    )
                                }
                            }
                        }
                        TextButton(onClick = {
                            manualMode = false
                            searchQuery = ""
                        }) { Text("Back to search") }
                    }

                    OutlinedTextField(
                        value = whereHeld,
                        onValueChange = { whereHeld = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Where held (optional)") },
                        placeholder = { Text("EasyEquities, bank, exchange, wallet…") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unitsText,
                        onValueChange = { unitsText = numericInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Units / quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Text("Currency you paid in", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    AppSettings.supportedCurrencies.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEach { ccy ->
                                FilterChip(
                                    selected = ccy == investedCurrency,
                                    onClick = { investedCurrency = ccy },
                                    label = { Text(ccy) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = investedText,
                        onValueChange = { investedText = numericInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount invested ($investedCurrency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Text("Price alerts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lowText,
                            onValueChange = { lowText = numericInput(it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Low ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = highText,
                            onValueChange = { highText = numericInput(it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("High ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Alerts enabled", fontWeight = FontWeight.Bold)
                            Text("Turn off to keep the investment without monitoring it.", style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }

                    error?.let { Text(it, color = LossRed, style = MaterialTheme.typography.bodySmall) }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val units = unitsText.toDoubleOrNull()
                            val invested = investedText.toDoubleOrNull()
                            val low = lowText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                            val high = highText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                            error = when {
                                name.isBlank() -> "Choose or enter an investment name."
                                symbol.isBlank() -> "Ticker / market symbol is required."
                                units == null || units <= 0.0 -> "Units must be greater than zero."
                                invested == null || invested < 0.0 -> "Amount invested must be zero or more."
                                lowText.isNotBlank() && low == null -> "Low alert is not a valid number."
                                highText.isNotBlank() && high == null -> "High alert is not a valid number."
                                low != null && high != null && low >= high -> "Low alert must be below high alert."
                                else -> null
                            }
                            if (error == null) {
                                onSave(
                                    Holding(
                                        id = initial?.id ?: UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        symbol = symbol.trim().uppercase(Locale.US),
                                        market = market.trim().ifBlank { "Market" },
                                        assetType = assetType,
                                        whereHeld = whereHeld.trim(),
                                        units = units!!,
                                        investedAmount = invested!!,
                                        investedCurrency = investedCurrency,
                                        currency = currency,
                                        lowAlert = low,
                                        highAlert = high,
                                        enabled = enabled,
                                        lastPrice = initial?.lastPrice,
                                        lastUpdatedEpochMs = initial?.lastUpdatedEpochMs,
                                        lowTriggered = initial?.lowTriggered ?: false,
                                        highTriggered = initial?.highTriggered ?: false,
                                        lastSource = initial?.lastSource
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
private fun numericInput(value: String): String {
    val cleaned = value.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }
    var seenDot = false
    return buildString {
        cleaned.forEachIndexed { index, ch ->
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !seenDot -> {
                    append(ch)
                    seenDot = true
                }
                ch == '-' && index == 0 -> append(ch)
            }
        }
    }
}

private fun number(value: Double): String {
    return if (value % 1.0 == 0.0) {
        String.format(Locale.US, "%,.0f", value)
    } else {
        String.format(Locale.US, "%,.4f", value).trimEnd('0').trimEnd('.')
    }
}

private fun money(value: Double, currency: String): String {
    val code = currency.uppercase(Locale.US)
    val prefix = when (code) {
        "ZAR" -> "R"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "CNY" -> "¥"
        "INR" -> "₹"
        else -> "$code "
    }
    return prefix + String.format(Locale.US, "%,.2f", value)
}

private fun priceMoney(value: Double, currency: String): String {
    val code = currency.uppercase(Locale.US)
    val prefix = when (code) {
        "ZAR" -> "R"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "CNY" -> "¥"
        "INR" -> "₹"
        else -> "$code "
    }
    val decimals = when {
        value.absoluteValue == 0.0 -> 2
        value.absoluteValue < 0.0001 -> 8
        value.absoluteValue < 0.01 -> 6
        value.absoluteValue < 1.0 -> 4
        else -> 2
    }
    return prefix + String.format(Locale.US, "%,.${decimals}f", value)
}

private fun signedMoney(value: Double, currency: String): String {
    val sign = if (value >= 0) "+" else "−"
    return sign + money(value.absoluteValue, currency)
}

private fun signedPct(value: Double): String {
    val sign = if (value >= 0) "+" else "−"
    return sign + String.format(Locale.US, "%.2f%%", value.absoluteValue)
}
