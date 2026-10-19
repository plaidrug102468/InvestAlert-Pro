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
        // JSE / South Africa
        AssetSuggestion("APN.JO","Aspen Pharmacare Holdings","JSE","Equity","ZAR"),
        AssetSuggestion("SOL.JO","Sasol Limited","JSE","Equity","ZAR"),
        AssetSuggestion("NPN.JO","Naspers Limited","JSE","Equity","ZAR"),
        AssetSuggestion("PRX.JO","Prosus N.V.","JSE","Equity","ZAR"),
        AssetSuggestion("MTN.JO","MTN Group Limited","JSE","Equity","ZAR"),
        AssetSuggestion("FSR.JO","FirstRand Limited","JSE","Equity","ZAR"),
        AssetSuggestion("SBK.JO","Standard Bank Group","JSE","Equity","ZAR"),
        AssetSuggestion("AGL.JO","Anglo American plc","JSE","Equity","ZAR"),
        AssetSuggestion("ANG.JO","AngloGold Ashanti plc","JSE","Equity","ZAR"),
        AssetSuggestion("CPI.JO","Capitec Bank Holdings","JSE","Equity","ZAR"),
        AssetSuggestion("SHP.JO","Shoprite Holdings","JSE","Equity","ZAR"),
        AssetSuggestion("BID.JO","Bid Corporation","JSE","Equity","ZAR"),
        AssetSuggestion("BTI.JO","British American Tobacco","JSE","Equity","ZAR"),
        AssetSuggestion("VOD.JO","Vodacom Group","JSE","Equity","ZAR"),
        AssetSuggestion("REM.JO","Remgro Limited","JSE","Equity","ZAR"),
        AssetSuggestion("GLN.JO","Glencore plc","JSE","Equity","ZAR"),
        AssetSuggestion("ABG.JO","Absa Group","JSE","Equity","ZAR"),
        AssetSuggestion("NED.JO","Nedbank Group","JSE","Equity","ZAR"),
        AssetSuggestion("OMU.JO","Old Mutual","JSE","Equity","ZAR"),
        AssetSuggestion("SLM.JO","Sanlam","JSE","Equity","ZAR"),
        AssetSuggestion("AGOGE.JO","Allan Gray - Orbis Global Equity Feeder AMETF","JSE","AMETF","ZAR"),
        AssetSuggestion("AGOGB.JO","Allan Gray - Orbis Global Balanced Feeder AMETF","JSE","AMETF","ZAR"),

        // Allan Gray managed funds / unit trusts
        AssetSuggestion("AGF:EQUITY","Allan Gray Equity Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:BALANCED","Allan Gray Balanced Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:TFBAL","Allan Gray Tax-Free Balanced Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:STABLE","Allan Gray Stable Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:INTEREST","Allan Gray Interest Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:BOND","Allan Gray Bond Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:INCOME","Allan Gray Income Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:MONEYMARKET","Allan Gray Money Market Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:ORBIS-EQ","Allan Gray - Orbis Global Equity Feeder Fund","Allan Gray","Unit Trust","ZAR"),
        AssetSuggestion("AGF:ORBIS-BAL","Allan Gray - Orbis Global Balanced Feeder Fund","Allan Gray","Unit Trust","ZAR"),

        // US / global equities
        AssetSuggestion("AAPL","Apple Inc.","NASDAQ","Equity","USD"),
        AssetSuggestion("MSFT","Microsoft Corporation","NASDAQ","Equity","USD"),
        AssetSuggestion("AMZN","Amazon.com, Inc.","NASDAQ","Equity","USD"),
        AssetSuggestion("GOOGL","Alphabet Inc.","NASDAQ","Equity","USD"),
        AssetSuggestion("META","Meta Platforms, Inc.","NASDAQ","Equity","USD"),
        AssetSuggestion("NVDA","NVIDIA Corporation","NASDAQ","Equity","USD"),
        AssetSuggestion("TSLA","Tesla, Inc.","NASDAQ","Equity","USD"),
        AssetSuggestion("NFLX","Netflix, Inc.","NASDAQ","Equity","USD"),
        AssetSuggestion("AMD","Advanced Micro Devices","NASDAQ","Equity","USD"),
        AssetSuggestion("AVGO","Broadcom Inc.","NASDAQ","Equity","USD"),
        AssetSuggestion("JPM","JPMorgan Chase & Co.","NYSE","Equity","USD"),
        AssetSuggestion("V","Visa Inc.","NYSE","Equity","USD"),
        AssetSuggestion("MA","Mastercard Incorporated","NYSE","Equity","USD"),
        AssetSuggestion("KO","The Coca-Cola Company","NYSE","Equity","USD"),
        AssetSuggestion("DIS","The Walt Disney Company","NYSE","Equity","USD"),
        AssetSuggestion("WMT","Walmart Inc.","NYSE","Equity","USD"),
        AssetSuggestion("BRK-B","Berkshire Hathaway Inc.","NYSE","Equity","USD"),

        // ETFs / indices
        AssetSuggestion("VOO","Vanguard S&P 500 ETF","NYSE Arca","ETF","USD"),
        AssetSuggestion("SPY","SPDR S&P 500 ETF Trust","NYSE Arca","ETF","USD"),
        AssetSuggestion("QQQ","Invesco QQQ Trust","NASDAQ","ETF","USD"),
        AssetSuggestion("VTI","Vanguard Total Stock Market ETF","NYSE Arca","ETF","USD"),
        AssetSuggestion("GLD","SPDR Gold Shares","NYSE Arca","ETF","USD"),
        AssetSuggestion("^GSPC","S&P 500 Index","Index","Index","USD"),
        AssetSuggestion("^IXIC","NASDAQ Composite","Index","Index","USD"),
        AssetSuggestion("^DJI","Dow Jones Industrial Average","Index","Index","USD"),

        // Crypto
        AssetSuggestion("BTC-USD","Bitcoin","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("ETH-USD","Ethereum","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("SOL-USD","Solana","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("XRP-USD","XRP","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("BNB-USD","BNB","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("ADA-USD","Cardano","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("DOGE-USD","Dogecoin","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("SHIB-USD","Shiba Inu","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("LINK-USD","Chainlink","Crypto","Cryptocurrency","USD"),
        AssetSuggestion("AVAX-USD","Avalanche","Crypto","Cryptocurrency","USD"),

        // Forex
        AssetSuggestion("USDZAR=X","USD/ZAR","Forex","Forex","ZAR"),
        AssetSuggestion("EURZAR=X","EUR/ZAR","Forex","Forex","ZAR"),
        AssetSuggestion("GBPZAR=X","GBP/ZAR","Forex","Forex","ZAR"),
        AssetSuggestion("EURUSD=X","EUR/USD","Forex","Forex","USD"),
        AssetSuggestion("GBPUSD=X","GBP/USD","Forex","Forex","USD"),
        AssetSuggestion("USDJPY=X","USD/JPY","Forex","Forex","JPY"),
        AssetSuggestion("AUDUSD=X","AUD/USD","Forex","Forex","USD"),
        AssetSuggestion("USDCHF=X","USD/CHF","Forex","Forex","CHF"),

        // Commodities
        AssetSuggestion("GC=F","Gold Futures","COMEX","Commodity / Future","USD"),
        AssetSuggestion("SI=F","Silver Futures","COMEX","Commodity / Future","USD"),
        AssetSuggestion("CL=F","Crude Oil WTI Futures","NYMEX","Commodity / Future","USD"),
        AssetSuggestion("BZ=F","Brent Crude Oil Futures","NYMEX","Commodity / Future","USD")
    )

    fun localSearch(query: String, limit: Int = 30): List<AssetSuggestion> {
        val q = query.trim()
        if (q.isBlank()) return featured.take(limit)
        val words = q.lowercase(Locale.US).split(" ").filter { it.isNotBlank() }
        return featured
            .map { item ->
                val searchable = "${item.name} ${item.symbol} ${item.exchange} ${item.assetType}".lowercase(Locale.US)
                val score = when {
                    item.symbol.equals(q, true) -> 100
                    item.name.equals(q, true) -> 95
                    item.symbol.startsWith(q, true) -> 90
                    item.name.startsWith(q, true) -> 85
                    words.all { searchable.contains(it) } -> 70
                    else -> 0
                }
                item to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    suspend fun search(query: String, limit: Int = 30): List<AssetSuggestion> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext localSearch(q, limit)

        val local = localSearch(q, limit)
        val remote = runCatching { searchRemote(q, limit) }.getOrDefault(emptyList())

        (local + remote)
            .distinctBy { it.symbol.uppercase(Locale.US) }
            .take(limit)
    }

    private fun searchRemote(query: String, limit: Int): List<AssetSuggestion> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val hosts = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
        var lastResult = emptyList<AssetSuggestion>()

        for (host in hosts) {
            val url = URL(
                "https://$host/v1/finance/search?q=$encoded&quotesCount=$limit&newsCount=0&enableFuzzyQuery=true"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) InvestAlert/1.4")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", "en-ZA,en;q=0.9")
            }
            try {
                if (connection.responseCode !in 200..299) continue
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val quotes = JSONObject(body).optJSONArray("quotes") ?: continue
                lastResult = buildList {
                    for (i in 0 until quotes.length()) {
                        val item = quotes.optJSONObject(i) ?: continue
                        val symbol = item.optString("symbol").trim()
                        if (symbol.isBlank()) continue
                        val type = item.optString("quoteType").ifBlank { "Other" }
                        if (!isSupportedType(type)) continue
                        add(
                            AssetSuggestion(
                                symbol = symbol,
                                name = item.optString("longname").ifBlank {
                                    item.optString("shortname").ifBlank { symbol }
                                },
                                exchange = normalizeExchange(
                                    item.optString("exchDisp").ifBlank {
                                        item.optString("exchange").ifBlank { "Market" }
                                    },
                                    type
                                ),
                                assetType = normalizeType(type),
                                currency = item.optString("currency").ifBlank {
                                    inferCurrency(symbol, type)
                                }
                            )
                        )
                    }
                }
                if (lastResult.isNotEmpty()) break
            } finally {
                connection.disconnect()
            }
        }
        return lastResult
    }

    private fun isSupportedType(raw: String): Boolean = raw.uppercase(Locale.US) in setOf(
        "EQUITY", "ETF", "MUTUALFUND", "CRYPTOCURRENCY", "CURRENCY", "FUTURE", "INDEX"
    )

    private fun inferCurrency(symbol: String, rawType: String): String? {
        val upper = symbol.uppercase(Locale.US)
        return when {
            upper.endsWith(".JO") -> "ZAR"
            upper.contains("-USD") -> "USD"
            upper.endsWith("=X") && upper.length >= 7 -> upper.substring(3, 6)
            rawType.equals("CRYPTOCURRENCY", true) -> "USD"
            else -> null
        }
    }

    private fun normalizeExchange(raw: String, rawType: String): String = when {
        rawType.equals("CRYPTOCURRENCY", true) || raw.equals("CCC", true) -> "Crypto"
        rawType.equals("CURRENCY", true) || raw.equals("CCY", true) -> "Forex"
        else -> raw
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
