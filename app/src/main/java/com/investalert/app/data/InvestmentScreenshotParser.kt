package com.investalert.app.data

import java.util.Locale
import java.util.UUID

data class ScreenshotInvestmentDraft(
    val token: String = UUID.randomUUID().toString(),
    val query: String = "",
    val units: Double? = null,
    val investedAmount: Double? = null,
    val investedCurrency: String? = null,
    val whereHeld: String? = null,
    val rawText: String = ""
)

object InvestmentScreenshotParser {
    private val brokers = listOf(
        "EasyEquities", "Binance", "Luno", "VALR", "Satrix", "Interactive Brokers",
        "eToro", "FNB", "Standard Bank", "Absa", "Nedbank", "Capitec"
    )

    private val stopSymbols = setOf(
        "ZAR","USD","EUR","GBP","TOTAL","VALUE","PRICE","UNITS","SHARES","QTY",
        "BUY","SELL","MARKET","PROFIT","LOSS","RETURN","PORTFOLIO","INVESTED"
    )

    fun parse(text: String, defaultCurrency: String): ScreenshotInvestmentDraft {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val broker = brokers.firstOrNull { b -> lines.any { it.contains(b, ignoreCase = true) } }

        val symbolRegex = Regex("""[A-Z]{1,7}(?:.JO|-USD|=X)?""")
        val symbol = lines.asSequence()
            .flatMap { symbolRegex.findAll(it.uppercase(Locale.US)).map { m -> m.value } }
            .firstOrNull { it !in stopSymbols }

        val units = valueAfterKeywords(lines, listOf("units","shares","quantity","qty","coins","tokens"))
        val investedLine = lines.firstOrNull {
            val l = it.lowercase(Locale.US)
            l.contains("invested") || l.contains("cost") || l.contains("amount paid") || l.contains("purchase value")
        }
        val invested = investedLine?.let { firstNumber(it) }

        val currency = detectCurrency(investedLine ?: text) ?: defaultCurrency

        val likelyName = lines.firstOrNull { line ->
            val lower = line.lowercase(Locale.US)
            line.length in 3..60 &&
                !line.any { it.isDigit() } &&
                !lower.contains("portfolio") &&
                !lower.contains("invested") &&
                !lower.contains("current value") &&
                brokers.none { line.contains(it, ignoreCase = true) }
        }

        return ScreenshotInvestmentDraft(
            query = symbol ?: likelyName.orEmpty(),
            units = units,
            investedAmount = invested,
            investedCurrency = currency,
            whereHeld = broker,
            rawText = text
        )
    }

    private fun valueAfterKeywords(lines: List<String>, keywords: List<String>): Double? {
        for (line in lines) {
            if (keywords.any { line.contains(it, ignoreCase = true) }) {
                firstNumber(line)?.let { return it }
            }
        }
        return null
    }

    private fun firstNumber(text: String): Double? {
        val regex = Regex("""(?<![A-Za-z])[-+]?d[d ,.]*(?:d)?""")
        val raw = regex.find(text)?.value ?: return null
        val cleaned = raw.replace(" ", "").replace(",", "")
        return cleaned.toDoubleOrNull()
    }

    private fun detectCurrency(text: String): String? {
        val upper = text.uppercase(Locale.US)
        return when {
            "ZAR" in upper || Regex("""(^|s)Rs?d""").containsMatchIn(text) -> "ZAR"
            "USD" in upper || "$" in text -> "USD"
            "EUR" in upper || "€" in text -> "EUR"
            "GBP" in upper || "£" in text -> "GBP"
            else -> null
        }
    }
}
