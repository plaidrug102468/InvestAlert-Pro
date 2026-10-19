package com.investalert.app.model

data class Holding(
    val id: String,
    val name: String,
    val symbol: String,
    val market: String,
    val assetType: String,
    val whereHeld: String,
    val units: Double,
    val investedAmount: Double,
    val investedCurrency: String,
    val currency: String,
    val lowAlert: Double?,
    val highAlert: Double?,
    val enabled: Boolean = true,
    val watchOnly: Boolean = false,
    val lastPrice: Double? = null,
    val dayChangePercent: Double? = null,
    val lastUpdatedEpochMs: Long? = null,
    val lowTriggered: Boolean = false,
    val highTriggered: Boolean = false,
    val lastSource: String? = null
) {
    val averageCostPerUnit: Double?
        get() = if (!watchOnly && units > 0.0 && investedAmount >= 0.0) investedAmount / units else null

    val currentValue: Double?
        get() = if (watchOnly) null else lastPrice?.let { it * units }
}
