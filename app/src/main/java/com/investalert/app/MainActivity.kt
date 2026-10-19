package com.investalert.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.investalert.app.data.AppSettings
import com.investalert.app.data.AssetSearchProvider
import com.investalert.app.data.AssetSuggestion
import com.investalert.app.data.CurrencyConverter
import com.investalert.app.data.HoldingStore
import com.investalert.app.data.InvestmentScreenshotParser
import com.investalert.app.data.OcrLine
import com.investalert.app.data.ScreenshotInvestmentDraft
import com.investalert.app.data.TraderAttachment
import com.investalert.app.data.TraderAttachmentProcessor
import com.investalert.app.data.TraderProEngine
import com.investalert.app.data.TransactionStore
import com.investalert.app.model.Holding
import com.investalert.app.model.InvestmentTransaction
import com.investalert.app.monitoring.ActiveMonitorService
import com.investalert.app.monitoring.MonitoringScheduler
import com.investalert.app.monitoring.PriceCheckEngine
import com.investalert.app.notifications.AppNotifications
import com.investalert.app.ui.InvestAlertTheme
import com.investalert.app.ui.InvestmentDialog
import com.investalert.app.ui.PortfolioScreen
import com.investalert.app.ui.SettingsScreen
import com.investalert.app.ui.TraderProScreen
import com.investalert.app.ui.TransactionDialog
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private lateinit var store: HoldingStore
    private lateinit var transactionStore: TransactionStore
    private lateinit var settings: AppSettings

    private var holdings by mutableStateOf<List<Holding>>(emptyList())
    private var transactions by mutableStateOf<List<InvestmentTransaction>>(emptyList())
    private var fxRates by mutableStateOf<Map<String, Double>>(emptyMap())
    private var isRefreshing by mutableStateOf(false)
    private var screenshotBusy by mutableStateOf(false)
    private var scanDraft by mutableStateOf<ScreenshotInvestmentDraft?>(null)
    private var traderAttachments by mutableStateOf<List<TraderAttachment>>(emptyList())
    private var traderAttachmentBusy by mutableStateOf(false)

    private var baseCurrency by mutableStateOf("ZAR")
    private var themeMode by mutableStateOf("SYSTEM")
    private var accent by mutableStateOf("PURPLE")
    private var compactCards by mutableStateOf(false)
    private var activeMonitoring by mutableStateOf(false)
    private var monitoringMinutes by mutableStateOf(15)
    private var priceAlerts by mutableStateOf(true)
    private var dailyMoveAlerts by mutableStateOf(false)
    private var dailyMoveThreshold by mutableStateOf(5.0)
    private var newsAlerts by mutableStateOf(false)
    private var morningBrief by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications are disabled for InvestAlert.", Toast.LENGTH_LONG).show()
        }
    }

    private val screenshotPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) readInvestmentScreenshot(uri)
    }

    private val traderFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) processTraderAttachments(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HoldingStore(this)
        transactionStore = TransactionStore(this)
        settings = AppSettings(this)
        AppNotifications.createChannels(this)
        reload()
        MonitoringScheduler.sync(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            InvestAlertTheme(themeMode = themeMode, accentName = accent) {
                InvestAlertApp(
                    holdings = holdings,
                    transactions = transactions,
                    fxRates = fxRates,
                    isRefreshing = isRefreshing,
                    screenshotBusy = screenshotBusy,
                    scanDraft = scanDraft,
                    baseCurrency = baseCurrency,
                    themeMode = themeMode,
                    accent = accent,
                    compactCards = compactCards,
                    activeMonitoring = activeMonitoring,
                    monitoringMinutes = monitoringMinutes,
                    priceAlerts = priceAlerts,
                    dailyMoveAlerts = dailyMoveAlerts,
                    dailyMoveThreshold = dailyMoveThreshold,
                    newsAlerts = newsAlerts,
                    morningBrief = morningBrief,
                    traderAttachments = traderAttachments,
                    traderAttachmentBusy = traderAttachmentBusy,
                    onRefresh = ::refreshPrices,
                    onSave = { holding ->
                        store.upsert(holding)
                        reload()
                        refreshPrices()
                    },
                    onDelete = { id ->
                        store.delete(id)
                        reload()
                    },
                    onToggleEnabled = { holding, enabled ->
                        store.upsert(holding.copy(enabled = enabled))
                        reload()
                    },
                    onOpenScreenshot = {
                        screenshotPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onScanConsumed = { scanDraft = null },
                    onSearchAssets = { query, callback ->
                        val local = AssetSearchProvider.localSearch(query)
                        callback(local)
                        lifecycleScope.launch {
                            val full = runCatching { AssetSearchProvider.search(query) }.getOrDefault(local)
                            if (full != local) callback(full)
                        }
                    },
                    onTransaction = ::applyTransaction,
                    onSetBaseCurrency = {
                        settings.setBaseCurrency(it)
                        baseCurrency = it
                        refreshFxRates()
                    },
                    onSetThemeMode = {
                        settings.setThemeMode(it)
                        themeMode = it
                    },
                    onSetAccent = {
                        settings.setAccent(it)
                        accent = it
                    },
                    onSetCompactCards = {
                        settings.setCompactCards(it)
                        compactCards = it
                    },
                    onToggleActive = ::updateActiveMonitoring,
                    onSetMonitoringMinutes = {
                        settings.setMonitoringMinutes(it)
                        monitoringMinutes = it
                        MonitoringScheduler.sync(this)
                    },
                    onSetPriceAlerts = {
                        settings.setPriceAlertsEnabled(it)
                        priceAlerts = it
                    },
                    onSetDailyMoveAlerts = {
                        settings.setDailyMoveAlertsEnabled(it)
                        dailyMoveAlerts = it
                    },
                    onSetDailyMoveThreshold = {
                        settings.setDailyMoveThreshold(it)
                        dailyMoveThreshold = it
                    },
                    onSetNewsAlerts = {
                        settings.setNewsAlertsEnabled(it)
                        newsAlerts = it
                        if (it) settings.setNewsSeeded(false)
                        MonitoringScheduler.sync(this)
                    },
                    onSetMorningBrief = {
                        settings.setMorningBriefEnabled(it)
                        morningBrief = it
                        MonitoringScheduler.sync(this)
                    },
                    onTestNotification = {
                        requestNotificationPermissionIfNeeded()
                        AppNotifications.showTestAlert(this)
                    },
                    onAttachTrader = {
                        traderFilePicker.launch(
                            arrayOf("image/*", "application/pdf", "text/*", "application/json")
                        )
                    },
                    onRemoveTraderAttachment = { index ->
                        if (index in traderAttachments.indices) {
                            traderAttachments = traderAttachments.toMutableList().also { it.removeAt(index) }
                        }
                    },
                    onAskTrader = { question, attachments, callback ->
                        lifecycleScope.launch {
                            val reply = runCatching {
                                TraderProEngine.answer(question, holdings, attachments)
                            }.getOrElse {
                                TraderProEngine.AssistantReply(
                                    "Trader Pro",
                                    "I couldn't complete that analysis: ${it.message ?: "market data unavailable"}."
                                )
                            }
                            callback(reply)
                            traderAttachments = emptyList()
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
        transactions = transactionStore.getAll()
        activeMonitoring = store.isActiveMonitoringEnabled()
        baseCurrency = settings.baseCurrency()
        themeMode = settings.themeMode()
        accent = settings.accent()
        compactCards = settings.compactCards()
        monitoringMinutes = settings.monitoringMinutes()
        priceAlerts = settings.priceAlertsEnabled()
        dailyMoveAlerts = settings.dailyMoveAlertsEnabled()
        dailyMoveThreshold = settings.dailyMoveThreshold()
        newsAlerts = settings.newsAlertsEnabled()
        morningBrief = settings.morningBriefEnabled()
        refreshFxRates()
    }

    private fun refreshFxRates() {
        val target = baseCurrency.uppercase(Locale.US)
        val currencies = holdings.flatMap {
            listOf(it.currency.uppercase(Locale.US), it.investedCurrency.uppercase(Locale.US))
        }.distinct()

        lifecycleScope.launch {
            val rates = mutableMapOf(target to 1.0)
            for (currency in currencies) {
                if (currency == target) {
                    rates[currency] = 1.0
                } else {
                    runCatching { CurrencyConverter.rate(currency, target) }
                        .getOrNull()
                        ?.let { rates[currency] = it }
                }
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
                "Updated ${summary.updated} item(s)."
            } else {
                "Updated ${summary.updated}; ${summary.errors.size} unavailable."
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

    private fun applyTransaction(
        holding: Holding,
        type: String,
        units: Double,
        amount: Double,
        note: String
    ) {
        if (holding.watchOnly || units <= 0) return
        val current = store.getAll().firstOrNull { it.id == holding.id } ?: return
        val avgCost = if (current.units > 0) current.investedAmount / current.units else 0.0

        val updated = if (type == "SELL") {
            val newUnits = max(0.0, current.units - units)
            val remainingCost = max(0.0, current.investedAmount - avgCost * units)
            current.copy(
                units = newUnits,
                investedAmount = if (newUnits == 0.0) 0.0 else remainingCost
            )
        } else {
            current.copy(
                units = current.units + units,
                investedAmount = current.investedAmount + amount
            )
        }

        store.upsert(updated)
        transactionStore.add(
            InvestmentTransaction(
                id = UUID.randomUUID().toString(),
                holdingId = current.id,
                type = type,
                units = units,
                amount = amount,
                currency = current.investedCurrency,
                epochMs = System.currentTimeMillis(),
                note = note
            )
        )
        reload()
        refreshPrices()
    }

    private fun readInvestmentScreenshot(uri: Uri) {
        screenshotBusy = true
        val image = runCatching { InputImage.fromFilePath(this, uri) }.getOrElse {
            screenshotBusy = false
            Toast.makeText(this, "Couldn't open that image.", Toast.LENGTH_LONG).show()
            return
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (result.text.isBlank()) {
                    Toast.makeText(this, "No readable investment details found.", Toast.LENGTH_LONG).show()
                } else {
                    val structuredLines = result.textBlocks.flatMap { block ->
                        block.lines.mapNotNull { line ->
                            line.boundingBox?.let { box ->
                                OcrLine(
                                    text = line.text,
                                    left = box.left,
                                    top = box.top,
                                    right = box.right,
                                    bottom = box.bottom
                                )
                            }
                        }
                    }
                    scanDraft = InvestmentScreenshotParser.parse(
                        ocrLines = structuredLines,
                        fullText = result.text,
                        defaultCurrency = baseCurrency
                    )
                    Toast.makeText(this, "Screenshot read. Review the detected values.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't read that screenshot.", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener {
                screenshotBusy = false
                recognizer.close()
            }
    }

    private fun processTraderAttachments(uris: List<Uri>) {
        if (traderAttachmentBusy) return
        traderAttachmentBusy = true
        lifecycleScope.launch {
            val loaded = mutableListOf<TraderAttachment>()
            for (uri in uris.take(6)) {
                val item = runCatching {
                    TraderAttachmentProcessor.process(this@MainActivity, uri)
                }.getOrElse { error ->
                    TraderAttachment(
                        name = uri.lastPathSegment ?: "attachment",
                        mimeType = contentResolver.getType(uri).orEmpty(),
                        extractedText = "",
                        kind = "Error: ${error.message ?: "unreadable"}"
                    )
                }
                loaded += item
            }
            traderAttachments = (traderAttachments + loaded).takeLast(6)
            traderAttachmentBusy = false
            Toast.makeText(
                this@MainActivity,
                "Attached ${loaded.size} file(s).",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
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
    transactions: List<InvestmentTransaction>,
    fxRates: Map<String, Double>,
    isRefreshing: Boolean,
    screenshotBusy: Boolean,
    scanDraft: ScreenshotInvestmentDraft?,
    baseCurrency: String,
    themeMode: String,
    accent: String,
    compactCards: Boolean,
    activeMonitoring: Boolean,
    monitoringMinutes: Int,
    priceAlerts: Boolean,
    dailyMoveAlerts: Boolean,
    dailyMoveThreshold: Double,
    newsAlerts: Boolean,
    morningBrief: Boolean,
    traderAttachments: List<TraderAttachment>,
    traderAttachmentBusy: Boolean,
    onRefresh: () -> Unit,
    onSave: (Holding) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (Holding, Boolean) -> Unit,
    onOpenScreenshot: () -> Unit,
    onScanConsumed: () -> Unit,
    onSearchAssets: (String, (List<AssetSuggestion>) -> Unit) -> Unit,
    onTransaction: (Holding, String, Double, Double, String) -> Unit,
    onSetBaseCurrency: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetAccent: (String) -> Unit,
    onSetCompactCards: (Boolean) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onSetMonitoringMinutes: (Int) -> Unit,
    onSetPriceAlerts: (Boolean) -> Unit,
    onSetDailyMoveAlerts: (Boolean) -> Unit,
    onSetDailyMoveThreshold: (Double) -> Unit,
    onSetNewsAlerts: (Boolean) -> Unit,
    onSetMorningBrief: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onAttachTrader: () -> Unit,
    onRemoveTraderAttachment: (Int) -> Unit,
    onAskTrader: (String, List<TraderAttachment>, (TraderProEngine.AssistantReply) -> Unit) -> Unit
) {
    var tab by remember { mutableStateOf(AppTab.PORTFOLIO) }
    var editing by remember { mutableStateOf<Holding?>(null) }
    var creating by remember { mutableStateOf(false) }
    var transacting by remember { mutableStateOf<Holding?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("InvestAlert Pro", fontWeight = FontWeight.Bold)
                        Text("Portfolio intelligence")
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
                baseCurrency = baseCurrency,
                fxRates = fxRates,
                isRefreshing = isRefreshing,
                compactCards = compactCards,
                onRefresh = onRefresh,
                onAdd = { creating = true },
                onEdit = { editing = it },
                onDelete = onDelete,
                onToggleEnabled = onToggleEnabled,
                onTransaction = { transacting = it }
            )
            AppTab.TRADER -> TraderProScreen(
                modifier = Modifier.padding(padding),
                holdings = holdings,
                attachments = traderAttachments,
                attachmentBusy = traderAttachmentBusy,
                onAttach = onAttachTrader,
                onRemoveAttachment = onRemoveTraderAttachment,
                onAskTrader = onAskTrader
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                baseCurrency = baseCurrency,
                themeMode = themeMode,
                accent = accent,
                compactCards = compactCards,
                activeMonitoring = activeMonitoring,
                monitoringMinutes = monitoringMinutes,
                priceAlerts = priceAlerts,
                dailyMoveAlerts = dailyMoveAlerts,
                dailyMoveThreshold = dailyMoveThreshold,
                newsAlerts = newsAlerts,
                morningBrief = morningBrief,
                onBaseCurrency = onSetBaseCurrency,
                onThemeMode = onSetThemeMode,
                onAccent = onSetAccent,
                onCompactCards = onSetCompactCards,
                onActiveMonitoring = onToggleActive,
                onMonitoringMinutes = onSetMonitoringMinutes,
                onPriceAlerts = onSetPriceAlerts,
                onDailyMoveAlerts = onSetDailyMoveAlerts,
                onDailyMoveThreshold = onSetDailyMoveThreshold,
                onNewsAlerts = onSetNewsAlerts,
                onMorningBrief = onSetMorningBrief,
                onTestNotification = onTestNotification
            )
        }
    }

    if (creating || editing != null) {
        InvestmentDialog(
            initial = editing,
            baseCurrency = baseCurrency,
            scanDraft = scanDraft,
            screenshotBusy = screenshotBusy,
            onPickScreenshot = onOpenScreenshot,
            onScanConsumed = onScanConsumed,
            onSearchAssets = onSearchAssets,
            onDismiss = {
                creating = false
                editing = null
                onScanConsumed()
            },
            onSave = {
                onSave(it)
                creating = false
                editing = null
                onScanConsumed()
            }
        )
    }

    transacting?.let { holding ->
        TransactionDialog(
            holding = holding,
            history = transactions.filter { it.holdingId == holding.id },
            onDismiss = { transacting = null },
            onApply = { type, units, amount, note ->
                onTransaction(holding, type, units, amount, note)
                transacting = null
            }
        )
    }
}
