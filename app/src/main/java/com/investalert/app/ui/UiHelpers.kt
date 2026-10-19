package com.investalert.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.absoluteValue

val GainGreen = Color(0xFF17823B)
val LossRed = Color(0xFFC62828)

@Composable
fun InvestAlertTheme(
    themeMode: String,
    accentName: String,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode.uppercase(Locale.US)) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val accent = when (accentName.uppercase(Locale.US)) {
        "BLUE" -> Color(0xFF1769AA)
        "GREEN" -> Color(0xFF2E7D32)
        "ORANGE" -> Color(0xFFB75B00)
        else -> Color(0xFF6D4FB3)
    }
    val onAccent = Color.White
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent,
            onPrimaryContainer = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.30f),
            tertiary = accent,
            onTertiary = onAccent
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.14f),
            tertiary = accent,
            onTertiary = onAccent
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun SimpleDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$label: $selected ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

fun money(value: Double, currency: String): String {
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

fun priceMoney(value: Double, currency: String): String {
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

fun signedMoney(value: Double, currency: String): String =
    (if (value >= 0) "+" else "−") + money(value.absoluteValue, currency)

fun signedPct(value: Double): String =
    (if (value >= 0) "+" else "−") + String.format(Locale.US, "%.2f%%", value.absoluteValue)

fun number(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.US, "%,.0f", value)
    else String.format(Locale.US, "%,.6f", value).trimEnd('0').trimEnd('.')

fun numericInput(value: String): String {
    val cleaned = value.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }
    var seenDot = false
    return buildString {
        cleaned.forEachIndexed { index, ch ->
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !seenDot -> { append(ch); seenDot = true }
                ch == '-' && index == 0 -> append(ch)
            }
        }
    }
}
