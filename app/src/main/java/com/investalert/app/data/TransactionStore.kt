package com.investalert.app.data

import android.content.Context
import com.investalert.app.model.InvestmentTransaction
import org.json.JSONArray
import org.json.JSONObject

class TransactionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("invest_alert_transactions", Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): List<InvestmentTransaction> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                runCatching { add(fromJson(array.getJSONObject(i))) }
            }
        }.sortedByDescending { it.epochMs }
    }

    fun forHolding(holdingId: String): List<InvestmentTransaction> =
        getAll().filter { it.holdingId == holdingId }

    @Synchronized
    fun add(tx: InvestmentTransaction) {
        val items = getAll().toMutableList()
        items += tx
        val array = JSONArray()
        items.sortedBy { it.epochMs }.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun toJson(tx: InvestmentTransaction) = JSONObject().apply {
        put("id", tx.id)
        put("holdingId", tx.holdingId)
        put("type", tx.type)
        put("units", tx.units)
        put("amount", tx.amount)
        put("currency", tx.currency)
        put("epochMs", tx.epochMs)
        put("note", tx.note)
    }

    private fun fromJson(o: JSONObject) = InvestmentTransaction(
        id = o.getString("id"),
        holdingId = o.getString("holdingId"),
        type = o.optString("type", "BUY"),
        units = o.optDouble("units", 0.0),
        amount = o.optDouble("amount", 0.0),
        currency = o.optString("currency", "ZAR"),
        epochMs = o.optLong("epochMs", 0L),
        note = o.optString("note", "")
    )

    companion object {
        private const val KEY = "transactions"
    }
}
