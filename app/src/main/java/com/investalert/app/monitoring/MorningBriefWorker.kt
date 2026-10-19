package com.investalert.app.monitoring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.investalert.app.data.AppSettings
import com.investalert.app.data.CurrencyConverter
import com.investalert.app.data.HoldingStore
import com.investalert.app.notifications.AppNotifications
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

class MorningBriefWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        if (!settings.morningBriefEnabled()) return Result.success()

        PriceCheckEngine.checkAll(applicationContext, notify = false)
        val holdings = HoldingStore(applicationContext).getAll()
        val owned = holdings.filter { !it.watchOnly }
        val base = settings.baseCurrency()
        val rates = mutableMapOf(base to 1.0)

        suspend fun rate(currency: String): Double? {
            val code = currency.uppercase(Locale.US)
            if (code == base) return 1.0
            rates[code]?.let { return it }
            val value = runCatching { CurrencyConverter.rate(code, base) }.getOrNull() ?: return null
            rates[code] = value
            return value
        }

        var invested = 0.0
        var current = 0.0
        var complete = true
        for (h in owned) {
            val ir = rate(h.investedCurrency)
            val cr = rate(h.currency)
            val cv = h.currentValue
            if (ir == null || cr == null || cv == null) {
                complete = false
                continue
            }
            invested += h.investedAmount * ir
            current += cv * cr
        }

        val mover = holdings.mapNotNull { h -> h.dayChangePercent?.let { h to it } }
            .maxByOrNull { it.second.absoluteValue }

        val body = buildString {
            if (complete && owned.isNotEmpty()) {
                val pnl = current - invested
                append("Portfolio ${money(current, base)} • ${if (pnl >= 0) "+" else "−"}${money(pnl.absoluteValue, base)}")
            } else {
                append("${owned.size} investment(s)")
            }
            mover?.let {
                append("\nLargest move: ${it.first.symbol} ${signedPct(it.second)}")
            }
            val watch = holdings.count { it.watchOnly }
            if (watch > 0) append("\nWatchlist: $watch item(s)")
        }

        AppNotifications.showMorningBrief(applicationContext, "InvestAlert morning brief", body)
        schedule(applicationContext, true)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "investalert_morning_brief"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE_WORK)
            if (!enabled) return

            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(7, 30)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)

            val request = OneTimeWorkRequestBuilder<MorningBriefWorker>()
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniqueWork(UNIQUE_WORK, androidx.work.ExistingWorkPolicy.REPLACE, request)
        }

        private fun money(value: Double, currency: String): String {
            val prefix = when (currency.uppercase(Locale.US)) {
                "ZAR" -> "R"
                "USD" -> "$"
                "EUR" -> "€"
                "GBP" -> "£"
                else -> "$currency "
            }
            return prefix + String.format(Locale.US, "%,.2f", value)
        }

        private fun signedPct(value: Double): String =
            (if (value >= 0) "+" else "−") + String.format(Locale.US, "%.2f%%", value.absoluteValue)
    }
}
