package com.kimaita.monies.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.kimaita.monies.R
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.ui.components.CurrencyText
import com.kimaita.monies.ui.components.HighlightsFilter
import com.kimaita.monies.ui.components.TimeFilter
import com.kimaita.monies.ui.components.TransactionItemStyle
import com.kimaita.monies.ui.components.TransactionListItem
import com.kimaita.monies.ui.theme.AppTheme
import com.kimaita.monies.ui.theme.RedExpense
import java.text.NumberFormat
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()

    HomeScreenContent(
        modifier = modifier,
        onViewAllTransactionsClick = { navController.navigate("transactions") },
        onViewAnalyticsClick = { navController.navigate("analytics") },
        onTimeFilterChange = viewModel::setTimeFilter,
        uiState = uiState,
        filters = filters,
        navController = navController,
        onMenuClick = { /* TODO: Open drawer */ },

        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit,
    onViewAllTransactionsClick: () -> Unit,
    onViewAnalyticsClick: () -> Unit,
    onTimeFilterChange: (TimeFilter) -> Unit,
    navController: NavController,
    uiState: HomeUiState,
    filters: TimeFilter,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface, topBar = {
            TopAppBar(
                title = {
                    uiState.phoneNumber?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
//                navigationIcon = {
//                IconButton(onClick = onMenuClick) {
//                    Icon(
//                        painter = painterResource(R.drawable.menu), contentDescription = "Menu"
//                    )
//                }
//            },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MPESA BALANCE", style = MaterialTheme.typography.labelSmall)
            CurrencyText(
                uiState.mpesaBalance
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OtherBalances(
                uiState.availableFuliza,
                uiState.outstandingFuliza,
                uiState.lockSavings,
                uiState.mshwariBalance,
                uiState.mshwariLoan
            )
            Spacer(modifier = Modifier.height(24.dp))

            Transactions(
                transactions = uiState.recentTransactions,
                onViewAllClick = onViewAllTransactionsClick,
                navController = navController
            )

            Spacer(modifier = Modifier.height(24.dp))

            Highlights(
                filters = filters,
                onFilterChange = onTimeFilterChange,
                onViewAnalyticsClick = onViewAnalyticsClick,
                income = uiState.incomeVsExpense.income.toFloat(),
                expense = uiState.incomeVsExpense.expense.toFloat(),
                largestIncome = uiState.largestIncome,
                largestExpense = uiState.largestExpense,
                transactionCount = uiState.transactionCount,
                totalCost = uiState.totalTransactionCost,
                changeInTotal = uiState.changeInTotal
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun OtherBalances(
    availableFuliza: Double?,
    outstandingFuliza: Double?,
    lockSavings: Double?,
    mshwariBalance: Double?,
    mshwariLoan: Double?
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "OTHER BALANCES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                painter = painterResource(if (expanded) R.drawable.collapse else R.drawable.expand),
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(16.dp))
            MshwariBalance(mshwariBalance, lockSavings, mshwariLoan)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            FulizaBalance(availableFuliza, outstandingFuliza)
        }
    }
}

@Composable
fun FulizaBalance(availableFuliza: Double?, outstandingFuliza: Double?) {
    Column(
        modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "FULIZA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyText(
                    availableFuliza ?: 0.0
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Borrowed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyText(
                    outstandingFuliza ?: 0.0
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = RedExpense
                    )
                }
            }
        }
    }
}

@Composable
fun MshwariBalance(balance: Double?, lockSavings: Double?, loan: Double?) {
    Column(
        modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "M-SHWARI",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyText(balance ?: 0.0) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Lock Savings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyText(lockSavings ?: 0.0) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                "Outstanding Loan",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CurrencyText(loan ?: 0.0) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = RedExpense
                )
            }
        }
    }
}


@Composable
fun Transactions(
    transactions: List<Transaction?>, onViewAllClick: () -> Unit, navController: NavController
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewAllClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TRANSACTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            painter = painterResource(R.drawable.outline_arrow_forward_24),
            contentDescription = "View All",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        transactions.forEach { transaction ->
            if (transaction != null) {
                TransactionListItem(
                    transaction = transaction,
                    style = TransactionItemStyle.TWO_LINE,
                    onSubjectClick = { subjectId ->
                        navController.navigate("account/$subjectId")
                    })

            }
        }
    }
}


@Composable
fun Highlights(
    filters: TimeFilter,
    onFilterChange: (TimeFilter) -> Unit,
    onViewAnalyticsClick: () -> Unit,
    income: Float,
    expense: Float,
    largestIncome: Transaction?,
    largestExpense: Transaction?,
    transactionCount: Int,
    totalCost: Double,
    changeInTotal: Double,
) {
    remember {
        NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
    }
    val total = income + expense

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onViewAnalyticsClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "HIGHLIGHTS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                painter = painterResource(R.drawable.outline_arrow_forward_24),
                contentDescription = "View Analytics",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HighlightsFilter(selectedFilter = filters, onFilterChange = onFilterChange)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, 0.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // INC Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.outline_incoming),
                        contentDescription = "Income",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "INC",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))

                CurrencyText(income.toDouble(), modifier = Modifier.fillMaxWidth()) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.titleLarge.fontSize)
                    )
                }
//                Text(
//                    "KES350 MORE",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Largest Transaction",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyText(largestIncome?.amount ?: 0.0) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = largestIncome?.subject?.name?.uppercase() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            // EXP Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.outline_outgoing),
                        contentDescription = "Expense",
                        tint = RedExpense
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "EXP",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = RedExpense,

                        )
                }
                Spacer(Modifier.height(8.dp))

                CurrencyText(expense.toDouble()) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.titleLarge.fontSize)

                    )
                }
//                Text(
//                    "KES 500 LESS",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Largest Transaction",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyText(largestExpense?.amount ?: 0.0) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = largestExpense?.subject?.name?.uppercase() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Total section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Total Moved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyText(total.absoluteValue.toDouble()) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        "$changeInTotal%",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Text(
                    "$transactionCount Transactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))

                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.width(8.dp))

                CurrencyText(totalCost) { text ->
                    Text(
                        "Cost $text",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AppTheme {
        val navController = rememberNavController()
        HomeScreenContent(
            onMenuClick = {},
            onViewAllTransactionsClick = {},
            onViewAnalyticsClick = {},
            onTimeFilterChange = {},
            uiState = HomeUiState(),
            filters = TimeFilter.WEEK,
            navController = navController
        )
    }
}