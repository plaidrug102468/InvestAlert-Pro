package com.investalert.app.data

import java.util.Locale
import java.util.UUID

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
        "EXCHANGE","CURRENT","PURCHASE","PREVIOUS","CLOSE","FSRS","FSR"
    )

    fun parse(text: String, defaultCurrency: String): ScreenshotInvestmentDraft {
        val lines = text.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }

        val lowerText = text.lowercase(Locale.US)
        val broker = brokers.firstOrNull { p -> p.needles.any { lowerText.contains(it) } }?.label

        val symbol = detectSymbol(lines)
        val wholeShares = valueNearLabel(
            lines,
            listOf("# shares", "shares", "whole shares", "whole units"),
            rejectLabels = listOf("fsr", "fraction")
        )
        val fractional = valueNearLabel(
            lines,
            listOf("# fsrs", "fsrs", "fsr", "fractional shares", "fractional units")
        )
        val genericUnits = valueNearLabel(lines, listOf("units", "quantity", "qty", "coins", "tokens"))

        val totalUnits = when {
            wholeShares != null || fractional != null -> (wholeShares ?: 0.0) + (fractional ?: 0.0)
            genericUnits != null -> genericUnits
            else -> null
        }

        val invested = valueNearLabel(
            lines,
            listOf("purchase value", "amount invested", "invested", "cost value", "amount paid")
        )
        val currentValue = valueNearLabel(
            lines,
            listOf("current value", "market value", "portfolio value")
        )
        val avgPurchase = valueNearLabel(
            lines,
            listOf("avg. purchase price", "avg purchase price", "average purchase price", "average buy", "avg cost")
        )

        val impliedCurrentPrice = if (
            currentValue != null &&
            totalUnits != null &&
            totalUnits > 0.0
        ) currentValue / totalUnits else null

        val currencyContext = buildString {
            lines.forEach { line ->
                val l = line.lowercase(Locale.US)
                if (
                    l.contains("purchase value") ||
                    l.contains("current value") ||
                    l.contains("avg") ||
                    l.contains("invested")
                ) append(line).append(' ')
            }
            append(text)
        }
        val currency = detectCurrency(currencyContext) ?: defaultCurrency

        val likelyName = detectLikelyName(lines, symbol, broker)

        return ScreenshotInvestmentDraft(
            query = symbol ?: likelyName.orEmpty(),
            units = totalUnits,
            wholeUnits = wholeShares,
            fractionalUnits = fractional,
            investedAmount = invested,
            currentValue = currentValue,
            impliedCurrentPrice = impliedCurrentPrice,
            averagePurchasePrice = avgPurchase,
            investedCurrency = currency,
            whereHeld = broker,
            rawText = text
        )
    }

    private fun detectSymbol(lines: List<String>): String? {
        val strict = Regex("""^[A-Z]{1,7}(?:\.JO|-USD|=X)?$""")
        lines.forEach { line ->
            val candidate = line.uppercase(Locale.US)
                .replace(" ", "")
                .replace(":", "")
                .trim()
            if (strict.matches(candidate) && candidate !in stopSymbols) return candidate
        }

        val embedded = Regex("""\b[A-Z]{1,7}(?:\.JO|-USD|=X)?\b""")
        return lines.asSequence()
            .flatMap { embedded.findAll(it.uppercase(Locale.US)).map { m -> m.value } }
            .firstOrNull { it !in stopSymbols }
    }

    private fun detectLikelyName(lines: List<String>, symbol: String?, broker: String?): String? {
        val badWords = listOf(
            "profit", "loss", "exchange", "holding", "current value", "purchase value",
            "average", "previous close", "shares", "fsr", "sell", "buy more",
            "portfolio", "watchlist", "notify", "funds"
        )
        return lines.firstOrNull { line ->
            val lower = line.lowercase(Locale.US)
            line.length in 4..70 &&
                line.any { it.isLetter() } &&
                !line.all { it.isUpperCase() || !it.isLetter() } &&
                symbol?.let { !line.equals(it, true) } != false &&
                broker?.let { !line.contains(it, true) } != false &&
                badWords.none { lower.contains(it) } &&
                !lower.contains("http") &&
                !lower.contains(".io")
        }
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

                for (offset in 1..3) {
                    val nextIndex = i + offset
                    if (nextIndex >= lines.size) break
                    val next = lines[nextIndex]
                    val nextLower = next.lowercase(Locale.US)

                    if (
                        offset > 1 &&
                        looksLikeAnotherFieldLabel(nextLower)
                    ) break

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
        val normalized = text
            .replace("O", "0")
            .replace("o", "0")
            .replace("−", "-")
        val regex = Regex("""[-+]?\d[\d\s,.]*""")
        val raw = regex.find(normalized)?.value ?: return null
        val cleaned = raw.replace(" ", "").replace(",", "").trimEnd('.')
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
}
