package com.investalert.app.monitoring

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.investalert.app.data.AppSettings
import com.investalert.app.data.HoldingStore
import com.investalert.app.data.NewsResearchProvider
import com.investalert.app.notifications.AppNotifications
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class NewsMonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        if (!settings.newsAlertsEnabled()) return Result.success()

        val holdings = HoldingStore(applicationContext).getAll().filter { it.enabled }.take(20)
        if (holdings.isEmpty()) return Result.success()

        val seen = settings.seenNews().toMutableSet()
        val newlySeen = mutableSetOf<String>()
        val seeded = settings.isNewsSeeded()

        for (holding in holdings) {
            val items = runCatching {
                NewsResearchProvider.news("${holding.name} ${holding.symbol}", 3)
            }.getOrDefault(emptyList())

            for (item in items) {
                if (item.key !in seen) {
                    newlySeen += item.key
                    if (seeded) {
                        AppNotifications.showNewsAlert(applicationContext, holding, item)
                    }
                }
            }
            delay(180)
        }

        settings.addSeenNews(newlySeen)
        if (!seeded) settings.setNewsSeeded(true)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "investalert_news_monitor"

        fun schedule(context: Context, minutes: Int, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_WORK)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<NewsMonitorWorker>(
                minutes.coerceAtLeast(15).toLong(),
                TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
