package com.brickarchivio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brickarchivio.app.ui.AppTheme
import com.brickarchivio.app.ui.catalog.CatalogScreen
import com.brickarchivio.app.ui.detail.DetailScreen
import com.brickarchivio.app.ui.inventory.ContainerDetailScreen
import com.brickarchivio.app.ui.inventory.ContainersScreen
import com.brickarchivio.app.ui.inventory.ExhibitionDetailScreen
import com.brickarchivio.app.ui.inventory.ExhibitionsScreen
import com.brickarchivio.app.ui.scan.ScanScreen
import com.brickarchivio.app.ui.search.SearchScreen
import com.brickarchivio.app.ui.settings.SettingsScreen

/** Schede della barra in basso. */
private enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int?) {
    CATALOG("catalog", "Catalogo", R.drawable.ic_brick),
    CONTAINERS("containers", "Contenitori", R.drawable.ic_box),
    EXHIBITIONS("exhibitions", "Esposizioni", R.drawable.ic_flag),
    SETTINGS("settings", "Impostazioni", null)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                val showBar = Tab.entries.any { it.route == route }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = { if (showBar) BottomBar(nav, route) }
                ) { padding ->
                    AppNavHost(nav, Modifier.padding(bottom = padding.calculateBottomPadding()))
                }
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    if (tab.icon != null) Icon(painterResource(tab.icon), tab.label)
                    else Icon(Icons.Default.Settings, tab.label)
                },
                label = { Text(tab.label) }
            )
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier) {
    NavHost(navController = nav, startDestination = Tab.CATALOG.route, modifier = modifier) {
        composable(Tab.CATALOG.route) {
            CatalogScreen(
                onOpenItem = { nav.navigate("item/$it") },
                onScan = { nav.navigate("scan") },
                onSearch = { nav.navigate("search") }
            )
        }
        composable(Tab.CONTAINERS.route) {
            ContainersScreen(onOpen = { nav.navigate("container/$it") })
        }
        composable(Tab.EXHIBITIONS.route) {
            ExhibitionsScreen(onOpen = { nav.navigate("exhibition/$it") })
        }
        composable(Tab.SETTINGS.route) {
            SettingsScreen()
        }
        composable("scan") {
            ScanScreen(
                onBack = { nav.popBackStack() },
                onOpenItem = { id -> nav.navigate("item/$id") { popUpTo(Tab.CATALOG.route) } }
            )
        }
        composable("search") {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onOpenItem = { id -> nav.navigate("item/$id") { popUpTo(Tab.CATALOG.route) } }
            )
        }
        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            DetailScreen(
                onBack = { nav.popBackStack() },
                onOpenContainer = { nav.navigate("container/$it") }
            )
        }
        composable("container/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            ContainerDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenItem = { nav.navigate("item/$it") }
            )
        }
        composable("exhibition/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            ExhibitionDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenContainer = { nav.navigate("container/$it") }
            )
        }
    }
}
