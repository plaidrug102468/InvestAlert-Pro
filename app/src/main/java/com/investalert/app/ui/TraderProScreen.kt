package com.investalert.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.investalert.app.data.TraderProEngine
import com.investalert.app.model.Holding
import java.util.Locale

private data class ChatTurn(
    val question: String,
    val reply: TraderProEngine.AssistantReply?
)

@Composable
fun TraderProScreen(
    modifier: Modifier,
    holdings: List<Holding>,
    onAskTrader: (String, (TraderProEngine.AssistantReply) -> Unit) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var turns by remember { mutableStateOf<List<ChatTurn>>(emptyList()) }
    val uriHandler = LocalUriHandler.current

    fun ask(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank() || isThinking) return
        isThinking = true
        question = ""
        val index = turns.size
        turns = turns + ChatTurn(prompt, null)
        onAskTrader(prompt) { reply ->
            val updated = turns.toMutableList()
            if (index in updated.indices) updated[index] = ChatTurn(prompt, reply)
            turns = updated
            isThinking = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Trader Pro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Portfolio, market and news research in one place.")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(false, { ask("Review my portfolio risk") }, { Text("Portfolio") }, modifier = Modifier.weight(1f))
            FilterChip(false, { ask("Best JSE opportunities to research") }, { Text("JSE") }, modifier = Modifier.weight(1f))
            FilterChip(false, { ask("Best US opportunities to research") }, { Text("US") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(false, { ask("Best crypto opportunities to research") }, { Text("Crypto") }, modifier = Modifier.weight(1f))
            FilterChip(false, { ask("Best forex opportunities to research") }, { Text("Forex") }, modifier = Modifier.weight(1f))
            FilterChip(false, { ask("What should I review for selling or reducing?") }, { Text("Sell review") }, modifier = Modifier.weight(1f))
        }

        turns.forEach { turn ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("You", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(turn.question)
                }
            }

            val result = turn.reply
            if (result == null) {
                Card(Modifier.fillMaxWidth()) {
                    Text("Analysing…", modifier = Modifier.padding(14.dp))
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(result.body)

                        result.analyses.forEach { a ->
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(a.symbol, fontWeight = FontWeight.Bold)
                                Text("${a.score}/100", fontWeight = FontWeight.Bold)
                            }
                            Text("${a.stance} • ${a.confidence} confidence")
                            Text(
                                "Price ${priceMoney(a.price, a.currency)}" +
                                    (a.rsi14?.let { " • RSI ${String.format(Locale.US, "%.1f", it)}" } ?: "") +
                                    (a.momentum30?.let { " • 30d ${signedPct(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelMedium
                            )
                            a.reasons.take(4).forEach { reason ->
                                Text("• $reason", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (result.links.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Related news", fontWeight = FontWeight.Bold)
                            result.links.forEach { item ->
                                TextButton(
                                    onClick = { runCatching { uriHandler.openUri(item.url) } },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(item.title)
                                        if (item.source.isNotBlank()) {
                                            Text(item.source, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        Text(result.sourceNote, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Trader Pro") },
            placeholder = { Text("e.g. Why is Sasol dropping? What should I research?") },
            minLines = 2
        )
        Button(
            onClick = { ask(question) },
            enabled = question.isNotBlank() && !isThinking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isThinking) "Analysing…" else "Send")
        }

        if (holdings.isEmpty()) {
            Text("Add investments or watchlist items to make Trader Pro portfolio-aware.")
        }
        Spacer(Modifier.height(96.dp))
    }
}
