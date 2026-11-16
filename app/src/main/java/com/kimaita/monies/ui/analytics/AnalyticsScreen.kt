package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.services.FilterKind
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.components.Segmented
import com.kimaita.monies.ui.components.TabTopBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class AnalyticsTab(val title: String) {
    OVERVIEW("Overview"), ACTIVITY("Activity"), SPENDING("Spending"), CASHFLOW("Cash flow")
}

private val tabOptions = AnalyticsTab.entries.map { it.title to it }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onSubject: (Long) -> Unit = {},
    onSettings: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.OVERVIEW) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listStates = AnalyticsTab.entries.associateWith { rememberLazyListState() }
    val active by viewModel.activeFilters.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshToday() }

    Scaffold(
        topBar = {
            TabTopBar("Analytics", onSettings) {
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (active.isNotEmpty()) Badge { Text("${active.size}") } }) {
                        Icon(painterResource(R.drawable.filter), "Filters")
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            TimeChips(viewModel)
            ActiveFilterChips(active, viewModel::clearFilter, viewModel::clearFilters)
            Spacer(Modifier.height(8.dp))
            Segmented(
                tabOptions, tab, { tab = it }, Modifier.padding(horizontal = 16.dp),
                radius = 14.dp, inset = 4.dp, itemPadding = PaddingValues(vertical = 8.dp),
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    AnalyticsTab.OVERVIEW -> OverviewTab(viewModel, listStates.getValue(tab)) { tab = it }
                    AnalyticsTab.ACTIVITY -> ActivityTab(viewModel, listStates.getValue(tab), onSubject)
                    AnalyticsTab.SPENDING -> SpendingTab(viewModel, listStates.getValue(tab), onSubject)
                    AnalyticsTab.CASHFLOW -> CashFlowTab(viewModel, listStates.getValue(tab))
                }
            }
        }
    }

    if (showFilters) FiltersSheet(viewModel) { showFilters = false }
}

/** The filters in use, one chip each; tapping a chip clears it. Nothing shows when none are set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChips(
    active: List<Pair<FilterKind, String>>,
    onClear: (FilterKind) -> Unit,
    onClearAll: () -> Unit,
) {
    if (active.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        active.forEach { (kind, label) ->
            InputChip(
                selected = true,
                onClick = { onClear(kind) },
                label = { Text(label, maxLines = 1) },
                trailingIcon = {
                    Icon(painterResource(R.drawable.close_small_24dp), "Remove", Modifier.size(16.dp))
                },
            )
        }
        if (active.size > 1) TextButton(onClick = onClearAll) { Text("Clear all") }
    }
}

/** Collects only timeFilter so unrelated filter edits don't recompose the chips. */
@Composable
private fun TimeChips(vm: AnalyticsViewModel) {
    val flow = remember(vm) { vm.filters.map { it.timeFilter }.distinctUntilChanged() }
    val timeFilter by flow.collectAsStateWithLifecycle(vm.filters.value.timeFilter)
    HighlightsFilter(timeFilter, onFilterChange = vm::setTimeFilter)
}
