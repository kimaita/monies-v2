package com.kimaita.monies.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kimaita.monies.R
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.ui.components.CurrencyText
import com.kimaita.monies.ui.components.TransactionItemStyle
import com.kimaita.monies.ui.components.TransactionListItem
import java.text.NumberFormat
import kotlin.math.abs

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    HomeScreenContent(
        modifier = modifier,
        onViewAllTransactions = { navController.navigate("transactions") },
        onSearch = { navController.navigate("transactions?search=true") },
        onViewAnalytics = { navController.navigate("analytics") },
        onTimeFilterChange = viewModel::setTimeFilter,
        onSubjectClick = { subjectId -> navController.navigate("account/$subjectId") },
        uiState = uiState,
        filter = filters,
        isSyncing = isSyncing

    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    uiState: HomeUiState,
    filter: TimeFilter,
    isSyncing: Boolean = false,
    onTimeFilterChange: (TimeFilter) -> Unit,
    onViewAllTransactions: () -> Unit,
    onViewAnalytics: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onSearch: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        uiState.phoneNumber?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSearch) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = "Search transactions"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                AnimatedVisibility(visible = isSyncing) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        },
    ) { pad ->
        Column(
            modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "M-PESA BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CurrencyText(uiState.mpesaBalance) {
                Text(
                    it,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(12.dp))
            ChangePill(change = uiState.changeInTotal, filter = filter, onClick = onViewAnalytics)

            Spacer(Modifier.height(20.dp))
            OtherBalances(
                availableFuliza = uiState.availableFuliza,
                outstandingFuliza = uiState.outstandingFuliza,
                mshwariBalance = uiState.mshwariBalance,
                mshwariLoan = uiState.mshwariLoan,
                lockSavings = uiState.lockSavings,
            )

            Spacer(Modifier.height(20.dp))
            InOutCard(
                filter = filter,
                income = uiState.incomeVsExpense.income,
                expense = uiState.incomeVsExpense.expense,
                onPeriod = onTimeFilterChange,
                onAnalytics = onViewAnalytics,
            )

            Spacer(Modifier.height(20.dp))
            Transactions(
                transactions = uiState.recentTransactions,
                onViewAllClick = onViewAllTransactions,
                onSubjectClick = onSubjectClick,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ---------------- change-in-balance pill ---------------- */

@Composable
private fun ChangePill(change: Double?, filter: TimeFilter, onClick: () -> Unit) {
    if (change == null) return
    val up = change >= 0
    val fg = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val bg = fg.copy(alpha = 0.12f)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (up) "▲" else "▼", color = fg, style = MaterialTheme.typography.labelSmall)
        Text(
            "${if (up) "+" else "−"}${money(abs(change))} ${periodWord(filter)}",
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text("›", color = fg.copy(alpha = 0.6f))
    }
}

/* ---------------- in & out card ---------------- */

@Composable
private fun InOutCard(
    filter: TimeFilter, income: Double, expense: Double,
    onPeriod: (TimeFilter) -> Unit, onAnalytics: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val net = income - expense
    val inFraction = (income / (income + expense).coerceAtLeast(1.0)).toFloat()

    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "IN & OUT",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                PeriodSegment(filter, onPeriod)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IoColumn("IN", income, cs.primary, Modifier.weight(1f))
                IoColumn("OUT", expense, cs.onSurface, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.error.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(inFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(cs.primary)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("Net "); append(if (net >= 0) "+" else "−"); append(
                        money(
                            abs(net)
                        )
                    )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (net >= 0) cs.primary else cs.error,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onAnalytics,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Analytics ›") }
            }
        }
    }
}

@Composable
private fun IoColumn(label: String, value: Double, color: Color, modifier: Modifier = Modifier) =
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            money(value),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }

