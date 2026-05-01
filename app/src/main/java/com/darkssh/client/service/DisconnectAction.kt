package com.darkssh.client.service

import com.darkssh.client.data.entity.Host

sealed class DisconnectAction {
    data object CloseImmediately : DisconnectAction()

    data class ShowReconnectOverlay(val reason: DisconnectReason) : DisconnectAction()

    data class AutoReconnect(val reason: DisconnectReason) : DisconnectAction()
}

fun decideDisconnectAction(reason: DisconnectReason, host: Host): DisconnectAction =
    when (reason) {
        DisconnectReason.USER_REQUESTED -> DisconnectAction.CloseImmediately
        DisconnectReason.AUTH_FAIL -> DisconnectAction.ShowReconnectOverlay(reason)
        DisconnectReason.NETWORK_LOST ->
            if (host.stayConnected) {
                DisconnectAction.AutoReconnect(reason)
            } else {
                DisconnectAction.ShowReconnectOverlay(reason)
            }
        else -> DisconnectAction.ShowReconnectOverlay(reason)
    }