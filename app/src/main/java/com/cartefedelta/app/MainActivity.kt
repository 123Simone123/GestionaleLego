package com.cartefedelta.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cartefedelta.app.ui.AppTheme
import com.cartefedelta.app.ui.CardScreen
import com.cartefedelta.app.ui.EditCardScreen
import com.cartefedelta.app.ui.HomeScreen
import com.cartefedelta.app.ui.ReceiveScreen
import com.cartefedelta.app.ui.ShareScreen
import com.cartefedelta.app.ui.SettingsScreen
import com.cartefedelta.app.ui.StorePickerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpen = { nav.navigate("card/$it") },
                            onAdd = { nav.navigate("pick") },
                            onEdit = { nav.navigate("edit/$it") },
                            onSettings = { nav.navigate("settings") },
                            onShareAll = { nav.navigate("share/-1") },
                            onReceive = { nav.navigate("receive") }
                        )
                    }
                    composable("pick") {
                        StorePickerScreen(
                            onPick = { store, name ->
                                nav.navigate("edit/-1?store=${store?.key ?: ""}&name=${Uri.encode(name)}") {
                                    popUpTo("pick") { inclusive = true }
                                }
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable(
                        "edit/{id}?store={store}&name={name}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("store") { type = NavType.StringType; defaultValue = "" },
                            navArgument("name") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: -1L
                        EditCardScreen(
                            cardId = id,
                            storeKey = entry.arguments?.getString("store")?.ifEmpty { null },
                            initialName = entry.arguments?.getString("name") ?: "",
                            onSaved = { savedId ->
                                if (id < 0) {
                                    nav.navigate("card/$savedId") { popUpTo("home") }
                                } else nav.popBackStack()
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable(
                        "card/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        CardScreen(
                            cardId = id,
                            onEdit = { nav.navigate("edit/$id") },
                            onShareQr = { nav.navigate("share/$id") },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable(
                        "share/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        ShareScreen(cardId = entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
                    }
                    composable("receive") { ReceiveScreen(onDone = { nav.popBackStack() }) }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
