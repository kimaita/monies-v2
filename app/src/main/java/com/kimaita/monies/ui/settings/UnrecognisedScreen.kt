package com.kimaita.monies.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kimaita.monies.R
import com.kimaita.monies.ui.components.CurrencyText
import com.kimaita.monies.ui.util.copyToClipboard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val messageDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnrecognisedScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val messages by viewModel.unparsed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unrecognised messages") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.outline_arrow_back_24), "Back")
                    }
                },
            )
        },
    ) { pad ->
        if (messages.isEmpty()) Text(
            "Nothing here.",
            modifier = Modifier.padding(pad).padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ) else LazyColumn(Modifier.padding(pad)) {
            items(messages, key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${m.address} · " + Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault())
                            .format(messageDateFmt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(m.body, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = {
                            copyToClipboard(context, "Message", m.body)
                        }) { Text("Copy") }
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.body)
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text("Share") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** A line's latest few transactions (date, type, amount, subject), so the user can tell lines apart. */
@Composable
internal fun Samples(viewModel: SettingsViewModel, accountId: Int) {
    val rows by remember(accountId) { viewModel.samples(accountId) }
        .collectAsStateWithLifecycle(emptyList())
    if (rows.isEmpty()) Text(
        "No transactions",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rows.forEach { t ->
        val type = t.type.displayName ?: t.type.name
        val who = t.subject?.let { it.name ?: it.number }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    who ?: type,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(t.transactionTime?.format(dateFmt), type.takeIf { who != null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CurrencyText(t.amount, isInc = t.isInc) { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
