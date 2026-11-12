package com.kimaita.monies.ui.export.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.export.ExportState

@Composable
fun ProgressPanel(s: ExportState.InProgress, onCancel: () -> Unit) {
    val fraction = s.total?.takeIf { it > 0 }?.let { (s.processed.toFloat() / it).coerceIn(0f, 1f) }
    val animated by animateFloatAsState(fraction ?: 0f, tween(300), label = "progress")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Exporting ${s.format.label}…", style = MaterialTheme.typography.titleSmall)
            Text(
                if (fraction != null) "${(animated * 100).toInt()}%"
                else "${s.processed}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (fraction != null) LinearProgressIndicator({ animated }, Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                s.total?.let { "${s.processed} of $it" }?.plus(" • runs in background")
                    ?: "Working… you can leave this screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}