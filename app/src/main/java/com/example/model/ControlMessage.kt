package com.example.model

import org.json.JSONObject

sealed class ControlMessage {
    abstract val type: String
    abstract fun toJson(): String

    data class Tap(
        val x: Float, // Normalized 0.0 .. 1.0 of Ubaid screen
        val y: Float
    ) : ControlMessage() {
        override val type = "tap"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("x", x.toDouble())
            put("y", y.toDouble())
        }.toString()
    }

    data class TouchDown(
        val x: Float,
        val y: Float,
        val pointerId: Int = 0
    ) : ControlMessage() {
        override val type = "touch_down"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("pointerId", pointerId)
        }.toString()
    }

    data class TouchMove(
        val x: Float,
        val y: Float,
        val pointerId: Int = 0
    ) : ControlMessage() {
        override val type = "touch_move"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("pointerId", pointerId)
        }.toString()
    }

    data class TouchUp(
        val x: Float,
        val y: Float,
        val pointerId: Int = 0
    ) : ControlMessage() {
        override val type = "touch_up"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("pointerId", pointerId)
        }.toString()
    }

    data class Key(
        val key: String, // "BACK", "HOME", "RECENTS"
        val keyCode: Int = when (key) {
            "BACK" -> 4
            "HOME" -> 3
            "RECENTS" -> 187
            else -> 0
        }
    ) : ControlMessage() {
        override val type = "key"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("key", key)
            put("keyCode", keyCode)
        }.toString()
    }

    data class Text(
        val text: String
    ) : ControlMessage() {
        override val type = "text"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("text", text)
        }.toString()
    }

    data class Ping(
        val timestamp: Long = System.currentTimeMillis()
    ) : ControlMessage() {
        override val type = "ping"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("timestamp", timestamp)
        }.toString()
    }
}
