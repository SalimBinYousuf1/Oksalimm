package com.example.model

import org.json.JSONObject
import java.net.URI

data class PairingInfo(
    val hostId: String,
    val secretKey: String,
    val sessionId: String,
    val hostName: String = "Ubaid Host",
    val pairedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun parse(payload: String): PairingInfo? {
            val trimmed = payload.trim()
            if (trimmed.isEmpty()) return null

            // 1. Try parsing as JSON
            try {
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    val json = JSONObject(trimmed)
                    val hostId = when {
                        json.has("hostId") -> json.getString("hostId")
                        json.has("deviceId") -> json.getString("deviceId")
                        json.has("id") -> json.getString("id")
                        else -> null
                    }
                    val secret = when {
                        json.has("secret") -> json.getString("secret")
                        json.has("secretKey") -> json.getString("secretKey")
                        json.has("token") -> json.getString("token")
                        json.has("pin") -> json.getString("pin")
                        else -> ""
                    }
                    val sessionId = when {
                        json.has("sessionId") -> json.getString("sessionId")
                        json.has("channelId") -> json.getString("channelId")
                        else -> hostId ?: ""
                    }
                    val hostName = json.optString("hostName", json.optString("name", "Ubaid Host"))

                    if (!hostId.isNullOrBlank()) {
                        return PairingInfo(
                            hostId = hostId.trim(),
                            secretKey = secret.trim(),
                            sessionId = if (sessionId.isNotBlank()) sessionId.trim() else hostId.trim(),
                            hostName = hostName.trim()
                        )
                    }
                }
            } catch (_: Exception) {
                // Not valid JSON, continue to other parsers
            }

            // 2. Try parsing as URI (e.g. ubaid://pair?hostId=...&secret=...)
            try {
                if (trimmed.startsWith("ubaid://", ignoreCase = true) ||
                    trimmed.startsWith("salim://", ignoreCase = true) ||
                    trimmed.startsWith("http", ignoreCase = true)
                ) {
                    val uri = URI(trimmed)
                    val query = uri.query ?: ""
                    val queryParams = query.split("&").associate { param ->
                        val parts = param.split("=")
                        if (parts.size >= 2) parts[0] to parts[1] else parts[0] to ""
                    }
                    val hostId = queryParams["hostId"] ?: queryParams["deviceId"] ?: queryParams["id"]
                    val secret = queryParams["secret"] ?: queryParams["token"] ?: ""
                    val sessionId = queryParams["sessionId"] ?: hostId ?: ""
                    val name = queryParams["name"] ?: "Ubaid Host"

                    if (!hostId.isNullOrBlank()) {
                        return PairingInfo(
                            hostId = hostId.trim(),
                            secretKey = secret.trim(),
                            sessionId = sessionId.trim(),
                            hostName = name.trim()
                        )
                    }
                }
            } catch (_: Exception) {
                // Not a valid URI
            }

            // 3. Try parsing colon/pipe delimited (e.g. ubaid-host-1234:secretKey)
            if (trimmed.contains(":") || trimmed.contains("|")) {
                val delimiter = if (trimmed.contains(":")) ":" else "|"
                val parts = trimmed.split(delimiter)
                if (parts.size >= 2 && parts[0].isNotBlank()) {
                    return PairingInfo(
                        hostId = parts[0].trim(),
                        secretKey = parts[1].trim(),
                        sessionId = parts[0].trim(),
                        hostName = if (parts.size >= 3) parts[2].trim() else "Ubaid Host"
                    )
                }
            }

            // 4. Plain host ID
            if (trimmed.length in 4..64 && !trimmed.contains(" ") && !trimmed.contains("\n")) {
                return PairingInfo(
                    hostId = trimmed,
                    secretKey = "",
                    sessionId = trimmed,
                    hostName = "Ubaid Host"
                )
            }

            return null
        }
    }
}
