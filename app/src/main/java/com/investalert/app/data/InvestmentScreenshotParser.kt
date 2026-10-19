package com.investalert.app.data

import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
    val height: Int get() = max(1, bottom - top)
}

data class ScreenshotInvestmentDraft(
    val token: String = UUID.randomUUID().toString(),
    val query: String = "",
    val units: Double? = null,
    val wholeUnits: Double? = null,
    val fractionalUnits: Double? = null,
    val investedAmount: Double? = null,
    val currentValue: Double? = null,
    val impliedCurrentPrice: Double? = null,
    val averagePurchasePrice: Double? = null,
    val investedCurrency: String? = null,
    val whereHeld: String? = null,
    val rawText: String = ""
)

object InvestmentScreenshotParser {
    private data class BrokerPattern(val label: String, val needles: List<String>)

    private val brokers = listOf(
        BrokerPattern("EasyEquities", listOf("easyequities", "easyequities.io", "form.easyequities")),
        BrokerPattern("Binance", listOf("binance")),
        BrokerPattern("Luno", listOf("luno")),
        BrokerPattern("VALR", listOf("valr")),
        BrokerPattern("Allan Gray", listOf("allan gray")),
        BrokerPattern("Satrix", listOf("satrix")),
        BrokerPattern("Interactive Brokers", listOf("interactive brokers", "ibkr")),
        BrokerPattern("eToro", listOf("etoro")),
        BrokerPattern("FNB", listOf("fnb")),
        BrokerPattern("Standard Bank", listOf("standard bank")),
        BrokerPattern("Absa", listOf("absa")),
        BrokerPattern("Nedbank", listOf("nedbank")),
        BrokerPattern("Capitec", listOf("capitec"))
    )

    private val stopSymbols = setOf(
        "ZAR","USD","EUR","GBP","TOTAL","VALUE","PRICE","UNITS","SHARES","SHARE","QTY",
        "BUY","SELL","MARKET","PROFIT","LOSS","RETURN","PORTFOLIO","INVESTED","OPEN",
        "EXCHANGE","CURRENT","PURCHASE","PREVIOUS","CLOSE","FSRS","FSR","JSE","NYSE",
        "NASDAQ","INVEST","WATCHLIST","NOTIFY","FUNDS"
    )

    fun parse(text: String, defaultCurrency: String): ScreenshotInvestmentDraft {
        val lines = text.lines()
            .mapIndexed { index, value ->
                OcrLine(
                    text = value.trim(),
                    left = 0,
                    top = index * 40,
                    right = 1000,
                    bottom = index * 40 + 30
                )
            }
            .filter { it.text.isNotBlank() }
        return parse(lines, text, defaultCurrency)
    }

    fun parse(
        ocrLines: List<OcrLine>,
        fullText: String,
        defaultCurrency: String
    ): ScreenshotInvestmentDraft {
        val lines = ocrLines
            .map { it.copy(text = cleanLine(it.text)) }
            .filter { it.text.isNotBlank() }

        val lowerText = fullText.lowercase(Locale.US)
        val broker = brokers.firstOrNull { p -> p.needles.any { lowerText.contains(it) } }?.label
            ?: detectBrokerFromLines(lines)

        val easyEquities = broker == "EasyEquities" ||
            lines.any { normalize(it.text).contains("easyequities") }

        val symbol = detectSymbol(lines)
        val likelyName = detectLikelyName(lines, symbol, broker)

        val wholeShares = if (easyEquities) {
            spatialValue(lines, listOf("# shares", "shares"), rejectLabels = listOf("fsr", "fraction"))
                ?: valueNearLabel(lines.map { it.text }, listOf("# shares", "shares"), listOf("fsr", "fraction"))
        } else {
            spatialValue(lines, listOf("whole shares", "shares", "whole units"), listOf("fsr", "fraction"))
                ?: valueNearLabel(lines.map { it.text }, listOf("whole shares", "shares", "whole units"), listOf("fsr", "fraction"))
        }

        val fractional = spatialValue(
            lines,
            listOf("# fsrs", "fsrs", "fsr", "fractional shares", "fractional units")
        ) ?: valueNearLabel(
            lines.map { it.text },
            listOf("# fsrs", "fsrs", "fsr", "fractional shares", "fractional units")
        )

        val genericUnits = spatialValue(
            lines,
            listOf("units", "quantity", "qty", "coins", "tokens")
        ) ?: valueNearLabel(
            lines.map { it.text },
            listOf("units", "quantity", "qty", "coins", "tokens")
        )

        val totalUnits = when {
            wholeShares != null || fractional != null -> (wholeShares ?: 0.0) + (fractional ?: 0.0)
            genericUnits != null -> genericUnits
            else -> null
        }

        val invested = spatialValue(
            lines,
            listOf("purchase value", "amount invested", "invested", "cost value", "amount paid")
        ) ?: valueNearLabel(
            lines.map { it.text },
            listOf("purchase value", "amount invested", "invested", "cost value", "amount paid")
        )

        val currentValue = spatialValue(
            lines,
            listOf("current value", "market value", "portfolio value")
        ) ?: valueNearLabel(
            lines.map { it.text },
            listOf("current value", "market value", "portfolio value")
        )

        val avgPurchase = spatialValue(
            lines,
            listOf("avg. purchase price", "avg purchase price", "average purchase price", "average buy", "avg cost")
        ) ?: valueNearLabel(
            lines.map { it.text },
            listOf("avg. purchase price", "avg purchase price", "average purchase price", "average buy", "avg cost")
        )

        val impliedCurrentPrice = if (
            currentValue != null &&
            totalUnits != null &&
            totalUnits > 0.0
        ) currentValue / totalUnits else null

        val currencyContext = buildString {
            lines.forEach { line ->
                val l = normalize(line.text)
                if (
                    l.contains("purchase value") ||
                    l.contains("current value") ||
                    l.contains("average") ||
                    l.contains("avg") ||
                    l.contains("invested")
                ) append(line.text).append(' ')
            }
            append(fullText)
        }
        val currency = detectCurrency(currencyContext) ?: defaultCurrency

        val query = when {
            symbol != null && symbol !in stopSymbols -> symbol
            !likelyName.isNullOrBlank() -> likelyName
            else -> ""
        }

        return ScreenshotInvestmentDraft(
            query = query,
            units = totalUnits,
            wholeUnits = wholeShares,
            fractionalUnits = fractional,
            investedAmount = invested,
            currentValue = currentValue,
            impliedCurrentPrice = impliedCurrentPrice,
            averagePurchasePrice = avgPurchase,
            investedCurrency = currency,
            whereHeld = broker,
            rawText = fullText
        )
    }