@Composable
private fun PeriodSegment(filter: TimeFilter, onPeriod: (TimeFilter) -> Unit) {
    val options =
        listOf("Day" to TimeFilter.DAY, "Week" to TimeFilter.WEEK, "Month" to TimeFilter.MONTH)
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (label, value) ->
            val on = filter == value
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) cs.surface else Color.Transparent)
                    .clickable { onPeriod(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) cs.onSurface else cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun OtherBalances(
    availableFuliza: Double?,
    outstandingFuliza: Double?,
    mshwariBalance: Double?,
    mshwariLoan: Double?,
    lockSavings: Double?,
) {
    var expanded by remember { mutableStateOf(false) }
    val fulizaMax = (availableFuliza ?: 0.0) + (outstandingFuliza ?: 0.0)
    val hasFuliza = fulizaMax > 0.0
    val owedFuliza = (outstandingFuliza ?: 0.0) > 0.0
    val hasLoan = (mshwariLoan ?: 0.0) > 0.0

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Text(
            "OTHER BALANCES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Crossfade(targetState = expanded, label = "balances") { open ->
            if (!open) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasFuliza) Pill {
                        Svc("Fuliza")
                        if (owedFuliza) {
                            Owe(money(outstandingFuliza!!)); Muted("/${money(fulizaMax)}")
                        } else {
                            Strong(money(availableFuliza ?: 0.0)); Muted("available")
                        }
                    }
                    mshwariBalance?.let { Pill { Svc("M-Shwari"); Strong(money(it)) } }
                    if (hasLoan) Pill { Svc("M-Shwari loan"); Owe(money(mshwariLoan!!)) }
                    lockSavings?.let { Pill { Svc("Lock"); Strong(money(it)) } }
                    Pill(onClick = { expanded = true }) {
                        Text(
                            "More ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                OtherBalancesPanel(
                    availableFuliza,
                    outstandingFuliza,
                    fulizaMax,
                    owedFuliza,
                    mshwariBalance,
                    mshwariLoan,
                    hasLoan,
                    lockSavings,
                    onCollapse = { expanded = false })
            }
        }
    }
}

@Composable
private fun OtherBalancesPanel(
    availableFuliza: Double?, outstandingFuliza: Double?, fulizaMax: Double, owedFuliza: Boolean,
    mshwariBalance: Double?, mshwariLoan: Double?, hasLoan: Boolean, lockSavings: Double?,
    onCollapse: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceContainerLow)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCollapse)
                .padding(vertical = 12.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text(
                "Balances",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
           Icon(painterResource(R.drawable.collapse), "Expand")
        }
        if (fulizaMax > 0.0) PanelRow(divider = false) {
            RowLine(
                "Fuliza",
                if (owedFuliza) "${money(outstandingFuliza ?: 0.0)} used" else "${
                    money(availableFuliza ?: 0.0)
                } available",
                owed = owedFuliza
            )
            Spacer(Modifier.height(9.dp))
            val used = ((outstandingFuliza ?: 0.0) / fulizaMax).toFloat()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(used)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(cs.error)
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "${money(outstandingFuliza ?: 0.0)} used · ${money(availableFuliza ?: 0.0)} available · ${
                    money(
                        fulizaMax
                    )
                } limit",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
            )
        }
        mshwariBalance?.let { PanelRow { RowLine("M-Shwari balance", money(it)) } }
        if (hasLoan) PanelRow { RowLine("M-Shwari loan", money(mshwariLoan ?: 0.0), owed = true) }
        lockSavings?.let { PanelRow { RowLine("Lock savings", money(it)) } }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PanelRow(divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Column(Modifier.padding(vertical = 13.dp), content = content)
}

@Composable
private fun RowLine(name: String, value: String, owed: Boolean = false) =
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            color = if (owed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }

/* pill primitives */
@Composable
private fun Pill(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
            .background(cs.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun Transactions(
    transactions: List<Transaction>, onViewAllClick: () -> Unit, onSubjectClick: (Long) -> Unit,
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
        if (transactions.isEmpty()) {
            Text(
                "No transactions yet",
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
                    onSubjectClick = onSubjectClick
                )
            }
        }
    }
}

@Composable
private fun Svc(t: String) = Text(
    t,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun Strong(t: String) =
    Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Owe(t: String) = Text(
    t,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.error
)

@Composable
private fun Muted(t: String) = Text(
    t,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)


private fun money(v: Double): String = NumberFormat.getIntegerInstance().format(v.toLong())
private fun periodWord(f: TimeFilter): String = when (f) {
    TimeFilter.DAY -> "today"; TimeFilter.WEEK -> "this week"; TimeFilter.MONTH -> "this month"
    TimeFilter.QUARTER -> "this quarter"; TimeFilter.SIX_MONTHS -> "last 6 months"
    TimeFilter.YTD -> "this year"; TimeFilter.ALL -> "all time"; is TimeFilter.CustomRange -> "selected range"
}