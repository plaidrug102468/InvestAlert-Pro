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
    val centerY: Double get() = (top + bottom) / 2.0
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

data class ScreenshotInvestmentDraft(
    val token: String = UUID.randomUUID().toString(),
    val query: String = "",
    val detectedSymbol: String? = null,
    val detectedName: String? = null,
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
        "EXCHANGE","CURRENT","PURCHASE","PREVIOUS","CLOSE","FSRS","FSR","MY","HOLDING",
        "JSE","BVB","NYSE","NASDAQ","AMEX","LSE","OTC","ETF","OPEN","CLOSED","ZAC"
    )

    fun parse(text: String, defaultCurrency: String): ScreenshotInvestmentDraft =
        parse(fullText = text, ocrLines = emptyList(), defaultCurrency = defaultCurrency)

    fun parse(
        ocrLines: List<OcrLine>,
        fullText: String,
        defaultCurrency: String
    ): ScreenshotInvestmentDraft {
        val rawLines = fullText.lines()
            .map { normalizeSpace(it) }
            .filter { it.isNotBlank() }

        val spatialLines = ocrLines
            .map { it.copy(text = normalizeSpace(it.text)) }
            .filter { it.text.isNotBlank() }

        val lowerText = fullText.lowercase(Locale.US)
        val looksLikeEasyEquities =
            lowerText.contains("easyequities") ||
                lowerText.contains("form.easyequities") ||
                lowerText.contains("# fsr") ||
                (
                    lowerText.contains("purchase value") &&
                    lowerText.contains("avg. purchase price") &&
                    lowerText.contains("buy more")
                )

        val broker = when {
            looksLikeEasyEquities -> "EasyEquities"
            else -> brokers.firstOrNull { p -> p.needles.any { lowerText.contains(it) } }?.label
        }

        val symbol = detectSymbol(if (spatialLines.isNotEmpty()) spatialLines.map { it.text } else rawLines)

        val wholeShares = bestValue(
            spatialLines = spatialLines,
            rawLines = rawLines,
            labels = listOf("# shares", "shares", "whole shares", "whole units"),
            rejectLabels = listOf("fsr", "fraction")
        )
        val fractional = bestValue(
            spatialLines = spatialLines,
            rawLines = rawLines,
            labels = listOf("# fsrs", "fsrs", "# fsr", "fsr", "fractional shares", "fractional units")
        )
        val genericUnits = bestValue(
            spatialLines = spatialLines,
            rawLines = rawLines,
            labels = listOf("units", "quantity", "qty", "coins", "tokens")
        )

        val detectedUnits = when {
            wholeShares != null || fractional != null -> (wholeShares ?: 0.0) + (fractional ?: 0.0)
            genericUnits != null -> genericUnits
            else -> null
        }

        val invested = bestValue(
            spatialLines = spatialLines,
            rawLines = rawLines,
            labels = listOf("purchase value", "amount invested", "invested", "cost value", "amount paid")
        )
        val currentValue = bestValue(
            spatialLines = spatialLines,
            rawLines = rawLines,
            labels = listOf("current value", "market value", "portfolio value")
        )
        val avgPurchase = bestValue(
            spatialLines = spatialLines,
            rawLines = rawLines,
            labels = listOf(
                "avg. purchase price", "avg purchase price",
                "average purchase price", "average buy", "avg cost"
            )
        )

        val totalUnits = detectedUnits ?: if (
            invested != null &&
            avgPurchase != null &&
            avgPurchase > 0.0
        ) {
            invested / avgPurchase
        } else null

        val impliedCurrentPrice = if (
            currentValue != null &&
            totalUnits != null &&
            totalUnits > 0.0
        ) currentValue / totalUnits else null

        val currencyContext = buildString {
            rawLines.forEach { line ->
                val l = line.lowercase(Locale.US)
                if (
                    l.contains("purchase value") ||
                    l.contains("current value") ||
                    l.contains("avg") ||
                    l.contains("invested")
                ) append(line).append(' ')
            }
            append(fullText)
        }
        val currency = detectCurrency(currencyContext) ?: defaultCurrency

        val likelyName = detectLikelyName(
            if (spatialLines.isNotEmpty()) spatialLines.map { it.text } else rawLines,
            symbol,
            broker
        )

        return ScreenshotInvestmentDraft(
            query = symbol ?: likelyName.orEmpty(),
            detectedSymbol = symbol,
            detectedName = likelyName,
            units = positiveOrNull(totalUnits),
            wholeUnits = nonNegativeOrNull(wholeShares),
            fractionalUnits = nonNegativeOrNull(fractional),
            investedAmount = positiveOrNull(invested),
            currentValue = positiveOrNull(currentValue),
            impliedCurrentPrice = positiveOrNull(impliedCurrentPrice),
            averagePurchasePrice = positiveOrNull(avgPurchase),
            investedCurrency = currency,
            whereHeld = broker,
            rawText = fullText
        )
    }

    private fun bestValue(
        spatialLines: List<OcrLine>,
        rawLines: List<String>,
        labels: List<String>,
        rejectLabels: List<String> = emptyList()
    ): Double? {
        if (spatialLines.isNotEmpty()) {
            layoutValue(spatialLines, labels, rejectLabels)?.let { return it }
        }
        return valueNearLabel(rawLines, labels, rejectLabels)
    }

    private fun layoutValue(
        lines: List<OcrLine>,
        labels: List<String>,
        rejectLabels: List<String>
    ): Double? {
        val labelLines = lines.filter { line ->
            val lower = line.text.lowercase(Locale.US)
            labels.any { lower.contains(it) } &&
                rejectLabels.none { lower.contains(it) }
        }

        for (labelLine in labelLines) {
            numberAfterLabel(labelLine.text, labels)?.let { return it }

            val verticalTolerance = max(30.0, labelLine.height * 1.6)
            val rightCandidates = lines
                .asSequence()
                .filter { it !== labelLine }
                .filter { candidate ->
                    candidate.left >= labelLine.left + 40 &&
                        abs(candidate.centerY - labelLine.centerY) <= verticalTolerance
                }
                .mapNotNull { candidate ->
                    firstNumber(candidate.text)?.let { value ->
                        val score =
                            abs(candidate.centerY - labelLine.centerY) +
                                max(0, candidate.left - labelLine.right) * 0.02
                        Triple(value, score, candidate)
                    }
                }
                .sortedBy { it.second }
                .toList()

            rightCandidates.firstOrNull()?.first?.let { return it }

            val belowCandidates = lines
                .asSequence()
                .filter { it.top >= labelLine.bottom }
                .filter { it.top - labelLine.bottom <= max(90, labelLine.height * 3) }
                .filter { abs(it.left - labelLine.left) < 260 }
                .mapNotNull { candidate ->
                    firstNumber(candidate.text)?.let { value ->
                        val score = (candidate.top - labelLine.bottom).toDouble() + abs(candidate.left - labelLine.left) * 0.05
                        value to score
                    }
                }
                .sortedBy { it.second }
                .toList()

            belowCandidates.firstOrNull()?.first?.let { return it }
        }
        return null
    }

    private fun detectSymbol(lines: List<String>): String? {
        val strict = Regex("""^[A-Z]{2,7}(?:\.JO|-USD|=X)?$""")
        val candidates = mutableListOf<Pair<String, Int>>()

        lines.forEachIndexed { index, raw ->
            val cleaned = raw.uppercase(Locale.US)
                .replace(":", "")
                .replace("•", "")
                .trim()

            val direct = cleaned.replace(" ", "")
            if (strict.matches(direct) && direct !in stopSymbols) {
                var score = 0
                val bare = direct.removeSuffix(".JO").removeSuffix("-USD").removeSuffix("=X")
                if (direct.endsWith(".JO") || direct.endsWith("-USD") || direct.endsWith("=X")) score += 120
                if (bare.length in 3..4) score += 100
                else if (bare.length == 2 || bare.length == 5) score += 45
                else score += 10
                if (index < 12) score += 15
                if (bare in setOf("ASPEN","HOLDINGS","LIMITED")) score -= 80
                candidates += direct to score
            }
        }

        val embedded = Regex("""\b[A-Z]{2,7}(?:\.JO|-USD|=X)?\b""")
        lines.forEachIndexed { index, raw ->
            embedded.findAll(raw.uppercase(Locale.US)).forEach { m ->
                val candidate = m.value
                if (candidate in stopSymbols) return@forEach
                val bare = candidate.removeSuffix(".JO").removeSuffix("-USD").removeSuffix("=X")
                var score = 0
                if (candidate.endsWith(".JO") || candidate.endsWith("-USD") || candidate.endsWith("=X")) score += 110
                if (bare.length in 3..4) score += 75
                else if (bare.length == 2 || bare.length == 5) score += 25
                if (index < 12) score += 10
                if (bare in setOf("ASPEN","HOLDINGS","LIMITED")) score -= 80
                candidates += candidate to score
            }
        }

        return candidates
            .groupBy { it.first }
            .mapValues { entry -> entry.value.maxOf { it.second } }
            .maxByOrNull { it.value }
            ?.key
    }

    private fun detectLikelyName(lines: List<String>, symbol: String?, broker: String?): String? {
        val badWords = listOf(
            "profit", "loss", "exchange", "my holding", "current value", "purchase value",
            "average", "previous close", "shares", "fsr", "sell", "buy more",
            "portfolio", "watchlist", "notify", "funds", "dividend rewards"
        )

        return lines
            .map { normalizeSpace(it) }
            .filter { line ->
                val lower = line.lowercase(Locale.US)
                line.length in 6..80 &&
                    line.any { it.isLetter() } &&
                    line.count { it.isDigit() } <= 1 &&
                    !line.all { it.isUpperCase() || !it.isLetter() } &&
                    symbol?.let { !line.equals(it, true) } != false &&
                    broker?.let { !line.contains(it, true) } != false &&
                    badWords.none { lower.contains(it) } &&
                    !lower.contains("http") &&
                    !lower.contains(".io")
            }
            .maxByOrNull { candidate ->
                var score = candidate.length
                val lower = candidate.lowercase(Locale.US)
                if (lower.contains("limited")) score += 30
                if (lower.contains("holdings")) score += 20
                if (candidate.contains(" ")) score += 10
                if (candidate.startsWith("0") || candidate.startsWith("O")) score -= 15
                score
            }
            ?.removePrefix("0")
            ?.trim()
    }

    private fun valueNearLabel(
        lines: List<String>,
        labels: List<String>,
        rejectLabels: List<String> = emptyList()
    ): Double? {
        for (i in lines.indices) {
            val lower = lines[i].lowercase(Locale.US)
            if (
                labels.any { lower.contains(it) } &&
                rejectLabels.none { lower.contains(it) }
            ) {
                numberAfterLabel(lines[i], labels)?.let { return it }

                for (offset in 1..6) {
                    val nextIndex = i + offset
                    if (nextIndex >= lines.size) break
                    val next = lines[nextIndex]
                    val nextLower = next.lowercase(Locale.US)

                    if (offset > 2 && looksLikeAnotherFieldLabel(nextLower)) break
                    firstNumber(next)?.let { return it }
                }
            }
        }
        return null
    }

    private fun numberAfterLabel(text: String, labels: List<String>): Double? {
        val lower = text.lowercase(Locale.US)
        val label = labels.firstOrNull { lower.contains(it) } ?: return null
        val idx = lower.indexOf(label)
        if (idx < 0) return null
        return firstNumber(text.substring(idx + label.length))
    }

    private fun looksLikeAnotherFieldLabel(text: String): Boolean {
        val labels = listOf(
            "current value", "purchase value", "avg", "previous close",
            "shares", "fsr", "profit", "loss", "exchange", "market value"
        )
        return labels.any { text.contains(it) }
    }

    private fun firstNumber(text: String): Double? {
        val regex = Regex("""[-+]?(?:[R$€£]\s*)?[0-9Oo][0-9Oo\s,.]*""")
        for (match in regex.findAll(text)) {
            val raw = match.value
            // Never turn a word/logo into zero: the OCR token must contain a real digit.
            if (raw.none { it.isDigit() }) continue
            val cleaned = raw
                .replace("R", "", ignoreCase = false)
                .replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .replace("O", "0")
                .replace("o", "0")
                .replace(" ", "")
                .replace(",", "")
                .replace("−", "-")
                .trimEnd('.')
            cleaned.toDoubleOrNull()?.let { return it }
        }
        return null
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

    private fun normalizeSpace(value: String): String =
        value.trim().replace(Regex("""\s+"""), " ")

    private fun positiveOrNull(value: Double?): Double? =
        value?.takeIf { it > 0.0 }

    private fun nonNegativeOrNull(value: Double?): Double? =
        value?.takeIf { it >= 0.0 }
}
