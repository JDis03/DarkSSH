package com.darkssh.client.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.GeneratePubkeyScreen
import com.darkssh.client.ui.screens.HostEditorScreen
import com.darkssh.client.ui.screens.HostListScreen
import com.darkssh.client.ui.screens.PubkeyListScreen
import com.darkssh.client.ui.screens.ServerSettingsScreen
import com.darkssh.client.ui.screens.SettingsScreen
import com.darkssh.client.ui.screens.SftpScreen

@Suppress("ktlint:standard:function-naming")
@Composable
fun DarkSSHNavHost(
    navController: NavHostController = rememberNavController(),
    terminalService: TerminalService? = null,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in setOf(
        BottomTab.Hosts.route, BottomTab.Keys.route, BottomTab.Settings.route,
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Computer, contentDescription = null) },
                        label = { Text("Hosts") },
                        selected = currentDestination?.hierarchy?.any { it.route == BottomTab.Hosts.route } == true,
                        onClick = {
                            navController.navigate(BottomTab.Hosts.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Key, contentDescription = null) },
                        label = { Text("Keys") },
                        selected = currentDestination?.hierarchy?.any { it.route == BottomTab.Keys.route } == true,
                        onClick = {
                            navController.navigate(BottomTab.Keys.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = currentDestination?.hierarchy?.any { it.route == BottomTab.Settings.route } == true,
                        onClick = {
                            navController.navigate(BottomTab.Settings.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.Hosts.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
        ) {
            composable(BottomTab.Hosts.route) {
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
                    onSftpClick = { host ->
                        navController.navigate(Screen.Sftp.createRoute(host.id))
                    },
                )
            }

            composable(BottomTab.Keys.route) {
                PubkeyListScreen(
                    onBack = { navController.popBackStack() },
                    onGenerateKey = {
                        navController.navigate(Screen.GeneratePubkey.route)
                    },
                    terminalService = terminalService,
                )
            }

            composable(BottomTab.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onServerSettings = { navController.navigate(Screen.ServerSettings.route) },
                )
            }
            
            composable(Screen.ServerSettings.route) {
                ServerSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(Screen.HostEditor.route) { backStackEntry ->
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

            composable(Screen.GeneratePubkey.route) {
                GeneratePubkeyScreen(
                    onNavigateBack = { navController.popBackStack() },
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
}