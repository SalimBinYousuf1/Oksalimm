package com.example.model

import org.json.JSONObject

data class NotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val relativeTime: String
        get() {
            val deltaMs = System.currentTimeMillis() - timestamp
            val seconds = deltaMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return when {
                seconds < 30 -> "Just now"
                minutes < 1 -> "${seconds}s ago"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                else -> "${hours / 24}d ago"
            }
        }

    companion object {
        fun fromJson(json: JSONObject): NotificationItem? {
            return try {
                val id = json.optString("id", "${json.optString("packageName")}_${json.optLong("timestamp")}")
                val pkg = json.optString("packageName", "unknown.app")
                val appName = json.optString("appName", json.optString("packageName", "System"))
                val title = json.optString("title", "Notification")
                val text = json.optString("text", "")
                val subText = if (json.has("subText")) json.getString("subText") else null
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                NotificationItem(
                    id = id,
                    packageName = pkg,
                    appName = appName,
                    title = title,
                    text = text,
                    subText = subText,
                    timestamp = timestamp
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
