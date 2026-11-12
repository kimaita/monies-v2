package com.kimaita.monies.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import com.kimaita.monies.models.TimeFilter


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
        contentPadding = PaddingValues(horizontal = 16.dp),
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