    private fun detectBrokerFromLines(lines: List<OcrLine>): String? {
        val combined = lines.joinToString(" ") { normalize(it.text) }
        return brokers.firstOrNull { p ->
            p.needles.any { combined.contains(it.lowercase(Locale.US)) }
        }?.label
    }

    private fun detectSymbol(lines: List<OcrLine>): String? {
        val strict = Regex("""^[A-Z]{1,7}(?:\.JO|-USD|=X)?$""")
        val candidates = lines.mapNotNull { line ->
            val candidate = line.text.uppercase(Locale.US)
                .replace(":", "")
                .trim()
            if (strict.matches(candidate) && candidate !in stopSymbols) {
                candidate to line
            } else null
        }

        if (candidates.isEmpty()) return null

        return candidates
            .sortedWith(
                compareBy<Pair<String, OcrLine>>(
                    { pair ->
                        when {
                            pair.first.endsWith(".JO") || pair.first.endsWith("-USD") || pair.first.endsWith("=X") -> 0
                            pair.first.length in 2..5 -> 1
                            else -> 2
                        }
                    },
                    { it.second.top }
                )
            )
            .firstOrNull()
            ?.first
    }

    private fun detectLikelyName(
        lines: List<OcrLine>,
        symbol: String?,
        broker: String?
    ): String? {
        val badWords = listOf(
            "profit", "loss", "exchange", "my holding", "current value", "purchase value",
            "average", "previous close", "shares", "fsr", "sell", "buy more",
            "portfolio", "watchlist", "notify", "my funds", "dividend rewards",
            "open", "market value"
        )

        val candidates = lines.mapNotNull { line ->
            val cleaned = line.text
                .replace(Regex("""^[^A-Za-z]+"""), "")
                .trim()
            val lower = normalize(cleaned)
            val words = cleaned.split(Regex("""\s+""")).count { it.any(Char::isLetter) }

            val good = cleaned.length in 4..80 &&
                cleaned.any(Char::isLetter) &&
                words >= 2 &&
                symbol?.let { !cleaned.equals(it, true) } != false &&
                broker?.let { !cleaned.contains(it, true) } != false &&
                badWords.none { lower.contains(it) } &&
                !lower.contains("http") &&
                !lower.contains(".io")

            if (good) cleaned to line else null
        }

        return candidates
            .sortedWith(compareBy<Pair<String, OcrLine>>({ it.second.top }, { -it.first.length }))
            .firstOrNull()
            ?.first
    }

