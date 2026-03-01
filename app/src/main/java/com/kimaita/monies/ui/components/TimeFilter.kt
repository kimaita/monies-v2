package com.kimaita.monies.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class TimeFilter {
    object DAY : TimeFilter()
    object WEEK : TimeFilter()
    object MONTH : TimeFilter()
    object QUARTER : TimeFilter()
    object SIX_MONTHS : TimeFilter()
    object YTD : TimeFilter()
    object ALL : TimeFilter()
    data class CustomRange(val startDate: LocalDateTime, val endDate: LocalDateTime) : TimeFilter()
}

fun getDatesForFilter(filter: TimeFilter): Pair<String?, String?> {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    return when (filter) {
        TimeFilter.DAY -> today.format(formatter) to  today.plusDays(1).format(formatter)

        TimeFilter.WEEK -> today.minusDays(6).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.MONTH -> today.minusMonths(1).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.QUARTER -> today.minusMonths(3).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.SIX_MONTHS -> today.minusMonths(6).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.YTD -> today.year.toString() + "-01-01" to today.plusDays(1).format(formatter)

        TimeFilter.ALL -> null to null

        is TimeFilter.CustomRange -> {
            val start = filter.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val end = filter.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            start to end
        }
    }
}

@Composable
fun HighlightsFilter(selectedFilter: TimeFilter, onFilterChange: (TimeFilter) -> Unit) {
    val filters = mapOf(
        "TODAY" to TimeFilter.DAY,
        "1W" to TimeFilter.WEEK,
        "1M" to TimeFilter.MONTH,
//        "3M" to TimeFilter.QUARTER,
        "6M" to TimeFilter.SIX_MONTHS,
        "YTD" to TimeFilter.YTD,
        "ALL" to TimeFilter.ALL
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(filters.entries.toList()) { (label, filter) ->
            val isSelected = selectedFilter == filter

            FilterChip(
                selected = isSelected,
                onClick = { onFilterChange(filter) },
                label = { Text(label, fontSize = 12.sp) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                })
        }
    }
}
