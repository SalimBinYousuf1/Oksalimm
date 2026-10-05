package com.example.model

sealed class ConnectionState {
    abstract val label: String
    abstract val detail: String

    object SetupIncomplete : ConnectionState() {
        override val label = "Setup Incomplete"
        override val detail = "Firebase configuration is missing or invalid"
    }

    object NotPaired : ConnectionState() {
        override val label = "Not Paired"
        override val detail = "Scan Ubaid's QR code to link devices"
    }

    data class Disconnected(
        val reason: String = "Disconnected from host"
    ) : ConnectionState() {
        override val label = "Disconnected"
        override val detail = reason
    }

    data class Connecting(
        val step: String = "Negotiating WebRTC peer connection..."
    ) : ConnectionState() {
        override val label = "Connecting"
        override val detail = step
    }

    data class WaitingOnUbaid(
        val instruction: String = "Waiting on Ubaid (needs tap on host device)"
    ) : ConnectionState() {
        override val label = "Waiting on Ubaid"
        override val detail = instruction
    }

    data class Connected(
        val pingMs: Long = 0,
        val isDirectP2p: Boolean = true,
        val resolution: String = "1080p",
        val fps: Int = 30
    ) : ConnectionState() {
        override val label = "Connected"
        override val detail = if (isDirectP2p) "Direct P2P • ${pingMs}ms" else "TURN Relay • ${pingMs}ms"
    }

    data class Reconnecting(
        val attempt: Int = 1,
        val maxAttempts: Int = 10,
        val reason: String = "Connection dropped. Auto-reconnecting..."
    ) : ConnectionState() {
        override val label = "Reconnecting ($attempt/$maxAttempts)"
        override val detail = reason
    }

    data class UbaidUnreachable(
        val lastSeenTime: String = "Unknown",
        val reason: String = "Host device is offline or screen capture is inactive."
    ) : ConnectionState() {
        override val label = "Ubaid Unreachable"
        override val detail = reason
    }
}
