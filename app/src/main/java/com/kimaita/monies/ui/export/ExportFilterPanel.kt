package com.kimaita.monies.ui.export

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R
import com.kimaita.monies.services.isSelected

/* ---------------- filters ---------------- */

@Composable
internal fun AddFiltersRow(activeCount: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(cs.primaryContainer),
                Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.add_24),
                    null,
                    Modifier.size(18.dp),
                    tint = cs.onPrimaryContainer
                )
            }
            Column {
                Text(
                    if (activeCount > 0) "Filters · $activeCount" else "Add filters",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Subject, type, category, direction",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
        Icon(painterResource(R.drawable.keyboard_arrow_right), null, tint = cs.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterPanel(vm: ExportViewModel, onCollapse: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sel by vm.sel.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val query by vm.subjectQuery.collectAsStateWithLifecycle()
    val results by vm.subjectResults.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::clearFilters) { Text("Clear all") }
                IconButton(onClick = onCollapse) {
                    Icon(
                        painterResource(R.drawable.collapse),
                        "Collapse"
                    )
                }
            }
        }

        FilterLabel("Direction")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirChip("All", sel.nature == null) { vm.setNature(null) }
            DirChip("Money in", sel.nature == true) { vm.setNature(true) }
            DirChip("Money out", sel.nature == false) { vm.setNature(false) }
        }

        if (sel.search.isNotBlank()) {
            FilterLabel("Search")
            InputChip(
                selected = true,
                onClick = { vm.setSearch("") },
                label = { Text(sel.search, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(painterResource(R.drawable.close_small_24dp), null, Modifier.size(16.dp))
                })
        }

        FilterLabel("Subject")
        OutlinedTextField(
            value = query, onValueChange = vm::setSubjectQuery, singleLine = true,
            placeholder = { Text("Search subjects…") },
            leadingIcon = { Icon(painterResource(R.drawable.search), null) },
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions.Default,
        )
        if (sel.subjects.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sel.subjects.forEach { ref ->
                InputChip(
                    selected = true,
                    onClick = { vm.removeSubject(ref) },
                    label = { Text(ref.name) },
                    trailingIcon = {
                        Icon(
                            painterResource(R.drawable.close_small_24dp),
                            null,
                            Modifier.size(16.dp)
                        )
                    })
            }
        }
        if (query.isNotBlank() && results.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                8.dp
            )
        ) {
            results.filter { r -> sel.subjects.none { it.id == r.id } }.take(10).forEach { ref ->
                AssistChip(
                    onClick = { vm.addSubject(ref) }, label = { Text(ref.name) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.add_24),
                            null,
                            Modifier.size(16.dp)
                        )
                    })
            }
        }

        options?.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
            FilterLabel("Categories")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    FilterChip(
                        selected = c.id in sel.categoryIds,
                        onClick = { vm.toggleCategory(c.id) },
                        label = { Text(c.name) })
                }
            }
        }

        options?.types?.takeIf { it.isNotEmpty() }?.let { types ->
            FilterLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    FilterChip(
                        selected = t.isSelected(sel.typeIds),
                        onClick = { vm.toggleType(t) },
                        label = { Text(t.label) })
                }
            }
        }
    }
}

@Composable
internal fun DirChip(label: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })

@Composable
private fun FilterLabel(t: String) =
    Text(
        t.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
