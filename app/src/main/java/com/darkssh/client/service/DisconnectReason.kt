package com.darkssh.client.service

enum class DisconnectReason {
    USER_REQUESTED,
    REMOTE_EOF,
    IO_ERROR,
    NETWORK_LOST,
    AUTH_FAIL,
    UNKNOWN,
}