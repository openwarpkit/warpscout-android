package io.github.openwarpkit.warpscout.ui

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.OperationState
import io.github.openwarpkit.warpscout.data.UpdateDownloadState
import io.github.openwarpkit.warpscout.ui.components.ScanOperationDock
import io.github.openwarpkit.warpscout.ui.components.SocksOperationDock
import io.github.openwarpkit.warpscout.ui.screen.AboutScreen
import io.github.openwarpkit.warpscout.ui.screen.ConfigScreen
import io.github.openwarpkit.warpscout.ui.screen.HistoryScreen
import io.github.openwarpkit.warpscout.ui.screen.OnboardingScreen
import io.github.openwarpkit.warpscout.ui.screen.ReportScreen
import io.github.openwarpkit.warpscout.ui.screen.ScanScreen
import io.github.openwarpkit.warpscout.ui.screen.SettingsScreen
import io.github.openwarpkit.warpscout.ui.screen.ToolsScreen
import io.github.openwarpkit.warpscout.ui.screen.UpdateAvailableSheet
import io.github.openwarpkit.warpscout.ui.screen.UpdateWizardScreen
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

internal enum class OperationDockKind {
    None,
    Scan,
    Socks
}

internal fun operationDockKind(state: OperationState): OperationDockKind = when {
    state.operation == "scan" -> OperationDockKind.Scan
    state.operation == "socks" && state.running && state.localPort != null -> OperationDockKind.Socks
    else -> OperationDockKind.None
}

@Composable
fun WarpScoutApp(viewModel: AppViewModel = hiltViewModel()) {
    val hasAccount by viewModel.hasAccount.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val updatePrompt by viewModel.updatePrompt.collectAsStateWithLifecycle()
    val updateDownloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    WarpScoutTheme(dynamicColor = settings.dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Box(Modifier.fillMaxSize()) {
                when (hasAccount) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    false -> OnboardingScreen(viewModel)
                    true -> MainNavigation(viewModel)
                }
                if (updateDownloadState is UpdateDownloadState.Idle) {
                    updatePrompt?.let { update ->
                        UpdateAvailableSheet(
                            update = update,
                            updateEnabled = !operation.running,
                            onUpdate = viewModel::startUpdate,
                            onDismiss = viewModel::dismissUpdatePrompt
                        )
                    }
                }
                UpdateWizardScreen(
                    state = updateDownloadState,
                    onCancel = viewModel::cancelUpdate,
                    onRetry = viewModel::retryUpdate,
                    onInstall = { viewModel.installUpdate(context) },
                    onOpenRelease = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                    onBack = { context.findActivity()?.moveTaskToBack(true) }
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun MainNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedPrimaryRoute = primaryRouteFor(currentDestination?.route)
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val historyNavigationState = remember { HistoryNavigationState() }
    val openLatestHistory = {
        operation.historyId?.let { historyId ->
            val request = historyNavigationState.openCompletedScan(historyId)
            navController.navigateToHistoryRoot(request)
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
                        val selected = selectedPrimaryRoute == destination.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigatePrimary(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { androidx.compose.material3.Text(stringResource(destination.label)) }
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    AppNavHost(
                        viewModel,
                        navController,
                        historyNavigationState.pendingFocus,
                        { request -> historyNavigationState.consumeFocus(request) }
                    )
                    OperationDock(
                        state = operation,
                        onStop = viewModel::stop,
                        onDismiss = viewModel::dismissOperation,
                        onOpenHistory = openLatestHistory,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        destinations.forEach { destination ->
                            val selected = selectedPrimaryRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigatePrimary(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { androidx.compose.material3.Text(stringResource(destination.label)) }
                            )
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
                    AppNavHost(
                        viewModel,
                        navController,
                        historyNavigationState.pendingFocus,
                        { request -> historyNavigationState.consumeFocus(request) }
                    )
                    OperationDock(
                        state = operation,
                        onStop = viewModel::stop,
                        onDismiss = viewModel::dismissOperation,
                        onOpenHistory = openLatestHistory,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationDock(
    state: OperationState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (operationDockKind(state)) {
        OperationDockKind.Scan -> ScanOperationDock(
            state = state,
            onStop = onStop,
            onDismiss = onDismiss,
            onOpenHistory = onOpenHistory,
            modifier = modifier
        )
        OperationDockKind.Socks -> SocksOperationDock(
            state = state,
            onStop = onStop,
            modifier = modifier
        )
        OperationDockKind.None -> Unit
    }
}

@Composable
private fun AppNavHost(
    viewModel: AppViewModel,
    navController: androidx.navigation.NavHostController,
    historyFocusRequest: HistoryFocusRequest?,
    onHistoryFocusConsumed: (HistoryFocusRequest) -> Unit
) {
    NavHost(navController = navController, startDestination = SCAN_ROUTE) {
        composable(SCAN_ROUTE) { ScanScreen(viewModel) }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                focusRequest = historyFocusRequest,
                onFocusConsumed = onHistoryFocusConsumed,
                onOpenReport = { historyId -> navController.navigate("report/$historyId") }
            )
        }
        composable("tools") {
            ToolsScreen(
                viewModel = viewModel,
                onOpenScan = { navController.navigatePrimary("scan") }
            )
        }
        composable("settings") { SettingsScreen(viewModel, onAbout = { navController.navigate("about") }) }
        composable("about") { AboutScreen(viewModel, onBack = { navController.popBackStack() }) }
        composable("report/{historyId}") { entry ->
            val historyId = entry.arguments?.getString("historyId")?.toLongOrNull()
            if (historyId != null) {
                ReportScreen(
                    viewModel = viewModel,
                    historyId = historyId,
                    onBack = { navController.popBackStack() },
                    onUseForScan = { navController.navigatePrimary(SCAN_ROUTE) },
                    onViewConfig = { format -> navController.navigate("config/$historyId/$format") }
                )
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

private fun primaryRouteFor(route: String?): String? = when {
    route == SCAN_ROUTE -> SCAN_ROUTE
    route == "history" -> "history"
    route == "tools" -> "tools"
    route == "settings" -> "settings"
    route == "about" -> "settings"
    route?.startsWith("report/") == true -> "history"
    route?.startsWith("config/") == true -> "history"
    else -> null
}

private fun androidx.navigation.NavHostController.navigatePrimary(route: String) {
    val options = primaryNavigationOptions(route)
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = options.savePoppedState }
        launchSingleTop = true
        restoreState = options.restoreState
    }
}

private fun androidx.navigation.NavHostController.navigateToHistoryRoot(request: HistoryNavigationRequest) {
    if (request.clearSavedHistory) clearBackStack(request.destination)
    navigate(request.destination) {
        popUpTo(graph.startDestinationId) { saveState = request.savePoppedState }
        launchSingleTop = true
        restoreState = request.restoreState
    }
}
