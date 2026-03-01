package com.kimaita.monies.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kimaita.monies.R
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.ui.components.TransactionItemStyle
import com.kimaita.monies.ui.components.TransactionListItem
import com.kimaita.monies.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val filteredTransactions = viewModel.filteredTransactions.collectAsLazyPagingItems()
    val transactionTypes by viewModel.transactionTypes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val filters by viewModel.filters.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var showBottomSheet by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(topBar = { ->
        TopAppBar(title = { ->
            if (searchVisible) {
                TextField(
                    value = filters.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search transactions...") },
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
            if (searchVisible) {
                IconButton(onClick = { searchVisible = false }) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_arrow_back_24),
                        contentDescription = "Close Search"
                    )
                }
            }
        }, actions = {
            IconButton(onClick = { searchVisible = !searchVisible }) {
                Icon(
                    painter = painterResource(id = R.drawable.search), contentDescription = "Search"
                )
            }
            IconButton(onClick = { showBottomSheet = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.filter), contentDescription = "Filter"
                )
            }
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.sort), contentDescription = "Sort"
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(text = {
                            val label = when (order) {
                                SortOrder.DATE_DESC -> "Newest first"
                                SortOrder.DATE_ASC -> "Oldest first"
                                SortOrder.AMOUNT_DESC -> "Largest first"
                                SortOrder.AMOUNT_ASC -> "Smallest first"
                            }
                            Text(label)
                        }, onClick = {
                            viewModel.setSortOrder(order)
                            sortMenuExpanded = false
                        }, trailingIcon = {
                            if (filters.sortOrder == order) {
                                Icon(
                                    painterResource(R.drawable.check_24px),
                                    null,
                                    Modifier.size(18.dp)
                                )
                            }
                        })
                    }
                }
            }

        })
    }) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            ActiveFiltersGroup(
                filters = filters,
                categories = categories,
                transactionTypes = transactionTypes.values.toList(),
                onRemoveFilter = { updatedFilters -> viewModel.updateFilters(updatedFilters) })
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    filteredTransactions.itemCount,
                    key = filteredTransactions.itemKey { it.id }) { index ->
                    val transaction = filteredTransactions[index]
                    if (transaction != null) {
                        TransactionListItem(
                            transaction = transaction,
                            style = TransactionItemStyle.TWO_LINE,
                            onSubjectClick = { subjectId ->
                                navController.navigate("account/$subjectId")
                            })
                    }

                }
                when (val state = filteredTransactions.loadState.append) {
                    is LoadState.Loading -> item { CircularProgressIndicator() }
                    is LoadState.Error -> item { Text("Error loading more...") }
                    else -> {}
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
                    transactionTypes = transactionTypes.values.toList(),
                    categories = categories,
                    onApply = {
                        viewModel.updateFilters(stagedFilters)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFiltersGroup(
    filters: TransactionFilters,
    categories: List<Category>,
    transactionTypes: List<TransactionType>,
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
        filters.typeFilters.forEach { typeId ->
            val typeName = transactionTypes.find { it.id == typeId }?.finalDisplayName ?: "Type"
            InputChip(
                selected = true,
                onClick = { onRemoveFilter(filters.copy(typeFilters = filters.typeFilters - typeId)) },
                label = { Text(typeName) },
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.close_small_24dp), null, Modifier.size(18.dp)
                    )
                })
        }
        filters.categoryFilters.forEach { catId ->
            val catName = categories.find { it.id == catId }?.name ?: "Category"
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
        ) {
            Text("NATURE", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filters.natureFilter == true,
                    onClick = { onFilterChange(filters.copy(natureFilter = if (filters.natureFilter == true) null else true)) },
                    label = { Text("Incoming") })
                FilterChip(
                    selected = filters.natureFilter == false,
                    onClick = { onFilterChange(filters.copy(natureFilter = if (filters.natureFilter == false) null else false)) },
                    label = { Text("Out") })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("TRANSACTION TYPE", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                transactionTypes.forEach { type ->
                    val isSelected = filters.typeFilters.contains(type.id)
                    FilterChip(selected = isSelected, onClick = {
                        val newTypes =
                            if (isSelected) filters.typeFilters - type.id else filters.typeFilters + type.id
                        onFilterChange(filters.copy(typeFilters = newTypes))

                    }, label = { Text(type.finalDisplayName) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
//            Text("CATEGORY", style = MaterialTheme.typography.labelMedium)
//            FlowRow(
//                horizontalArrangement = Arrangement.spacedBy(8.dp),
//            ) {
//                categories.forEach { category ->
//                    val isSelected = filters.categoryFilters.contains(category.id)
//                    FilterChip(selected = isSelected, onClick = {
//                        val newCats =
//                            if (isSelected) filters.categoryFilters - category.id else filters.categoryFilters + category.id
//                        onFilterChange(filters.copy(categoryFilters = newCats))
//                    }, label = { Text(category.name) })
//                }
//            }
//            Spacer(modifier = Modifier.height(16.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onCancel) { Text("CANCEL") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onApply) { Text("APPLY") }
        }
    }

}


@Preview(showBackground = true)
@Composable
fun TransactionsScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        TransactionsScreen(navController = navController)
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
