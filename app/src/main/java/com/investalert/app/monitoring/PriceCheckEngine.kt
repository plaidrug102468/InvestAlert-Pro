package com.investalert.app.monitoring

import android.content.Context
import com.investalert.app.data.AppSettings
import com.investalert.app.data.HoldingStore
import com.investalert.app.data.YahooPriceProvider
import com.investalert.app.notifications.AppNotifications
import kotlinx.coroutines.delay
import kotlin.math.abs

data class CheckSummary(
    val checked: Int,
    val updated: Int,
    val errors: List<String>
)

object PriceCheckEngine {
    suspend fun checkAll(context: Context, notify: Boolean = true): CheckSummary {
        val store = HoldingStore(context)
        val settings = AppSettings(context)
        val holdings = store.getAll().toMutableList()
        var checked = 0
        var updatedCount = 0
        val errors = mutableListOf<String>()

        for (index in holdings.indices) {
            val holding = holdings[index]
            if (!holding.enabled) continue
            if (holding.symbol.startsWith("AGF:") || holding.assetType.equals("Unit Trust", true)) {
                continue
            }
            checked++
            try {
                val quote = YahooPriceProvider.fetchPrice(holding.symbol)
                val evaluated = AlertEvaluator.evaluate(holding, quote.price)
                val updated = evaluated.updated.copy(
                    currency = quote.currency,
                    dayChangePercent = quote.dayChangePercent,
                    lastUpdatedEpochMs = quote.fetchedAtEpochMs,
                    lastSource = quote.source
                )
                holdings[index] = updated
                updatedCount++

                if (notify && settings.priceAlertsEnabled()) {
                    evaluated.events.forEach { AppNotifications.showThresholdAlert(context, updated, it) }
                }

                val move = quote.dayChangePercent
                if (
                    notify &&
                    settings.dailyMoveAlertsEnabled() &&
                    move != null &&
                    abs(move) >= settings.dailyMoveThreshold() &&
                    settings.shouldAlertDailyMove(updated.symbol)
                ) {
                    AppNotifications.showDailyMoveAlert(context, updated, move)
                    settings.markDailyMoveAlerted(updated.symbol)
                }
            } catch (e: Exception) {
                errors += "${holding.symbol}: ${e.message ?: "price unavailable"}"
            }
            delay(180)
        }

        store.saveAll(holdings)

        if (holdings.any { it.currency.equals("USD", true) || it.investedCurrency.equals("USD", true) }) {
            runCatching { YahooPriceProvider.fetchPrice("ZAR=X") }
                .onSuccess { if (it.price > 0) store.setUsdZar(it.price) }
        }

        return CheckSummary(checked, updatedCount, errors)
    }
}
