package com.investalert.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object YahooPriceProvider {
    data class Quote(
        val symbol: String,
        val price: Double,
        val currency: String,
        val dayChangePercent: Double?,
        val fetchedAtEpochMs: Long,
        val source: String = "Public market feed (may be delayed)"
    )

    suspend fun fetchPrice(symbol: String): Quote = withContext(Dispatchers.IO) {
        val hosts = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
        var lastError: Throwable? = null
        for (host in hosts) {
            try {
                return@withContext fetchFromHost(host, symbol)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException(lastError?.message ?: "Price unavailable for $symbol")
    }

    private fun fetchFromHost(host: String, symbol: String): Quote {
        val encoded = URLEncoder.encode(symbol.trim(), Charsets.UTF_8.name())
        val url = URL("https://$host/v8/finance/chart/$encoded?interval=1m&range=1d")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) InvestAlert/1.4")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "en-ZA,en;q=0.9")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Price feed HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val result = root.getJSONObject("chart").optJSONArray("result")
                ?: throw IllegalStateException("No price returned for $symbol")
            if (result.length() == 0) throw IllegalStateException("Unknown symbol $symbol")

            val item = result.getJSONObject(0)
            val meta = item.getJSONObject("meta")
            var price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) price = latestClose(item)
            if (price.isNaN() || price <= 0.0) throw IllegalStateException("No valid price for $symbol")

            var previousClose = meta.optDouble("chartPreviousClose", Double.NaN)
            if (previousClose.isNaN()) previousClose = meta.optDouble("previousClose", Double.NaN)

            var currency = meta.optString("currency", "USD").ifBlank { "USD" }
            if (currency.equals("ZAc", ignoreCase = true)) {
                price /= 100.0
                if (!previousClose.isNaN()) previousClose /= 100.0
                currency = "ZAR"
            }

            val dayChangePercent = if (!previousClose.isNaN() && previousClose > 0.0) {
                (price / previousClose - 1.0) * 100.0
            } else null

            return Quote(
                symbol = symbol,
                price = price,
                currency = currency.uppercase(),
                dayChangePercent = dayChangePercent,
                fetchedAtEpochMs = System.currentTimeMillis()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun latestClose(item: JSONObject): Double {
        val quotes = item.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?.optJSONArray("close") ?: return Double.NaN
        for (i in quotes.length() - 1 downTo 0) {
            if (!quotes.isNull(i)) {
                val value = quotes.optDouble(i, Double.NaN)
                if (!value.isNaN()) return value
            }
        }
        return Double.NaN
    }
}
