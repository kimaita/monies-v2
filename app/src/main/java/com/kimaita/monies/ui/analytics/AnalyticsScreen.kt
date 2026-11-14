package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.components.Segmented
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class AnalyticsTab(val title: String) {
    OVERVIEW("Overview"), ACTIVITY("Activity"), SPENDING("Spending"), CASHFLOW("Cash flow")
}

private val tabOptions = AnalyticsTab.entries.map { it.title to it }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.OVERVIEW) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listStates = AnalyticsTab.entries.associateWith { rememberLazyListState() }
    val scope by viewModel.scopeLabel.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshToday() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Analytics", fontWeight = FontWeight.SemiBold)
                        scope?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showFilters = true }) {
                        Icon(painterResource(R.drawable.filter), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Filters")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            TimeChips(viewModel)
            Spacer(Modifier.height(8.dp))
            Segmented(
                tabOptions, tab, { tab = it }, Modifier.padding(horizontal = 16.dp),
                radius = 14.dp, inset = 4.dp, itemPadding = PaddingValues(vertical = 8.dp),
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    AnalyticsTab.OVERVIEW -> OverviewTab(viewModel, listStates.getValue(tab)) { tab = it }
                    AnalyticsTab.ACTIVITY -> ActivityTab(viewModel, listStates.getValue(tab))
                    AnalyticsTab.SPENDING -> SpendingTab(viewModel, listStates.getValue(tab))
                    AnalyticsTab.CASHFLOW -> CashFlowTab(viewModel, listStates.getValue(tab))
                }
            }
        }
    }

    if (showFilters) FiltersSheet(viewModel) { showFilters = false }
}

/* ----------------------------- chrome ----------------------------- */

// Collects only timeFilter so unrelated filter edits don't recompose the chips.
@Composable
private fun TimeChips(vm: AnalyticsViewModel) {
    val flow = remember(vm) { vm.filters.map { it.timeFilter }.distinctUntilChanged() }
    val timeFilter by flow.collectAsStateWithLifecycle(vm.filters.value.timeFilter)
    HighlightsFilter(timeFilter, onFilterChange = vm::setTimeFilter)
}
