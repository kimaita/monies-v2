package com.kimaita.monies.ui.export

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.kimaita.monies.R
import com.kimaita.monies.ui.util.toUtcDate
import com.kimaita.monies.ui.util.toUtcMillis
import java.time.LocalDate

/** A compact date chip like Home's 1M / YTD ones; [description] is what TalkBack reads. */
@Composable
internal fun BandChip(
    text: String?,
    description: String,
    selected: Boolean,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    val calendar = @Composable {
        Icon(painterResource(R.drawable.date_range), null, Modifier.size(FilterChipDefaults.IconSize))
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        leadingIcon = if (icon && text != null) calendar else null,
        label = { if (text != null) Text(text, fontSize = 12.sp) else calendar() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangeDialog(
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate, LocalDate) -> Unit,
) {
    val lastDay = today.toUtcMillis()
    val st = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.toUtcMillis(),
        initialSelectedEndDateMillis = if (start != null) end?.toUtcMillis() else null,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= lastDay
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = st.selectedStartDateMillis != null,
                onClick = {
                    st.selectedStartDateMillis?.let {
                        val from = it.toUtcDate()
                        onPick(from, st.selectedEndDateMillis?.toUtcDate() ?: from)
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DateRangePicker(st, showModeToggle = true) }
}
