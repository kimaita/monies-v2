package com.kimaita.monies.ui.subject

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.kimaita.monies.models.export.NamedRef
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kimaita.monies.R
import com.kimaita.monies.models.Category
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.Subject
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.TransactionType
import com.kimaita.monies.ui.components.SortMenu
import com.kimaita.monies.ui.components.TextDialog
import com.kimaita.monies.ui.components.TransactionListItem
import com.kimaita.monies.ui.navigateSingleTop
import com.kimaita.monies.ui.transactions.traceRoute
import com.kimaita.monies.ui.theme.AppTheme
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: SubjectViewModel = hiltViewModel()
) {
    val transactions = viewModel.subjectTransactions.collectAsLazyPagingItems()
    val subject by viewModel.subjectDetails.collectAsStateWithLifecycle()
    val stats by viewModel.subjectStats.collectAsStateWithLifecycle()
    val categoryName by viewModel.categoryName.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf(SubjectDialog.None) }
    var target by remember { mutableStateOf<NamedRef<Long>?>(null) }

    when (dialog) {
        SubjectDialog.Rename -> {
            TextDialog(
                title = "Rename", initial = subject?.name ?: "", requireText = true,
                onDismiss = { dialog = SubjectDialog.None },
                onConfirm = { viewModel.rename(it); dialog = SubjectDialog.None },
            )
        }

        SubjectDialog.Pick -> {
            val query by viewModel.subjectQuery.collectAsStateWithLifecycle()
            val results by viewModel.subjectResults.collectAsStateWithLifecycle()
            AlertDialog(
                onDismissRequest = { dialog = SubjectDialog.None },
                title = { Text("Merge into…") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = query, onValueChange = viewModel::setSubjectQuery, singleLine = true,
                            placeholder = { Text("Search subjects…") },
                        )
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(results.take(10), key = { it.id }) { ref ->
                                Text(
                                    ref.name,
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { target = ref; dialog = SubjectDialog.Confirm }
                                        .padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialog = SubjectDialog.None }) { Text("Cancel") } },
            )
        }

        SubjectDialog.Confirm -> target?.let { t ->
            AlertDialog(
                onDismissRequest = { dialog = SubjectDialog.None },
                title = { Text("Merge into ${t.name}?") },
                text = {
                    Text("All transactions of ${subject?.name.orEmpty()} move to ${t.name}, and new messages from it will land there too.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = SubjectDialog.None
                        viewModel.mergeInto(t) { navController.navigateUp() }
                    }) { Text("Merge") }
                },
                dismissButton = { TextButton(onClick = { dialog = SubjectDialog.None }) { Text("Cancel") } },
            )
        }

        SubjectDialog.None -> Unit
    }

    SubjectScreenContent(
        modifier = modifier,
        transactions = transactions,
        filters = filters,
        name = subject?.name ?: "",
        number = subject?.number ?: "",
        incomingTotal = stats.incomingTotal,
        outgoingTotal = stats.outgoingTotal,
        incomingCount = stats.incomingCount,
        outgoingCount = stats.outgoingCount,
        totalCost = stats.totalCost,
        category = categoryName ?: "Uncategorised",
        onBackClick = {
            navController.navigateUp()
        },
        onSortOrderChange = { newOrder -> viewModel.setSortOrder(newOrder) },
        onRenameClick = { dialog = SubjectDialog.Rename },
        onMergeClick = { viewModel.setSubjectQuery(""); dialog = SubjectDialog.Pick },
        onTraceFrom = { t -> traceRoute(t)?.let { navController.navigateSingleTop(it) } },
    )

}

