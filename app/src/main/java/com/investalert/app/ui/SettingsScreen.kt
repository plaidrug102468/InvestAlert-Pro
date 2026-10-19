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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.investalert.app.BuildConfig
import com.investalert.app.data.AppSettings

@Composable
fun SettingsScreen(
    modifier: Modifier,
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
    onBaseCurrency: (String) -> Unit,
    onThemeMode: (String) -> Unit,
    onAccent: (String) -> Unit,
    onCompactCards: (Boolean) -> Unit,
    onActiveMonitoring: (Boolean) -> Unit,
    onMonitoringMinutes: (Int) -> Unit,
    onPriceAlerts: (Boolean) -> Unit,
    onDailyMoveAlerts: (Boolean) -> Unit,
    onDailyMoveThreshold: (Double) -> Unit,
    onNewsAlerts: (Boolean) -> Unit,
    onMorningBrief: (Boolean) -> Unit,
    onTestNotification: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Display", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SimpleDropdown(
            label = "Base currency",
            selected = baseCurrency,
            options = AppSettings.supportedCurrencies,
            onSelected = onBaseCurrency
        )
        SimpleDropdown(
            label = "Theme",
            selected = themeMode.lowercase().replaceFirstChar { it.uppercase() },
            options = AppSettings.themeModes.map { it.lowercase().replaceFirstChar { ch -> ch.uppercase() } },
            onSelected = { onThemeMode(it.uppercase()) }
        )
        SimpleDropdown(
            label = "Accent",
            selected = accent.lowercase().replaceFirstChar { it.uppercase() },
            options = AppSettings.accents.map { it.lowercase().replaceFirstChar { ch -> ch.uppercase() } },
            onSelected = { onAccent(it.uppercase()) }
        )
        SettingToggle(
            title = "Compact investment cards",
            subtitle = "Show the essentials first and hide extra figures.",
            checked = compactCards,
            onChecked = onCompactCards
        )

        HorizontalDivider()
        Text("Monitoring & alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        SimpleDropdown(
            label = "Background checks",
            selected = when (monitoringMinutes) {
                60 -> "1 hour"
                180 -> "3 hours"
                else -> "$monitoringMinutes min"
            },
            options = listOf("15 min", "30 min", "1 hour", "3 hours"),
            onSelected = {
                val minutes = when (it) {
                    "30 min" -> 30
                    "1 hour" -> 60
                    "3 hours" -> 180
                    else -> 15
                }
                onMonitoringMinutes(minutes)
            }
        )

        SettingToggle(
            title = "Price target alerts",
            subtitle = "Notify when a holding or watchlist item crosses its low or high target.",
            checked = priceAlerts,
            onChecked = onPriceAlerts
        )

        SettingToggle(
            title = "Large daily move alerts",
            subtitle = "Notify once per day when a monitored instrument moves beyond your threshold.",
            checked = dailyMoveAlerts,
            onChecked = onDailyMoveAlerts
        )
        if (dailyMoveAlerts) {
            SimpleDropdown(
                label = "Daily move threshold",
                selected = "${dailyMoveThreshold.toInt()}%",
                options = listOf("3%", "5%", "8%", "10%", "15%"),
                onSelected = { onDailyMoveThreshold(it.removeSuffix("%").toDoubleOrNull() ?: 5.0) }
            )
        }

        SettingToggle(
            title = "Investment news alerts",
            subtitle = "Check monitored holdings and watchlist items for new headlines.",
            checked = newsAlerts,
            onChecked = onNewsAlerts
        )

        SettingToggle(
            title = "Morning brief",
            subtitle = "Daily portfolio summary around 07:30.",
            checked = morningBrief,
            onChecked = onMorningBrief
        )

        SettingToggle(
            title = "Active monitoring",
            subtitle = if (activeMonitoring) "Faster checks are active." else "Optional faster checks while the monitoring service is running.",
            checked = activeMonitoring,
            onChecked = onActiveMonitoring
        )

        OutlinedButton(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
            Text("Send test notification")
        }

        HorizontalDivider()
        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Market prices and headlines can be delayed. Verify important trade decisions.", style = MaterialTheme.typography.bodySmall)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
