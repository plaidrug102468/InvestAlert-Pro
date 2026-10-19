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
        val sma200: Double?,
        val rsi14: Double?,
        val momentum30: Double?,
        val momentum90: Double?,
        val volatility30: Double?,
        val yearLow: Double?,
        val yearHigh: Double?,
        val chartPrices: List<Double> = emptyList()
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

    suspend fun answer(
        question: String,
        holdings: List<Holding>,
        attachments: List<TraderAttachment> = emptyList(),
        conversationContext: String = ""
    ): AssistantReply = withContext(Dispatchers.IO) {
        val attachmentText = attachments.joinToString("\n\n") { it.summary }.take(18000)
        val q = question.trim().lowercase(Locale.US)
        if (q.isBlank()) return@withContext AssistantReply("Trader Pro","Ask about your portfolio, a holding, market risk, or market opportunities.")

        val matched = holdings.firstOrNull { h ->
            q.contains(h.name.lowercase(Locale.US)) || q.contains(h.symbol.removeSuffix(".JO").lowercase(Locale.US))
        }

        val followUpSymbol = if (
            matched == null &&
            conversationContext.isNotBlank() &&
            listOf(" it", "that", "this one", "same", "what about", "and what").any { q.contains(it) }
        ) {
            recentSymbol(conversationContext)
        } else null

        val followUpAsset = followUpSymbol?.let { sym ->
            runCatching { AssetSearchProvider.search(sym, 5).firstOrNull { it.symbol.equals(sym, true) } }
                .getOrNull()
                ?: holdings.firstOrNull { it.symbol.equals(sym, true) }?.let {
                    AssetSuggestion(it.symbol, it.name, it.market, it.assetType, it.currency)
                }
        }

        val broadOpportunity = listOf(
            "top ", "top5", "top 5", "best ", "where to invest", "places to invest",
            "what should i invest", "what should we invest", "what should i buy",
            "what to buy", "opportunities", "opportunity today", "invest in today",
            "investment ideas", "ideas today", "research candidates"
        ).any { q.contains(it) }

        val genericScan = broadOpportunity || listOf(
            "portfolio", "risk", "sell", "reduce", "crypto", "forex", "jse", " us "
        ).any { q.contains(it) }

        val mentionedAsset = if (!broadOpportunity) AssetSearchProvider.mentionedAsset(question) else null

        val discovered = if (matched == null && mentionedAsset == null && !genericScan && q.length >= 3) {
            runCatching { AssetSearchProvider.search(question, 5).firstOrNull() }.getOrNull()
        } else null

        val attachmentAsset = if (attachments.isNotEmpty()) {
            val parsed = InvestmentScreenshotParser.parse(attachmentText, "ZAR")
            val query = parsed.detectedSymbol ?: parsed.detectedName ?: parsed.query
            if (query.isNotBlank()) {
                runCatching {
                    val matches = AssetSearchProvider.search(query, 8)
                    AssetSearchProvider.bestScreenshotMatch(parsed, matches) ?: matches.firstOrNull()
                }.getOrNull()
            } else null
        } else null

        val comparisonAssets = if (q.contains("compare") || q.contains(" vs ") || q.contains(" versus ")) {
            discoverComparisonAssets(question)
        } else emptyList()

        when {
            comparisonAssets.size >= 2 -> compareAssets(comparisonAssets.take(3))
            broadOpportunity -> scan(
                jseUniverse.take(5) + usUniverse.take(5) +
                    listOf("VOO","QQQ","GLD") +
                    cryptoUniverse.take(3),
                holdings,
                "Top research candidates now"
            )
            followUpAsset != null -> analyzeDiscovered(followUpAsset)
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
            attachmentAsset != null && (q.contains("this") || q.contains("attachment") || q.contains("file") || q.contains("screenshot") || q.contains("analyse") || q.contains("analyze") || question.isBlank()) ->
                analyzeAttachment(attachmentAsset, attachments)
            mentionedAsset != null -> analyzeDiscovered(mentionedAsset)
            discovered != null -> analyzeDiscovered(discovered)
            q.contains("sell") || q.contains("reduce") || q.contains("worst") -> sellReview(holdings)
            q.contains("portfolio") || q.contains("risk") || q.contains("divers") -> portfolioReview(holdings)
            q.contains("crypto") || q.contains("coin") || q.contains("bitcoin") -> scan(cryptoUniverse, holdings, "Crypto market scan")
            q.contains("forex") || q.contains("currency") || q.contains("fx") -> scan(forexUniverse, holdings, "Forex market scan")
            q.contains("jse") || q.contains("south africa") -> scan(jseUniverse, holdings, "JSE market scan")
            q.contains("us") || q.contains("america") || q.contains("nasdaq") -> scan(usUniverse, holdings, "US market scan")
            q.contains("best") || q.contains("buy") || q.contains("opportun") || q.contains("invest") -> scan(jseUniverse.take(6)+usUniverse.take(6)+cryptoUniverse.take(4), holdings, "Cross-market scan")
            attachments.isNotEmpty() -> attachmentSummary(attachments)
            else -> AssistantReply(
                "Trader Pro",
                "Ask me to analyse a stock, forex pair or cryptocurrency; compare investments; explain a market move; review your portfolio; scan opportunities; or attach a screenshot, PDF, CSV or text statement."
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
        for (symbol in universe.distinct()) {
            if (symbol in owned) continue
            runCatching { analyzeSymbol(symbol) }.getOrNull()?.let { results += it }
            delay(80)
        }
        val top = results.sortedByDescending { it.score }.take(5)
        if (top.isEmpty()) return AssistantReply(title,"Market history is unavailable right now.")

        val links = mutableListOf<NewsItem>()
        for (candidate in top.take(3)) {
            val asset = AssetSearchProvider.search(candidate.symbol, 3).firstOrNull()
            val query = asset?.let { "${it.name} ${it.symbol}" } ?: candidate.symbol
            runCatching { NewsResearchProvider.news(query, 2) }
                .getOrDefault(emptyList())
                .forEach { item -> if (links.none { it.key == item.key }) links += item }
        }

        return AssistantReply(
            title = title,
            body = buildString {
                append("Based on the current trend, momentum, RSI and volatility screen, the five strongest research candidates in this watch universe are: ")
                append(top.joinToString("; ") { "${it.symbol} ${it.score}/100 (${it.stance})" })
                append(". These are research candidates, not automatic buy instructions. Review the charts, recent news, valuation, fees and your time horizon before acting.")
            },
            analyses = top,
            links = links.take(6)
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
            reasons += "Approximate 52-week range: ${fmtPrice(yearLow)} to ${fmtPrice(yearHigh)}."
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

        return Analysis(
            symbol = symbol,
            price = price,
            currency = s.second,
            score = score,
            stance = stance,
            confidence = confidence,
            reasons = reasons,
            sma20 = sma20,
            sma50 = sma50,
            sma200 = sma200,
            rsi14 = rsi,
            momentum30 = momentum30,
            momentum90 = momentum90,
            volatility30 = vol,
            yearLow = yearLow,
            yearHigh = yearHigh,
            chartPrices = closes.takeLast(minOf(90, closes.size))
        )
    }

    private fun recentSymbol(context: String): String? {
        val explicit = Regex("""\b[A-Z0-9^]{2,8}(?:\.JO|-USD|=X)\b""")
            .findAll(context.uppercase(Locale.US))
            .map { it.value }
            .toList()
        if (explicit.isNotEmpty()) return explicit.last()

        val common = listOf(
            "BTC-USD","ETH-USD","SOL-USD","XRP-USD","SHIB-USD",
            "APN.JO","SOL.JO","NPN.JO","MSFT","AAPL","NVDA","TSLA","AMZN"
        )
        return common.lastOrNull { context.uppercase(Locale.US).contains(it) }
    }

    private suspend fun analyzeAttachment(
        asset: AssetSuggestion,
        attachments: List<TraderAttachment>
    ): AssistantReply {
        val base = analyzeDiscovered(asset)
        val names = attachments.joinToString(", ") { it.name }
        return base.copy(
            body = "I read $names and matched the content to ${asset.name} (${asset.symbol}). " + base.body
        )
    }

    private fun attachmentSummary(attachments: List<TraderAttachment>): AssistantReply {
        val readable = attachments.filter { it.extractedText.isNotBlank() }
        val unsupported = attachments.filter { it.extractedText.isBlank() }
        val body = buildString {
            if (readable.isNotEmpty()) {
                append("I read: ")
                append(readable.joinToString { "${it.name} (${it.kind})" })
                append(". Ask me to analyse the investment, compare it with another asset, explain figures, or review risk.")
            }
            if (unsupported.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append("I couldn't extract text from: ")
                append(unsupported.joinToString { it.name })
                append(". Images, PDFs, CSV, TXT and JSON work best in this build.")
            }
        }
        return AssistantReply("Attachment ready", body.ifBlank { "Attachment received." })
    }

    private suspend fun discoverComparisonAssets(question: String): List<AssetSuggestion> {
        val cleaned = question
            .replace("compare", " ", ignoreCase = true)
            .replace("versus", " vs ", ignoreCase = true)
            .replace("with", " vs ", ignoreCase = true)
        val parts = cleaned.split(Regex("""\s+vs\s+|\s+and\s+|,""", RegexOption.IGNORE_CASE))
            .map { it.trim().trim('?', '.', ':', ';') }
            .filter { it.length >= 2 }
            .take(3)

        val out = mutableListOf<AssetSuggestion>()
        for (part in parts) {
            runCatching { AssetSearchProvider.search(part, 5).firstOrNull() }
                .getOrNull()
                ?.let { if (out.none { a -> a.symbol.equals(it.symbol, true) }) out += it }
        }
        return out
    }

    private suspend fun compareAssets(assets: List<AssetSuggestion>): AssistantReply {
        val analyses = mutableListOf<Analysis>()
        for (asset in assets) {
            runCatching { analyzeSymbol(asset.symbol) }.getOrNull()?.let { analyses += it }
            delay(80)
        }
        if (analyses.size < 2) {
            return AssistantReply("Comparison", "I couldn't retrieve enough comparable market history right now.")
        }
        val ranked = analyses.sortedByDescending { it.score }
        val body = buildString {
            append("Current technical ranking: ")
            append(ranked.joinToString("; ") { "${it.symbol} ${it.score}/100 (${it.stance})" })
            append(". ")
            val leader = ranked.first()
            append("${leader.symbol} currently has the strongest combined trend/momentum score. ")
            append("Compare this with valuation, fees, taxes, fundamentals and your time horizon before acting.")
        }
        return AssistantReply("Investment comparison", body, ranked)
    }

    private fun fmtPrice(v: Double): String = when {
        kotlin.math.abs(v) < 0.0001 -> String.format(Locale.US, "%.8f", v)
        kotlin.math.abs(v) < 1.0 -> String.format(Locale.US, "%.4f", v)
        else -> String.format(Locale.US, "%.2f", v)
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
