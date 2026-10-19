package com.investalert.app.monitoring

import com.investalert.app.model.Holding

enum class AlertType { LOW, HIGH }

data class AlertEvent(val type: AlertType, val threshold: Double, val actualPrice: Double)

data class Evaluation(val updated: Holding, val events: List<AlertEvent>)

object AlertEvaluator {
    fun evaluate(holding: Holding, newPrice: Double): Evaluation {
        var lowTriggered = holding.lowTriggered
        var highTriggered = holding.highTriggered
        val events = mutableListOf<AlertEvent>()

        holding.lowAlert?.let { low ->
            if (!lowTriggered && newPrice <= low) {
                lowTriggered = true
                events += AlertEvent(AlertType.LOW, low, newPrice)
            } else if (lowTriggered && newPrice > low) lowTriggered = false
        }

        holding.highAlert?.let { high ->
            if (!highTriggered && newPrice >= high) {
                highTriggered = true
                events += AlertEvent(AlertType.HIGH, high, newPrice)
            } else if (highTriggered && newPrice < high) highTriggered = false
        }

        return Evaluation(
            holding.copy(lastPrice = newPrice, lowTriggered = lowTriggered, highTriggered = highTriggered),
            events
        )
    }
}
