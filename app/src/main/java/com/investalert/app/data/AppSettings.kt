package com.investalert.app.data

import android.content.Context
import java.time.LocalDate

class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("invest_alert_settings", Context.MODE_PRIVATE)

    fun baseCurrency(): String = prefs.getString(KEY_BASE_CURRENCY, "ZAR") ?: "ZAR"
    fun setBaseCurrency(currency: String) = prefs.edit().putString(KEY_BASE_CURRENCY, currency.uppercase()).apply()

    fun themeMode(): String = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
    fun setThemeMode(value: String) = prefs.edit().putString(KEY_THEME, value).apply()

    fun accent(): String = prefs.getString(KEY_ACCENT, "PURPLE") ?: "PURPLE"
    fun setAccent(value: String) = prefs.edit().putString(KEY_ACCENT, value).apply()

    fun compactCards(): Boolean = prefs.getBoolean(KEY_COMPACT, false)
    fun setCompactCards(value: Boolean) = prefs.edit().putBoolean(KEY_COMPACT, value).apply()

    fun monitoringMinutes(): Int = prefs.getInt(KEY_MONITOR_MINUTES, 15).coerceAtLeast(15)
    fun setMonitoringMinutes(value: Int) = prefs.edit().putInt(KEY_MONITOR_MINUTES, value.coerceAtLeast(15)).apply()

    fun priceAlertsEnabled(): Boolean = prefs.getBoolean(KEY_PRICE_ALERTS, true)
    fun setPriceAlertsEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_PRICE_ALERTS, value).apply()

    fun newsAlertsEnabled(): Boolean = prefs.getBoolean(KEY_NEWS_ALERTS, false)
    fun setNewsAlertsEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_NEWS_ALERTS, value).apply()

    fun dailyMoveAlertsEnabled(): Boolean = prefs.getBoolean(KEY_DAILY_MOVE_ALERTS, false)
    fun setDailyMoveAlertsEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_DAILY_MOVE_ALERTS, value).apply()

    fun dailyMoveThreshold(): Double = prefs.getFloat(KEY_DAILY_MOVE_THRESHOLD, 5f).toDouble()
    fun setDailyMoveThreshold(value: Double) = prefs.edit().putFloat(KEY_DAILY_MOVE_THRESHOLD, value.toFloat().coerceIn(1f, 25f)).apply()

    fun morningBriefEnabled(): Boolean = prefs.getBoolean(KEY_MORNING_BRIEF, false)
    fun setMorningBriefEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_MORNING_BRIEF, value).apply()

    fun isNewsSeeded(): Boolean = prefs.getBoolean(KEY_NEWS_SEEDED, false)
    fun setNewsSeeded(value: Boolean) = prefs.edit().putBoolean(KEY_NEWS_SEEDED, value).apply()

    fun seenNews(): Set<String> = prefs.getStringSet(KEY_SEEN_NEWS, emptySet()) ?: emptySet()
    fun addSeenNews(keys: Collection<String>) {
        val merged = (seenNews() + keys).toList().takeLast(250).toSet()
        prefs.edit().putStringSet(KEY_SEEN_NEWS, merged).apply()
    }

    fun shouldAlertDailyMove(symbol: String): Boolean {
        val key = "daily_move_${symbol.uppercase()}"
        val today = LocalDate.now().toString()
        return prefs.getString(key, null) != today
    }

    fun markDailyMoveAlerted(symbol: String) {
        prefs.edit().putString("daily_move_${symbol.uppercase()}", LocalDate.now().toString()).apply()
    }

    companion object {
        private const val KEY_BASE_CURRENCY = "base_currency"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_COMPACT = "compact_cards"
        private const val KEY_MONITOR_MINUTES = "monitoring_minutes"
        private const val KEY_PRICE_ALERTS = "price_alerts"
        private const val KEY_NEWS_ALERTS = "news_alerts"
        private const val KEY_DAILY_MOVE_ALERTS = "daily_move_alerts"
        private const val KEY_DAILY_MOVE_THRESHOLD = "daily_move_threshold"
        private const val KEY_MORNING_BRIEF = "morning_brief"
        private const val KEY_NEWS_SEEDED = "news_seeded"
        private const val KEY_SEEN_NEWS = "seen_news"

        val supportedCurrencies = listOf(
            "ZAR", "USD", "EUR", "GBP", "AUD", "CAD", "JPY", "CHF", "NZD",
            "CNY", "HKD", "SGD", "AED", "INR", "BRL"
        )
        val themeModes = listOf("SYSTEM", "LIGHT", "DARK")
        val accents = listOf("PURPLE", "BLUE", "GREEN", "ORANGE")
        val monitoringOptions = listOf(15, 30, 60, 180)
    }
}