    private fun spatialValue(
        lines: List<OcrLine>,
        labels: List<String>,
        rejectLabels: List<String> = emptyList()
    ): Double? {
        val labelLine = lines.firstOrNull { line ->
            val normalized = normalize(line.text)
            labels.any { labelMatches(normalized, it) } &&
                rejectLabels.none { normalized.contains(normalize(it)) }
        } ?: return null

        numberAfterLabel(labelLine.text, labels)?.let { return it }

        val candidates = lines.mapNotNull { candidate ->
            if (candidate === labelLine) return@mapNotNull null
            val value = extractStandaloneNumber(candidate.text) ?: return@mapNotNull null

            val labelHeight = labelLine.height.toDouble()
            val dy = abs(candidate.centerY - labelLine.centerY)
            val rightOf = candidate.left >= labelLine.left
            val sameRow = dy <= labelHeight * 1.25
            val below = candidate.top >= labelLine.top &&
                candidate.top - labelLine.bottom <= labelLine.height * 4
            val horizontalOverlap = candidate.right >= labelLine.left && candidate.left <= labelLine.right

            val score = when {
                sameRow && rightOf -> dy + max(0, candidate.left - labelLine.right) * 0.05
                below && horizontalOverlap -> 100.0 + (candidate.top - labelLine.bottom)
                below -> 250.0 + (candidate.top - labelLine.bottom) + abs(candidate.centerX - labelLine.centerX) * 0.1
                else -> Double.MAX_VALUE
            }

            if (score == Double.MAX_VALUE) null else Triple(value, score, candidate)
        }

        return candidates.minByOrNull { it.second }?.first
    }

    private fun valueNearLabel(
        lines: List<String>,
        labels: List<String>,
        rejectLabels: List<String> = emptyList()
    ): Double? {
        for (i in lines.indices) {
            val lower = normalize(lines[i])
            if (
                labels.any { labelMatches(lower, it) } &&
                rejectLabels.none { lower.contains(normalize(it)) }
            ) {
                numberAfterLabel(lines[i], labels)?.let { return it }

                for (offset in 1..4) {
                    val nextIndex = i + offset
                    if (nextIndex >= lines.size) break
                    val next = lines[nextIndex]
                    val nextLower = normalize(next)

                    if (looksLikeAnotherFieldLabel(nextLower)) break
                    extractStandaloneNumber(next)?.let { return it }
                }
            }
        }
        return null
    }

    private fun numberAfterLabel(text: String, labels: List<String>): Double? {
        val lower = normalize(text)
        val label = labels.firstOrNull { labelMatches(lower, it) } ?: return null
        val labelNorm = normalize(label)
        val idx = lower.indexOf(labelNorm)
        if (idx < 0) return null

        val rawIndex = approximateRawIndex(text, labelNorm)
        if (rawIndex >= text.length) return null
        return extractStandaloneNumber(text.substring(rawIndex))
    }

    private fun approximateRawIndex(text: String, normalizedLabel: String): Int {
        val lower = text.lowercase(Locale.US)
        val words = normalizedLabel.split(' ').filter { it.isNotBlank() }
        val lastWord = words.lastOrNull() ?: return 0
        val idx = lower.lastIndexOf(lastWord)
        return if (idx >= 0) idx + lastWord.length else 0
    }

    private fun looksLikeAnotherFieldLabel(text: String): Boolean {
        val labels = listOf(
            "current value", "purchase value", "avg", "average", "previous close",
            "shares", "fsr", "profit", "loss", "exchange", "market value"
        )
        return labels.any { text.contains(it) }
    }

    private fun extractStandaloneNumber(text: String): Double? {
        val normalized = text
            .replace("−", "-")
            .replace("R ", "R")
            .trim()

        val regex = Regex("""[-+]?(?:R|\$|€|£)?\s*\d[\d\s,]*(?:\.\d+)?""")
        val raw = regex.find(normalized)?.value ?: return null
        val cleaned = raw
            .replace("R", "", ignoreCase = true)
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace(" ", "")
            .replace(",", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun detectCurrency(text: String): String? {
        val upper = text.uppercase(Locale.US)
        return when {
            "ZAR" in upper || Regex("""(^|\s)R\s?\d""").containsMatchIn(text) -> "ZAR"
            "USD" in upper || "$" in text -> "USD"
            "EUR" in upper || "€" in text -> "EUR"
            "GBP" in upper || "£" in text -> "GBP"
            "AUD" in upper -> "AUD"
            "CAD" in upper -> "CAD"
            else -> null
        }
    }

    private fun labelMatches(normalizedText: String, label: String): Boolean {
        val n = normalize(label)
        if (normalizedText.contains(n)) return true

        val compactText = normalizedText.replace(" ", "")
        val compactLabel = n.replace(" ", "")
        return compactText.contains(compactLabel)
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.US)
            .replace("#", " ")
            .replace(".", " ")
            .replace(":", " ")
            .replace(Regex("""[^a-z0-9/$£€=\-]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun cleanLine(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
}
