package com.darkssh.client.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.HostEditorScreen
import com.darkssh.client.ui.screens.HostListScreen
import com.darkssh.client.ui.screens.PubkeyListScreen
import com.darkssh.client.ui.screens.SettingsScreen

@Composable
fun DarkSSHNavHost(
    navController: NavHostController = rememberNavController(),
    onNavigateToConsole: (hostId: Long) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = Screen.HostList.route,
    ) {
        composable(Screen.HostList.route) {
            HostListScreen(
                onHostClick = { host ->
                    navController.navigate(Screen.Console.createRoute(host.id))
                },
                onAddHostClick = {
                    navController.navigate(Screen.HostEditor.createRoute())
                },
                onEditHostClick = { host ->
                    navController.navigate(Screen.HostEditor.createRoute(host.id))
                },
                onPubkeysClick = {
                    navController.navigate(Screen.PubkeyList.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable(Screen.HostEditor.route) {
            HostEditorScreen(
                onSaved = { navController.popBackStack() },
                onCancelled = { navController.popBackStack() },
            )
        }

        composable(Screen.Console.route) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId")?.toLongOrNull() ?: return@composable
            ConsoleScreen(
                hostId = hostId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.PubkeyList.route) {
            PubkeyListScreen(
                onBack = { navController.popBackStack() },
                onGenerateKey = {
                    navController.navigate(Screen.GeneratePubkey.route)
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}