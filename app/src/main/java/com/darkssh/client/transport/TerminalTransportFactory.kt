package com.darkssh.client.transport

import com.darkssh.client.BuildConfig
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService

/** Select once, before connecting. Never retry an error with another SSH backend. */
internal object TerminalTransportFactory {
    fun create(
        host: Host,
        bridge: TerminalBridge,
        service: TerminalService,
        knownHostRepository: KnownHostRepository,
        useCbssh: Boolean = BuildConfig.USE_CBSSH_TERMINAL,
    ): AbsTransport = if (useCbssh) {
        CbsshTerminalTransport(host, bridge, service, knownHostRepository)
    } else {
        SSH(host, bridge, service, knownHostRepository)
    }
}
