package com.investalert.app.data

import com.investalert.app.model.Holding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

object TraderProEngine {
    data class Analysis(
        val symbol: String,
        val price: Double,
        val currency: String,
        val score: Int,
        val stance: String,
        val confidence: String,
        val reasons: List<String>,
        val sma20: Double?,
        val sma50: Double?,
        val rsi14: Double?,
        val momentum30: Double?,
        val volatility30: Double?
    )

    data class AssistantReply(
        val title: String,
        val body: String,
        val analyses: List<Analysis> = emptyList(),
        val links: List<NewsItem> = emptyList(),
        val sourceNote: String = "Market research only. Prices and headlines can be delayed; verify important decisions."
    )

    private val usUniverse = listOf("MSFT","AAPL","GOOGL","AMZN","NVDA","META","JPM","V","BRK-B","TSLA","AVGO","COST","NFLX","AMD","LLY")
    private val jseUniverse = listOf("NPN.JO","SOL.JO","FSR.JO","SBK.JO","AGL.JO","ANG.JO","BTI.JO","CPI.JO","APN.JO","MTN.JO","BID.JO","SHP.JO")
    private val cryptoUniverse = listOf("BTC-USD","ETH-USD","SOL-USD","XRP-USD","BNB-USD","ADA-USD","DOGE-USD","SHIB-USD","LINK-USD","AVAX-USD")
    private val forexUniverse = listOf("USDZAR=X","EURZAR=X","GBPZAR=X","EURUSD=X","GBPUSD=X","USDJPY=X","AUDUSD=X","USDCHF=X")

    suspend fun answer(question: String, holdings: List<Holding>): AssistantReply = withContext(Dispatchers.IO) {
        val q = question.trim().lowercase(Locale.US)
        if (q.isBlank()) return@withContext AssistantReply("Trader Pro","Ask about your portfolio, a holding, market risk, or market opportunities.")

        val matched = holdings.firstOrNull { h ->
            q.contains(h.name.lowercase(Locale.US)) || q.contains(h.symbol.removeSuffix(".JO").lowercase(Locale.US))
        }

        val genericScan = listOf("best", "where to invest", "opportun", "portfolio", "risk", "sell", "reduce", "crypto", "forex", "jse", " us ").any { q.contains(it) }
        val discovered = if (matched == null && !genericScan && q.length >= 3) {
            runCatching { AssetSearchProvider.search(question, 5).firstOrNull() }.getOrNull()
        } else null

        when {
            matched != null -> {
                val a = runCatching { analyzeSymbol(matched.symbol) }.getOrNull()
                if (a == null) AssistantReply("${matched.name} review","Recent market history is unavailable right now.")
                else {
                    val news = runCatching {
                        NewsResearchProvider.news("${matched.name} ${matched.symbol}", 4)
                    }.getOrDefault(emptyList())
                    AssistantReply(
                        title = "${matched.name} • ${matched.symbol}",
                        body = buildString {
                            append("${a.stance} • score ${a.score}/100 • ${a.confidence} confidence. ")
                            append(a.reasons.joinToString(" "))
                            if (news.isNotEmpty()) append(" Recent related news is linked below.")
                        },
                        analyses = listOf(a),
                        links = news
                    )
                }
            }
            discovered != null -> analyzeDiscovered(discovered)
            q.contains("sell") || q.contains("reduce") || q.contains("worst") -> sellReview(holdings)
            q.contains("portfolio") || q.contains("risk") || q.contains("divers") -> portfolioReview(holdings)
            q.contains("crypto") || q.contains("coin") || q.contains("bitcoin") -> scan(cryptoUniverse, holdings, "Crypto market scan")
            q.contains("forex") || q.contains("currency") || q.contains("fx") -> scan(forexUniverse, holdings, "Forex market scan")
            q.contains("jse") || q.contains("south africa") -> scan(jseUniverse, holdings, "JSE market scan")
            q.contains("us") || q.contains("america") || q.contains("nasdaq") -> scan(usUniverse, holdings, "US market scan")
            q.contains("best") || q.contains("buy") || q.contains("opportun") || q.contains("invest") -> scan(jseUniverse.take(6)+usUniverse.take(6)+cryptoUniverse.take(4), holdings, "Cross-market scan")
            else -> AssistantReply(
                "Trader Pro",
                "I can analyse a specific holding, review portfolio risk, or scan a watch universe using trend, momentum, RSI and volatility."
            )
        }
    }

    private suspend fun analyzeDiscovered(asset: AssetSuggestion): AssistantReply {
        val a = runCatching { analyzeSymbol(asset.symbol) }.getOrNull()
            ?: return AssistantReply(asset.name, "I couldn't retrieve enough recent market history for ${asset.symbol}.")
        val news = runCatching { NewsResearchProvider.news("${asset.name} ${asset.symbol}", 4) }.getOrDefault(emptyList())
        return AssistantReply(
            title = "${asset.name} • ${asset.symbol}",
            body = buildString {
                append("${a.stance} • score ${a.score}/100 • ${a.confidence} confidence. ")
                append(a.reasons.joinToString(" "))
                if (news.isNotEmpty()) append(" Recent related news is linked below.")
            },
            analyses = listOf(a),
            links = news
        )
    }

