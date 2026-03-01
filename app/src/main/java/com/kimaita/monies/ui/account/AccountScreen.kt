package com.kimaita.monies.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.kimaita.monies.ui.components.CurrencyText
import com.kimaita.monies.ui.components.TransactionListItem
import com.kimaita.monies.ui.theme.AppTheme
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val transactions = viewModel.subjectTransactions.collectAsLazyPagingItems()
    val subject by viewModel.subjectDetails.collectAsState()
    val stats by viewModel.subjectStats.collectAsState()
    val filters by viewModel.filters.collectAsState()


    AccountScreenContent(
        modifier = modifier.padding(horizontal = 8.dp),
        transactions = transactions,
        filters = filters,
        name = subject?.name ?: "",
        number = subject?.number ?: "",
        incomingTotal = stats.incomingTotal,
        outgoingTotal = stats.outgoingTotal,
        incomingCount = stats.incomingCount,
        outgoingCount = stats.outgoingCount,
        totalCost = stats.totalCost,
        category = "General",
        onBackClick = {
            navController.navigateUp()
        },
        onSortOrderChange = { newOrder -> viewModel.setSortOrder(newOrder) },
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreenContent(
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
) {

    val isPerson = incomingCount > 0 && outgoingCount > 0

    Scaffold(
        topBar = {
            TopAppBar(title = { }, navigationIcon = {
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
                        onSubjectClick = { /* Already on subject screen */ })
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
fun ProfileHeader(isPerson: Boolean, name: String, number: String?, category: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (isPerson) R.drawable.outline_account_circle_24 else R.drawable.business_center_24dp),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            number?.let {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.label),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = category ?: "Category",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
fun HighlightsSection(
    totalCost: Double,
    incomingTotal: Double?,
    outgoingTotal: Double?,
    incomingCount: Int,
    outgoingCount: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Transaction Volume",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (incomingTotal != null && incomingCount > 0) {
                        HighlightStatItem(
                            modifier = Modifier.weight(1f),
                            amount = incomingTotal,
                            count = incomingCount,
                            label = "Incoming",
                            isInc = true
                        )
                    }

                    if (outgoingTotal != null && outgoingCount > 0) {
                        HighlightStatItem(
                            modifier = Modifier.weight(1f),
                            amount = outgoingTotal,
                            count = outgoingCount,
                            label = "Outgoing",
                            isInc = false
                        )
                    }
                }

                Text(
                    text = "Cost KES ${"%.2f".format(totalCost)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HighlightStatItem(
    modifier: Modifier = Modifier, amount: Double, count: Int, label: String, isInc: Boolean
) {
    val iconColor = if (isInc) Color(0xFF006D3B) else Color(0xFFD32F2F)
    val icon = if (isInc) R.drawable.arrow_circle_down_24dp else R.drawable.arrow_circle_up_24dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(32.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            CurrencyText(amount = amount) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.titleMedium.fontSize)

                )
            }
            Text(
                text = "$count $label",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun TransactionsHeader(
    currentSortOrder: SortOrder, onSortOrderChange: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.sort),
                    contentDescription = "Sort",
                    modifier = Modifier.size(24.dp)
                )
            }
            DropdownMenu(
                expanded = expanded, onDismissRequest = { expanded = false }) {
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
                        onSortOrderChange(order)
                        expanded = false
                    }, trailingIcon = {
                        if (currentSortOrder == order) {
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
    }
}

// Mock Data Helper for Previews
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


        AccountScreenContent(
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
            onSortOrderChange = { })
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

        AccountScreenContent(
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
            onSortOrderChange = { })
    }
}