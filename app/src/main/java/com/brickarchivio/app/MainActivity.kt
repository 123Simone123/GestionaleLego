package com.brickarchivio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brickarchivio.app.ui.AppTheme
import com.brickarchivio.app.ui.catalog.CatalogScreen
import com.brickarchivio.app.ui.detail.DetailScreen
import com.brickarchivio.app.ui.scan.ScanScreen
import com.brickarchivio.app.ui.search.SearchScreen
import com.brickarchivio.app.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "catalog") {
                    composable("catalog") {
                        CatalogScreen(
                            onOpenItem = { nav.navigate("item/$it") },
                            onScan = { nav.navigate("scan") },
                            onSearch = { nav.navigate("search") },
                            onSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("scan") {
                        ScanScreen(
                            onBack = { nav.popBackStack() },
                            onOpenItem = { id ->
                                nav.navigate("item/$id") { popUpTo("catalog") }
                            }
                        )
                    }
                    composable("search") {
                        SearchScreen(
                            onBack = { nav.popBackStack() },
                            onOpenItem = { id -> nav.navigate("item/$id") { popUpTo("catalog") } }
                        )
                    }
                    composable(
                        "item/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) {
                        DetailScreen(onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
