package com.investalert.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.investalert.app.data.AppSettings
import com.investalert.app.data.AssetSuggestion
import com.investalert.app.data.ScreenshotInvestmentDraft
import com.investalert.app.model.Holding
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID

@Composable
fun InvestmentDialog(
    initial: Holding?,
    baseCurrency: String,
    scanDraft: ScreenshotInvestmentDraft?,
    screenshotBusy: Boolean,
    onPickScreenshot: () -> Unit,
    onScanConsumed: () -> Unit,
    onSearchAssets: (String, (List<AssetSuggestion>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Holding) -> Unit
) {
    var searchQuery by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var suggestions by remember { mutableStateOf<List<AssetSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var selectedAsset by remember(initial?.id) {
        mutableStateOf(initial?.let { AssetSuggestion(it.symbol, it.name, it.market, it.assetType, it.currency) })
    }
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var symbol by remember(initial?.id) { mutableStateOf(initial?.symbol ?: "") }
    var market by remember(initial?.id) { mutableStateOf(initial?.market ?: "") }
    var assetType by remember(initial?.id) { mutableStateOf(initial?.assetType ?: "Investment") }
    var currency by remember(initial?.id) { mutableStateOf(initial?.currency ?: "USD") }
    var investedCurrency by remember(initial?.id) { mutableStateOf(initial?.investedCurrency ?: baseCurrency) }
    var whereHeld by remember(initial?.id) { mutableStateOf(initial?.whereHeld ?: "") }
    var unitsText by remember(initial?.id) { mutableStateOf(initial?.units?.takeIf { it > 0 }?.toString() ?: "") }
    var investedText by remember(initial?.id) { mutableStateOf(initial?.investedAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var manualCurrentValueText by remember(initial?.id) { mutableStateOf(initial?.manualCurrentValue?.takeIf { it >= 0 }?.toString() ?: "") }
    var lowText by remember(initial?.id) { mutableStateOf(initial?.lowAlert?.toString() ?: "") }
    var highText by remember(initial?.id) { mutableStateOf(initial?.highAlert?.toString() ?: "") }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }
    var watchOnly by remember(initial?.id) { mutableStateOf(initial?.watchOnly ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualMode by remember(initial?.id) { mutableStateOf(false) }
    var importedNotice by remember { mutableStateOf(false) }
    var importedWholeUnits by remember { mutableStateOf<Double?>(null) }
    var importedFractionalUnits by remember { mutableStateOf<Double?>(null) }
    var importedCurrentValue by remember { mutableStateOf<Double?>(null) }
    var importedAveragePrice by remember { mutableStateOf<Double?>(null) }
    var importedPrice by remember { mutableStateOf<Double?>(null) }

    fun applyAsset(suggestion: AssetSuggestion) {
        selectedAsset = suggestion
        searchQuery = suggestion.name
        name = suggestion.name
        symbol = suggestion.symbol
        market = suggestion.exchange
        assetType = suggestion.assetType
        suggestion.currency?.let {
            currency = if (it.equals("ZAc", true)) "ZAR" else it.uppercase(Locale.US)
        }
        suggestions = emptyList()
    }

    LaunchedEffect(scanDraft?.token) {
        val draft = scanDraft ?: return@LaunchedEffect
        draft.units?.let { unitsText = number(it).replace(",", "") }
        draft.investedAmount?.let { investedText = it.toString() }
        draft.investedCurrency?.let { investedCurrency = it }
        draft.whereHeld?.let { whereHeld = it }
        importedWholeUnits = draft.wholeUnits
        importedFractionalUnits = draft.fractionalUnits
        importedCurrentValue = draft.currentValue
        draft.currentValue?.let { manualCurrentValueText = it.toString() }
        importedAveragePrice = draft.averagePurchasePrice
        importedPrice = draft.impliedCurrentPrice
        if (draft.query.isNotBlank()) {
            searchQuery = draft.query
            searching = true
            onSearchAssets(draft.query) { matches ->
                searching = false
                suggestions = matches
                if (selectedAsset == null && matches.isNotEmpty()) {
                    applyAsset(matches.first())
                }
            }
        }
        importedNotice = true
        onScanConsumed()
    }

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
            onSearchAssets("") { suggestions = it }
            return@LaunchedEffect
        }
        if (query.length < 2) {
            suggestions = emptyList()
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
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(if (initial == null) "Add investment" else "Edit investment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    OutlinedButton(
                        onClick = onPickScreenshot,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !screenshotBusy
                    ) {
                        Text(if (screenshotBusy) "Reading screenshot…" else "Scan investment screenshot")
                    }
                    if (importedNotice) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Screenshot details detected", fontWeight = FontWeight.Bold)
                                if (importedWholeUnits != null || importedFractionalUnits != null) {
                                    Text(
                                        "Whole shares: ${number(importedWholeUnits ?: 0.0)} • Fractional: ${number(importedFractionalUnits ?: 0.0)} • Total: ${unitsText.ifBlank { "—" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                importedCurrentValue?.let {
                                    Text("Screenshot current value: ${money(it, investedCurrency)}", style = MaterialTheme.typography.bodySmall)
                                }
                                importedAveragePrice?.let {
                                    Text("Screenshot average price: ${priceMoney(it, investedCurrency)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Please review the imported fields before saving.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Watchlist only", fontWeight = FontWeight.Bold)
                            Text("Track it without adding it to portfolio value.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = watchOnly, onCheckedChange = { watchOnly = it })
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (selectedAsset?.name != it) selectedAsset = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search investment") },
                        placeholder = { Text("Aspen, Bitcoin, Apple, USD/ZAR…") },
                        singleLine = true
                    )
                    if (searching) Text("Searching…", style = MaterialTheme.typography.labelMedium)

                    if (suggestions.isNotEmpty() && selectedAsset == null && !manualMode) {
                        Text(if (searchQuery.isBlank()) "Popular investments" else "Suggestions", fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                suggestions.forEach { suggestion ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth().clickable { applyAsset(suggestion) }.padding(12.dp)
                                    ) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                            Column(Modifier.padding(12.dp)) {
                                Text(asset.name, fontWeight = FontWeight.Bold)
                                Text("${asset.symbol} • ${asset.assetType} • ${asset.exchange}")
                                TextButton(onClick = {
                                    selectedAsset = null
                                    searchQuery = ""
                                    name = ""
                                    symbol = ""
                                    market = ""
                                    assetType = "Investment"
                                }) { Text("Choose different investment") }
                            }
                        }
                    }

                    if (selectedAsset == null && !manualMode) {
                        TextButton(onClick = { manualMode = true; suggestions = emptyList() }) {
                            Text("Enter manually")
                        }
                    }

                    if (manualMode) {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Investment name") }, singleLine = true)
                        OutlinedTextField(symbol, { symbol = it.uppercase(Locale.US) }, Modifier.fillMaxWidth(), label = { Text("Ticker / symbol") }, singleLine = true)
                        OutlinedTextField(market, { market = it }, Modifier.fillMaxWidth(), label = { Text("Market / exchange") }, singleLine = true)
                        SimpleDropdown("Market currency", currency, AppSettings.supportedCurrencies, { currency = it })
                        TextButton(onClick = { manualMode = false; searchQuery = "" }) { Text("Back to search") }
                    }

                    if (!watchOnly) {
                        OutlinedTextField(
                            value = whereHeld,
                            onValueChange = { whereHeld = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Where held (optional)") },
                            placeholder = { Text("EasyEquities, Binance, bank, wallet…") },
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
                        SimpleDropdown("Currency paid", investedCurrency, AppSettings.supportedCurrencies, { investedCurrency = it })
                        OutlinedTextField(
                            value = investedText,
                            onValueChange = { investedText = numericInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Amount invested ($investedCurrency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        if (symbol.startsWith("AGF:") || assetType.equals("Unit Trust", true)) {
                            OutlinedTextField(
                                value = manualCurrentValueText,
                                onValueChange = { manualCurrentValueText = numericInput(it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Current value ($investedCurrency)") },
                                supportingText = { Text("Managed-fund value; update from your latest statement or screenshot.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }
                    }

                    Text("Price alerts", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            lowText, { lowText = numericInput(it) }, Modifier.weight(1f),
                            label = { Text("Low ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            highText, { highText = numericInput(it) }, Modifier.weight(1f),
                            label = { Text("High ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Monitor this item", fontWeight = FontWeight.Bold)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }

                    error?.let { Text(it, color = LossRed, style = MaterialTheme.typography.bodySmall) }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val units = if (watchOnly) 0.0 else unitsText.toDoubleOrNull()
                            val invested = if (watchOnly) 0.0 else investedText.toDoubleOrNull()
                            val low = lowText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                            val high = highText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                            val manualCurrentValue = manualCurrentValueText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                            error = when {
                                name.isBlank() -> "Choose or enter an investment."
                                symbol.isBlank() -> "Ticker / symbol is required."
                                !watchOnly && (units == null || units <= 0) -> "Units must be greater than zero."
                                !watchOnly && (invested == null || invested < 0) -> "Enter the amount invested."
                                lowText.isNotBlank() && low == null -> "Low alert is not valid."
                                highText.isNotBlank() && high == null -> "High alert is not valid."
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
                                        whereHeld = if (watchOnly) "" else whereHeld.trim(),
                                        units = units ?: 0.0,
                                        investedAmount = invested ?: 0.0,
                                        investedCurrency = investedCurrency,
                                        currency = currency,
                                        lowAlert = low,
                                        highAlert = high,
                                        enabled = enabled,
                                        watchOnly = watchOnly,
                                        lastPrice = initial?.lastPrice ?: importedPrice,
                                        manualCurrentValue = if (symbol.startsWith("AGF:") || assetType.equals("Unit Trust", true)) manualCurrentValue else initial?.manualCurrentValue,
                                        dayChangePercent = initial?.dayChangePercent,
                                        lastUpdatedEpochMs = initial?.lastUpdatedEpochMs,
                                        lowTriggered = initial?.lowTriggered ?: false,
                                        highTriggered = initial?.highTriggered ?: false,
                                        lastSource = initial?.lastSource
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            }
        }
    }
}
