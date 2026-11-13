package com.kimaita.monies.ui.transactions

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.services.TypeGroup
import com.kimaita.monies.services.isSelected
import com.kimaita.monies.services.toggleGroup
import com.kimaita.monies.services.typeGroups
import com.kimaita.monies.ui.components.SortMenu
import com.kimaita.monies.ui.components.TransactionItemStyle
import com.kimaita.monies.ui.navigateSingleTop
import com.kimaita.monies.ui.navigateToTab
import com.kimaita.monies.ui.components.TransactionListItem
import com.kimaita.monies.ui.theme.AppTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: TransactionsViewModel = hiltViewModel(),
    openSearch: Boolean = false,
) {
    val filteredTransactions = viewModel.filteredTransactions.collectAsLazyPagingItems()
    val transactionTypes by viewModel.transactionTypes.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val subjectMatches by viewModel.subjectMatches.collectAsStateWithLifecycle()

    TransactionsScreenContent(
        modifier = modifier,
        filteredTransactions = filteredTransactions,
        subjectMatches = subjectMatches,
        transactionTypes = transactionTypes,
        categories = categories,
        filters = filters,
        openSearch = openSearch,
        onSetSearchQuery = viewModel::setSearchQuery,
        onSetSortOrder = viewModel::setSortOrder,
        onUpdateFilters = viewModel::updateFilters,
        onExport = {
            viewModel.exportResults()
            // Pop without saving, so Transactions is not restored later and Back from Export lands on Home.
            navController.popBackStack()
            navController.navigateToTab("export")
        },
        onBack = { navController.popBackStack() },
        onTransactionClick = { subjectId ->
            navController.navigateSingleTop("subject/$subjectId")
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreenContent(
    modifier: Modifier = Modifier,
    filteredTransactions: LazyPagingItems<Transaction>,
    subjectMatches: List<SubjectCount> = emptyList(),
    transactionTypes: List<TransactionType>,
    categories: List<Category>,
    filters: TransactionFilters,
    openSearch: Boolean = false,
    onSetSearchQuery: (String) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onUpdateFilters: (TransactionFilters) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit = {},
    onTransactionClick: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var showBottomSheet by remember { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(openSearch) }

    val focusRequester = remember { FocusRequester() }

    val closeSearch = {
        searchVisible = false
        onSetSearchQuery("")
    }
    BackHandler(enabled = searchVisible, onBack = closeSearch)

    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            if (searchVisible) {
                TextField(
                    value = filters.searchQuery,
                    onValueChange = onSetSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
            } else {
                Text(text = "Transactions")
            }
        }, navigationIcon = {
            IconButton(onClick = { if (searchVisible) closeSearch() else onBack() }) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_arrow_back_24),
                    contentDescription = if (searchVisible) "Close search" else "Back"
                )
            }
        }, actions = {
            IconButton(onClick = { if (searchVisible) closeSearch() else searchVisible = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.search), contentDescription = "Search"
                )
            }
            IconButton(onClick = { showBottomSheet = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.filter), contentDescription = "Filter"
                )
            }
