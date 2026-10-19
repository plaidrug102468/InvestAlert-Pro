package com.investalert.app.data

import android.content.Context
import com.investalert.app.model.Holding
import org.json.JSONArray
import org.json.JSONObject

class HoldingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("invest_alert", Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): List<Holding> {
        val raw = prefs.getString(KEY_HOLDINGS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                runCatching { add(fromJson(array.getJSONObject(i))) }
            }
        }
    }

    @Synchronized
    fun saveAll(holdings: List<Holding>) {
        val array = JSONArray()
        holdings.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_HOLDINGS, array.toString()).apply()
    }

    @Synchronized
    fun upsert(holding: Holding) {
        val items = getAll().toMutableList()
        val index = items.indexOfFirst { it.id == holding.id }
        if (index >= 0) items[index] = holding else items.add(holding)
        saveAll(items)
    }

    @Synchronized
    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    fun getUsdZar(): Double? {
        val value = prefs.getFloat(KEY_USD_ZAR, Float.NaN)
        return if (value.isNaN()) null else value.toDouble()
    }

    fun setUsdZar(value: Double) {
        prefs.edit().putFloat(KEY_USD_ZAR, value.toFloat()).apply()
    }

    fun isActiveMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_ACTIVE, false)

    fun setActiveMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACTIVE, enabled).apply()
    }

    private fun toJson(h: Holding) = JSONObject().apply {
        put("id", h.id)
        put("name", h.name)
        put("symbol", h.symbol)
        put("market", h.market)
        put("assetType", h.assetType)
        put("whereHeld", h.whereHeld)
        put("units", h.units)
        put("investedAmount", h.investedAmount)
        put("investedCurrency", h.investedCurrency)
        put("currency", h.currency)
        put("lowAlert", h.lowAlert ?: JSONObject.NULL)
        put("highAlert", h.highAlert ?: JSONObject.NULL)
        put("enabled", h.enabled)
        put("lastPrice", h.lastPrice ?: JSONObject.NULL)
        put("dayChangePercent", h.dayChangePercent ?: JSONObject.NULL)
        put("lastUpdatedEpochMs", h.lastUpdatedEpochMs ?: JSONObject.NULL)
        put("lowTriggered", h.lowTriggered)
        put("highTriggered", h.highTriggered)
        put("lastSource", h.lastSource ?: JSONObject.NULL)
    }

    private fun fromJson(o: JSONObject) = Holding(
        id = o.getString("id"),
        name = o.getString("name"),
        symbol = o.getString("symbol"),
        market = o.optString("market", "Other"),
        assetType = o.optString("assetType", "Investment"),
        whereHeld = o.optString("whereHeld", ""),
        units = o.optDouble("units", 0.0),
        investedAmount = o.optDouble("investedAmount", 0.0),
        investedCurrency = o.optString("investedCurrency", o.optString("currency", "ZAR")),
        currency = o.optString("currency", "USD"),
        lowAlert = nullableDouble(o, "lowAlert"),
        highAlert = nullableDouble(o, "highAlert"),
        enabled = o.optBoolean("enabled", true),
        lastPrice = nullableDouble(o, "lastPrice"),
        dayChangePercent = nullableDouble(o, "dayChangePercent"),
        lastUpdatedEpochMs = nullableLong(o, "lastUpdatedEpochMs"),
        lowTriggered = o.optBoolean("lowTriggered", false),
        highTriggered = o.optBoolean("highTriggered", false),
        lastSource = if (o.isNull("lastSource")) null else o.optString("lastSource")
    )

    private fun nullableDouble(o: JSONObject, key: String): Double? =
        if (!o.has(key) || o.isNull(key)) null else o.optDouble(key).takeIf { !it.isNaN() }

    private fun nullableLong(o: JSONObject, key: String): Long? =
        if (!o.has(key) || o.isNull(key)) null else o.optLong(key)

    companion object {
        private const val KEY_HOLDINGS = "holdings"
        private const val KEY_USD_ZAR = "usd_zar"
        private const val KEY_ACTIVE = "active_monitoring"
    }
}
