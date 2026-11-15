package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.services.isSelected
import com.kimaita.monies.services.toggleGroup
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.components.Segmented

private val natureOptions: List<Pair<String, Boolean?>> =
    listOf("All" to null, "Money in" to true, "Money out" to false)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FiltersSheet(vm: AnalyticsViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filters by vm.filters.collectAsStateWithLifecycle()
    val opts by vm.filterOptions.collectAsStateWithLifecycle()
    val query by vm.subjectQuery.collectAsStateWithLifecycle()
    val results by vm.subjectResults.collectAsStateWithLifecycle()
    val chosen by vm.selectedSubjects.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text("Direction", style = MaterialTheme.typography.labelLarge)
            Segmented(natureOptions, filters.natureFilter, vm::setNature)

            opts?.types?.takeIf { it.isNotEmpty() }?.let { types ->
                Text("Type", style = MaterialTheme.typography.labelLarge)
                val picked = filters.typeFilters.toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { group ->
                        FilterChip(
                            selected = group.isSelected(picked),
                            onClick = { vm.setTypeFilters(picked.toggleGroup(group).toList()) },
                            label = { Text(group.label) })
                    }
                }
            }
            opts?.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
                Text("Category", style = MaterialTheme.typography.labelLarge)
                MultiSelectChips(
                    cats,
                    filters.categoryFilters
                ) { vm.setCategoryFilters(filters.categoryFilters.toggle(it)) }
            }

            Text("Subjects", style = MaterialTheme.typography.labelLarge)
            if (chosen.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chosen.forEach { ref ->
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
                        },
                    )
                }
            }
            OutlinedTextField(
                value = query, onValueChange = vm::setSubjectQuery,
                placeholder = { Text("Search subjects") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions.Default,
            )
            if (results.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                results.take(12).forEach { ref ->
                    val on = chosen.any { it.id == ref.id }
                    FilterChip(
                        selected = on,
                        onClick = { if (on) vm.removeSubject(ref) else vm.addSubject(ref) },
                        label = { Text(ref.name) },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = vm::clearFilters,
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectChips(
    options: List<NamedRef<Int>>,
    selected: List<Int>,
    onToggle: (Int) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { ref ->
            FilterChip(
                selected = ref.id in selected,
                onClick = { onToggle(ref.id) },
                label = { Text(ref.name) })
        }
    }
}
private fun List<Int>.toggle(id: Int): List<Int> = if (id in this) this - id else this + id
