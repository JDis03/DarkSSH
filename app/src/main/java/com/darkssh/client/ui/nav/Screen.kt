package com.darkssh.client.ui.nav

sealed class Screen(
    val route: String,
) {
    data object HostList : Screen("host_list")

    data object HostEditor : Screen("host_editor?hostId={hostId}") {
        fun createRoute(hostId: Long = -1L): String = if (hostId == -1L) "host_editor" else "host_editor?hostId=$hostId"
    }

    data object Console : Screen("console/{hostId}") {
        fun createRoute(hostId: Long): String = "console/$hostId"
    }

    data object PubkeyList : Screen("pubkey_list")

    data object GeneratePubkey : Screen("generate_pubkey")

    data object PubkeyEditor : Screen("pubkey_editor/{pubkeyId}") {
        fun createRoute(pubkeyId: Long): String = "pubkey_editor/$pubkeyId"
    }

    data object PortForwardList : Screen("port_forward_list/{hostId}") {
        fun createRoute(hostId: Long): String = "port_forward_list/$hostId"
    }

    data object Settings : Screen("settings")
}
