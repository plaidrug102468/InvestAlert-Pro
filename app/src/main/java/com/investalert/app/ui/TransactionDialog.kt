package com.investalert.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.investalert.app.model.Holding
import com.investalert.app.model.InvestmentTransaction
import java.text.DateFormat
import java.util.Date

@Composable
fun TransactionDialog(
    holding: Holding,
    history: List<InvestmentTransaction>,
    onDismiss: () -> Unit,
    onApply: (type: String, units: Double, amount: Double, note: String) -> Unit
) {
    var type by remember { mutableStateOf("BUY") }
    var unitsText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${holding.name} transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Current: ${number(holding.units)} units • avg cost ${holding.averageCostPerUnit?.let { priceMoney(it, holding.investedCurrency) } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall
                )

                SimpleDropdown(
                    label = "Transaction",
                    selected = if (type == "BUY") "Buy" else "Sell",
                    options = listOf("Buy", "Sell"),
                    onSelected = { type = it.uppercase() }
                )
                OutlinedTextField(
                    value = unitsText,
                    onValueChange = { unitsText = numericInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Units") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = numericInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (type == "BUY") "Amount paid (${holding.investedCurrency})" else "Amount received (${holding.investedCurrency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note (optional)") },
                    singleLine = true
                )

                error?.let { Text(it, color = LossRed, style = MaterialTheme.typography.bodySmall) }

                if (history.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Recent", fontWeight = FontWeight.Bold)
                    Column(
                        modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        history.take(8).forEach { tx ->
                            Text(
                                "${tx.type} • ${number(tx.units)} units • ${money(tx.amount, tx.currency)} • " +
                                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(tx.epochMs)),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val units = unitsText.toDoubleOrNull()
                            val amount = amountText.toDoubleOrNull()
                            error = when {
                                units == null || units <= 0 -> "Enter units greater than zero."
                                amount == null || amount < 0 -> "Enter a valid amount."
                                type == "SELL" && units > holding.units -> "You cannot sell more units than you hold."
                                else -> null
                            }
                            if (error == null) onApply(type, units!!, amount!!, note.trim())
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            }
        }
    }
}
