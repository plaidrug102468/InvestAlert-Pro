package com.investalert.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetSearchProviderTest {
    @Test
    fun screenshotMatch_prefersAspenOnEasyEquities() {
        val draft = ScreenshotInvestmentDraft(
            query = "APN",
            detectedSymbol = "APN",
            detectedName = "Aspen Pharmacare Holdings Limited",
            units = 2.8871,
            investedAmount = 421.48,
            investedCurrency = "ZAR",
            whereHeld = "EasyEquities",
            rawText = "Aspen Pharmacare Holdings Limited APN JSE Current Value R445.31"
        )
        val matches = listOf(
            AssetSuggestion("XFOA.RO", "Fondul Oamenilor De Afaceri", "BVB", "Equity", "RON"),
            AssetSuggestion("APN.JO", "Aspen Pharmacare Holdings", "JSE", "Equity", "ZAR")
        )

        val best = AssetSearchProvider.bestScreenshotMatch(draft, matches)

        assertEquals("APN.JO", best?.symbol)
    }

    @Test
    fun screenshotMatch_doesNotAutoselectWeakUnrelatedResult() {
        val draft = ScreenshotInvestmentDraft(
            query = "ASPEN",
            detectedSymbol = "ASPEN",
            detectedName = null,
            investedCurrency = "ZAR",
            whereHeld = "EasyEquities",
            rawText = "ASPEN Current Value R445.31"
        )
        val matches = listOf(
            AssetSuggestion("XFOA.RO", "Fondul Oamenilor De Afaceri", "BVB", "Equity", "RON")
        )

        assertNull(AssetSearchProvider.bestScreenshotMatch(draft, matches))
    }
}
