package com.investalert.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentScreenshotParserTest {
    @Test
    fun parsesEasyEquitiesFractionalHoldingUsingOcrGeometry() {
        val lines = listOf(
            OcrLine("Aspen Pharmacare Holdings Limited", 150, 190, 575, 225),
            OcrLine("APN", 315, 345, 385, 385),
            OcrLine("PROFIT/LOSS", 320, 405, 485, 435),
            OcrLine("R23.83", 320, 455, 405, 490),
            OcrLine("JSE", 605, 455, 665, 490),
            OcrLine("Current Value", 65, 745, 260, 785),
            OcrLine("R445.31", 535, 745, 650, 785),
            OcrLine("Purchase Value", 65, 820, 280, 860),
            OcrLine("R421.48", 535, 820, 650, 860),
            OcrLine("Avg. Purchase Price", 65, 900, 310, 940),
            OcrLine("R145.99", 535, 900, 650, 940),
            OcrLine("Previous Close Price", 65, 970, 320, 1010),
            OcrLine("R156.70", 535, 970, 650, 1010),
            OcrLine("# Shares", 65, 1050, 190, 1090),
            OcrLine("2", 615, 1050, 635, 1090),
            OcrLine("# FSRs", 65, 1120, 170, 1160),
            OcrLine("0.8871", 575, 1120, 650, 1160),
            OcrLine("form.easyequities.io", 100, 50, 400, 80)
        )

        val draft = InvestmentScreenshotParser.parse(
            ocrLines = lines,
            fullText = lines.joinToString("\n") { it.text },
            defaultCurrency = "ZAR"
        )

        assertEquals("APN", draft.query)
        assertEquals("EasyEquities", draft.whereHeld)
        assertEquals(2.0, draft.wholeUnits!!, 0.000001)
        assertEquals(0.8871, draft.fractionalUnits!!, 0.000001)
        assertEquals(2.8871, draft.units!!, 0.000001)
        assertEquals(421.48, draft.investedAmount!!, 0.001)
        assertEquals(445.31, draft.currentValue!!, 0.001)
        assertEquals(145.99, draft.averagePurchasePrice!!, 0.001)
        assertTrue(draft.impliedCurrentPrice!! in 154.20..154.30)
    }

    @Test
    fun wordsContainingOAreNotMistakenForZero() {
        val lines = listOf(
            OcrLine("Avg. Purchase Price", 40, 100, 250, 130),
            OcrLine("Previous Close Price", 40, 145, 260, 175)
        )
        val draft = InvestmentScreenshotParser.parse(
            ocrLines = lines,
            fullText = lines.joinToString("\n") { it.text },
            defaultCurrency = "ZAR"
        )
        assertEquals(null, draft.averagePurchasePrice)
    }
}
