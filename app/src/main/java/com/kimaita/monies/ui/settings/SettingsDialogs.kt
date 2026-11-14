package com.kimaita.monies.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kimaita.monies.data.database.models.Category

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) = Row(
    Modifier
        .fillMaxWidth()
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    RadioButton(selected = selected, onClick = null)
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
}

@Composable
internal fun CategoryDialog(
    initial: Category?, onDismiss: () -> Unit, onConfirm: (String, Double?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var budget by remember { mutableStateOf(initial?.monthlyBudget?.let { formatBudget(it) }.orEmpty()) }
    val valid = name.isNotBlank() && (budget.isBlank() || budget.toDoubleOrNull() != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    budget, { budget = it }, label = { Text("Monthly budget (optional)") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name.trim(), budget.toDoubleOrNull()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatBudget(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