    private suspend fun sellReview(holdings: List<Holding>): AssistantReply {
        if (holdings.isEmpty()) return AssistantReply("Sell / reduce review", "Add investments first so I can review your actual portfolio.")
        val analyses = mutableListOf<Analysis>()
        for (holding in holdings.filter { it.enabled }.take(12)) {
            runCatching { analyzeSymbol(holding.symbol) }.getOrNull()?.let { analyses += it }
            delay(80)
        }
        val weakest = analyses.sortedBy { it.score }.take(5)
        if (weakest.isEmpty()) return AssistantReply("Sell / reduce review", "I couldn't retrieve enough market history to review your holdings right now.")
        return AssistantReply(
            "Sell / reduce review",
            "These holdings currently have the weakest technical/risk scores: " +
                weakest.joinToString("; ") { "${it.symbol} ${it.score}/100 (${it.stance})" } +
                ". Treat this as a review list, not an automatic sell instruction; consider your original reason for owning each position, fees, tax and time horizon.",
            weakest
        )
    }

    private suspend fun portfolioReview(holdings: List<Holding>): AssistantReply {
        if (holdings.isEmpty()) return AssistantReply("Portfolio review","Add investments first so I can analyse your holdings.")
        val analyses = mutableListOf<Analysis>()
        for (holding in holdings.filter { it.enabled }.take(8)) {
            runCatching { analyzeSymbol(holding.symbol) }.getOrNull()?.let { analyses += it }
            delay(80)
        }
        val marketMix = holdings.groupBy { it.market }.mapValues { it.value.size }
        val body = buildString {
            append("You have ${holdings.size} holding(s). ")
            append("Market mix: ${marketMix.entries.joinToString { "${it.key} ${it.value}" }}. ")
            if (analyses.isNotEmpty()) {
                val weakest = analyses.minByOrNull { it.score }
                val strongest = analyses.maxByOrNull { it.score }
                if (strongest != null) append("Strongest current technical score: ${strongest.symbol} ${strongest.score}/100. ")
                if (weakest != null) append("Weakest current technical score: ${weakest.symbol} ${weakest.score}/100. ")
            }
            append("Use this as a research view rather than an automatic trade instruction.")
        }
        return AssistantReply("Portfolio review", body, analyses.sortedByDescending { it.score }.take(5))
    }

    private suspend fun scan(universe: List<String>, holdings: List<Holding>, title: String): AssistantReply {
        val owned = holdings.map { it.symbol.uppercase(Locale.US) }.toSet()
        val results = mutableListOf<Analysis>()
        for (symbol in universe) {
            if (symbol in owned) continue
            runCatching { analyzeSymbol(symbol) }.getOrNull()?.let { results += it }
            delay(100)
        }
        val top = results.sortedByDescending { it.score }.take(5)
        if (top.isEmpty()) return AssistantReply(title,"Market history is unavailable right now.")
        return AssistantReply(
            title,
            "Highest-scoring symbols in the current watch universe: " + top.joinToString("; ") { "${it.symbol} ${it.score}/100 (${it.stance})" } + ". Use this as a shortlist for further research.",
            top
        )
    }

