package com.example.model

import org.json.JSONObject

data class HostStatus(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val networkType: String = "WiFi",
    val isScreenOn: Boolean = true,
    val fps: Int = 30,
    val latencyMs: Long = 0,
    val resolution: String = "1080x2400",
    val temperatureC: Float = 32.0f,
    val modelName: String = "Ubaid Device",
    val androidVersion: String = "Android 14",
    val connectionType: String = "Direct P2P",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(json: JSONObject): HostStatus {
            return HostStatus(
                batteryPercent = json.optInt("batteryPercent", json.optInt("battery", 100)),
                isCharging = json.optBoolean("isCharging", json.optBoolean("charging", false)),
                networkType = json.optString("networkType", json.optString("network", "WiFi")),
                isScreenOn = json.optBoolean("isScreenOn", json.optBoolean("screenOn", true)),
                fps = json.optInt("fps", 30),
                latencyMs = json.optLong("latencyMs", json.optLong("ping", 0)),
                resolution = json.optString("resolution", "1080x2400"),
                temperatureC = json.optDouble("temperatureC", json.optDouble("temperature", 32.0)).toFloat(),
                modelName = json.optString("modelName", json.optString("model", "Ubaid Host")),
                androidVersion = json.optString("androidVersion", "Android 14"),
                connectionType = json.optString("connectionType", "Direct P2P"),
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
