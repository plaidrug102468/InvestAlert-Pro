package com.investalert.app.model

data class InvestmentTransaction(
    val id: String,
    val holdingId: String,
    val type: String,
    val units: Double,
    val amount: Double,
    val currency: String,
    val epochMs: Long,
    val note: String = ""
)
