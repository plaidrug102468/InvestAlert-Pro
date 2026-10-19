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
import androidx.compose.material3.OutlinedButton
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
import com.investalert.app.data.TraderAttachment
import com.investalert.app.data.TraderProEngine
import com.investalert.app.model.Holding
import java.util.Locale

private data class ChatTurn(
    val question: String,
    val attachmentNames: List<String>,
    val reply: TraderProEngine.AssistantReply?
)

@Composable
fun TraderProScreen(
    modifier: Modifier,
    holdings: List<Holding>,
    attachments: List<TraderAttachment>,
    attachmentBusy: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onAskTrader: (String, List<TraderAttachment>, String, (TraderProEngine.AssistantReply) -> Unit) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var turns by remember { mutableStateOf<List<ChatTurn>>(emptyList()) }
    val uriHandler = LocalUriHandler.current

    fun ask(text: String) {
        val prompt = text.trim()
        if ((prompt.isBlank() && attachments.isEmpty()) || isThinking) return
        isThinking = true
        question = ""
        val sentAttachments = attachments.toList()
        val displayPrompt = prompt.ifBlank { "Analyse these attachments" }
        val context = turns.takeLast(6).joinToString("\n") { turn ->
            buildString {
                append("User: ").append(turn.question)
                turn.reply?.let {
                    append("\nTrader Pro: ").append(it.title).append(" — ").append(it.body)
                    if (it.analyses.isNotEmpty()) {
                        append(" Symbols: ").append(it.analyses.joinToString { a -> a.symbol })
                    }
                }
            }
        }
        val index = turns.size
        turns = turns + ChatTurn(displayPrompt, sentAttachments.map { it.name }, null)
        onAskTrader(displayPrompt, sentAttachments, context) { reply ->
            val updated = turns.toMutableList()
            if (index in updated.indices) updated[index] = ChatTurn(displayPrompt, sentAttachments.map { it.name }, reply)
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
                Text("Markets, forex, crypto, portfolio research, charts, news and statement analysis.")
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("You", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(turn.question)
                    if (turn.attachmentNames.isNotEmpty()) {
                        Text(
                            turn.attachmentNames.joinToString(prefix = "Attached: "),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            val result = turn.reply
            if (result == null) {
                Card(Modifier.fillMaxWidth()) {
                    Text("Analysing markets, portfolio and sources…", modifier = Modifier.padding(14.dp))
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
                                    (a.momentum30?.let { " • 30d ${signedPct(it)}" } ?: "") +
                                    (a.momentum90?.let { " • 90d ${signedPct(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (a.chartPrices.size >= 2) {
                                MarketChart(
                                    prices = a.chartPrices,
                                    label = "${a.symbol} • ~3 months",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            a.reasons.take(6).forEach { reason ->
                                Text("• $reason", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (result.links.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Related news & sources", fontWeight = FontWeight.Bold)
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

        if (attachments.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Attachments", fontWeight = FontWeight.Bold)
                    attachments.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${item.kind}" + if (item.extractedText.isBlank()) " • text unavailable" else " • ready",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(onClick = { onRemoveAttachment(index) }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Trader Pro") },
            placeholder = { Text("Why is Bitcoin moving? Compare Sasol vs Aspen. Analyse this statement…") },
            minLines = 2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onAttach,
                enabled = !attachmentBusy && !isThinking,
                modifier = Modifier.weight(0.42f)
            ) {
                Text(if (attachmentBusy) "Reading…" else "+ Attach")
            }
            Button(
                onClick = { ask(question) },
                enabled = (question.isNotBlank() || attachments.isNotEmpty()) && !isThinking,
                modifier = Modifier.weight(0.58f)
            ) {
                Text(if (isThinking) "Analysing…" else "Send")
            }
        }
        Text(
            "Attachments supported: screenshots/images, PDF statements, CSV, TXT and JSON.",
            style = MaterialTheme.typography.labelSmall
        )

        if (holdings.isEmpty()) {
            Text("Add investments or watchlist items to make Trader Pro portfolio-aware.")
        }
        Spacer(Modifier.height(96.dp))
    }
}
