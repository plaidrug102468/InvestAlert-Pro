package com.investalert.app.data

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("invest_alert_settings", Context.MODE_PRIVATE)

    fun baseCurrency(): String = prefs.getString(KEY_BASE_CURRENCY, "ZAR") ?: "ZAR"

    fun setBaseCurrency(currency: String) {
        prefs.edit().putString(KEY_BASE_CURRENCY, currency.uppercase()).apply()
    }

    companion object {
        private const val KEY_BASE_CURRENCY = "base_currency"

        val supportedCurrencies = listOf(
            "ZAR", "USD", "EUR", "GBP", "AUD", "CAD", "JPY", "CHF", "NZD",
            "CNY", "HKD", "SGD", "AED", "INR", "BRL"
        )
    }
}
