package com.kimaita.monies.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kimaita.monies.R
import com.kimaita.monies.ui.account.AccountScreen
import com.kimaita.monies.ui.analytics.AnalyticsScreen
import com.kimaita.monies.ui.export.ExportScreen
import com.kimaita.monies.ui.home.HomeScreen
import com.kimaita.monies.ui.profile.ProfileScreen
import com.kimaita.monies.ui.transactions.TransactionsScreen

enum class TopDest(val route: String, val label: String) {
    HOME("home", "Home"),
    ANALYTICS("analytics", "Analytics"),
    EXPORT("export", "Export"),
}

@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = TopDest.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                TopDest.entries.forEach { dest ->
                    val selected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { if (!selected) navController.navigateToTab(dest.route) },
                        icon = {
                            when (dest) {
                                TopDest.HOME -> Icon(painterResource(R.drawable.ic_home), null)
                                TopDest.ANALYTICS -> Icon(
                                    painterResource(R.drawable.ic_insights),
                                    null
                                )

                                TopDest.EXPORT -> Icon(painterResource(R.drawable.ic_export), null)
                            }
                        },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        MoniesNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}

@Composable
fun MoniesNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {

    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable("home") {
            HomeScreen(
                navController = navController,
            )
        }
        composable(
            route = "transactions?search={search}",
            arguments = listOf(navArgument("search") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) { entry ->
            TransactionsScreen(
                navController = navController,
                viewModel = hiltViewModel(),
                openSearch = entry.arguments?.getBoolean("search") ?: false,
            )
        }
        composable("analytics") {
            AnalyticsScreen(
                viewModel = hiltViewModel()
            )
        }
        composable("account") {
            ProfileScreen(
                navController = navController,
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = "account/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) {
            AccountScreen(navController = navController)
        }
        composable("export") { ExportScreen(viewModel = hiltViewModel()) { } }
    }
}


fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}