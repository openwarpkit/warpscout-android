package io.github.openwarpkit.warpscout.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.ui.components.ScanOperationDock
import io.github.openwarpkit.warpscout.ui.screen.AboutScreen
import io.github.openwarpkit.warpscout.ui.screen.ConfigScreen
import io.github.openwarpkit.warpscout.ui.screen.HistoryScreen
import io.github.openwarpkit.warpscout.ui.screen.OnboardingScreen
import io.github.openwarpkit.warpscout.ui.screen.ReportScreen
import io.github.openwarpkit.warpscout.ui.screen.ScanScreen
import io.github.openwarpkit.warpscout.ui.screen.SettingsScreen
import io.github.openwarpkit.warpscout.ui.screen.ToolsScreen
import io.github.openwarpkit.warpscout.ui.theme.WarpScoutTheme

private data class Destination(
    val route: String,
    val label: Int,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("scan", R.string.nav_scan, Icons.Outlined.Search),
    Destination("history", R.string.nav_history, Icons.Outlined.History),
    Destination("tools", R.string.nav_tools, Icons.Outlined.Build),
    Destination("settings", R.string.nav_settings, Icons.Outlined.Settings)
)

@Composable
fun WarpScoutApp(viewModel: AppViewModel = hiltViewModel()) {
    val hasAccount by viewModel.hasAccount.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    WarpScoutTheme(dynamicColor = settings.dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            when (hasAccount) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                false -> OnboardingScreen(viewModel)
                true -> MainNavigation(viewModel)
            }
        }
    }
}

@Composable
private fun MainNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    var highlightedHistoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val showScanDock = operation.operation == "scan"
    val openLatestHistory = {
        operation.historyId?.let {
            highlightedHistoryId = it
            navController.navigatePrimary("history")
        }
        Unit
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigatePrimary(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { androidx.compose.material3.Text(stringResource(destination.label)) }
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.weight(1f)) {
                        AppNavHost(viewModel, navController, highlightedHistoryId)
                    }
                    if (showScanDock) {
                        ScanOperationDock(
                            state = operation,
                            onStop = viewModel::stop,
                            onDismiss = viewModel::dismissOperation,
                            onOpenHistory = openLatestHistory
                        )
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    Column {
                        if (showScanDock) {
                            ScanOperationDock(
                                state = operation,
                                onStop = viewModel::stop,
                                onDismiss = viewModel::dismissOperation,
                                onOpenHistory = openLatestHistory
                            )
                        }
                        if (destinations.any { it.route == currentDestination?.route }) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 0.dp
                            ) {
                                destinations.forEach { destination ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navController.navigatePrimary(destination.route) },
                                        icon = { Icon(destination.icon, contentDescription = null) },
                                        label = { androidx.compose.material3.Text(stringResource(destination.label)) }
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                ) {
                    AppNavHost(viewModel, navController, highlightedHistoryId)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    viewModel: AppViewModel,
    navController: androidx.navigation.NavHostController,
    highlightedHistoryId: Long?
) {
    NavHost(navController = navController, startDestination = "scan") {
        composable("scan") { ScanScreen(viewModel) }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                highlightedId = highlightedHistoryId,
                onViewReport = { historyId -> navController.navigate("report/$historyId") },
                onViewConfig = { historyId, format -> navController.navigate("config/$historyId/$format") }
            )
        }
        composable("tools") { ToolsScreen(viewModel) }
        composable("settings") { SettingsScreen(viewModel, onAbout = { navController.navigate("about") }) }
        composable("about") { AboutScreen(viewModel, onBack = { navController.popBackStack() }) }
        composable("report/{historyId}") { entry ->
            val historyId = entry.arguments?.getString("historyId")?.toLongOrNull()
            if (historyId != null) {
                ReportScreen(viewModel, historyId, onBack = { navController.popBackStack() })
            }
        }
        composable("config/{historyId}/{format}") { entry ->
            val historyId = entry.arguments?.getString("historyId")?.toLongOrNull()
            val format = entry.arguments?.getString("format")
            if (historyId != null && format != null) {
                ConfigScreen(
                    viewModel = viewModel,
                    historyId = historyId,
                    format = format,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigatePrimary(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
