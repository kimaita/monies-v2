package com.kimaita.monies.ui.export.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox

import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.export.NamedRef
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeFilterSelector(current: TimeFilter, onSelect: (TimeFilter) -> Unit) {
    val presets = remember {
        listOf(
            "Day" to TimeFilter.DAY,
            "Week" to TimeFilter.WEEK,
            "Month" to TimeFilter.MONTH,
            "Quarter" to TimeFilter.QUARTER,
            "6 Months" to TimeFilter.SIX_MONTHS,
            "YTD" to TimeFilter.YTD,
            "All" to TimeFilter.ALL,
        )
    }
    val custom = current as? TimeFilter.CustomRange
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (label, value) ->
                FilterChip(
                    selected = current == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) })
            }
            FilterChip(
                selected = custom != null,
                onClick = {
                    if (custom == null) {
                        val now = LocalDate.now()
                        onSelect(
                            TimeFilter.CustomRange(
                                now.minusDays(30).atStartOfDay(), now.atTime(LocalTime.MAX)
                            )
                        )
                    }
                },
                label = { Text("Custom") },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.date_range),
                        null,
                        Modifier.size(18.dp)
                    )
                },
            )
        }
        if (custom != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    "From",
                    custom.startDate.toLocalDate(),
                    { showStart = true },
                    null,
                    Modifier.weight(1f)
                )
                DateField(
                    "To",
                    custom.endDate.toLocalDate(),
                    { showEnd = true },
                    null,
                    Modifier.weight(1f)
                )
            }
        }
    }

    if (custom != null && showStart) DatePickerSheet(
        custom.startDate.toLocalDate(), { showStart = false }) { d ->
        onSelect(custom.copy(startDate = d.atStartOfDay())); showStart = false
    }
    if (custom != null && showEnd) DatePickerSheet(
        custom.endDate.toLocalDate(), { showEnd = false }) { d ->
        onSelect(custom.copy(endDate = d.atTime(LocalTime.MAX))); showEnd =
        false   // end-of-day for inclusive <=
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NatureSelector(current: Boolean?, onSelect: (Boolean?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == null,
            onClick = { onSelect(null) },
            label = { Text("All") })
        FilterChip(
            selected = current == true,
            onClick = { onSelect(true) },
            label = { Text("Income") })
        FilterChip(
            selected = current == false,
            onClick = { onSelect(false) },
            label = { Text("Expense") })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectChips(items: List<NamedRef<Int>>, selected: List<Int>, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val isSel = item.id in selected
            FilterChip(
                selected = isSel,
                onClick = { onToggle(item.id) },
                label = { Text(item.name) },
                leadingIcon = if (isSel) {
                    { Icon(painterResource(R.drawable.check_24px), null, Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
}

@Composable
fun DateField(
    label: String, value: LocalDate?, onPick: () -> Unit, onClear: (() -> Unit)?, modifier: Modifier
) {
    OutlinedTextField(
        value = value?.toString() ?: "Any",
        onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = {
            if (value != null && onClear != null) IconButton(onClear) {
                Icon(
                    painterResource(R.drawable.close_small_24dp),
                    "Clear"
                )
            }
            else Icon(painterResource(R.drawable.date_range), "Date Range")
        },
        modifier = modifier.clickable(onClick = onPick),
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <ID> SingleSelectField(
    label: String, selectedId: ID?, items: List<NamedRef<ID>>, onSelect: (ID?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = items.firstOrNull { it.id == selectedId }?.name ?: "Any"
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = selectedName, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Any") }, onClick = { onSelect(null); expanded = false })
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    onClick = { onSelect(item.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
            ?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let {
                    onPick(
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    )
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    ) { DatePicker(st) }
}

@Composable
fun SubjectField(
    summary: String,
    onOpen: () -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Subject", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    summary, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                )
            }
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        painterResource(R.drawable.close_small_24dp),
                        contentDescription = "Clear subjects"
                    )
                }
            } else {
                Icon(
                    painterResource(R.drawable.search), contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
