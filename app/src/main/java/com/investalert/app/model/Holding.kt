package com.investalert.app.model

data class Holding(
    val id: String,
    val name: String,
    val symbol: String,
    val market: String,
    val whereHeld: String,
    val units: Double,
    val investedAmount: Double,
    val currency: String,
    val lowAlert: Double?,
    val highAlert: Double?,
    val enabled: Boolean = true,
    val lastPrice: Double? = null,
    val lastUpdatedEpochMs: Long? = null,
    val lowTriggered: Boolean = false,
    val highTriggered: Boolean = false,
    val lastSource: String? = null
) {
    val averageBuyPrice: Double?
        get() = if (units > 0.0 && investedAmount >= 0.0) investedAmount / units else null

    val currentValue: Double?
        get() = lastPrice?.let { it * units }

    val profitLoss: Double?
        get() = currentValue?.let { it - investedAmount }

    val profitLossPercent: Double?
        get() = if (investedAmount > 0.0) profitLoss?.div(investedAmount)?.times(100.0) else null
}
