package com.investalert.app.monitoring

import android.content.Context
import com.investalert.app.data.AppSettings

object MonitoringScheduler {
    fun sync(context: Context) {
        val settings = AppSettings(context)
        PriceCheckWorker.schedule(context, settings.monitoringMinutes())
        NewsMonitorWorker.schedule(context, settings.monitoringMinutes(), settings.newsAlertsEnabled())
        MorningBriefWorker.schedule(context, settings.morningBriefEnabled())
    }
}
