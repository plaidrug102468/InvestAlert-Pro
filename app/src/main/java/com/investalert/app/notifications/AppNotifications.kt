package com.investalert.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.investalert.app.MainActivity
import com.investalert.app.data.NewsItem
import com.investalert.app.model.Holding
import com.investalert.app.monitoring.AlertEvent
import com.investalert.app.monitoring.AlertType
import java.util.Locale
import kotlin.math.absoluteValue

object AppNotifications {
    const val ALERTS_CHANNEL = "threshold_alerts"
    const val NEWS_CHANNEL = "investment_news"
    const val BRIEF_CHANNEL = "morning_brief"
    const val MONITOR_CHANNEL = "active_monitoring"
    const val MONITOR_NOTIFICATION_ID = 1101

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(ALERTS_CHANNEL, "Price alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Low, high and large market-move alerts"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(NEWS_CHANNEL, "Investment news", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New headlines for monitored investments and watchlist items"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(BRIEF_CHANNEL, "Morning brief", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily portfolio summary"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(MONITOR_CHANNEL, "Active monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while faster monitoring is enabled"
            }
        )
    }

    fun showThresholdAlert(context: Context, holding: Holding, event: AlertEvent) {
        val isHigh = event.type == AlertType.HIGH
        val title = if (isHigh) "📈 ${holding.name} high alert" else "📉 ${holding.name} low alert"
        val body = "${holding.symbol}: ${price(event.actualPrice, holding.currency)} • target ${price(event.threshold, holding.currency)}"

        notify(
            context = context,
            id = holding.id.hashCode() xor event.type.hashCode(),
            channel = ALERTS_CHANNEL,
            title = title,
            body = body,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    fun showDailyMoveAlert(context: Context, holding: Holding, changePct: Double) {
        val direction = if (changePct >= 0) "up" else "down"
        val body = buildString {
            append("${holding.symbol} is $direction ${signedPct(changePct)} today")
            holding.lastPrice?.let { append(" • ${price(it, holding.currency)}") }
        }
        notify(
            context = context,
            id = holding.id.hashCode() xor 0x4405,
            channel = ALERTS_CHANNEL,
            title = "Market move • ${holding.name}",
            body = body,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    fun showNewsAlert(context: Context, holding: Holding, item: NewsItem) {
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
        val pending = PendingIntent.getActivity(
            context,
            item.key.hashCode(),
            browser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NEWS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("News • ${holding.name}")
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.title + if (item.source.isNotBlank()) "\n${item.source}" else ""))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(item.key.hashCode(), notification) }
    }

    fun showMorningBrief(context: Context, title: String, body: String) {
        notify(
            context = context,
            id = 7310,
            channel = BRIEF_CHANNEL,
            title = title,
            body = body,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    fun activeMonitoringNotification(context: Context, intervalMinutes: Int) =
        NotificationCompat.Builder(context, MONITOR_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("InvestAlert monitoring")
            .setContentText("Checking monitored investments about every $intervalMinutes minutes")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun showTestAlert(context: Context) {
        notify(
            context = context,
            id = 9090,
            channel = ALERTS_CHANNEL,
            title = "InvestAlert test",
            body = "Notifications are working on this device.",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        priority: Int
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun price(value: Double, currency: String): String {
        val code = currency.uppercase(Locale.US)
        val prefix = when (code) {
            "ZAR" -> "R"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$code "
        }
        val decimals = when {
            value.absoluteValue < 0.0001 -> 8
            value.absoluteValue < 0.01 -> 6
            else -> 2
        }
        return prefix + String.format(Locale.US, "%,.${decimals}f", value)
    }

    private fun signedPct(value: Double): String =
        (if (value >= 0) "+" else "−") + String.format(Locale.US, "%.2f%%", value.absoluteValue)
}
