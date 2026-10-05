package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.PairingInfo

class PairingStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("salim_pairing_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOST_ID = "paired_host_id"
        private const val KEY_SECRET_KEY = "paired_secret_key"
        private const val KEY_SESSION_ID = "paired_session_id"
        private const val KEY_HOST_NAME = "paired_host_name"
        private const val KEY_PAIRED_AT = "paired_at_timestamp"
        private const val KEY_LAST_CONNECTED = "last_connected_timestamp"
    }

    fun getPairingInfo(): PairingInfo? {
        val hostId = prefs.getString(KEY_HOST_ID, null) ?: return null
        val secretKey = prefs.getString(KEY_SECRET_KEY, "") ?: ""
        val sessionId = prefs.getString(KEY_SESSION_ID, hostId) ?: hostId
        val hostName = prefs.getString(KEY_HOST_NAME, "Ubaid Host") ?: "Ubaid Host"
        val pairedAt = prefs.getLong(KEY_PAIRED_AT, System.currentTimeMillis())

        return PairingInfo(
            hostId = hostId,
            secretKey = secretKey,
            sessionId = sessionId,
            hostName = hostName,
            pairedAt = pairedAt
        )
    }

    fun savePairingInfo(info: PairingInfo) {
        prefs.edit()
            .putString(KEY_HOST_ID, info.hostId)
            .putString(KEY_SECRET_KEY, info.secretKey)
            .putString(KEY_SESSION_ID, info.sessionId)
            .putString(KEY_HOST_NAME, info.hostName)
            .putLong(KEY_PAIRED_AT, info.pairedAt)
            .apply()
    }

    fun recordSuccessfulConnection() {
        prefs.edit().putLong(KEY_LAST_CONNECTED, System.currentTimeMillis()).apply()
    }

    fun getLastConnectedTime(): Long {
        return prefs.getLong(KEY_LAST_CONNECTED, 0L)
    }

    fun clearPairing() {
        prefs.edit().clear().apply()
    }
}
