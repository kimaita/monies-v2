package com.kimaita.monies.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kimaita.monies.R
import com.kimaita.monies.ui.subject.SubjectScreen
import com.kimaita.monies.ui.analytics.AnalyticsScreen
import com.kimaita.monies.ui.export.ExportScreen
import com.kimaita.monies.ui.home.HomeScreen
import com.kimaita.monies.ui.settings.SettingsScreen
import com.kimaita.monies.ui.settings.UnrecognisedScreen
import com.kimaita.monies.ui.transactions.TRACE_AFTER_ARG
import com.kimaita.monies.ui.transactions.TRACE_SINCE_ARG
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
        contentWindowInsets = WindowInsets(0),
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
        MoniesNavHost(
            navController = navController,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        )
    }
}

@Composable
fun MoniesNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable("home") {
            HomeScreen(
                navController = navController,
            )
        }
        composable(
            route = "transactions?search={search}&$TRACE_SINCE_ARG={$TRACE_SINCE_ARG}&$TRACE_AFTER_ARG={$TRACE_AFTER_ARG}",
            arguments = listOf(
                navArgument("search") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(TRACE_SINCE_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(TRACE_AFTER_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            )
        ) { entry ->
            TransactionsScreen(
                navController = navController,
                openSearch = entry.arguments?.getBoolean("search") ?: false,
            )
        }
        composable("analytics") {
            AnalyticsScreen(
                onSubject = { navController.navigateSingleTop("subject/$it") },
                onSettings = { navController.navigateSingleTop("settings") },
            )
        }
        composable("settings") { SettingsScreen(navController = navController) }
        composable("settings/unrecognised") { UnrecognisedScreen(navController = navController) }
        composable(
            route = "subject/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) {
            SubjectScreen(navController = navController)
        }
        composable("export") { ExportScreen(onSettings = { navController.navigateSingleTop("settings") }) }
    }
}


/** Opens a screen above the current one; a double tap does not stack it twice. */
fun NavController.navigateSingleTop(route: String) = navigate(route) { launchSingleTop = true }

/**
 * Switches tab, keeping the state of Analytics and Export. Home is the start destination, so it is
 * reached by popping back to it: navigating "to" Home with `restoreState` can restore whatever tab
 * was just popped (the pop files it under Home's id when Home has no saved entry), which left the
 * Home tab apparently doing nothing.
 */
fun NavController.navigateToTab(route: String) {
    val home = graph.findStartDestination()
    if (route == TopDest.HOME.route) popBackStack(home.id, inclusive = false, saveState = true)
    else navigate(route) {
        popUpTo(home.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
