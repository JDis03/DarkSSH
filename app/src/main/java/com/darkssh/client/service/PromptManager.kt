package com.darkssh.client.service

sealed class PromptRequest {
    data class StringPrompt(
        val prompt: String,
        val echo: Boolean,
    ) : PromptRequest()

    data class BooleanPrompt(
        val prompt: String,
    ) : PromptRequest()

    data class HostKeyPrompt(
        val hostname: String,
        val port: Int,
        val fingerprints: String,
    ) : PromptRequest()
}

sealed class PromptResponse {
    data class StringResponse(val value: String?) : PromptResponse()

    data class BooleanResponse(val value: Boolean) : PromptResponse()
}