//            IconButton(onClick = { scope.launch { onExport() } }) {
//                Icon(
//                    painter = painterResource(id = R.drawable.csv_export),
//                    contentDescription = "Export"
//                )
//            }
            SortMenu(filters.sortOrder, onSetSortOrder)

        })
    }) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            val hasActiveFilters =
                filters.natureFilter != null || filters.typeFilters.isNotEmpty() || filters.categoryFilters.isNotEmpty()

            if (hasActiveFilters) {
                val categoryMap = remember(categories) { categories.associateBy { it.id } }
                val groups = remember(transactionTypes) { typeGroups(transactionTypes) }

                ActiveFiltersGroup(
                    filters = filters,
                    categoryMap = categoryMap,
                    typeGroups = groups,
                    onRemoveFilter = onUpdateFilters
                )
            }
            if (hasActiveFilters || filters.searchQuery.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onExport) {
                        Icon(painterResource(R.drawable.csv_export), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export these results")
                    }
                }
            }
            if (hasActiveFilters) Spacer(modifier = Modifier.height(8.dp))
            val refresh = filteredTransactions.loadState.refresh
            if (filteredTransactions.itemCount == 0 && refresh !is LoadState.NotLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (refresh is LoadState.Error) {
                        RetryRow("Couldn't load transactions", filteredTransactions::retry)
                    } else {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredTransactions.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No transactions found",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subjectMatches, key = { "subject_${it.subjectId}" }) { match ->
                        SubjectMatchRow(match, onClick = { onTransactionClick(match.subjectId) })
                    }
                    items(
                        count = filteredTransactions.itemCount,
                        key = filteredTransactions.itemKey { it.id },
                        contentType = { "transaction_item" }) { index ->
                        val transaction = filteredTransactions[index]
                        if (transaction != null) {
                            TransactionListItem(
                                transaction = transaction,
                                style = TransactionItemStyle.TWO_LINE,
                                onSubjectClick = onTransactionClick
                            )
                        }

                    }
                    when (filteredTransactions.loadState.append) {
                        is LoadState.Loading -> item { CircularProgressIndicator() }
                        is LoadState.Error -> item {
                            RetryRow("Couldn't load more", filteredTransactions::retry)
                        }
                        else -> {}
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false }, sheetState = sheetState
            ) {
                var stagedFilters by remember { mutableStateOf(filters) }

                FilterBottomSheetContent(
                    filters = stagedFilters,
                    transactionTypes = transactionTypes,
                    categories = categories,
                    onApply = {
                        onUpdateFilters(stagedFilters)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    },
                    onCancel = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    },
                    onFilterChange = { stagedFilters = it })
            }
        }
    }
}

/** A subject whose name matches the search: one collapsed row that opens the subject. */
@Composable
private fun SubjectMatchRow(match: SubjectCount, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(vertical = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = match.name.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = match.name.uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${match.count} transaction${if (match.count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            painterResource(R.drawable.keyboard_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

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

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text("NATURE", style = MaterialTheme.typography.labelMedium)
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
fun TransactionsScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        val mockTransactions = listOf<Transaction>(
            Transaction(
                id = 1L,
                message = "Money received from John Doe",
                amount = 1500.0,
                cost = 0.0,
                code = "RKJ82910",
                transactionTime = LocalDateTime.now(),
                subject = null,
                type = com.kimaita.monies.models.TransactionType(
                    name = "Received",
                    isInc = true,
                    id = 1,
                    displayName = "Received",
                ),
                category = com.kimaita.monies.models.Category(id = 1, name = "General"),
                isInc = true
            ),
            Transaction(
                id = 2L,
                message = "Paid to Supermarket",
                amount = 2450.0,
                cost = 25.0,
                code = "SPO29102",
                transactionTime = LocalDateTime.now().minusDays(1),
                subject = null,
                type = com.kimaita.monies.models.TransactionType(
                    name = "Paybill",
                    isInc = false,
                    id = 2,
                    displayName = "Paybill"
                ),
                category = com.kimaita.monies.models.Category(id = 2, name = "Groceries"),
                isInc = false
            ),
            Transaction(
                id = 3L,
                message = "Sent to Jane Smith",
                amount = 500.0,
                cost = 12.0,
                code = "TRX11022",
                transactionTime = LocalDateTime.now().minusHours(5),
                subject = null,
                type = com.kimaita.monies.models.TransactionType(
                    name = "Sent",
                    isInc = false,
                    id = 3,
                    displayName = "Send Money"
                ),
                category = null,
                isInc = false
            )
        )
        val pagingData = PagingData.from(mockTransactions)
        val filteredTransactions =
            flowOf(pagingData).collectAsLazyPagingItems()

        TransactionsScreenContent(
            filteredTransactions = filteredTransactions,
            transactionTypes = emptyList(),
            categories = emptyList(),
            filters = TransactionFilters(),
            onSetSearchQuery = {},
            onSetSortOrder = {},
            onUpdateFilters = {},
            onExport = {},
            onTransactionClick = {})
    }
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
