package com.investalert.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.investalert.app.MainActivity
import com.investalert.app.model.Holding
import com.investalert.app.monitoring.AlertEvent
import com.investalert.app.monitoring.AlertType
import java.util.Locale
import kotlin.math.absoluteValue

object AppNotifications {
    const val ALERTS_CHANNEL = "threshold_alerts"
    const val MONITOR_CHANNEL = "active_monitoring"
    const val MONITOR_NOTIFICATION_ID = 1101

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ALERTS_CHANNEL,
                "Investment alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "High and low investment threshold alerts" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                MONITOR_CHANNEL,
                "Active monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows while faster monitoring is enabled" }
        )
    }

    fun showThresholdAlert(context: Context, holding: Holding, event: AlertEvent) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val isHigh = event.type == AlertType.HIGH
        val title = if (isHigh) "📈 ${holding.name} high alert" else "📉 ${holding.name} low alert"
        val currentValue = event.actualPrice * holding.units
        val pnl = currentValue - holding.investedAmount
        val pnlPct = if (holding.investedAmount > 0) pnl / holding.investedAmount * 100 else 0.0
        val direction = if (pnl >= 0) "+" else "−"
        val body = buildString {
            append("${holding.symbol}: ${money(event.actualPrice, holding.currency)}")
            append(" • target ${money(event.threshold, holding.currency)}")
            if (holding.investedAmount > 0) {
                append("\nValue ${money(currentValue, holding.currency)}")
                append(" • P/L $direction${money(pnl.absoluteValue, holding.currency)} (${String.format(Locale.US, "%.1f", pnlPct)}%)")
            }
        }

        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(holding.id.hashCode() xor event.type.hashCode(), notification)
        }
    }

    fun activeMonitoringNotification(context: Context, intervalMinutes: Int) =
        NotificationCompat.Builder(context, MONITOR_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("InvestAlert monitoring is active")
            .setContentText("Checking your enabled investments about every $intervalMinutes minutes")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun showTestAlert(context: Context) {
        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("InvestAlert test")
            .setContentText("Notifications are working on this device.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(9090, notification) }
    }

    private fun money(value: Double, currency: String): String {
        val prefix = when (currency.uppercase()) {
            "ZAR" -> "R"
            "USD" -> "$"
            else -> "$currency "
        }
        return prefix + String.format(Locale.US, "%,.2f", value)
    }
}
