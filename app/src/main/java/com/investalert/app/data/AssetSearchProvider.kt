package com.investalert.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

data class AssetSuggestion(
    val symbol: String,
    val name: String,
    val exchange: String,
    val assetType: String,
    val currency: String?
)

object AssetSearchProvider {
    private val featured = listOf(
        AssetSuggestion("APN.JO", "Aspen Pharmacare Holdings", "JSE", "Equity", "ZAR"),
        AssetSuggestion("SOL.JO", "Sasol Limited", "JSE", "Equity", "ZAR"),
        AssetSuggestion("NPN.JO", "Naspers Limited", "JSE", "Equity", "ZAR"),
        AssetSuggestion("MTN.JO", "MTN Group Limited", "JSE", "Equity", "ZAR"),
        AssetSuggestion("FSR.JO", "FirstRand Limited", "JSE", "Equity", "ZAR"),
        AssetSuggestion("SBK.JO", "Standard Bank Group", "JSE", "Equity", "ZAR"),
        AssetSuggestion("AGL.JO", "Anglo American plc", "JSE", "Equity", "ZAR"),
        AssetSuggestion("CPI.JO", "Capitec Bank Holdings", "JSE", "Equity", "ZAR"),
        AssetSuggestion("AAPL", "Apple Inc.", "NASDAQ", "Equity", "USD"),
        AssetSuggestion("MSFT", "Microsoft Corporation", "NASDAQ", "Equity", "USD"),
        AssetSuggestion("AMZN", "Amazon.com, Inc.", "NASDAQ", "Equity", "USD"),
        AssetSuggestion("GOOGL", "Alphabet Inc.", "NASDAQ", "Equity", "USD"),
        AssetSuggestion("META", "Meta Platforms, Inc.", "NASDAQ", "Equity", "USD"),
        AssetSuggestion("NVDA", "NVIDIA Corporation", "NASDAQ", "Equity", "USD"),
        AssetSuggestion("TSLA", "Tesla, Inc.", "NASDAQ", "Equity", "USD"),
        AssetSuggestion("BTC-USD", "Bitcoin", "Crypto", "Cryptocurrency", "USD"),
        AssetSuggestion("ETH-USD", "Ethereum", "Crypto", "Cryptocurrency", "USD"),
        AssetSuggestion("SOL-USD", "Solana", "Crypto", "Cryptocurrency", "USD"),
        AssetSuggestion("XRP-USD", "XRP", "Crypto", "Cryptocurrency", "USD"),
        AssetSuggestion("EURUSD=X", "EUR/USD", "FX", "Forex", "USD"),
        AssetSuggestion("GBPUSD=X", "GBP/USD", "FX", "Forex", "USD"),
        AssetSuggestion("USDZAR=X", "USD/ZAR", "FX", "Forex", "ZAR"),
        AssetSuggestion("EURZAR=X", "EUR/ZAR", "FX", "Forex", "ZAR"),
        AssetSuggestion("GC=F", "Gold Futures", "COMEX", "Commodity", "USD"),
        AssetSuggestion("SI=F", "Silver Futures", "COMEX", "Commodity", "USD"),
        AssetSuggestion("CL=F", "Crude Oil WTI Futures", "NYMEX", "Commodity", "USD")
    )

    suspend fun search(query: String, limit: Int = 30): List<AssetSuggestion> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) {
            return@withContext featured.take(limit)
        }

        val local = featured.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.symbol.contains(q, ignoreCase = true) ||
                it.assetType.contains(q, ignoreCase = true)
        }

        val remote = runCatching { searchRemote(q, limit) }.getOrDefault(emptyList())
        (local + remote)
            .distinctBy { it.symbol.uppercase(Locale.US) }
            .take(limit)
    }

    private fun searchRemote(query: String, limit: Int): List<AssetSuggestion> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = URL("https://query1.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=$limit&newsCount=0")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Mozilla/5.0 InvestAlert/1.1")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val quotes = JSONObject(body).optJSONArray("quotes") ?: return emptyList()
            return buildList {
                for (i in 0 until quotes.length()) {
                    val item = quotes.optJSONObject(i) ?: continue
                    val symbol = item.optString("symbol").trim()
                    if (symbol.isBlank()) continue
                    val type = item.optString("quoteType").ifBlank { "Other" }
                    add(
                        AssetSuggestion(
                            symbol = symbol,
                            name = item.optString("longname").ifBlank {
                                item.optString("shortname").ifBlank { symbol }
                            },
                            exchange = item.optString("exchDisp").ifBlank {
                                item.optString("exchange").ifBlank { "Market" }
                            },
                            assetType = normalizeType(type),
                            currency = item.optString("currency").ifBlank { null }
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeType(raw: String): String = when (raw.uppercase(Locale.US)) {
        "EQUITY" -> "Equity"
        "ETF" -> "ETF"
        "MUTUALFUND" -> "Fund"
        "CRYPTOCURRENCY" -> "Cryptocurrency"
        "CURRENCY" -> "Forex"
        "FUTURE" -> "Commodity / Future"
        "INDEX" -> "Index"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}
