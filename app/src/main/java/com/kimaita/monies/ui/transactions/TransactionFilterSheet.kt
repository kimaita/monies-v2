package com.kimaita.monies.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.TraceStart
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.services.TypeGroup
import com.kimaita.monies.services.isSelected
import com.kimaita.monies.services.toggleGroup
import com.kimaita.monies.services.typeGroups
import com.kimaita.monies.ui.theme.AppTheme
import com.kimaita.monies.ui.util.formatDayTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFiltersGroup(
    filters: TransactionFilters,
    categoryMap: Map<Int, Category>,
    typeGroups: List<TypeGroup>,
    onRemoveFilter: (TransactionFilters) -> Unit
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filters.natureFilter != null) {
            InputChip(
                selected = true,
                onClick = { onRemoveFilter(filters.copy(natureFilter = null)) },
                label = { Text(if (filters.natureFilter) "Incoming" else "Outgoing") },
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.close_small_24dp), null, Modifier.size(18.dp)
                    )
                })
        }
        typeGroups.filter { g -> g.ids.any { it in filters.typeFilters } }.forEach { group ->
            InputChip(
                selected = true,
                onClick = { onRemoveFilter(filters.copy(typeFilters = filters.typeFilters - group.ids.toSet())) },
                label = { Text(group.label) },
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.close_small_24dp), null, Modifier.size(18.dp)
                    )
                })
        }
        filters.categoryFilters.forEach { catId ->
            val catName = categoryMap[catId]?.name ?: "Category"
            InputChip(
                selected = true,
                onClick = { onRemoveFilter(filters.copy(categoryFilters = filters.categoryFilters - catId)) },
                label = { Text(catName) },
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.close_small_24dp), null, Modifier.size(18.dp)
                    )
                })
        }
    }
}

@Composable
fun FilterBottomSheetContent(
    filters: TransactionFilters,
    transactionTypes: List<TransactionType>,
    categories: List<Category>,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onFilterChange: (TransactionFilters) -> Unit
) {

    val scrollState = rememberScrollState()
    var pickingStart by remember { mutableStateOf(false) }

    if (pickingStart) {
        TraceStartDialog(
            initial = filters.since?.time,
            onDismiss = { pickingStart = false },
            onPick = {
                pickingStart = false
                onFilterChange(filters.copy(since = TraceStart(it)))
            })
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text("SINCE", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val since = filters.since
                FilterChip(
                    selected = since != null,
                    onClick = { pickingStart = true },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.date_range), null, Modifier.size(FilterChipDefaults.IconSize))
                    },
                    label = {
                        Text(
                            when {
                                since == null -> "Any time"
                                since.afterId != null -> "Just after ${formatDayTime(since.time)}"
                                else -> formatDayTime(since.time)
                            }
                        )
                    })
                if (since != null) {
                    TextButton(onClick = { onFilterChange(filters.copy(since = null)) }) { Text("Clear") }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("DIRECTION", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filters.natureFilter == true,
                    onClick = {
                        onFilterChange(
                            filters.withNature(if (filters.natureFilter == true) null else true, transactionTypes)
                        )
                    },
                    label = { Text("Incoming") })
                FilterChip(
                    selected = filters.natureFilter == false,
                    onClick = {
                        onFilterChange(
                            filters.withNature(if (filters.natureFilter == false) null else false, transactionTypes)
                        )
                    },
                    label = { Text("Outgoing") })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("TRANSACTION TYPE", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val picked = filters.typeFilters.toSet()
                typeGroups(typesFor(filters.natureFilter, transactionTypes)).forEach { group ->
                    FilterChip(selected = group.isSelected(picked), onClick = {
                        onFilterChange(filters.copy(typeFilters = picked.toggleGroup(group).toList()))
                    }, label = { Text(group.label) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("CATEGORY", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    val isSelected = filters.categoryFilters.contains(category.id)
                    FilterChip(selected = isSelected, onClick = {
                        val newCats =
                            if (isSelected) filters.categoryFilters - category.id else filters.categoryFilters + category.id
                        onFilterChange(filters.copy(categoryFilters = newCats))
                    }, label = { Text(category.name) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onCancel) { Text("CANCEL") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onApply) { Text("APPLY") }
        }
    }

}

/** The types to offer for a direction: all of them when [nature] is null, else those going that way. */
internal fun typesFor(nature: Boolean?, types: List<TransactionType>): List<TransactionType> =
    if (nature == null) types else types.filter { it.isInc == nature }

/** These filters with a new direction; selected types that go the other way are dropped. */
internal fun TransactionFilters.withNature(
    nature: Boolean?, types: List<TransactionType>
): TransactionFilters {
    if (nature == null) return copy(natureFilter = null)
    val allowed = typesFor(nature, types).map { it.id }.toSet()
    return copy(natureFilter = nature, typeFilters = typeFilters.filter { it in allowed })
}

@Preview(showBackground = true)
@Composable
fun FilterBottomSheetContentPreview() {
    AppTheme {
        FilterBottomSheetContent(
            filters = TransactionFilters(), transactionTypes = listOf(
                TransactionType(
                    name = "Sent", pattern = "", isInc = false
                ), TransactionType(
                    name = "Received", pattern = "", isInc = true
                ), TransactionType(
                    name = "Paybill", pattern = "", isInc = false
                ), TransactionType(
                    name = "Airtime", pattern = "", isInc = false
                )
            ), categories = listOf(
                Category(name = "Groceries"), Category(name = "Transport"), Category(name = "Rent")
            ), onApply = {}, onCancel = {}, onFilterChange = {})
    }
}
