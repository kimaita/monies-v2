package com.kimaita.monies.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R
import com.kimaita.monies.models.TraceStart
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatAmount
import com.kimaita.monies.ui.util.formatDayTime
import com.kimaita.monies.ui.util.toUtcDate
import com.kimaita.monies.ui.util.toUtcMillis
import java.time.LocalDate
import java.time.LocalDateTime

private const val DASH = "—"
private const val HIDDEN = "••••"

/**
 * What came in and went out since [start], and the balance then and now. [summary] is null while
 * it loads; the figures show a dash, never a zero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TraceCard(
    start: TraceStart,
    summary: TraceSummary?,
    hideBalances: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val totals = summary?.totals
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(top = 12.dp)
                ) {
                    Text("Since ${formatDayTime(start.time)}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        countLine(totals?.count, start.afterId != null),
                        style = MaterialTheme.typography.labelMedium,
                        color = muted
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(painterResource(R.drawable.close_small_24dp), contentDescription = "Stop tracing")
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Figure("In", totals?.income, MoniesTheme.extra.positive)
                Figure("Out", totals?.expense, MoniesTheme.extra.negative)
                Figure("Fees", totals?.fees, MaterialTheme.colorScheme.onSurface)
            }
            Row(Modifier.padding(top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Balance", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = muted)
                Text(
                    "${balanceText(summary?.balanceThen, hideBalances)} → ${balanceText(summary?.balanceNow, hideBalances)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun countLine(count: Int?, afterPicked: Boolean): String {
    if (count == null) return DASH
    val n = "$count transaction${if (count == 1) "" else "s"}"
    return if (afterPicked) "$n after the one you picked" else n
}

private fun balanceText(v: Double?, hidden: Boolean) = when {
    hidden -> HIDDEN
    v == null -> DASH
    else -> formatAmount(v)
}

/** A labelled amount; a dash while loading, and muted when it rounds to zero, so a 0 doesn't read as a signal. */
@Composable
private fun Figure(label: String, amount: Double?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            amount?.let(::formatAmount) ?: DASH,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (amount == null || amount < 0.5) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}

/**
 * Picks when a trace starts: a day, then a time on it (midnight unless changed). Days after today
 * can't be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TraceStartDialog(
    initial: LocalDateTime?,
    onDismiss: () -> Unit,
    onPick: (LocalDateTime) -> Unit,
) {
    var dayMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = remember { LocalDate.now().toUtcMillis() }
    val day = dayMillis
    if (day == null) {
        val dates = rememberDatePickerState(
            initialSelectedDateMillis = initial?.toLocalDate()?.toUtcMillis() ?: today,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dates.selectedDateMillis != null,
                    onClick = { dayMillis = dates.selectedDateMillis },
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(dates, showModeToggle = true) }
    } else {
        val time = rememberTimePickerState(initialHour = initial?.hour ?: 0, initialMinute = initial?.minute ?: 0)
        var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
        TimePickerDialog(
            onDismissRequest = onDismiss,
            title = { TimePickerDialogDefaults.Title(displayMode = mode) },
            confirmButton = {
                TextButton(onClick = { onPick(day.toUtcDate().atTime(time.hour, time.minute)) }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dayMillis = null }) { Text("Back") } },
            modeToggleButton = {
                TimePickerDialogDefaults.DisplayModeToggle(
                    onDisplayModeChange = {
                        mode = if (mode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input
                        else TimePickerDisplayMode.Picker
                    },
                    displayMode = mode,
                )
            },
        ) {
            if (mode == TimePickerDisplayMode.Picker) TimePicker(time) else TimeInput(time)
        }
    }
}