    suspend fun analyzeSymbol(symbol: String): Analysis {
        val s = fetchHistory(symbol)
        val closes = s.third
        require(closes.size >= 20) { "Not enough history" }
        val price = closes.last()
        val sma20 = closes.takeLast(20).average()
        val sma50 = if (closes.size >= 50) closes.takeLast(50).average() else null
        val sma200 = if (closes.size >= 200) closes.takeLast(200).average() else null
        val rsi = rsi(closes,14)
        val momentum30 = if (closes.size >= 31) (price / closes[closes.size-31] - 1.0) * 100.0 else null
        val momentum90 = if (closes.size >= 91) (price / closes[closes.size-91] - 1.0) * 100.0 else null
        val vol = volatility(closes.takeLast(minOf(31,closes.size)))
        val yearWindow = closes.takeLast(minOf(252, closes.size))
        val yearLow = yearWindow.minOrNull()
        val yearHigh = yearWindow.maxOrNull()

        var score = 50
        val reasons = mutableListOf<String>()
        if (price > sma20) { score += 10; reasons += "Price is above the 20-day average." }
        else { score -= 10; reasons += "Price is below the 20-day average." }

        sma50?.let {
            if (price > it) { score += 10; reasons += "Price is above the 50-day average." }
            else { score -= 10; reasons += "Price is below the 50-day average." }
        }

        sma200?.let {
            if (price > it) { score += 7; reasons += "Long-term trend is above the 200-day average." }
            else { score -= 7; reasons += "Long-term trend is below the 200-day average." }
        }

        rsi?.let {
            when {
                it in 40.0..65.0 -> { score += 10; reasons += "RSI is in a balanced momentum range (${fmt1(it)})." }
                it > 72.0 -> { score -= 10; reasons += "RSI is elevated (${fmt1(it)})." }
                it < 30.0 -> { score -= 5; reasons += "RSI is weak (${fmt1(it)})." }
                else -> reasons += "RSI is ${fmt1(it)}."
            }
        }

        momentum30?.let {
            when {
                it > 8 -> { score += 10; reasons += "30-day momentum is strong (${signedPct(it)})." }
                it > 0 -> { score += 5; reasons += "30-day momentum is positive (${signedPct(it)})." }
                it < -10 -> { score -= 10; reasons += "30-day momentum is sharply negative (${signedPct(it)})." }
                else -> { score -= 5; reasons += "30-day momentum is negative (${signedPct(it)})." }
            }
        }

        momentum90?.let {
            if (it > 0) score += 4 else score -= 4
            reasons += "90-day momentum is ${signedPct(it)}."
        }

        vol?.let {
            if (it > 80) { score -= 10; reasons += "Recent volatility is very high (${fmt1(it)}% annualised)." }
            else if (it > 55) { score -= 8; reasons += "Recent volatility is high (${fmt1(it)}% annualised)." }
            else reasons += "Recent volatility is about ${fmt1(it)}% annualised."
        }

        if (yearLow != null && yearHigh != null && yearHigh > yearLow) {
            val rangePosition = (price - yearLow) / (yearHigh - yearLow) * 100.0
            reasons += when {
                rangePosition >= 92 -> "Price is near its 52-week high; momentum is strong, but entry risk is less forgiving."
                rangePosition <= 15 -> "Price is near its 52-week low; treat this as a recovery/risk situation rather than a bargain by default."
                else -> "Price is around ${fmt1(rangePosition)}% of its 52-week range."
            }
        }

        score = score.coerceIn(0,100)
        val stance = when {
            score >= 75 -> "Strong trend"
            score >= 60 -> "Positive trend"
            score >= 45 -> "Neutral"
            score >= 30 -> "Weak trend"
            else -> "High volatility / weak trend"
        }
        val confidence = when {
            closes.size >= 200 -> "Medium-high"
            closes.size >= 90 -> "Medium"
            else -> "Low-medium"
        }

        return Analysis(symbol,price,s.second,score,stance,confidence,reasons,sma20,sma50,rsi,momentum30,vol)
    }

    private fun fetchHistory(symbol: String): Triple<String,String,List<Double>> {
        val encoded = URLEncoder.encode(symbol.trim(),Charsets.UTF_8.name())
        val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=1d&range=1y")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent","Mozilla/5.0 InvestAlert/1.4")
            setRequestProperty("Accept","application/json")
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) error("Market data HTTP $code")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val result = JSONObject(body).getJSONObject("chart").optJSONArray("result") ?: error("No market history")
            if (result.length()==0) error("Unknown symbol")
            val item = result.getJSONObject(0)
            val meta = item.getJSONObject("meta")
            var currency = meta.optString("currency","USD").ifBlank { "USD" }
            val closesJson = item.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close") ?: error("No closing-price history")
            val closes = buildList {
                for (i in 0 until closesJson.length()) {
                    if (!closesJson.isNull(i)) {
                        var v = closesJson.optDouble(i,Double.NaN)
                        if (!v.isNaN() && v>0) {
                            if (currency.equals("ZAc",true)) v /= 100.0
                            add(v)
                        }
                    }
                }
            }
            if (currency.equals("ZAc",true)) currency = "ZAR"
            return Triple(symbol.uppercase(Locale.US),currency.uppercase(Locale.US),closes)
        } finally {
            c.disconnect()
        }
    }

    private fun rsi(closes: List<Double>, period: Int): Double? {
        if (closes.size <= period) return null
        var gains = 0.0
        var losses = 0.0
        val start = closes.size - period - 1
        for (i in start+1 until closes.size) {
            val d = closes[i]-closes[i-1]
            if (d >= 0) gains += d else losses -= d
        }
        if (losses == 0.0) return 100.0
        val rs = (gains/period)/(losses/period)
        return 100.0 - (100.0/(1.0+rs))
    }

    private fun volatility(closes: List<Double>): Double? {
        if (closes.size < 3) return null
        val returns = closes.zipWithNext { a,b -> (b/a)-1.0 }
        val mean = returns.average()
        val variance = returns.sumOf { (it-mean).pow(2) }/(returns.size-1)
        return sqrt(variance)*sqrt(252.0)*100.0
    }

    private fun fmt1(v: Double) = String.format(Locale.US,"%.1f",v)
    private fun signedPct(v: Double) = (if (v>=0) "+" else "−") + String.format(Locale.US,"%.1f%%",kotlin.math.abs(v))
}