private enum class SubjectDialog { None, Rename, Pick, Confirm }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreenContent(
    modifier: Modifier = Modifier,
    transactions: LazyPagingItems<Transaction>,
    filters: TransactionFilters,
    name: String,
    number: String,
    category: String?,
    incomingTotal: Double,
    outgoingTotal: Double,
    incomingCount: Int,
    outgoingCount: Int,
    totalCost: Double,
    onBackClick: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onRenameClick: () -> Unit = {},
    onMergeClick: () -> Unit = {},
    onTraceFrom: (Transaction) -> Unit = {},
) {

    val isPerson = incomingCount > 0 && outgoingCount > 0

    Scaffold(
        topBar = {
            TopAppBar(title = { }, actions = {
                TextButton(onClick = onRenameClick) { Text("Rename") }
                TextButton(onClick = onMergeClick) { Text("Merge into…") }
            }, navigationIcon = {
                IconButton(onClick = { onBackClick() }) {
                    Icon(
                        painter = painterResource(R.drawable.outline_arrow_back_24),
                        contentDescription = "Back"
                    )
                }
            })
        }) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ProfileHeader(
                    isPerson = isPerson, name = name, number = number, category = category
                )
                Spacer(modifier = Modifier.height(32.dp))
                HighlightsSection(
                    totalCost = totalCost,
                    incomingTotal = incomingTotal,
                    outgoingTotal = outgoingTotal,
                    incomingCount = incomingCount,
                    outgoingCount = outgoingCount
                )
                Spacer(modifier = Modifier.height(24.dp))
                TransactionsHeader(
                    currentSortOrder = filters.sortOrder, onSortOrderChange = onSortOrderChange
                )
            }

            items(transactions.itemCount, key = transactions.itemKey { it.id }) { index ->
                val transaction = transactions[index]
                if (transaction != null) {
                    TransactionListItem(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        transaction = transaction,
                        onTraceFrom = onTraceFrom,
                        onSubjectClick = {})
                    if (index < transactions.itemCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

}

@Composable
fun TransactionsHeader(
    currentSortOrder: SortOrder, onSortOrderChange: (SortOrder) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TRANSACTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SortMenu(currentSortOrder, onSortOrderChange)
    }
}

private fun getMockTransaction(id: Long, isInc: Boolean, amount: Double) = Transaction(
    id = id,
    amount = amount,
    code = "XYZ${100 + id}",
    transactionTime = LocalDateTime.now().minusDays(id),
    isInc = isInc,
    cost = 15.0,
    message = "Mock Transaction Message",
    subject = Subject(id = 1, name = "John Doe", number = "0712345678"),
    type = TransactionType(
        id = 1, name = "paybill", isInc = isInc, displayName = "Paybill"
    ),
    category = Category(id = 1, name = "Utilities"),

    )

@Preview(showBackground = true, name = "Subject Profile")
@Composable
fun SubjectProfilePreview() {
    AppTheme {
        val transactions = listOf(
            getMockTransaction(1, true, 530.0),
            getMockTransaction(2, false, 300.0),
            getMockTransaction(3, false, 70.0)
        )
        val fakeTransactions = flowOf(PagingData.from(transactions)).collectAsLazyPagingItems()


        SubjectScreenContent(
            name = "John Doe",
            number = "0701020304",
            category = "CATEGORY",
            transactions = fakeTransactions,
            onBackClick = {},
            incomingTotal = 3000.00,
            outgoingTotal = 1000.00,
            incomingCount = 8,
            outgoingCount = 2,
            totalCost = 0.0,
            filters = TransactionFilters(),
            onSortOrderChange = {})
    }
}

@Preview(showBackground = true, name = "Business Profile")
@Composable
fun BusinessProfilePreview() {
    AppTheme {
        val mockList = listOf(
            getMockTransaction(1, false, 1530.0), getMockTransaction(2, false, 1930.0)
        )

        val pagingData = PagingData.from(mockList)
        val fakeTransactions = flowOf(pagingData).collectAsLazyPagingItems()

        SubjectScreenContent(
            transactions = fakeTransactions,
            name = "SAFARICOM PLC",
            number = "444444",
            category = "Utilities",
            onBackClick = { },
            incomingTotal = 0.00,
            outgoingTotal = 1000.00,
            incomingCount = 0,
            outgoingCount = 6,
            totalCost = 5.0,
            filters = TransactionFilters(),
            onSortOrderChange = {})
    }
}
