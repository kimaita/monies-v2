package com.kimaita.monies.ui.analytics

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.ui.components.HighlightsFilter
import com.kimaita.monies.ui.theme.AppTheme
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.Pie

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: AnalyticsViewModel = viewModel()
) {
    val incomeVsExpense by viewModel.incomeVsExpense.collectAsState()
    val selectedPeriod by viewModel.timeFilter.collectAsState()
    val categorySpending by viewModel.categorySpending.collectAsState()
    val typeSpending by viewModel.typeSpending.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Analytics") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_arrow_back_24),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HighlightsFilter(
                selectedFilter = selectedPeriod,
                onFilterChange = { newPeriod -> viewModel.setTimeFilter(newPeriod) })

            Spacer(modifier = Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Income")
                        Text("KES ${incomeVsExpense.income}")
                    }
                }
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Expense")
                        Text("KES ${incomeVsExpense.expense}")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Transaction Type Volume")
                    CategorySpendingPieChart(categorySpending = typeSpending)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Daily Summary")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.LightGray.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Bar Chart Placeholder")
                    }
                }
            }
        }
    }
}


@Composable
fun CategorySpendingPieChart(categorySpending: List<CategorySpending>) {
    if (categorySpending.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No spending data for this period.")
        }
    } else {


        val pieData = remember(categorySpending) {
            val chartColors = generateDistinctColors(categorySpending.size)
            categorySpending.mapIndexed { index, spending ->
                Pie(
                    data = spending.total,
                    label = spending.name,
                    color = chartColors[index]
                )
            }
        }


        PieChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(vertical = 16.dp),
            data = pieData,
            spaceDegree = 5f,
            selectedPaddingDegree = 4f,
            style = Pie.Style.Stroke(width = 40.dp)
        )
    }
}

fun generateDistinctColors(count: Int): List<Color> {
    return List(count) { index ->
        val hue = (index.toFloat() * (360f / count.coerceAtLeast(1)))
        Color.hsl(hue = hue, saturation = 0.7f, lightness = 0.6f)
    }
}

@Preview(showBackground = true)
@Composable
fun AnalyticsScreenPreview() {
    AppTheme {
        val navController = rememberNavController()
        AnalyticsScreen(navController = navController)
    }
}
