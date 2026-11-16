package com.kimaita.monies.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kimaita.monies.R
import com.kimaita.monies.ui.components.TabTopBar
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.ui.components.CurrencyText
import com.kimaita.monies.ui.transactions.traceRoute
import com.kimaita.monies.ui.components.TransactionItemStyle
import com.kimaita.monies.ui.components.TransactionListItem
import com.kimaita.monies.ui.navigateSingleTop
import com.kimaita.monies.ui.navigateToTab
import com.kimaita.monies.ui.util.formatAmount

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshToday() }
    LaunchedEffect(viewModel) {
        viewModel.errorEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    HomeScreenContent(
        modifier = modifier,
        onViewAllTransactions = { navController.navigateSingleTop("transactions") },
        onSearch = { navController.navigateSingleTop("transactions?search=true") },
        onSettings = { navController.navigateSingleTop("settings") },
        onToggleHideBalances = viewModel::toggleHideBalances,
        onViewAnalytics = { navController.navigateToTab("analytics") },
        onTimeFilterChange = viewModel::setTimeFilter,
        onSubjectClick = { subjectId -> navController.navigateSingleTop("subject/$subjectId") },
        onTraceFrom = { t -> traceRoute(t)?.let { navController.navigateSingleTop(it) } },
        uiState = uiState,
        filter = filters,
        isSyncing = isSyncing,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    uiState: HomeUiState?,
    filter: TimeFilter,
    isSyncing: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onTimeFilterChange: (TimeFilter) -> Unit,
    onViewAllTransactions: () -> Unit,
    onViewAnalytics: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onTraceFrom: (Transaction) -> Unit = {},
    onSearch: () -> Unit = {},
    onSettings: () -> Unit = {},
    onToggleHideBalances: () -> Unit = {},
) {
    val hide = uiState?.hideBalances == true
    fun bal(v: Double) = if (hide) HIDDEN else money(v)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TabTopBar("Monies", onSettings) {
                    IconButton(onClick = onSearch) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = "Search transactions"
                        )
                    }
                }
                AnimatedVisibility(visible = isSyncing) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        },
    ) { pad ->
        if (uiState == null) {
            Box(modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (uiState.accountBalances.size > 1) "BALANCE" else "M-PESA BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onToggleHideBalances, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(if (hide) R.drawable.visibility_off else R.drawable.visibility),
                        contentDescription = if (hide) "Show balances" else "Hide balances",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            val balance = uiState.mpesaBalance
            if (hide || balance == null) Text(
                if (hide) HIDDEN else DASH,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold
            ) else CurrencyText(balance) {
                Text(
                    it,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 20.sp,
                        maxFontSize = MaterialTheme.typography.displayMedium.fontSize
                    ),
                )
            }
            if (uiState.selectedAccount == null && uiState.accountBalances.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.accountBalances.forEach { account ->
                        Pill {
                            Svc(account.name)
                            Strong(bal(account.balance ?: 0.0))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ChangePill(change = uiState.changeInTotal, filter = filter, onClick = onViewAnalytics)

            if (((uiState.availableFuliza ?: 0.0) + (uiState.outstandingFuliza ?: 0.0)) > 0.0 ||
                uiState.mshwariBalance != null || uiState.lockSavings != null || uiState.mshwariLoan != null
            ) {
                Spacer(Modifier.height(20.dp))
                OtherBalances(
                    availableFuliza = uiState.availableFuliza,
                    outstandingFuliza = uiState.outstandingFuliza,
                    mshwariBalance = uiState.mshwariBalance,
                    mshwariLoan = uiState.mshwariLoan,
                    lockSavings = uiState.lockSavings,
                    hide = hide,
                )
            }

            Spacer(Modifier.height(20.dp))
            InOutCard(
                filter = filter,
                income = uiState.incomeVsExpense?.income,
                expense = uiState.incomeVsExpense?.expense,
                onPeriod = onTimeFilterChange,
                onAnalytics = onViewAnalytics,
            )

            Spacer(Modifier.height(20.dp))
            Transactions(
                transactions = uiState.recentTransactions,
                onViewAllClick = onViewAllTransactions,
                onSubjectClick = onSubjectClick,
                onTraceFrom = onTraceFrom,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun Transactions(
    transactions: List<Transaction>?, onViewAllClick: () -> Unit, onSubjectClick: (Long) -> Unit,
    onTraceFrom: (Transaction) -> Unit = {},
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
        if (transactions == null || transactions.isEmpty()) {
            Text(
                if (transactions == null) DASH else "No transactions yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        } else {
            transactions.forEach { transaction ->
                TransactionListItem(
                    transaction = transaction,
                    style = TransactionItemStyle.TWO_LINE,
                    onTraceFrom = onTraceFrom,
                    onSubjectClick = onSubjectClick
                )
            }
        }
    }
}

internal const val HIDDEN = "••••"

/** Shown where a stream failed and there is no earlier value; never a 0. */
internal const val DASH = "—"

internal fun money(v: Double): String = formatAmount(v)
internal fun periodWord(f: TimeFilter): String = when (f) {
    TimeFilter.DAY -> "today"; TimeFilter.WEEK -> "this week"; TimeFilter.MONTH -> "this month"
    TimeFilter.SIX_MONTHS -> "last 6 months"; TimeFilter.YTD -> "this year"
    TimeFilter.ALL -> "all time"; is TimeFilter.CustomRange -> "selected range"
}
