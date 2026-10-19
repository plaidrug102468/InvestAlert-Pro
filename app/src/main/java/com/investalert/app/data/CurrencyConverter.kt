package com.investalert.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CurrencyConverter {
    suspend fun rate(from: String, to: String): Double = withContext(Dispatchers.IO) {
        val f = from.uppercase()
        val t = to.uppercase()
        if (f == t) return@withContext 1.0

        val direct = "$f$t=X"
        runCatching { YahooPriceProvider.fetchPrice(direct).price }
            .getOrElse {
                val inverse = YahooPriceProvider.fetchPrice("$t$f=X").price
                if (inverse <= 0.0) throw IllegalStateException("No FX rate for $f/$t")
                1.0 / inverse
            }
    }

    suspend fun convert(amount: Double, from: String, to: String): Double {
        return amount * rate(from, to)
    }
}
