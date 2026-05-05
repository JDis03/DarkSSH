package com.darkssh.client.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.GeneratePubkeyScreen
import com.darkssh.client.ui.screens.HostEditorScreen
import com.darkssh.client.ui.screens.HostListScreen
import com.darkssh.client.ui.screens.PubkeyListScreen
import com.darkssh.client.ui.screens.SettingsScreen
import com.darkssh.client.ui.screens.SftpScreen

@Suppress("ktlint:standard:function-naming")
@Composable
fun DarkSSHNavHost(
    navController: NavHostController = rememberNavController(),
    terminalService: TerminalService? = null,
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
                onSftpClick = { host ->
                    navController.navigate(Screen.Sftp.createRoute(host.id))
                },
            )
        }

        composable(Screen.HostEditor.route) {
            HostEditorScreen(
                onSave = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Screen.Console.route) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId")?.toLongOrNull() ?: return@composable
            ConsoleScreen(
                hostId = hostId,
                onBack = { navController.popBackStack() },
                terminalService = terminalService,
            )
        }

        composable(Screen.PubkeyList.route) {
            PubkeyListScreen(
                onBack = { navController.popBackStack() },
                onGenerateKey = {
                    navController.navigate(Screen.GeneratePubkey.route)
                },
                terminalService = terminalService,
            )
        }

        composable(Screen.GeneratePubkey.route) {
            GeneratePubkeyScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Sftp.route) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId")?.toLongOrNull() ?: return@composable
            SftpScreen(
                hostId = hostId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}