package com.randwise.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.randwise.app.AppContainer
import com.randwise.app.ui.components.AuroraBackground
import com.randwise.app.ui.components.CoachOrb
import com.randwise.app.ui.components.FloatingNavBar
import com.randwise.app.ui.components.NavItem
import com.randwise.app.ui.screens.AccountsScreen
import com.randwise.app.ui.screens.AddTransactionSheet
import com.randwise.app.ui.screens.BudgetScreen
import com.randwise.app.ui.screens.HomeScreen
import com.randwise.app.ui.screens.ImportScreen
import com.randwise.app.ui.screens.InsightsScreen
import com.randwise.app.ui.screens.LedgerScreen
import com.randwise.app.ui.screens.OnboardingScreen
import com.randwise.app.ui.screens.SettingsScreen
import com.randwise.app.ui.theme.Palette

/** Space every tab screen leaves at the bottom so content scrolls clear of the floating nav. */
val NavClearance = 120.dp

/** Side margin for page content; carousels ignore it so they bleed to the screen edge. */
val Gutter = 20.dp

fun Modifier.gutter(): Modifier = this.padding(horizontal = Gutter)

private val hubs = listOf(
    NavItem("home", "Home", Icons.Rounded.GridView),
    NavItem("insights", "Coach", Icons.Rounded.AutoAwesome),
    NavItem("budget", "Budget", Icons.Rounded.DonutLarge),
    NavItem("accounts", "Accounts", Icons.Rounded.AccountBalanceWallet),
    NavItem("settings", "Vault", Icons.Rounded.Tune),
)

@Composable
fun RandWiseRoot(container: AppContainer) {
    val factory = remember {
        viewModelFactory {
            initializer { FinanceViewModel(container) }
            initializer { ImportViewModel(container) }
            initializer { CoachViewModel(container) }
        }
    }
    val vm: FinanceViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CoachOrb(Modifier.size(72.dp), thinking = true) }
            state.snapshot.profile == null -> OnboardingScreen(vm)
            else -> MainShell(vm, factory)
        }
    }
}

@Composable
private fun MainShell(vm: FinanceViewModel, factory: ViewModelProvider.Factory) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showNav = hubs.any { it.route == route }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Ask for notifications only once the user is set up and reminders are on — not over the welcome screen.
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(settings.billReminders) {
        if (settings.billReminders && android.os.Build.VERSION.SDK_INT >= 33) {
            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = "home", modifier = Modifier.fillMaxSize().statusBarsPadding(),
            enterTransition = { fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.985f) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable("home") {
                HomeScreen(
                    vm, onAdd = { showAdd = true }, onOpenLedger = { nav.navigate("ledger/0") },
                    onImport = { go("accounts") }, onCoach = { go("insights") },
                )
            }
            composable("insights") { InsightsScreen(vm, viewModel(factory = factory)) }
            composable("budget") { BudgetScreen(vm) }
            composable("accounts") {
                AccountsScreen(vm, onImport = { id -> nav.navigate("import/$id") }, onLedger = { id -> nav.navigate("ledger/$id") }, onAdd = { showAdd = true })
            }
            composable("settings") { SettingsScreen(vm) }
            composable("import/{accountId}", listOf(navArgument("accountId") { type = NavType.LongType })) { entry ->
                ImportScreen(vm, viewModel(factory = factory), entry.arguments?.getLong("accountId") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable("ledger/{accountId}", listOf(navArgument("accountId") { type = NavType.LongType })) { entry ->
                LedgerScreen(vm, entry.arguments?.getLong("accountId") ?: 0L, onBack = { nav.popBackStack() })
            }
        }

        AnimatedVisibility(
            showNav,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            FloatingNavBar(hubs, route, onSelect = { go(it.route) })
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)) { data ->
            Snackbar(data, containerColor = Palette.Navy700, contentColor = Palette.Text, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
        }
    }

    if (showAdd) AddTransactionSheet(vm, onDismiss = { showAdd = false })
}
